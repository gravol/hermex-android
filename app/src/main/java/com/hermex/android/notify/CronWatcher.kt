package com.hermex.android.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hermex.android.service.CronAlarmReceiver
import com.hermex.core.data.auth.KeychainStore
import com.hermex.core.network.DashboardApiClient
import com.hermex.core.network.DebugLog
import com.hermex.core.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Scheduled-alarm cron watcher (v0.1.74) — the "calendar sync" model.
 *
 * Instead of polling the server every minute, the app learns each job's
 * next run time from the API (`next_run_at`), arms a local AlarmManager
 * alarm for shortly after it, wakes ONLY then, checks whether the run
 * finished, notifies, and re-arms for the next occurrence.
 *
 * Triggers to re-sync:
 *  - app start / keepalive service start
 *  - every alarm fire (re-arms everything from fresh data)
 *  - the gateway's `cron.changed` WS broadcast (forwarded by the VM)
 *  - device reboot (CronAlarmReceiver ACTION_BOOT)
 */
object CronWatcher {

    private const val TAG = "CronWatcher"
    private const val PREFS = "cron_watcher"
    private const val KEY_LAST_SEEN = "last_seen_run_"
    private const val KEY_CHECK_COUNT = "check_count_"
    private const val RUN_BUFFER_MS = 120_000L   // alarm at next_run + 2 min (job runtime)
    private const val RE_CHECK_MS = 300_000L     // still-running runs re-checked every 5 min
    private const val MAX_CHECKS_PER_RUN = 24    // 2h patience for a run that never ends
    private const val MIN_SYNC_GAP_MS = 10_000L  // debounce cron.changed storms

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastSyncMs = 0L

    /** Called from anywhere (app start, service, receiver, WS event). Cheap + debounced. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (now - lastSyncMs < MIN_SYNC_GAP_MS) return
        lastSyncMs = now
        scope.launch {
            try {
                if (!ensureAuthenticated(app)) return@launch
                val jobs = DashboardApiClient.cronJobs()
                if (jobs is NetworkResult.Success) {
                    armAlarms(app, jobs.data)
                    catchUpMissedRuns(app, jobs.data)
                }
            } catch (e: Exception) {
                Log.w(TAG, "sync failed: ${e.message}")
            }
        }
    }

    /**
     * v0.1.77: notify for runs that FINISHED but were never reported — e.g. the
     * app was asleep past an alarm, or a job was triggered manually. Bounded:
     * only jobs with next_run within 48h, only runs that started within the
     * last 12h, max 3 notifications per sync.
     */
    private suspend fun catchUpMissedRuns(context: Context, jobs: List<DashboardApiClient.CronJob>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val soon = jobs
            .filter { it.state != "paused" && it.enabled }
            .mapNotNull { job -> job.nextRunAt?.let { parseIso(it) }?.let { job to it } }
            .filter { it.second > now && it.second - now < 48 * 3600_000L }
            .sortedBy { it.second }
            .take(8)
        var notified = 0
        for ((job, _) in soon) {
            if (notified >= 3) break
            val runsResult = DashboardApiClient.cronRuns(job.id, limit = 1)
            if (runsResult !is NetworkResult.Success) continue
            val run = runsResult.data.runs.firstOrNull() ?: continue
            val lastSeen = prefs.getString(KEY_LAST_SEEN + job.id, null)
            if (run.id == lastSeen) continue
            val startedAt = run.startedAt?.let { (it * 1000).toLong() } ?: 0L
            if (startedAt < now - 12 * 3600_000L) continue  // too old to surface
            if (run.endedAt == null) continue               // still running — alarms handle it
            prefs.edit().putString(KEY_LAST_SEEN + job.id, run.id).apply()
            NotificationHelper.postCronRun(context, job.name, run.title, run.id)
            notified++
        }
        if (notified > 0) DebugLog.log("CRON", "Watcher", "catch-up: $notified missed run notification(s)")
    }

