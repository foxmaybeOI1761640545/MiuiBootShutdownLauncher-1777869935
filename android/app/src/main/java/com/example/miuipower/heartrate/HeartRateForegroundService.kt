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
    private lateinit var autoController: AutoHeartRateController
    private var removeSink: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        manager = HeartRateEnvironment.manager(applicationContext)
        autoController = HeartRateEnvironment.autoController(applicationContext)
        removeSink = HeartRateEnvironment.addEventSink { eventName, data, _ ->
            if (eventName == "heartRateStateChanged" ||
                eventName == "autoHeartRateStateChanged" ||
                eventName == "heartRateUploadQueueChanged"
            ) {
                val notificationState = notificationState()
                updateNotification(notificationState)
                if (!notificationState.optBoolean("serviceRunning", false) &&
                    !notificationState.optBoolean("autoEnabled", false)
                ) {
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
            ACTION_START_AUTO -> {
                manager.markForegroundServiceRunning(visible = true)
                autoController.markServiceStarted()
                autoController.enable()
                startForegroundCompat(notificationState())
            }
            ACTION_STOP_AUTO -> {
                autoController.disable()
                autoController.markServiceStopped()
                manager.markForegroundServiceStopped()
                stopForegroundCompat()
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_RETRY_UPLOAD -> {
                autoController.retryUploadNow()
                startForegroundCompat(notificationState())
            }
            ACTION_START_RECORDING -> {
                manager.markForegroundServiceRunning(visible = true)
                startForegroundCompat(notificationState())
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
        autoController.markServiceStopped()
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

    private fun notificationState(): JSObject {
        val state = manager.getStateJson()
        val autoState = autoController.getStateJson()
        if (autoState.optBoolean("enabled", false)) {
            state.put("autoEnabled", true)
            state.put("autoCurrentChunkRows", autoState.optInt("currentChunkRows", 0))
            state.put("autoChunkSize", autoState.optInt("chunkSize", 1_200))
            state.put("autoPendingUploadChunks", autoState.optInt("pendingUploadChunks", 0))
            if (autoState.optBoolean("scanning", false)) {
                state.put("status", HeartRateBleManager.STATUS_SCANNING)
            }
        } else {
            state.put("autoEnabled", false)
        }
        return state
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
        const val ACTION_START_AUTO = "com.example.miuipower.heartrate.START_AUTO"
        const val ACTION_STOP_AUTO = "com.example.miuipower.heartrate.STOP_AUTO"
        const val ACTION_RETRY_UPLOAD = "com.example.miuipower.heartrate.RETRY_UPLOAD"
    }
}
