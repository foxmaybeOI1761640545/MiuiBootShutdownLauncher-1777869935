package com.example.miuipower.heartrate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONObject

class AutoHeartRateController(
    private val context: Context,
    private val manager: HeartRateBleManager,
    private val chunkWriter: HeartRateChunkWriter,
    private val uploadQueue: HeartRateUploadQueue,
    private val emit: (eventName: String, data: JSObject, retain: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var serviceRunning = false
    private var scanning = false
    private var connecting = false
    private var scanLoopStartedAtMs = 0L
    private var reconnectAttempt = 0
    private var scanRunnable: Runnable? = null
    private var lastTargetDevice: HeartRateDevice? = null
    private var lastError = ""

    init {
        manager.setAutoSampleSink { sample ->
            handleAutoSample(sample)
        }
    }

    @Synchronized
    fun getSettings(): AutoHeartRateSettings = AutoHeartRateSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        targetDeviceName = preferences.getString(KEY_TARGET_NAME, TargetDeviceMatcher.TARGET_NAME)
            ?.takeIf { it.isNotBlank() } ?: TargetDeviceMatcher.TARGET_NAME,
        targetDeviceAddress = preferences.getString(KEY_TARGET_ADDRESS, null)?.takeIf { it.isNotBlank() },
        chunkSize = preferences.getInt(KEY_CHUNK_SIZE, 1_200).coerceAtLeast(1),
        overlapRows = preferences.getInt(KEY_OVERLAP_ROWS, 10).coerceAtLeast(0),
        uploadCsv = preferences.getBoolean(KEY_UPLOAD_CSV, true),
        uploadJsonl = preferences.getBoolean(KEY_UPLOAD_JSONL, true),
        deleteLocalAfterUpload = preferences.getBoolean(KEY_DELETE_LOCAL, true),
        retryIntervalMs = preferences.getLong(KEY_RETRY_INTERVAL_MS, 60_000L).coerceAtLeast(5_000L),
        failureNotifyThreshold = preferences.getInt(KEY_FAILURE_THRESHOLD, 3).coerceAtLeast(1),
    )

    @Synchronized
    fun saveSettings(options: JSObject): JSObject {
        val current = getSettings()
        preferences.edit()
            .putString(KEY_TARGET_NAME, options.optString("targetDeviceName", current.targetDeviceName).ifBlank { TargetDeviceMatcher.TARGET_NAME })
            .putString(KEY_TARGET_ADDRESS, options.optString("targetDeviceAddress", current.targetDeviceAddress ?: ""))
            .putInt(KEY_CHUNK_SIZE, options.optInt("chunkSize", current.chunkSize).coerceAtLeast(1))
            .putInt(KEY_OVERLAP_ROWS, options.optInt("overlapRows", current.overlapRows).coerceAtLeast(0))
            .putBoolean(KEY_UPLOAD_CSV, options.optBoolean("uploadCsv", current.uploadCsv))
            .putBoolean(KEY_UPLOAD_JSONL, options.optBoolean("uploadJsonl", current.uploadJsonl))
            .putBoolean(KEY_DELETE_LOCAL, options.optBoolean("deleteLocalAfterUpload", current.deleteLocalAfterUpload))
            .putLong(KEY_RETRY_INTERVAL_MS, options.optLong("retryIntervalMs", current.retryIntervalMs).coerceAtLeast(5_000L))
            .putInt(KEY_FAILURE_THRESHOLD, options.optInt("failureNotifyThreshold", current.failureNotifyThreshold).coerceAtLeast(1))
            .apply()
        emitState()
        return result(true, "auto_heart_rate_settings_saved").apply {
            put("settings", getSettings().toJson())
        }
    }

    fun markServiceStarted() {
        synchronized(this) {
            serviceRunning = true
            lastError = ""
        }
        emitState()
        if (getSettings().enabled) {
            startLoop()
        }
    }

    fun markServiceStopped() {
        synchronized(this) {
            serviceRunning = false
            scanning = false
            connecting = false
            cancelScanLocked()
        }
        emitState()
    }

    fun enable(): JSObject {
        preferences.edit().putBoolean(KEY_ENABLED, true).apply()
        emitState()
        startLoop()
        return result(true, "auto_heart_rate_enabled").apply {
            put("state", getStateJson())
        }
    }

    fun disable(): JSObject {
        preferences.edit().putBoolean(KEY_ENABLED, false).apply()
        synchronized(this) {
            cancelScanLocked()
            scanning = false
            connecting = false
            lastError = ""
        }
        manager.stopAutoRecording {
            // State is emitted by manager callbacks and below.
        }
        emitState()
        return result(true, "auto_heart_rate_disabled").apply {
            put("state", getStateJson())
        }
    }

    fun retryUploadNow(): JSObject {
        uploadQueue.trigger()
        return result(true, "auto_upload_retry_requested").apply {
            put("queue", uploadQueue.getStateJson())
        }
    }

    fun getUploadQueueState(): JSObject = uploadQueue.getStateJson()

    fun clearUploadedLocalChunks(): JSObject = chunkWriter.clearUploadedLocalChunks()

    fun storageStats(): JSObject = chunkWriter.storageStats()

    fun handleHeartRateStateChanged(state: JSObject) {
        if (!getSettings().enabled || !serviceRunning) {
            return
        }
        val status = state.optString("status", "")
        if (state.optBoolean("autoRecordingActive", false) || status == HeartRateBleManager.STATUS_RECORDING) {
            synchronized(this) {
                scanning = false
                connecting = false
                reconnectAttempt = 0
                lastError = ""
            }
            emitState()
            return
        }
        if (status == HeartRateBleManager.STATUS_DISCONNECTED ||
            status == HeartRateBleManager.STATUS_IDLE ||
            status == HeartRateBleManager.STATUS_ERROR ||
            status == HeartRateBleManager.STATUS_BLUETOOTH_OFF
        ) {
            synchronized(this) {
                connecting = false
                lastError = state.optString("error", lastError)
            }
            scheduleNextScan(if (status == HeartRateBleManager.STATUS_BLUETOOTH_OFF) 10_000L else 3_000L)
            emitState()
        }
    }

    fun getStateJson(): JSObject {
        val settings = getSettings()
        val managerState = manager.getStateJson()
        val queue = uploadQueue.getStateJson()
        val storage = chunkWriter.storageStats()
        val bluetoothOn = isBluetoothOn()
        return JSObject().apply {
            put("enabled", settings.enabled)
            put("targetName", settings.targetDeviceName)
            if (settings.targetDeviceAddress.isNullOrBlank()) {
                put("targetAddress", JSONObject.NULL)
            } else {
                put("targetAddress", settings.targetDeviceAddress)
            }
            put("serviceRunning", serviceRunning)
            put("bluetoothOn", bluetoothOn)
            put("scanning", scanning)
            put("connecting", connecting || managerState.optString("status") == HeartRateBleManager.STATUS_CONNECTING)
            put("connected", managerState.optJSONObject("device") != null && managerState.optString("status") != HeartRateBleManager.STATUS_DISCONNECTED)
            put("recording", managerState.optBoolean("autoRecordingActive", false))
            put("currentChunkRows", storage.optInt("autoCurrentChunkRows", 0))
            put("chunkSize", settings.chunkSize)
            put("overlapRows", settings.overlapRows)
            put("pendingUploadChunks", queue.optInt("pendingChunks", 0))
            put("uploadRunning", queue.optBoolean("uploadRunning", false))
            put("consecutiveUploadFailures", queue.optInt("consecutiveUploadFailures", 0))
            put("lastThreeUploadErrors", queue.optJSONArray("lastThreeUploadErrors") ?: JSArray())
            put("lastUploadError", lastError)
            put("lastTargetDevice", lastTargetDevice?.toJson() ?: JSONObject.NULL)
            put("lastNativeUpdateMs", System.currentTimeMillis())
            put("managerStatus", managerState.optString("status", "idle"))
        }
    }

    private fun startLoop() {
        val settings = getSettings()
        if (!settings.enabled) {
            return
        }
        synchronized(this) {
            if (!serviceRunning) {
                lastError = "Auto Mode requires HeartRateForegroundService."
                emitState()
                return
            }
            if (scanLoopStartedAtMs == 0L) {
                scanLoopStartedAtMs = System.currentTimeMillis()
            }
            cancelScanLocked()
        }
        scheduleNextScan(0L)
        uploadQueue.trigger()
    }

    private fun scheduleNextScan(delayMs: Long) {
        synchronized(this) {
            if (!getSettings().enabled || !serviceRunning) {
                return
            }
            cancelScanLocked()
            scanRunnable = Runnable {
                scanRunnable = null
                runScanPass()
            }
            mainHandler.postDelayed(scanRunnable!!, delayMs)
        }
    }

    private fun runScanPass() {
        val settings = getSettings()
        if (!settings.enabled || !serviceRunning) {
            return
        }
        if (!hasBlePermissions()) {
            synchronized(this) {
                scanning = false
                connecting = false
                lastError = "Bluetooth permission required."
            }
            emitState()
            scheduleNextScan(30_000L)
            return
        }
        if (!isBluetoothOn()) {
            synchronized(this) {
                scanning = false
                connecting = false
                lastError = "Bluetooth is off."
            }
            emitState()
            scheduleNextScan(10_000L)
            return
        }
        if (manager.isAutoRecordingActive()) {
            synchronized(this) {
                scanning = false
                connecting = false
                reconnectAttempt = 0
            }
            emitState()
            return
        }

        synchronized(this) {
            scanning = true
            connecting = false
            lastError = ""
        }
        emitState()
        manager.scan(10_000L) { result ->
            mainHandler.post {
                handleScanComplete(result)
            }
        }
    }

    private fun handleScanComplete(result: JSObject) {
        val settings = getSettings()
        if (!settings.enabled || !serviceRunning) {
            return
        }
        val devices = result.optJSONArray("devices") ?: JSArray()
        val target = (0 until devices.length())
            .mapNotNull { index -> devices.optJSONObject(index) }
            .firstOrNull { TargetDeviceMatcher.matches(JSObject(it.toString()), settings) }

        if (target == null) {
            synchronized(this) {
                scanning = false
                connecting = false
                lastError = "Target ${settings.targetDeviceName} not found."
            }
            emitState()
            scheduleNextScan(scanRestMs())
            return
        }

        val device = HeartRateDevice(
            address = target.optString("address", ""),
            name = target.optString("name", ""),
            rssi = target.optInt("rssi", 0),
            lastSeenMs = target.optLong("lastSeenMs", System.currentTimeMillis()),
            remembered = target.optBoolean("remembered", false),
            heartRateServiceAdvertised = target.optBoolean("heartRateServiceAdvertised", false),
        )
        synchronized(this) {
            scanning = false
            connecting = true
            lastTargetDevice = device
            lastError = ""
        }
        emitState()
        manager.startAutoConnectRecording(device.address, device.name) {
            reconnectAttempt += 1
        }
    }

    private fun handleAutoSample(sample: HeartRateSample) {
        val settings = getSettings()
        if (!settings.enabled || !serviceRunning) {
            return
        }
        val closedChunk = chunkWriter.appendSample(sample, settings)
        if (closedChunk != null) {
            uploadQueue.enqueueClosedChunk(closedChunk)
        }
        emitState()
    }

    private fun scanRestMs(): Long {
        val elapsed = System.currentTimeMillis() - scanLoopStartedAtMs
        return when {
            elapsed < 2 * 60_000L -> 5_000L
            elapsed < 10 * 60_000L -> 20_000L
            else -> 60_000L
        }
    }

    private fun cancelScanLocked() {
        scanRunnable?.let { mainHandler.removeCallbacks(it) }
        scanRunnable = null
    }

    private fun emitState() {
        emit("autoHeartRateStateChanged", getStateJson(), true)
    }

    private fun isBluetoothOn(): Boolean {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    private fun hasBlePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun result(ok: Boolean, method: String): JSObject = JSObject().apply {
        put("ok", ok)
        put("method", method)
    }

    companion object {
        private const val PREFS_NAME = "heart_rate_auto.v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TARGET_NAME = "target_name"
        private const val KEY_TARGET_ADDRESS = "target_address"
        private const val KEY_CHUNK_SIZE = "chunk_size"
        private const val KEY_OVERLAP_ROWS = "overlap_rows"
        private const val KEY_UPLOAD_CSV = "upload_csv"
        private const val KEY_UPLOAD_JSONL = "upload_jsonl"
        private const val KEY_DELETE_LOCAL = "delete_local"
        private const val KEY_RETRY_INTERVAL_MS = "retry_interval_ms"
        private const val KEY_FAILURE_THRESHOLD = "failure_threshold"
    }
}
