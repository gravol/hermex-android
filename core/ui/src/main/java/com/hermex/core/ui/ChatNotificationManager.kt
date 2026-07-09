// File: app/src/main/java/com/hermex/core/service/HermesForegroundService.kt

package com.hermex.core.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hermex.ui.MainActivity
import com.hermex.R
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A ForegroundService that maintains a long-running SSE connection.
 * Ensures the response stream is not interrupted by the OS when the app is backgrounded.
 */
class HermesForegroundService : Service() {

    private var client: OkHttpClient? = null
    private var call: Call? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val notificationId = 8080
    private val CHANNEL_ID = "hermex_stream_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Streaming response..."))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Starts the streaming process.
     * @param url The SSE endpoint URL
     * @param chatId The chat ID to display in the notification
     */
    fun startStream(url: String, chatId: String) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Connection", "keep-alive")
            .build()

        call = client?.newCall(request)
        
        scope.launch {
            try {
                call?.execute()?.use { response ->
                    if (!response.isSuccessful) {
                        stopSelf()
                        return@launch
                    }

                    response.body?.string()?.use { body ->
                        // In a real SSE scenario, we parse line by line.
                        // Here we simulate the continuous reading logic.
                        // Note: For true SSE, use response.body?.source() and readLine()
                        
                        // Mocking the stream processing loop
                        body.lines().forEach { line ->
                            if (isCancelled) {
                                call?.cancel()
                                return@forEach
                            }
                            
                            // Handle SSE "data:" lines here
                            // Example: processEvent(line, chatId)
                            
                            // Update notification text to show progress
                            val notification = buildNotification("Streaming: ${line.take(20)}...")
                            updateNotification(notification)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                updateNotification(buildNotification("Connection lost. Retrying..."))
                // Implement retry logic or stop service
            } finally {
                // Cleanup
                call?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Hermex Streaming",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hermex_notification)
            .setContentTitle("Hermex AI")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Prevents swiping away
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        client?.dispatcher?.executorService?.shutdown()
    }
}