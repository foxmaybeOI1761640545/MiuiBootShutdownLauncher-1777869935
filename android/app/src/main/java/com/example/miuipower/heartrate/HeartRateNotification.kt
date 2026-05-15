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
    private const val CHANNEL_ID = "heart_rate_recording"
    private const val CHANNEL_NAME = "Heart rate recording"

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
    }

    fun build(context: Context, state: JSObject): Notification {
        ensureChannel(context)
        val stopIntent = Intent(context, HeartRateForegroundService::class.java).apply {
            action = HeartRateForegroundService.ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Heart rate recording")
            .setContentText(contentText(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop Recording", stopPendingIntent)
            .build()
    }

    private fun contentText(state: JSObject): String {
        val status = state.optString("status", "idle")
        val device = state.optJSONObject("device")
        val latestSample = state.optJSONObject("latestSample")
        val deviceName = device?.optString("name")?.takeIf { it.isNotBlank() }
            ?: device?.optString("address")?.takeIf { it.isNotBlank() }
            ?: "heart-rate belt"
        val bpm = latestSample?.optInt("bpm", 0)?.takeIf { it > 0 }
        val battery = state.optInt("batteryLevel", -1).takeIf { it >= 0 }
        val samples = state.optInt("sampleCount", 0)
        val reconnectDelay = state.optLong("nextReconnectDelayMs", 0L).takeIf { it > 0L }

        return when (status) {
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
                val batteryText = battery?.let { " · Battery $it%" } ?: ""
                "$deviceName · $bpmText$batteryText · $samples samples"
            }
        }
    }
}
