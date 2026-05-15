package com.example.miuipower.heartrate

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HeartRateStorage(private val filesDir: File) {
    private val rootDir = File(filesDir, "heart_rate")
    private val samplesFile = File(rootDir, "samples.jsonl")
    private val sessionsFile = File(rootDir, "sessions.jsonl")

    @Synchronized
    fun appendSample(sample: HeartRateSample) {
        ensureRootDir()
        samplesFile.appendText(sample.toJson().toString() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun appendSession(sessionId: String, device: HeartRateDevice?) {
        ensureRootDir()
        val line = JSObject().apply {
            put("sessionId", sessionId)
            put("startedAtMs", System.currentTimeMillis())
            if (device != null) {
                put("deviceAddress", device.address)
                put("deviceName", device.name ?: "")
            }
        }
        sessionsFile.appendText(line.toString() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun readSamples(limit: Int, sinceMs: Long?): JSArray {
        val output = JSArray()
        if (!samplesFile.exists()) {
            return output
        }

        val safeLimit = limit.coerceIn(1, 2_000)
        val parsed = samplesFile.readLines(Charsets.UTF_8)
            .asSequence()
            .mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }
            .filter { item ->
                val timestamp = item.optLong("timestampMs", 0L)
                sinceMs == null || timestamp >= sinceMs
            }
            .toList()
            .takeLast(safeLimit)

        for (item in parsed) {
            output.put(item)
        }
        return output
    }

    @Synchronized
    fun clear() {
        ensureRootDir()
        samplesFile.writeText("", Charsets.UTF_8)
        sessionsFile.writeText("", Charsets.UTF_8)
    }

    fun newSessionId(): String {
        return SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
    }

    private fun ensureRootDir() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }
}
