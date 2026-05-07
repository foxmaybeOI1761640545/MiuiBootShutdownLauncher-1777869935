package com.example.miuipower

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject

data class FocusInfo(
    val source: String,
    val packageName: String?,
    val className: String?,
    val windowTitle: String?,
    val texts: List<String>,
    val timestamp: Long,
) {
    fun toDisplayLines(): List<String> {
        val lines = mutableListOf<String>()
        lines += "来源：$source"
        lines += "包名：${packageName ?: "(unknown)"}"
        lines += "页面：${className ?: "(unknown)"}"
        if (!windowTitle.isNullOrBlank()) {
            lines += "标题：$windowTitle"
        }
        if (texts.isNotEmpty()) {
            lines += "文本：${texts.take(8).joinToString(" / ")}"
        }
        lines += "时间：$timestamp"
        return lines
    }

    fun toDisplayText(): String = toDisplayLines().joinToString("\n")

    fun toWindowFocusResult(): JSObject {
        val lines = JSArray()
        toDisplayLines().forEach { lines.put(it) }

        val textsArray = JSArray()
        texts.forEach { textsArray.put(it) }

        return JSObject().apply {
            put("ok", true)
            put("source", source)
            put("command", "accessibility_cache")
            put("lines", lines)
            put("raw", toDisplayText())
            put("error", "")
            put("exitCode", 0)
            put("timedOut", false)
            put("packageName", packageName ?: "")
            put("className", className ?: "")
            put("windowTitle", windowTitle ?: "")
            put("texts", textsArray)
            put("timestamp", timestamp)
        }
    }
}