    /** A specific job's alarm fired — check its run, notify if finished, re-arm all. */
    fun onAlarm(context: Context, jobId: String) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (!ensureAuthenticated(app)) return@launch
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val runsResult = DashboardApiClient.cronRuns(jobId, limit = 1)
                if (runsResult is NetworkResult.Success) {
                    val run = runsResult.data.runs.firstOrNull()
                    val lastSeen = prefs.getString(KEY_LAST_SEEN + jobId, null)
                    if (run != null && run.id != lastSeen) {
                        val finished = run.endedAt != null
                        val checks = prefs.getInt(KEY_CHECK_COUNT + jobId, 0)
                        if (finished || checks >= MAX_CHECKS_PER_RUN) {
                            prefs.edit()
                                .putString(KEY_LAST_SEEN + jobId, run.id)
                                .remove(KEY_CHECK_COUNT + jobId)
                                .apply()
                            if (finished) {
                                // Job name for the notification
                                val jobsResult = DashboardApiClient.cronJobs()
                                val jobName = (jobsResult as? NetworkResult.Success)
                                    ?.data?.firstOrNull { it.id == jobId }?.name ?: jobId
                                NotificationHelper.postCronRun(app, jobName, run.title, run.id)
                            }
                            // Re-arm everything from fresh data (schedule may have moved)
                            sync(app)
                        } else {
                            // Run started but hasn't finished yet — check again shortly.
                            // CRITICAL (v0.1.77): do NOT sync() here — sync() re-arms
                            // this job's alarm with the SAME request code, clobbering
                            // the re-check alarm (the v0.1.74 bug that killed morning
                            // cron notifications). The re-check arms alone; the
                            // notify/give-up path syncs.
                            prefs.edit().putInt(KEY_CHECK_COUNT + jobId, checks + 1).apply()
                            armOne(app, jobId, System.currentTimeMillis() + RE_CHECK_MS)
                        }
                    } else {
                        // No new run (rescheduled / paused) — re-arm from fresh data
                        sync(app)
                    }
                } else {
                    sync(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "onAlarm failed: ${e.message}")
                sync(app)
            }
        }
    }

    private suspend fun ensureAuthenticated(context: Context): Boolean {
        val url = KeychainStore.getDashboardUrl(context) ?: return false
        val password = KeychainStore.getDashboardPassword(context) ?: return false
        val username = KeychainStore.getDashboardUsername(context) ?: "jeff"
        if (DashboardApiClient.baseUrl() != url) DashboardApiClient.setDashboardUrl(url)
        DashboardApiClient.setPassword(password)
        DashboardApiClient.setUsername(username)
        // Ensure a fresh cookie before REST calls (cheap; 401 re-login also exists)
        when (DashboardApiClient.login(username, password)) {
            is NetworkResult.Success -> return true
            is NetworkResult.HttpError -> {
                // Gateway auth may have changed — try once more, then give up quietly
                return false
            }
            else -> return false
        }
    }

    private fun armAlarms(context: Context, jobs: List<DashboardApiClient.CronJob>) {
        val now = System.currentTimeMillis()
        var armed = 0
        for (job in jobs) {
            if (job.state == "paused" || !job.enabled) continue
            val nextRun = job.nextRunAt?.let { parseIso(it) } ?: continue
            if (nextRun <= now) continue  // stale or mid-run; skip (re-check on next alarm)
            armOne(context, job.id, nextRun + RUN_BUFFER_MS)
            armed++
        }
        DebugLog.log("CRON", "Watcher", "armed $armed alarms from ${jobs.size} jobs")
    }

    private fun armOne(context: Context, jobId: String, fireAtMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CronAlarmReceiver::class.java).apply {
            action = CronAlarmReceiver.ACTION_ALARM
            putExtra(CronAlarmReceiver.EXTRA_JOB_ID, jobId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            jobId.hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setAndAllowWhileIdle: inexact but fires in Doze — no exact-alarm permission needed
        runCatching { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi) }
    }

    private fun parseIso(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
}
