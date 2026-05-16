package com.example.miuipower.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.util.UUID

class HeartRateBleManager(
    private val context: Context,
    private val storage: HeartRateStorage,
    private val emit: (eventName: String, data: JSObject, retain: Boolean) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val devices = linkedMapOf<String, HeartRateDevice>()

    private var status = STATUS_IDLE
    private var connectedDevice: HeartRateDevice? = null
    private var latestSample: HeartRateSample? = null
    private var sampleCount = 0
    private var errorText = ""
    private var recording = false
    private var currentSessionId = "live"
    private var bluetoothGatt: BluetoothGatt? = null
    private var bodySensorLocation: String? = null
    private var batteryLevel: Int? = null
    private var pendingScanCallback: ScanCallback? = null
    private var pendingScanCompletion: ((JSObject) -> Unit)? = null
    private var scanUsesGenericFallback = false
    private var sawDeviceInScanPhase = false
    private var serviceRunning = false
    private var foregroundNotificationVisible = false
    private var autoReconnectEnabled = preferences.getBoolean(KEY_AUTO_RECONNECT, true)
    private var reconnectAttempt = 0
    private var nextReconnectDelayMs: Long? = null
    private var pendingRecordingStart = false
    private var manualStopRequested = false
    private var reconnectRunnable: Runnable? = null

    fun getStateJson(): JSObject = synchronized(this) {
        buildStateJson()
    }

    fun getLastDeviceJson(): JSObject {
        val device = getLastDevice()
        return JSObject().apply {
            put("device", device?.toJson() ?: JSONObject.NULL)
        }
    }

    fun hasRecordingStartTarget(): Boolean = synchronized(this) {
        connectedDevice != null || getLastDevice() != null
    }

    fun isRecordingActive(): Boolean = synchronized(this) {
        recording || serviceRunning || pendingRecordingStart
    }

    fun markForegroundServiceRunning(visible: Boolean) {
        synchronized(this) {
            serviceRunning = true
            foregroundNotificationVisible = visible
            errorText = ""
        }
        emitState()
    }

    fun markForegroundServiceStopped() {
        synchronized(this) {
            serviceRunning = false
            foregroundNotificationVisible = false
            pendingRecordingStart = false
        }
        emitState()
    }

    fun setAutoReconnect(enabled: Boolean): JSObject {
        synchronized(this) {
            autoReconnectEnabled = enabled
            preferences.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
            if (!enabled) {
                cancelReconnectLocked()
            }
        }
        emitState()
        return operationResult(true, if (enabled) "auto_reconnect_enabled" else "auto_reconnect_disabled")
    }

    fun exportHistory(context: Context, format: String, sinceMs: Long?, untilMs: Long?): JSObject {
        return storage.exportHistory(context, format, sinceMs, untilMs)
    }

    fun getLastExport(context: Context): JSObject {
        return storage.getLastExport(context)
    }

    fun getOrCreateExport(context: Context, format: String): HeartRateExportFile {
        return storage.getOrCreateExport(context, format)
    }

    fun storageStats(context: Context): JSObject {
        return storage.storageStats(context)
    }

    @SuppressLint("MissingPermission")
    fun scan(durationMs: Long, onComplete: (JSObject) -> Unit) {
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            setErrorState(STATUS_ERROR, "Bluetooth is unavailable")
            onComplete(devicesResult())
            return
        }
        if (!adapter.isEnabled) {
            setErrorState(STATUS_BLUETOOTH_OFF, "Bluetooth is off")
            onComplete(devicesResult())
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            setErrorState(STATUS_ERROR, "BLE scanner is unavailable")
            onComplete(devicesResult())
            return
        }

        stopScan(resolvePending = false)
        synchronized(this) {
            devices.clear()
            status = STATUS_SCANNING
            errorText = ""
            scanUsesGenericFallback = false
            sawDeviceInScanPhase = false
            pendingScanCompletion = onComplete
        }
        emitState()

        val safeDuration = durationMs.coerceIn(2_000L, 15_000L)
        startScanPhase(scanner, safeDuration, genericFallback = false)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String?, onComplete: (JSObject) -> Unit) {
        val normalizedAddress = address.trim()
        if (normalizedAddress.isEmpty()) {
            onComplete(operationResult(false, "invalid_args", "Missing device address"))
            return
        }

        val adapter = bluetoothAdapter()
        if (adapter == null) {
            setErrorState(STATUS_ERROR, "Bluetooth is unavailable")
            onComplete(operationResult(false, "bluetooth_unavailable"))
            return
        }
        if (!adapter.isEnabled) {
            setErrorState(STATUS_BLUETOOTH_OFF, "Bluetooth is off")
            onComplete(operationResult(false, STATUS_BLUETOOTH_OFF))
            return
        }

        stopScan(resolvePending = true)
        disconnectInternal(resetState = false)

        val device = runCatching { adapter.getRemoteDevice(normalizedAddress) }.getOrNull()
        if (device == null) {
            setErrorState(STATUS_ERROR, "Invalid Bluetooth address")
            onComplete(operationResult(false, "invalid_address"))
            return
        }

        val displayDevice = HeartRateDevice(
            address = normalizedAddress,
            name = name?.takeIf { it.isNotBlank() } ?: knownDeviceName(device),
            remembered = false,
        )
        synchronized(this) {
            cancelReconnectLocked()
            connectedDevice = displayDevice
            status = STATUS_CONNECTING
            errorText = ""
            bodySensorLocation = null
            batteryLevel = null
            manualStopRequested = false
        }
        emitState()

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, gattCallback)
            }
            if (bluetoothGatt == null) {
                setErrorState(STATUS_ERROR, "connectGatt returned null")
                onComplete(operationResult(false, "connect_failed"))
                return
            }
            mainHandler.postDelayed({
                val currentStatus = synchronized(this) { status }
                if (currentStatus == STATUS_CONNECTING) {
                    setErrorState(STATUS_ERROR, "Connection timed out")
                }
            }, CONNECT_TIMEOUT_MS)
            onComplete(operationResult(true, "connecting"))
        } catch (error: SecurityException) {
            setErrorState(STATUS_PERMISSION_REQUIRED, error.message ?: "Bluetooth permission required")
            onComplete(operationResult(false, STATUS_PERMISSION_REQUIRED, error.message))
        } catch (error: Exception) {
            setErrorState(STATUS_ERROR, error.message ?: "Connect failed")
            onComplete(operationResult(false, "connect_failed", error.message))
        }
    }

    fun disconnect(onComplete: (JSObject) -> Unit) {
        synchronized(this) {
            manualStopRequested = true
            pendingRecordingStart = false
            cancelReconnectLocked()
        }
        disconnectInternal(resetState = true)
        onComplete(operationResult(true, "disconnect"))
    }

    fun startRecording(onComplete: (JSObject) -> Unit) {
        startForegroundRecording(onComplete)
    }

    fun startForegroundRecording(onComplete: (JSObject) -> Unit) {
        var deviceToConnect: HeartRateDevice? = null
        synchronized(this) {
            serviceRunning = true
            foregroundNotificationVisible = true
            manualStopRequested = false
            cancelReconnectLocked()

            if (connectedDevice != null && (status == STATUS_CONNECTED || status == STATUS_RECORDING)) {
                beginRecordingLocked()
                emitState()
                onComplete(operationResult(true, "recording_started").apply {
                    put("sessionId", currentSessionId)
                })
                return
            }

            deviceToConnect = getLastDevice()
            if (deviceToConnect == null) {
                serviceRunning = false
                foregroundNotificationVisible = false
                pendingRecordingStart = false
                onComplete(operationResult(false, "no_last_device", "Connect a heart-rate device before starting background recording"))
                emitState()
                return
            }

            pendingRecordingStart = true
            status = STATUS_CONNECTING
            errorText = ""
        }
        emitState()
        val target = deviceToConnect ?: return
        connect(target.address, target.name) {
            onComplete(operationResult(true, "foreground_service_connecting"))
        }
    }

    fun stopRecording(onComplete: (JSObject) -> Unit) {
        stopForegroundRecording(onComplete)
    }

    fun stopForegroundRecording(onComplete: (JSObject) -> Unit) {
        val sessionToStop: String?
        val samplesToStop: Int
        synchronized(this) {
            manualStopRequested = true
            cancelReconnectLocked()
            status = STATUS_STOPPING
            sessionToStop = if (recording && currentSessionId != "live") currentSessionId else null
            samplesToStop = sampleCount
            recording = false
            pendingRecordingStart = false
            serviceRunning = false
            foregroundNotificationVisible = false
            nextReconnectDelayMs = null
            reconnectAttempt = 0
        }
        emitState()
        if (sessionToStop != null) {
            storage.appendSessionStop(sessionToStop, samplesToStop)
        }
        disconnectInternal(resetState = true)
        onComplete(operationResult(true, "recording_stopped").apply {
            put("sessionId", currentSessionId)
        })
    }

    private fun beginRecordingLocked() {
        if (!recording) {
            currentSessionId = storage.newSessionId()
            sampleCount = 0
            storage.appendSessionStart(currentSessionId, connectedDevice)
        }
        recording = true
        pendingRecordingStart = false
        serviceRunning = true
        foregroundNotificationVisible = true
        status = STATUS_RECORDING
        reconnectAttempt = 0
        nextReconnectDelayMs = null
        errorText = ""
    }

    fun readHistory(limit: Int, sinceMs: Long?): JSObject = JSObject().apply {
        put("samples", storage.readSamples(limit, sinceMs))
    }

    fun clearHistory(): JSObject {
        if (isRecordingActive()) {
            return operationResult(false, "recording_active", "Stop recording before clearing heart-rate data.")
        }
        storage.clearHistory()
        synchronized(this) {
            sampleCount = 0
        }
        emitState()
        return operationResult(true, "history_cleared")
    }

    fun clearExportCache(context: Context): JSObject {
        if (isRecordingActive()) {
            return operationResult(false, "recording_active", "Stop recording before clearing heart-rate data.")
        }
        storage.clearExportCache(context)
        return operationResult(true, "export_cache_cleared")
    }

    fun clearAll(context: Context): JSObject {
        if (isRecordingActive()) {
            return operationResult(false, "recording_active", "Stop recording before clearing heart-rate data.")
        }
        storage.clearAll(context)
        synchronized(this) {
            sampleCount = 0
            latestSample = null
        }
        emitState()
        return operationResult(true, "heart_rate_data_cleared")
    }

    @SuppressLint("MissingPermission")
    private fun startScanPhase(scanner: android.bluetooth.le.BluetoothLeScanner, durationMs: Long, genericFallback: Boolean) {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    handleScanResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                setErrorState(STATUS_ERROR, "BLE scan failed: $errorCode")
                stopScan(resolvePending = true)
            }
        }

        synchronized(this) {
            pendingScanCallback = callback
            scanUsesGenericFallback = genericFallback
            sawDeviceInScanPhase = false
        }

        val filters = if (genericFallback) {
            emptyList()
        } else {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID)).build())
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (error: SecurityException) {
            setErrorState(STATUS_PERMISSION_REQUIRED, error.message ?: "Bluetooth permission required")
            stopScan(resolvePending = true)
            return
        } catch (error: Exception) {
            setErrorState(STATUS_ERROR, error.message ?: "BLE scan failed")
            stopScan(resolvePending = true)
            return
        }

        mainHandler.postDelayed({
            val shouldFallback = synchronized(this) {
                pendingScanCallback === callback && !genericFallback && !sawDeviceInScanPhase
            }
            if (shouldFallback) {
                stopScan(resolvePending = false)
                startScanPhase(scanner, durationMs / 2L, genericFallback = true)
                return@postDelayed
            }

            val shouldComplete = synchronized(this) { pendingScanCallback === callback }
            if (shouldComplete) {
                stopScan(resolvePending = true)
            }
        }, if (genericFallback) durationMs else durationMs / 2L)
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val address = result.device?.address ?: return
        val advertisedService = result.scanRecord?.serviceUuids?.any { it.uuid == HEART_RATE_SERVICE_UUID } == true
        val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() } ?: knownDeviceName(result.device)
        val device = HeartRateDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            lastSeenMs = System.currentTimeMillis(),
            remembered = getLastDevice()?.address == address,
            heartRateServiceAdvertised = advertisedService,
        )

        synchronized(this) {
            sawDeviceInScanPhase = true
            devices[address] = device
        }
        emit("heartRateDeviceFound", device.toJson(), false)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan(resolvePending: Boolean) {
        val adapter = bluetoothAdapter()
        val callback = synchronized(this) {
            val existing = pendingScanCallback
            pendingScanCallback = null
            existing
        }
        if (callback != null) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        }

        val completion = synchronized(this) {
            if (resolvePending) {
                pendingScanCompletion.also { pendingScanCompletion = null }
            } else {
                null
            }
        }

        if (resolvePending) {
            synchronized(this) {
                if (status == STATUS_SCANNING) {
                    status = if (connectedDevice != null) STATUS_CONNECTED else STATUS_IDLE
                }
            }
            emitState()
            completion?.invoke(devicesResult())
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(resetState: Boolean) {
        val gatt = bluetoothGatt
        bluetoothGatt = null
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        if (resetState) {
            synchronized(this) {
                cancelReconnectLocked()
                status = STATUS_DISCONNECTED
                connectedDevice = null
                latestSample = null
                bodySensorLocation = null
                batteryLevel = null
                recording = false
                pendingRecordingStart = false
                serviceRunning = false
                foregroundNotificationVisible = false
                nextReconnectDelayMs = null
                reconnectAttempt = 0
            }
            emitState()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                synchronized(this@HeartRateBleManager) {
                    status = STATUS_CONNECTING
                    errorText = ""
                }
                emitState()
                runCatching { gatt.discoverServices() }
                    .onFailure { setErrorState(STATUS_ERROR, it.message ?: "Service discovery failed") }
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val detail = if (statusCode == BluetoothGatt.GATT_SUCCESS) "" else "GATT disconnected: $statusCode"
                runCatching { gatt.close() }
                if (bluetoothGatt === gatt) {
                    bluetoothGatt = null
                }
                val shouldReconnect = synchronized(this@HeartRateBleManager) {
                    errorText = detail
                    connectedDevice = connectedDevice?.copy(remembered = getLastDevice()?.address == connectedDevice?.address)
                    (recording || pendingRecordingStart) &&
                        serviceRunning &&
                        autoReconnectEnabled &&
                        !manualStopRequested &&
                        connectedDevice != null
                }
                if (shouldReconnect) {
                    scheduleReconnect(detail)
                } else {
                    val sessionToStop: String?
                    val samplesToStop: Int
                    synchronized(this@HeartRateBleManager) {
                        sessionToStop = if (recording && currentSessionId != "live") currentSessionId else null
                        samplesToStop = sampleCount
                        status = STATUS_DISCONNECTED
                        recording = false
                        pendingRecordingStart = false
                        serviceRunning = false
                        foregroundNotificationVisible = false
                    }
                    if (sessionToStop != null) {
                        storage.appendSessionStop(sessionToStop, samplesToStop)
                    }
                    emitState()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                setErrorState(STATUS_ERROR, "Service discovery failed: $statusCode")
                return
            }
            val heartRateService = gatt.getService(HEART_RATE_SERVICE_UUID)
            val measurement = heartRateService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (measurement == null) {
                setErrorState(STATUS_ERROR, "Heart Rate Measurement is unavailable")
                return
            }

            val enabled = enableHeartRateNotifications(gatt, measurement)
            if (!enabled) {
                setErrorState(STATUS_ERROR, "Heart Rate Notify setup failed")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
            if (descriptor.uuid != CCCD_UUID) {
                return
            }
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                setErrorState(STATUS_ERROR, "CCCD write failed: $statusCode")
                return
            }

            val device = synchronized(this@HeartRateBleManager) { connectedDevice }
            if (device != null) {
                saveLastDevice(device)
            }
            synchronized(this@HeartRateBleManager) {
                if (pendingRecordingStart || recording) {
                    beginRecordingLocked()
                } else {
                    status = STATUS_CONNECTED
                }
                errorText = ""
                reconnectAttempt = 0
                nextReconnectDelayMs = null
            }
            emitState()
            readBodySensorOrBattery(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleCharacteristicChanged(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            statusCode: Int,
        ) {
            @Suppress("DEPRECATION")
            handleCharacteristicRead(gatt, characteristic.uuid, characteristic.value, statusCode)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            statusCode: Int,
        ) {
            handleCharacteristicRead(gatt, characteristic.uuid, value, statusCode)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableHeartRateNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val localEnabled = runCatching {
            gatt.setCharacteristicNotification(characteristic, true)
        }.getOrDefault(false)
        if (!localEnabled) {
            return false
        }

        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBodySensorOrBattery(gatt: BluetoothGatt) {
        val bodySensor = gatt.getService(HEART_RATE_SERVICE_UUID)
            ?.getCharacteristic(BODY_SENSOR_LOCATION_UUID)
        if (bodySensor != null && runCatching { gatt.readCharacteristic(bodySensor) }.getOrDefault(false)) {
            return
        }
        readBattery(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun readBattery(gatt: BluetoothGatt) {
        val battery = gatt.getService(BATTERY_SERVICE_UUID)
            ?.getCharacteristic(BATTERY_LEVEL_UUID)
        if (battery != null) {
            runCatching { gatt.readCharacteristic(battery) }
        }
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, statusCode: Int) {
        if (statusCode != BluetoothGatt.GATT_SUCCESS) {
            return
        }
        when (uuid) {
            BODY_SENSOR_LOCATION_UUID -> {
                val parsed = value.firstOrNull()?.toInt()?.and(0xFF)
                synchronized(this) {
                    bodySensorLocation = bodySensorLocationName(parsed)
                }
                emitState()
                readBattery(gatt)
            }
            BATTERY_LEVEL_UUID -> {
                val parsed = value.firstOrNull()?.toInt()?.and(0xFF)
                synchronized(this) {
                    batteryLevel = parsed
                }
                emitState()
            }
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, value: ByteArray) {
        if (uuid != HEART_RATE_MEASUREMENT_UUID) {
            return
        }
        val parsed = HeartRateMeasurementParser.parse(value) ?: return
        val sample = synchronized(this) {
            val device = connectedDevice
            val nextSample = HeartRateSample(
                timestampMs = System.currentTimeMillis(),
                sessionId = if (recording) currentSessionId else "live",
                deviceAddress = device?.address ?: "",
                deviceName = device?.name,
                bpm = parsed.bpm,
                bpmFormat = parsed.bpmFormat,
                rawHex = parsed.rawHex,
                flags = parsed.flags,
                sensorContactSupported = parsed.sensorContactSupported,
                sensorContactDetected = parsed.sensorContactDetected,
                energyExpended = parsed.energyExpended,
                rrIntervals = parsed.rrIntervals,
                rrIntervalsMs = parsed.rrIntervalsMs,
                bodySensorLocation = bodySensorLocation,
                batteryLevel = batteryLevel,
            )
            latestSample = nextSample
            if (recording) {
                sampleCount += 1
                storage.appendSample(nextSample)
            }
            nextSample
        }
        emit("heartRateSample", sample.toJson(), false)
        emitState()
    }

    private fun devicesResult(): JSObject = synchronized(this) {
        JSObject().apply {
            put("devices", JSArray(devices.values.map { it.toJson() }))
            put("usedFallback", scanUsesGenericFallback)
        }
    }

    private fun operationResult(ok: Boolean, method: String, error: String? = null): JSObject =
        JSObject().apply {
            put("ok", ok)
            put("method", method)
            if (error != null) {
                put("error", error)
            }
            put("state", getStateJson())
        }

    private fun setErrorState(nextStatus: String, error: String) {
        Log.w(TAG, error)
        synchronized(this) {
            status = nextStatus
            errorText = error
        }
        emitState()
    }

    private fun emitState() {
        emit("heartRateStateChanged", getStateJson(), true)
    }

    private fun buildStateJson(): JSObject = JSObject().apply {
        put("status", status)
        put("device", connectedDevice?.toJson() ?: JSONObject.NULL)
        put("latestSample", latestSample?.toJson() ?: JSONObject.NULL)
        put("sampleCount", sampleCount)
        put("serviceRunning", serviceRunning)
        put("foregroundNotificationVisible", foregroundNotificationVisible)
        put("autoReconnectEnabled", autoReconnectEnabled)
        put("reconnectAttempt", reconnectAttempt)
        if (nextReconnectDelayMs == null) {
            put("nextReconnectDelayMs", JSONObject.NULL)
        } else {
            put("nextReconnectDelayMs", nextReconnectDelayMs)
        }
        if (recording && currentSessionId != "live") {
            put("sessionId", currentSessionId)
        } else {
            put("sessionId", JSONObject.NULL)
        }
        put("recording", recording)
        put("error", errorText)
        put("bodySensorLocation", bodySensorLocation ?: "")
        put("lastNativeUpdateMs", System.currentTimeMillis())
        if (latestSample == null) {
            put("lastSampleTimestampMs", JSONObject.NULL)
        } else {
            put("lastSampleTimestampMs", latestSample?.timestampMs)
        }
        if (batteryLevel == null) {
            put("batteryLevel", JSONObject.NULL)
        } else {
            put("batteryLevel", batteryLevel)
        }
    }

    private fun scheduleReconnect(detail: String) {
        synchronized(this) {
            if (!serviceRunning || manualStopRequested || !autoReconnectEnabled) {
                return
            }
            if (connectedDevice == null) {
                status = STATUS_DISCONNECTED
                pendingRecordingStart = false
                recording = false
                return
            }
            val delayMs = HeartRateReconnectPolicy.delayForAttempt(reconnectAttempt)
            reconnectAttempt += 1
            nextReconnectDelayMs = delayMs
            status = STATUS_RECONNECTING
            errorText = detail
            pendingRecordingStart = true
            cancelReconnectLocked()
            reconnectRunnable = Runnable {
                reconnectRunnable = null
                attemptReconnect()
            }
            mainHandler.postDelayed(reconnectRunnable!!, delayMs)
        }
        emitState()
    }

    private fun attemptReconnect() {
        val targetDevice = synchronized(this) {
            if (!serviceRunning || manualStopRequested || !autoReconnectEnabled) {
                return
            }
            connectedDevice ?: getLastDevice() ?: return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            setErrorState(STATUS_ERROR, "Bluetooth is unavailable")
            return
        }
        if (!adapter.isEnabled) {
            synchronized(this) {
                status = STATUS_BLUETOOTH_OFF
                nextReconnectDelayMs = null
            }
            emitState()
            return
        }
        if (!hasBluetoothRuntimePermissions()) {
            synchronized(this) {
                status = STATUS_PERMISSION_REQUIRED
                nextReconnectDelayMs = null
            }
            emitState()
            return
        }
        connect(targetDevice.address, targetDevice.name) {
            // State changes are emitted through GATT callbacks.
        }
    }

    private fun cancelReconnectLocked() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        nextReconnectDelayMs = null
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun hasBluetoothRuntimePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun knownDeviceName(device: BluetoothDevice?): String? {
        return runCatching { device?.name?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun getLastDevice(): HeartRateDevice? {
        val address = preferences.getString(KEY_LAST_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        val name = preferences.getString(KEY_LAST_NAME, null)?.takeIf { it.isNotBlank() }
        return HeartRateDevice(address = address, name = name, remembered = true)
    }

    private fun saveLastDevice(device: HeartRateDevice) {
        preferences.edit()
            .putString(KEY_LAST_ADDRESS, device.address)
            .putString(KEY_LAST_NAME, device.name ?: "")
            .apply()
        synchronized(this) {
            connectedDevice = device.copy(remembered = true)
        }
    }

    companion object {
        private const val TAG = "HeartRateBleManager"
        private const val PREFS_NAME = "heart_rate.v1"
        private const val KEY_LAST_ADDRESS = "last_address"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val CONNECT_TIMEOUT_MS = 15_000L

        const val STATUS_IDLE = "idle"
        const val STATUS_PERMISSION_REQUIRED = "permission_required"
        const val STATUS_BLUETOOTH_OFF = "bluetooth_off"
        const val STATUS_SCANNING = "scanning"
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_RECORDING = "recording"
        const val STATUS_RECONNECTING = "reconnecting"
        const val STATUS_DISCONNECTED = "disconnected"
        const val STATUS_STOPPING = "stopping"
        const val STATUS_ERROR = "error"

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val BODY_SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
