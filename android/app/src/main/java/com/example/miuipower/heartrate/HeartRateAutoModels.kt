package com.example.miuipower.heartrate

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONObject

data class AutoHeartRateSettings(
    val enabled: Boolean = false,
    val targetDeviceName: String = TargetDeviceMatcher.TARGET_NAME,
    val targetDeviceAddress: String? = null,
    val chunkSize: Int = 1_200,
    val overlapRows: Int = 10,
    val uploadCsv: Boolean = true,
    val uploadJsonl: Boolean = true,
    val deleteLocalAfterUpload: Boolean = true,
    val retryIntervalMs: Long = 60_000L,
    val failureNotifyThreshold: Int = 3,
) {
    fun toJson(): JSObject = JSObject().apply {
        put("enabled", enabled)
        put("targetDeviceName", targetDeviceName)
        if (targetDeviceAddress.isNullOrBlank()) {
            put("targetDeviceAddress", JSONObject.NULL)
        } else {
            put("targetDeviceAddress", targetDeviceAddress)
        }
        put("chunkSize", chunkSize)
        put("overlapRows", overlapRows)
        put("uploadCsv", uploadCsv)
        put("uploadJsonl", uploadJsonl)
        put("deleteLocalAfterUpload", deleteLocalAfterUpload)
        put("retryIntervalMs", retryIntervalMs)
        put("failureNotifyThreshold", failureNotifyThreshold)
    }
}

data class HeartRateUploadFailureRecord(
    val timestampMs: Long,
    val chunkId: String,
    val fileType: String,
    val attempt: Int,
    val errorType: String,
    val httpCode: Int?,
    val message: String,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("timestampMs", timestampMs)
        put("chunkId", chunkId)
        put("fileType", fileType)
        put("attempt", attempt)
        put("errorType", errorType)
        if (httpCode == null) {
            put("httpCode", JSONObject.NULL)
        } else {
            put("httpCode", httpCode)
        }
        put("message", message)
    }

    fun toJson(): JSObject = JSObject(toJsonObject().toString())

    companion object {
        fun fromJson(item: JSONObject): HeartRateUploadFailureRecord = HeartRateUploadFailureRecord(
            timestampMs = item.optLong("timestampMs", 0L),
            chunkId = item.optString("chunkId", ""),
            fileType = item.optString("fileType", ""),
            attempt = item.optInt("attempt", 0),
            errorType = item.optString("errorType", "unknown"),
            httpCode = item.opt("httpCode")?.takeUnless { it == JSONObject.NULL }?.let { item.optInt("httpCode") },
            message = item.optString("message", ""),
        )
    }
}

fun failureRecordsToJson(records: List<HeartRateUploadFailureRecord>): JSArray {
    val output = JSArray()
    records.forEach { output.put(it.toJson()) }
    return output
}
