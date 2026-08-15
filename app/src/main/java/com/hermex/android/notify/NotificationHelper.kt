package com.hermex.android.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hermex.android.MainActivity
import java.net.URLEncoder

/**
 * Local notifications for Hermex (v0.1.74).
 *
 * Two channels:
 *  - "turns" — a chat turn finished while you weren't looking at it
 *  - "cron"  — a scheduled cron job finished running
 *
 * Tapping either deep-links into the app: MainActivity receives
 * `open_session` (the session key — a chat session or a cron run session)
 * and navigates to chat/{sessionKey}.
 */
object NotificationHelper {

    const val CHANNEL_TURNS = "turns"
    const val CHANNEL_CRON = "cron"
    const val CHANNEL_ALERTS = "alerts"
    const val EXTRA_OPEN_SESSION = "open_session"
    const val EXTRA_OPEN_TITLE = "open_title"

    private const val ID_TURNS = 1001
    private const val ID_CRON = 1002
    private const val ID_APPROVAL = 1003

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TURNS, "Turn finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when a chat turn finishes while you're away"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CRON, "Cron jobs", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when scheduled cron jobs finish"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Approval requests and urgent notices"
                enableVibration(true)
            }
        )
    }

    private fun openSessionIntent(context: Context, sessionKey: String, title: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SESSION, sessionKey)
            putExtra(EXTRA_OPEN_TITLE, title)
        }
        return PendingIntent.getActivity(
            context,
            sessionKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** A chat turn finished while the chat screen wasn't visible. */
    fun postTurnFinished(context: Context, sessionKey: String, sessionTitle: String, preview: String) {
        ensureChannels(context)
        val title = sessionTitle.ifBlank { "Hermes" }
        val text = preview.take(200).ifBlank { "Turn finished" }
        val notification = NotificationCompat.Builder(context, CHANNEL_TURNS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openSessionIntent(context, sessionKey, title))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_TURNS, notification) }
    }

    /** A cron job produced a finished run. Tap opens the run's session. */
    fun postCronRun(
        context: Context,
        jobName: String,
        runTitle: String?,
        runId: String,
        output: String? = null,
        missedLabel: String? = null,
    ) {
        ensureChannels(context)
        val title = "Cron: ${jobName.ifBlank { "job" }}"
        val text = missedLabel.orEmpty() + (output?.take(400)?.ifBlank { null }
            ?: runTitle?.take(120)
            ?: "Run finished")
        val notification = NotificationCompat.Builder(context, CHANNEL_CRON)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openSessionIntent(context, runId, text))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_CRON, notification) }
    }

    /** Approval request while the user isn't watching the chat (v0.1.84). */
    fun postApproval(context: Context, sessionKey: String, toolName: String, args: String) {
        ensureChannels(context)
        val text = if (args.isBlank()) "Command needs your approval"
        else "Approval needed: $toolName — ${args.take(120)}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Approval needed")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openSessionIntent(context, sessionKey, "Approval needed"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_APPROVAL, notification) }
    }

    /** Deep-link route helper: chat/{sessionKey}/{encodedTitle} */
    fun chatRoute(sessionKey: String, title: String): String {
        val encoded = URLEncoder.encode(title.ifBlank { sessionKey }, "UTF-8")
        return "chat/$sessionKey/$encoded"
    }
}
