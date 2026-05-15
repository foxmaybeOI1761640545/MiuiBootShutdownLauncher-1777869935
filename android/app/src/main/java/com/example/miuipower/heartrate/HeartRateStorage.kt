package com.example.miuipower.heartrate

import android.content.Context
import androidx.core.content.FileProvider
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
        appendSessionStart(sessionId, device)
    }

    @Synchronized
    fun appendSessionStart(sessionId: String, device: HeartRateDevice?) {
        ensureRootDir()
        val line = JSObject().apply {
            put("sessionId", sessionId)
            put("startMs", System.currentTimeMillis())
            put("status", "started")
            if (device != null) {
                put("deviceAddress", device.address)
                put("deviceName", device.name ?: "")
            }
        }
        sessionsFile.appendText(line.toString() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun appendSessionStop(sessionId: String, sampleCount: Int) {
        ensureRootDir()
        val line = JSObject().apply {
            put("sessionId", sessionId)
            put("endMs", System.currentTimeMillis())
            put("sampleCount", sampleCount)
            put("status", "stopped")
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
        val parsed = readSampleObjects(sinceMs, null)
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

    @Synchronized
    fun exportHistory(context: Context, format: String, sinceMs: Long?, untilMs: Long?): JSObject {
        ensureRootDir()
        val normalizedFormat = if (format == "csv") "csv" else "jsonl"
        val exportDir = File(context.cacheDir, "heart_rate_exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = if (normalizedFormat == "csv") "csv" else "jsonl"
        val mimeType = if (normalizedFormat == "csv") "text/csv" else "application/x-ndjson"
        val outputFile = File(exportDir, "heart_rate_$timestamp.$extension")
        val samples = readSampleObjects(sinceMs, untilMs).toList()

        if (normalizedFormat == "csv") {
            val header = "timestampMs,sessionId,deviceName,bpm,rrMs,batteryLevel,rawHex\n"
            val rows = samples.joinToString(separator = "\n") { sample ->
                listOf(
                    sample.optLong("timestampMs", 0L).toString(),
                    csvCell(sample.optString("sessionId", "")),
                    csvCell(sample.optString("deviceName", "")),
                    sample.optInt("bpm", 0).toString(),
                    csvCell(rrMsCell(sample)),
                    sample.opt("batteryLevel")?.takeUnless { it == JSONObject.NULL }?.toString() ?: "",
                    csvCell(sample.optString("rawHex", "")),
                ).joinToString(",")
            }
            outputFile.writeText(header + rows + if (rows.isNotEmpty()) "\n" else "", Charsets.UTF_8)
        } else {
            outputFile.writeText(samples.joinToString(separator = "\n") { it.toString() } + if (samples.isNotEmpty()) "\n" else "", Charsets.UTF_8)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile,
        )

        return JSObject().apply {
            put("ok", true)
            put("method", "file_provider")
            put("format", normalizedFormat)
            put("contentUri", uri.toString())
            put("fileName", outputFile.name)
            put("mimeType", mimeType)
            put("rowCount", samples.size)
        }
    }

    private fun ensureRootDir() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    private fun readSampleObjects(sinceMs: Long?, untilMs: Long?): Sequence<JSONObject> {
        if (!samplesFile.exists()) {
            return emptySequence()
        }
        return samplesFile.readLines(Charsets.UTF_8)
            .asSequence()
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
            .filter { item ->
                val timestamp = item.optLong("timestampMs", 0L)
                (sinceMs == null || timestamp >= sinceMs) && (untilMs == null || timestamp <= untilMs)
            }
    }

    private fun rrMsCell(sample: JSONObject): String {
        val rr = sample.optJSONArray("rrIntervalsMs") ?: return ""
        return (0 until rr.length()).joinToString("|") { index -> rr.optInt(index).toString() }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
