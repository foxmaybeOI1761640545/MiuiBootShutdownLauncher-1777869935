package com.example.miuipower.heartrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.miuipower.MainActivity
import com.getcapacitor.JSObject

object HeartRateNotification {
    const val NOTIFICATION_ID = 10705
    const val UPLOAD_FAILURE_NOTIFICATION_ID = 10706
    private const val CHANNEL_ID = "heart_rate_recording"
    private const val CHANNEL_NAME = "Heart rate recording"
    private const val ALERT_CHANNEL_ID = "heart_rate_alerts"
    private const val ALERT_CHANNEL_NAME = "Heart rate alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Heart-rate belt recording status"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Heart-rate upload failure alerts"
            setShowBadge(true)
        }
        manager.createNotificationChannel(alertChannel)
    }

    fun build(context: Context, state: JSObject): Notification {
        ensureChannel(context)
        val autoEnabled = state.optBoolean("autoEnabled", false)
        val stopIntent = Intent(context, HeartRateForegroundService::class.java).apply {
            action = if (autoEnabled) {
                HeartRateForegroundService.ACTION_STOP_AUTO
            } else {
                HeartRateForegroundService.ACTION_STOP_RECORDING
            }
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val retryIntent = Intent(context, HeartRateForegroundService::class.java).apply {
            action = HeartRateForegroundService.ACTION_RETRY_UPLOAD
        }
        val retryPendingIntent = PendingIntent.getService(
            context,
            3,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = createOpenAppPendingIntent(context)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(if (autoEnabled) "Heart rate auto recording" else "Heart rate recording")
            .setContentText(contentText(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(0, if (autoEnabled) "Stop Auto" else "Stop Recording", stopPendingIntent)
            .addAction(0, "Retry Upload", retryPendingIntent)
            .build()
    }

    fun showUploadFailure(context: Context, payload: JSObject) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("GitHub upload failed")
            .setContentText("${payload.optInt("consecutiveUploadFailures", 3)} consecutive failures. Tap to view details.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent(context))
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(UPLOAD_FAILURE_NOTIFICATION_ID, notification)
    }

    private fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        intent.apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("openPage", "run")
            putExtra("fromNotification", true)
        }

        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentText(state: JSObject): String {
        val status = state.optString("status", "idle")
        val autoEnabled = state.optBoolean("autoEnabled", false)
        val device = state.optJSONObject("device")
        val latestSample = state.optJSONObject("latestSample")
        val deviceName = device?.optString("name")?.takeIf { it.isNotBlank() }
            ?: device?.optString("address")?.takeIf { it.isNotBlank() }
            ?: if (autoEnabled) "10705-1" else "heart-rate belt"
        val bpm = latestSample?.optInt("bpm", 0)?.takeIf { it > 0 }
        val battery = state.optInt("batteryLevel", -1).takeIf { it >= 0 }
        val samples = state.optInt("sampleCount", 0)
        val reconnectDelay = state.optLong("nextReconnectDelayMs", 0L).takeIf { it > 0L }

        return when (status) {
            HeartRateBleManager.STATUS_SCANNING -> "Looking for $deviceName"
            HeartRateBleManager.STATUS_CONNECTING -> "Connecting to $deviceName..."
            HeartRateBleManager.STATUS_RECONNECTING -> {
                val seconds = reconnectDelay?.let { (it / 1000L).coerceAtLeast(1L) }
                if (seconds == null) "Reconnecting to $deviceName..." else "Reconnecting in ${seconds}s..."
            }
            HeartRateBleManager.STATUS_BLUETOOTH_OFF -> "Bluetooth is off"
            HeartRateBleManager.STATUS_PERMISSION_REQUIRED -> "Permission required"
            HeartRateBleManager.STATUS_ERROR -> state.optString("error", "Heart-rate recording needs attention")
            else -> {
                val bpmText = bpm?.let { "$it BPM" } ?: "waiting for BPM"
                val batteryText = battery?.let { " | Battery $it%" } ?: ""
                val chunkText = if (autoEnabled) {
                    " | chunk ${state.optInt("autoCurrentChunkRows", 0)}/${state.optInt("autoChunkSize", 1200)}"
                } else {
                    ""
                }
                val queueText = if (autoEnabled) {
                    " | queue ${state.optInt("autoPendingUploadChunks", 0)}"
                } else {
                    ""
                }
                "$deviceName | $bpmText$batteryText | $samples samples$chunkText$queueText"
            }
        }
    }
}
