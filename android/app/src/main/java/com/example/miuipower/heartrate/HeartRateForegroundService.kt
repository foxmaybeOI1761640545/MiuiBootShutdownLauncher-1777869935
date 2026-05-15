package com.example.miuipower.heartrate

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.getcapacitor.JSObject

class HeartRateForegroundService : Service() {
    private lateinit var manager: HeartRateBleManager
    private var removeSink: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        manager = HeartRateEnvironment.manager(applicationContext)
        removeSink = HeartRateEnvironment.addEventSink { eventName, data, _ ->
            if (eventName == "heartRateStateChanged") {
                updateNotification(data)
                if (!data.optBoolean("serviceRunning", false)) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START_RECORDING) {
            ACTION_STOP_RECORDING -> {
                manager.stopForegroundRecording {
                    stopForegroundCompat()
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_START_RECORDING -> {
                manager.markForegroundServiceRunning(visible = true)
                startForegroundCompat(manager.getStateJson())
                manager.startForegroundRecording { result ->
                    if (!result.optBoolean("ok", false)) {
                        Log.w(TAG, "Heart-rate foreground start failed: ${result.optString("method")}")
                        manager.markForegroundServiceStopped()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeSink?.invoke()
        removeSink = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(state: JSObject) {
        val notification = HeartRateNotification.build(this, state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                HeartRateNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(HeartRateNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: JSObject) {
        val notification = HeartRateNotification.build(this, state)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(HeartRateNotification.NOTIFICATION_ID, notification)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "HeartRateForegroundService"
        const val ACTION_START_RECORDING = "com.example.miuipower.heartrate.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.miuipower.heartrate.STOP_RECORDING"
    }
}
