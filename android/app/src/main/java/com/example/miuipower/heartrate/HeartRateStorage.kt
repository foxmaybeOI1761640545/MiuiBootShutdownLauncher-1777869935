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

data class HeartRateExportFile(
    val file: File,
    val result: JSObject,
)

class HeartRateStorage(private val filesDir: File) {
    private val rootDir = File(filesDir, "heart_rate")
    private val samplesFile = File(rootDir, "samples.jsonl")
    private val sessionsFile = File(rootDir, "sessions.jsonl")
    private val lastExportFile = File(rootDir, "last_export.json")

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
        return createExportFile(context, format, sinceMs, untilMs).result
    }

    @Synchronized
    fun getLastExport(context: Context): JSObject {
        val metadata = readLastExportMetadata()
            ?: return JSObject().apply {
                put("ok", false)
                put("method", "no_export")
                put("error", "No heart-rate export has been created yet.")
            }
        return exportFileFromMetadata(context, metadata)?.result ?: JSObject().apply {
            put("ok", false)
            put("method", "last_export_missing")
            put("format", metadata.optString("format", ""))
            put("fileName", metadata.optString("fileName", ""))
            put("rowCount", metadata.optInt("rowCount", 0))
            put("createdAtMs", metadata.optLong("createdAtMs", 0L))
            put("error", "The last heart-rate export file is no longer available.")
        }
    }

    @Synchronized
    fun getOrCreateExport(context: Context, format: String): HeartRateExportFile {
        val normalizedFormat = normalizeFormat(format)
        return readLastExport(context, normalizedFormat) ?: createExportFile(context, normalizedFormat, null, null)
    }

    private fun createExportFile(context: Context, format: String, sinceMs: Long?, untilMs: Long?): HeartRateExportFile {
        ensureRootDir()
        val normalizedFormat = normalizeFormat(format)
        val exportDir = File(context.cacheDir, "heart_rate_exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = if (normalizedFormat == "csv") "csv" else "jsonl"
        val mimeType = mimeTypeForFormat(normalizedFormat)
        val outputFile = uniqueExportFile(exportDir, "heart_rate_$timestamp", extension)
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

        val createdAtMs = System.currentTimeMillis()
        val result = buildExportResult(
            context = context,
            file = outputFile,
            format = normalizedFormat,
            mimeType = mimeType,
            rowCount = samples.size,
            createdAtMs = createdAtMs,
        )
        writeLastExportMetadata(outputFile, result)
        return HeartRateExportFile(outputFile, result)
    }

    private fun readLastExport(context: Context, format: String?): HeartRateExportFile? {
        val metadata = readLastExportMetadata() ?: return null
        val metadataFormat = metadata.optString("format", "")
        if (format != null && metadataFormat != format) {
            return null
        }
        return exportFileFromMetadata(context, metadata)
    }

    private fun exportFileFromMetadata(context: Context, metadata: JSONObject): HeartRateExportFile? {
        val path = metadata.optString("path", "").takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return null
        }
        val format = normalizeFormat(metadata.optString("format", "jsonl"))
        val mimeType = metadata.optString("mimeType", mimeTypeForFormat(format)).takeIf { it.isNotBlank() }
            ?: mimeTypeForFormat(format)
        val rowCount = metadata.optInt("rowCount", 0)
        val createdAtMs = metadata.optLong("createdAtMs", file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())
        return HeartRateExportFile(
            file,
            buildExportResult(context, file, format, mimeType, rowCount, createdAtMs),
        )
    }

    private fun buildExportResult(
        context: Context,
        file: File,
        format: String,
        mimeType: String,
        rowCount: Int,
        createdAtMs: Long,
    ): JSObject {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        return JSObject().apply {
            put("ok", true)
            put("method", "cache_fileprovider")
            put("format", format)
            put("contentUri", uri.toString())
            put("fileName", file.name)
            put("mimeType", mimeType)
            put("rowCount", rowCount)
            put("sizeBytes", file.length())
            put("createdAtMs", createdAtMs)
        }
    }

    private fun writeLastExportMetadata(file: File, result: JSObject) {
        ensureRootDir()
        val metadata = JSONObject().apply {
            put("path", file.absolutePath)
            put("fileName", result.optString("fileName", file.name))
            put("format", result.optString("format", "jsonl"))
            put("mimeType", result.optString("mimeType", "application/x-ndjson"))
            put("rowCount", result.optInt("rowCount", 0))
            put("sizeBytes", result.optLong("sizeBytes", file.length()))
            put("createdAtMs", result.optLong("createdAtMs", System.currentTimeMillis()))
        }
        lastExportFile.writeText(metadata.toString(), Charsets.UTF_8)
    }

    private fun readLastExportMetadata(): JSONObject? {
        if (!lastExportFile.exists()) {
            return null
        }
        return runCatching { JSONObject(lastExportFile.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun uniqueExportFile(exportDir: File, baseName: String, extension: String): File {
        var candidate = File(exportDir, "$baseName.$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(exportDir, "${baseName}_$suffix.$extension")
            suffix += 1
        }
        return candidate
    }

    private fun normalizeFormat(format: String): String {
        return if (format == "csv") "csv" else "jsonl"
    }

    private fun mimeTypeForFormat(format: String): String {
        return if (format == "csv") "text/csv" else "application/x-ndjson"
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
