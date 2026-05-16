package com.example.miuipower.heartrate

import com.getcapacitor.JSObject
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HeartRateChunkWriter(private val filesDir: File) {
    private val rootDir = File(filesDir, "heart_rate/auto")
    private val chunksDir = File(rootDir, "chunks")
    private val exportsDir = File(rootDir, "exports")
    private val metadataDir = File(rootDir, "metadata")
    private val tailsDir = File(rootDir, "tails")
    private val stateFile = File(rootDir, "state.json")
    private val nextOverlapFile = File(rootDir, "next_overlap.jsonl")
    private val overlapFile = File(rootDir, "overlap.jsonl")
    private val summaryFile = File(rootDir, "upload_summary.jsonl")

    @Synchronized
    fun appendSample(sample: HeartRateSample, settings: AutoHeartRateSettings): JSONObject? {
        ensureDirs()
        val state = readState()
        val meta = ensureOpenChunk(state, settings)
        val nextSampleSeq = state.optLong("nextSampleSeq", 1L)
        val row = exportSample(sample, meta, nextSampleSeq, isOverlap = false, source = null)
        File(meta.getString("chunkLocalPath")).appendText(row.toString() + "\n", Charsets.UTF_8)

        val realRows = meta.optInt("realRows", 0) + 1
        meta.put("realRows", realRows)
        meta.put("rows", meta.optInt("overlapRows", 0) + realRows)
        meta.put("lastUpdatedAtMs", System.currentTimeMillis())
        writeMetadata(meta)
        state.put("nextSampleSeq", nextSampleSeq + 1L)
        writeState(state)

        return if (realRows >= settings.chunkSize.coerceAtLeast(1)) {
            closeOpenChunk(meta, settings)
        } else {
            null
        }
    }

    @Synchronized
    fun pendingChunks(): List<JSONObject> {
        ensureDirs()
        return metadataDir.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { readJsonFile(it) }
            .filter { it.optString("status") != "open" && it.optString("status") != "uploaded" }
            .sortedBy { it.optInt("chunkSeq", 0) }
    }

    @Synchronized
    fun updateChunkMetadata(meta: JSONObject) {
        writeMetadata(meta)
    }

    @Synchronized
    fun appendUploadSummary(meta: JSONObject) {
        ensureDirs()
        summaryFile.appendText(summaryObject(meta).toString() + "\n", Charsets.UTF_8)
    }

    @Synchronized
    fun persistOverlapFromTail(meta: JSONObject) {
        val tailPath = meta.optString("tailLocalPath", "")
        val tailFile = File(tailPath)
        if (tailPath.isBlank() || !tailFile.exists()) {
            return
        }
        overlapFile.writeText(tailFile.readText(Charsets.UTF_8), Charsets.UTF_8)
    }

    @Synchronized
    fun deleteLocalFilesForUploadedChunk(meta: JSONObject) {
        listOf(
            meta.optString("chunkLocalPath", ""),
            meta.optString("csvLocalPath", ""),
            meta.optString("jsonlLocalPath", ""),
            meta.optString("tailLocalPath", ""),
        )
            .filter { it.isNotBlank() }
            .map { File(it) }
            .filter { it.exists() && it.isFile }
            .forEach { it.delete() }
    }

    @Synchronized
    fun storageStats(): JSObject {
        ensureDirs()
        val pending = pendingChunks()
        val failed = pending.filter { it.optString("status") == "failed" }
        val pendingSize = pending.sumOf { meta ->
            listOf("chunkLocalPath", "csvLocalPath", "jsonlLocalPath")
                .sumOf { key -> File(meta.optString(key, "")).takeIf { it.exists() }?.length() ?: 0L }
        }
        val open = readOpenChunkMetadata()
        return JSObject().apply {
            put("autoCurrentChunkRows", open?.optInt("realRows", 0) ?: 0)
            put("autoPendingChunkCount", pending.size)
            put("autoFailedChunkCount", failed.size)
            put("autoPendingUploadSizeBytes", pendingSize)
            put("autoUploadedSummaryCount", countRows(summaryFile))
        }
    }

    @Synchronized
    fun clearUploadedLocalChunks(): JSObject {
        var deleted = 0
        metadataDir.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { readJsonFile(it) }
            .filter { it.optString("status") == "uploaded" }
            .forEach { meta ->
                val before = listOf(
                    meta.optString("chunkLocalPath", ""),
                    meta.optString("csvLocalPath", ""),
                    meta.optString("jsonlLocalPath", ""),
                    meta.optString("tailLocalPath", ""),
                ).count { path -> path.isNotBlank() && File(path).exists() }
                deleteLocalFilesForUploadedChunk(meta)
                deleted += before
            }
        return JSObject().apply {
            put("ok", true)
            put("method", "auto_uploaded_chunks_cleared")
            put("deletedFiles", deleted)
        }
    }

    private fun ensureOpenChunk(state: JSONObject, settings: AutoHeartRateSettings): JSONObject {
        readOpenChunkMetadata()?.let { return it }

        val chunkSeq = state.optInt("nextChunkSeq", 1)
        val createdAtMs = System.currentTimeMillis()
        val sessionId = state.optString("sessionId", "").ifBlank {
            SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(createdAtMs)).also {
                state.put("sessionId", it)
            }
        }
        val chunkId = "${sessionId}_part${chunkSeq.toString().padStart(4, '0')}"
        val chunkFile = File(chunksDir, "$chunkId.jsonl")
        val meta = JSONObject().apply {
            put("chunkId", chunkId)
            put("sessionId", sessionId)
            put("chunkSeq", chunkSeq)
            put("createdAtMs", createdAtMs)
            put("realRows", 0)
            put("overlapRows", 0)
            put("rows", 0)
            put("chunkLocalPath", chunkFile.absolutePath)
            put("csvUploadStatus", "pending")
            put("jsonlUploadStatus", "pending")
            put("uploadAttemptCount", 0)
            put("status", "open")
        }
        val overlapRows = readJsonLines(nextOverlapFile).takeLast(settings.overlapRows.coerceAtLeast(0))
        if (overlapRows.isNotEmpty()) {
            overlapRows.forEach { source ->
                val row = JSONObject(source.toString()).apply {
                    put("chunkId", chunkId)
                    put("chunkSeq", chunkSeq)
                    put("isOverlap", true)
                    put("sourceChunkId", source.optString("chunkId", ""))
                    put("sourceSampleSeq", source.optLong("sampleSeq", 0L))
                }
                chunkFile.appendText(row.toString() + "\n", Charsets.UTF_8)
            }
            meta.put("overlapRows", overlapRows.size)
            meta.put("rows", overlapRows.size)
        }
        writeMetadata(meta)
        return meta
    }

    private fun closeOpenChunk(meta: JSONObject, settings: AutoHeartRateSettings): JSONObject {
        val closedAtMs = System.currentTimeMillis()
        val chunkId = meta.getString("chunkId")
        val csvFile = File(exportsDir, "$chunkId.csv")
        val jsonlFile = File(exportsDir, "$chunkId.jsonl")
        val chunkFile = File(meta.getString("chunkLocalPath"))
        val rows = readJsonLines(chunkFile)
        val realRows = rows.filter { !it.optBoolean("isOverlap", false) }
        writeCsv(csvFile, rows)
        jsonlFile.writeText(rows.joinToString(separator = "\n") { it.toString() } + "\n", Charsets.UTF_8)

        val tailFile = File(tailsDir, "$chunkId.tail.jsonl")
        val tailRows = realRows.takeLast(settings.overlapRows.coerceAtLeast(0))
        tailFile.writeText(tailRows.joinToString(separator = "\n") { it.toString() } + if (tailRows.isNotEmpty()) "\n" else "", Charsets.UTF_8)
        nextOverlapFile.writeText(tailFile.readText(Charsets.UTF_8), Charsets.UTF_8)

        meta.put("closedAtMs", closedAtMs)
        meta.put("csvFileName", csvFile.name)
        meta.put("jsonlFileName", jsonlFile.name)
        meta.put("csvLocalPath", csvFile.absolutePath)
        meta.put("jsonlLocalPath", jsonlFile.absolutePath)
        meta.put("tailLocalPath", tailFile.absolutePath)
        meta.put("status", "closed")
        writeMetadata(meta)

        val state = readState()
        state.put("nextChunkSeq", meta.optInt("chunkSeq", 1) + 1)
        writeState(state)
        return meta
    }

    private fun exportSample(
        sample: HeartRateSample,
        meta: JSONObject,
        sampleSeq: Long,
        isOverlap: Boolean,
        source: JSONObject?,
    ): JSONObject = JSONObject(sample.toJson().toString()).apply {
        put("datetimeShanghai", formatShanghaiDateTime(sample.timestampMs))
        put("timeShanghai", formatShanghaiTime(sample.timestampMs))
        put("chunkId", meta.getString("chunkId"))
        put("chunkSeq", meta.getInt("chunkSeq"))
        put("sampleSeq", sampleSeq)
        put("isOverlap", isOverlap)
        if (source != null) {
            put("sourceChunkId", source.optString("chunkId", ""))
            put("sourceSampleSeq", source.optLong("sampleSeq", 0L))
        }
    }

    private fun writeCsv(file: File, rows: List<JSONObject>) {
        val header = "timestampMs,datetimeShanghai,timeShanghai,sessionId,chunkId,chunkSeq,sampleSeq,isOverlap,sourceChunkId,sourceSampleSeq,deviceName,bpm,rrMs,batteryLevel,rawHex\n"
        val body = rows.joinToString(separator = "\n") { sample ->
            listOf(
                sample.optLong("timestampMs", 0L).toString(),
                csvCell(sample.optString("datetimeShanghai", "")),
                csvCell(sample.optString("timeShanghai", "")),
                csvCell(sample.optString("sessionId", "")),
                csvCell(sample.optString("chunkId", "")),
                sample.optInt("chunkSeq", 0).toString(),
                sample.optLong("sampleSeq", 0L).toString(),
                sample.optBoolean("isOverlap", false).toString(),
                csvCell(sample.optString("sourceChunkId", "")),
                sample.optLong("sourceSampleSeq", 0L).takeIf { it > 0L }?.toString() ?: "",
                csvCell(sample.optString("deviceName", "")),
                sample.optInt("bpm", 0).toString(),
                csvCell(rrMsCell(sample)),
                sample.opt("batteryLevel")?.takeUnless { it == JSONObject.NULL }?.toString() ?: "",
                csvCell(sample.optString("rawHex", "")),
            ).joinToString(",")
        }
        file.writeText(header + body + if (body.isNotEmpty()) "\n" else "", Charsets.UTF_8)
    }

    private fun summaryObject(meta: JSONObject): JSONObject = JSONObject().apply {
        put("chunkId", meta.optString("chunkId", ""))
        put("sessionId", meta.optString("sessionId", ""))
        put("chunkSeq", meta.optInt("chunkSeq", 0))
        put("rows", meta.optInt("rows", 0))
        put("realRows", meta.optInt("realRows", 0))
        put("overlapRows", meta.optInt("overlapRows", 0))
        put("createdAtMs", meta.optLong("createdAtMs", 0L))
        put("closedAtMs", meta.optLong("closedAtMs", 0L))
        put("uploadedAtMs", System.currentTimeMillis())
        put("csvRemotePath", meta.optString("csvRemotePath", ""))
        put("jsonlRemotePath", meta.optString("jsonlRemotePath", ""))
        put("csvHtmlUrl", meta.optString("csvHtmlUrl", ""))
        put("jsonlHtmlUrl", meta.optString("jsonlHtmlUrl", ""))
        put("csvCommitSha", meta.optString("csvCommitSha", ""))
        put("jsonlCommitSha", meta.optString("jsonlCommitSha", ""))
    }

    private fun readOpenChunkMetadata(): JSONObject? =
        metadataDir.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { readJsonFile(it) }
            .firstOrNull { it.optString("status") == "open" }

    private fun writeMetadata(meta: JSONObject) {
        ensureDirs()
        File(metadataDir, "${meta.getString("chunkId")}.json").writeText(meta.toString(2), Charsets.UTF_8)
    }

    private fun readState(): JSONObject {
        ensureDirs()
        return readJsonFile(stateFile) ?: JSONObject().apply {
            put("nextChunkSeq", 1)
            put("nextSampleSeq", 1L)
        }
    }

    private fun writeState(state: JSONObject) {
        stateFile.writeText(state.toString(2), Charsets.UTF_8)
    }

    private fun readJsonFile(file: File): JSONObject? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun readJsonLines(file: File): List<JSONObject> {
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }
        return file.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun ensureDirs() {
        listOf(rootDir, chunksDir, exportsDir, metadataDir, tailsDir).forEach { it.mkdirs() }
    }

    private fun countRows(file: File): Int {
        if (!file.exists()) {
            return 0
        }
        return file.useLines(Charsets.UTF_8) { lines -> lines.count { it.isNotBlank() } }
    }

    private fun rrMsCell(sample: JSONObject): String {
        val rr = sample.optJSONArray("rrIntervalsMs") ?: return ""
        return (0 until rr.length()).joinToString("|") { index -> rr.optInt(index).toString() }
    }

    private fun formatShanghaiDateTime(timestampMs: Long): String =
        SHANGHAI_DATE_TIME_FORMAT.get().format(Date(timestampMs))

    private fun formatShanghaiTime(timestampMs: Long): String =
        SHANGHAI_TIME_FORMAT.get().format(Date(timestampMs))

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    companion object {
        private val SHANGHAI_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        private val SHANGHAI_DATE_TIME_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
                timeZone = SHANGHAI_TIME_ZONE
            }
        }
        private val SHANGHAI_TIME_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("HH:mm:ss", Locale.CHINA).apply {
                timeZone = SHANGHAI_TIME_ZONE
            }
        }
    }
}
