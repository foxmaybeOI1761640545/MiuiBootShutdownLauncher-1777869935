package com.example.miuipower.heartrate

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class HeartRateUploadQueue(
    context: Context,
    private val chunkWriter: HeartRateChunkWriter,
    private val githubUploader: HeartRateGithubUploader,
    private val settingsProvider: () -> AutoHeartRateSettings,
    private val emit: (eventName: String, data: JSObject, retain: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    fun getStateJson(): JSObject {
        val pending = chunkWriter.pendingChunks()
        return JSObject().apply {
            put("pendingChunks", pending.size)
            put("uploadRunning", running)
            put("consecutiveUploadFailures", preferences.getInt(KEY_CONSECUTIVE_FAILURES, 0))
            put("lastThreeUploadErrors", failuresJson(readLastFailures()))
            val current = pending.firstOrNull()
            if (current == null) {
                put("currentChunkId", JSONObject.NULL)
                put("currentStatus", "idle")
            } else {
                put("currentChunkId", current.optString("chunkId", ""))
                put("currentStatus", current.optString("status", "pending"))
            }
        }
    }

    fun enqueueClosedChunk(meta: JSONObject) {
        emit("heartRateChunkClosed", JSObject(meta.toString()), false)
        trigger()
    }

    fun trigger() {
        if (running) {
            return
        }
        running = true
        emitState()
        thread(name = "HeartRateUploadQueue") {
            try {
                processQueue()
            } finally {
                running = false
                emitState()
            }
        }
    }

    private fun processQueue() {
        while (settingsProvider().enabled) {
            val meta = chunkWriter.pendingChunks().firstOrNull() ?: return
            val uploaded = processSingleChunk(meta, settingsProvider())
            if (!uploaded) {
                scheduleRetry()
                return
            }
        }
    }

    private fun processSingleChunk(meta: JSONObject, settings: AutoHeartRateSettings): Boolean {
        meta.put("status", "uploading")
        chunkWriter.updateChunkMetadata(meta)
        emitState()

        if (settings.uploadCsv && meta.optString("csvUploadStatus", "pending") != "uploaded") {
            val ok = uploadFile(meta, fileType = "csv", localPathKey = "csvLocalPath", fileNameKey = "csvFileName")
            if (!ok) {
                return false
            }
        } else if (!settings.uploadCsv) {
            meta.put("csvUploadStatus", "uploaded")
        }

        if (settings.uploadJsonl && meta.optString("jsonlUploadStatus", "pending") != "uploaded") {
            val ok = uploadFile(meta, fileType = "jsonl", localPathKey = "jsonlLocalPath", fileNameKey = "jsonlFileName")
            if (!ok) {
                return false
            }
        } else if (!settings.uploadJsonl) {
            meta.put("jsonlUploadStatus", "uploaded")
        }

        meta.put("status", "uploaded")
        meta.put("uploadedAtMs", System.currentTimeMillis())
        chunkWriter.updateChunkMetadata(meta)
        chunkWriter.appendUploadSummary(meta)
        chunkWriter.persistOverlapFromTail(meta)
        if (settings.deleteLocalAfterUpload) {
            chunkWriter.deleteLocalFilesForUploadedChunk(meta)
        }
        resetFailures()
        emit("heartRateChunkUploaded", JSObject(meta.toString()), false)
        emitState()
        return true
    }

    private fun uploadFile(meta: JSONObject, fileType: String, localPathKey: String, fileNameKey: String): Boolean {
        val localPath = meta.optString(localPathKey, "")
        val file = File(localPath)
        val fileName = meta.optString(fileNameKey, file.name)
        val remotePath = githubUploader.autoUploadPath(fileName, meta.optLong("createdAtMs", System.currentTimeMillis()))
        val attempt = meta.optInt("uploadAttemptCount", 0) + 1
        meta.put("uploadAttemptCount", attempt)
        chunkWriter.updateChunkMetadata(meta)

        val result = try {
            githubUploader.uploadLocalFile(
                file = file,
                remotePath = remotePath,
                message = "Add heart-rate ${meta.optString("chunkId", "")} $fileType",
                rowCount = meta.optInt("rows", 0),
            )
        } catch (error: Exception) {
            JSObject().apply {
                put("ok", false)
                put("method", "github_contents_api")
                put("fileName", fileName)
                put("path", remotePath)
                put("rowCount", meta.optInt("rows", 0))
                put("errorType", "network")
                put("error", error.message ?: error.javaClass.simpleName)
            }
        }

        if (result.optBoolean("ok", false)) {
            meta.put("${fileType}UploadStatus", "uploaded")
            meta.put("${fileType}RemotePath", result.optString("path", remotePath))
            meta.put("${fileType}HtmlUrl", result.optString("htmlUrl", ""))
            meta.put("${fileType}CommitSha", result.optString("commitSha", ""))
            meta.put("status", if (fileType == "csv") "partial_uploaded" else "uploading")
            chunkWriter.updateChunkMetadata(meta)
            return true
        }

        val failure = HeartRateUploadFailureRecord(
            timestampMs = System.currentTimeMillis(),
            chunkId = meta.optString("chunkId", ""),
            fileType = fileType,
            attempt = attempt,
            errorType = result.optString("errorType", "unknown"),
            httpCode = result.optInt("statusCode", 0).takeIf { it > 0 },
            message = result.optString("error", "Upload failed"),
        )
        meta.put("${fileType}UploadStatus", "failed")
        meta.put("status", if (isPartiallyUploaded(meta)) "partial_uploaded" else "failed")
        appendFailureToMeta(meta, failure)
        chunkWriter.updateChunkMetadata(meta)
        recordFailure(failure)
        emit("heartRateUploadFailed", failure.toJson().apply {
            put("state", getStateJson())
        }, false)
        maybeNotifyFailureThreshold()
        emitState()
        return false
    }

    private fun isPartiallyUploaded(meta: JSONObject): Boolean =
        meta.optString("csvUploadStatus") == "uploaded" || meta.optString("jsonlUploadStatus") == "uploaded"

    private fun appendFailureToMeta(meta: JSONObject, failure: HeartRateUploadFailureRecord) {
        val existing = meta.optJSONArray("lastThreeErrors") ?: JSONArray()
        val next = JSONArray()
        val start = (existing.length() - 2).coerceAtLeast(0)
        for (index in start until existing.length()) {
            next.put(existing.optJSONObject(index))
        }
        next.put(failure.toJsonObject())
        meta.put("lastThreeErrors", next)
    }

    private fun recordFailure(failure: HeartRateUploadFailureRecord) {
        val last = readLastFailures().takeLast(2) + failure
        preferences.edit()
            .putInt(KEY_CONSECUTIVE_FAILURES, preferences.getInt(KEY_CONSECUTIVE_FAILURES, 0) + 1)
            .putString(KEY_LAST_FAILURES, JSONArray(last.map { it.toJsonObject() }).toString())
            .apply()
    }

    private fun resetFailures() {
        preferences.edit()
            .putInt(KEY_CONSECUTIVE_FAILURES, 0)
            .putString(KEY_LAST_FAILURES, JSONArray().toString())
            .apply()
    }

    private fun readLastFailures(): List<HeartRateUploadFailureRecord> {
        val raw = preferences.getString(KEY_LAST_FAILURES, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index) }
            .map { HeartRateUploadFailureRecord.fromJson(it) }
    }

    private fun maybeNotifyFailureThreshold() {
        val threshold = settingsProvider().failureNotifyThreshold.coerceAtLeast(1)
        val failures = preferences.getInt(KEY_CONSECUTIVE_FAILURES, 0)
        if (failures < threshold) {
            return
        }
        val payload = JSObject().apply {
            put("consecutiveUploadFailures", failures)
            put("lastThreeUploadErrors", failuresJson(readLastFailures()))
        }
        emit("heartRateUploadAlert", payload, true)
        HeartRateNotification.showUploadFailure(appContext, payload)
    }

    private fun scheduleRetry() {
        val delayMs = settingsProvider().retryIntervalMs.coerceAtLeast(5_000L)
        mainHandler.postDelayed({
            if (settingsProvider().enabled) {
                trigger()
            }
        }, delayMs)
    }

    private fun emitState() {
        emit("heartRateUploadQueueChanged", getStateJson(), true)
    }

    private fun failuresJson(records: List<HeartRateUploadFailureRecord>): JSArray {
        val output = JSArray()
        records.forEach { output.put(it.toJson()) }
        return output
    }

    companion object {
        private const val PREFS_NAME = "heart_rate_auto_upload.v1"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
        private const val KEY_LAST_FAILURES = "last_failures"
    }
}
