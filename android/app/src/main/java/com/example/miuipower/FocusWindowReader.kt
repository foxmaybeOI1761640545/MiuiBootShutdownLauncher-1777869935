package com.example.miuipower

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import java.util.concurrent.TimeUnit

object FocusWindowReader {
    private const val COMMAND = "dumpsys window"
    private const val MAX_OUTPUT_CHARS = 1_000_000
    private val focusPatterns = listOf("mCurrentFocus", "mFocusedApp")

    data class Result(
        val ok: Boolean,
        val command: String,
        val lines: List<String>,
        val raw: String,
        val error: String,
        val exitCode: Int,
        val timedOut: Boolean,
    ) {
        fun toJSObject(): JSObject {
            val jsLines = JSArray()
            lines.forEach { jsLines.put(it) }
            return JSObject().apply {
                put("ok", ok)
                put("command", command)
                put("lines", jsLines)
                put("raw", raw)
                put("error", error)
                put("exitCode", exitCode)
                put("timedOut", timedOut)
            }
        }
    }

    fun read(): Result {
        val output = StringBuilder()
        var exitCode = -1
        var timedOut = false

        return try {
            val process = ProcessBuilder("sh", "-c", COMMAND)
                .redirectErrorStream(true)
                .start()

            val readerThread = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < MAX_OUTPUT_CHARS) {
                            output.appendLine(line)
                        }
                    }
                }
            }
            readerThread.start()

            if (process.waitFor(4, TimeUnit.SECONDS)) {
                exitCode = process.exitValue()
            } else {
                timedOut = true
                process.destroyForcibly()
            }
            readerThread.join(500)

            fromOutput(output.toString(), exitCode, timedOut)
        } catch (e: Exception) {
            Result(
                ok = false,
                command = COMMAND,
                lines = emptyList(),
                raw = output.toString(),
                error = e.message ?: e.javaClass.simpleName,
                exitCode = exitCode,
                timedOut = timedOut,
            )
        }
    }

    private fun fromOutput(raw: String, exitCode: Int, timedOut: Boolean): Result {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { line ->
                focusPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }
            }
            .toList()

        val error = when {
            timedOut -> "dumpsys window 执行超时"
            raw.contains("Permission Denial", ignoreCase = true) ->
                "普通应用通常没有 android.permission.DUMP，无法读取完整 dumpsys window"
            exitCode != 0 -> "dumpsys window 返回非 0 状态：$exitCode"
            lines.isEmpty() -> "没有匹配到 mCurrentFocus 或 mFocusedApp"
            else -> ""
        }

        return Result(
            ok = lines.isNotEmpty() && error.isEmpty(),
            command = COMMAND,
            lines = lines,
            raw = raw.take(8000),
            error = error,
            exitCode = exitCode,
            timedOut = timedOut,
        )
    }
}
