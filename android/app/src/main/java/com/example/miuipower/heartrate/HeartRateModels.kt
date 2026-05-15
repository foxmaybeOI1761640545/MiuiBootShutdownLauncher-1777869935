package com.example.miuipower.heartrate

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.util.Locale

data class HeartRateDevice(
    val address: String,
    val name: String?,
    val rssi: Int? = null,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val remembered: Boolean = false,
    val heartRateServiceAdvertised: Boolean = false,
) {
    fun toJson(): JSObject = JSObject().apply {
        put("address", address)
        put("name", name ?: "")
        if (rssi != null) {
            put("rssi", rssi)
        }
        put("lastSeenMs", lastSeenMs)
        put("remembered", remembered)
        put("heartRateServiceAdvertised", heartRateServiceAdvertised)
    }
}

data class HeartRateSample(
    val timestampMs: Long,
    val sessionId: String,
    val deviceAddress: String,
    val deviceName: String?,
    val bpm: Int,
    val bpmFormat: String,
    val rawHex: String,
    val flags: Int,
    val sensorContactSupported: Boolean,
    val sensorContactDetected: Boolean?,
    val energyExpended: Int?,
    val rrIntervals: List<Double>,
    val rrIntervalsMs: List<Int>,
    val bodySensorLocation: String?,
    val batteryLevel: Int?,
) {
    fun toJson(): JSObject = JSObject().apply {
        put("timestampMs", timestampMs)
        put("sessionId", sessionId)
        put("deviceAddress", deviceAddress)
        put("deviceName", deviceName ?: "")
        put("bpm", bpm)
        put("bpmFormat", bpmFormat)
        put("rawHex", rawHex)
        put("flags", flags)
        put("sensorContactSupported", sensorContactSupported)
        if (sensorContactDetected == null) {
            put("sensorContactDetected", JSONObject.NULL)
        } else {
            put("sensorContactDetected", sensorContactDetected)
        }
        if (energyExpended == null) {
            put("energyExpended", JSONObject.NULL)
        } else {
            put("energyExpended", energyExpended)
        }
        put("rrIntervals", JSArray(rrIntervals))
        put("rrIntervalsMs", JSArray(rrIntervalsMs))
        if (bodySensorLocation == null) {
            put("bodySensorLocation", JSONObject.NULL)
        } else {
            put("bodySensorLocation", bodySensorLocation)
        }
        if (batteryLevel == null) {
            put("batteryLevel", JSONObject.NULL)
        } else {
            put("batteryLevel", batteryLevel)
        }
    }
}

data class ParsedHeartRateMeasurement(
    val bpm: Int,
    val bpmFormat: String,
    val rawHex: String,
    val flags: Int,
    val sensorContactSupported: Boolean,
    val sensorContactDetected: Boolean?,
    val energyExpended: Int?,
    val rrIntervals: List<Double>,
    val rrIntervalsMs: List<Int>,
)

object HeartRateMeasurementParser {
    fun parse(data: ByteArray): ParsedHeartRateMeasurement? {
        if (data.size < 2) {
            return null
        }

        val flags = data[0].toInt() and 0xFF
        val bpmIsUint16 = flags and 0x01 != 0
        val bpmLength = if (bpmIsUint16) 2 else 1
        var offset = 1
        if (data.size < offset + bpmLength) {
            return null
        }

        val bpm = if (bpmIsUint16) {
            readUInt16(data, offset)
        } else {
            data[offset].toInt() and 0xFF
        }
        offset += bpmLength

        val sensorContactSupported = flags and 0x04 != 0
        val sensorContactDetected = if (sensorContactSupported) {
            flags and 0x02 != 0
        } else {
            null
        }

        val energyExpended = if (flags and 0x08 != 0) {
            if (data.size < offset + 2) {
                return null
            }
            readUInt16(data, offset).also { offset += 2 }
        } else {
            null
        }

        val rrIntervalsRaw = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (data.size >= offset + 2) {
                rrIntervalsRaw.add(readUInt16(data, offset))
                offset += 2
            }
        }

        return ParsedHeartRateMeasurement(
            bpm = bpm,
            bpmFormat = if (bpmIsUint16) "uint16" else "uint8",
            rawHex = data.toHexString(),
            flags = flags,
            sensorContactSupported = sensorContactSupported,
            sensorContactDetected = sensorContactDetected,
            energyExpended = energyExpended,
            rrIntervals = rrIntervalsRaw.map { it / 1024.0 },
            rrIntervalsMs = rrIntervalsRaw.map { ((it * 1000.0) / 1024.0).toInt() },
        )
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
}

object HeartRateReconnectPolicy {
    private val delaysMs = longArrayOf(1_000L, 3_000L, 5_000L, 10_000L)

    fun delayForAttempt(attempt: Int): Long {
        val safeIndex = attempt.coerceAtLeast(0).coerceAtMost(delaysMs.lastIndex)
        return delaysMs[safeIndex]
    }
}

fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02X".format(Locale.US, byte.toInt() and 0xFF) }

fun bodySensorLocationName(value: Int?): String? = when (value) {
    null -> null
    0 -> "Other"
    1 -> "Chest"
    2 -> "Wrist"
    3 -> "Finger"
    4 -> "Hand"
    5 -> "Ear Lobe"
    6 -> "Foot"
    else -> "Unknown $value"
}
