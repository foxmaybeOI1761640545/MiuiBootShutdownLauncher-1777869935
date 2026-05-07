package com.example.miuipower

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {
    @Volatile
    private var lastWindowPackage: String? = null

    @Volatile
    private var lastWindowClass: String? = null

    @Volatile
    private var lastWindowTimestamp: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> updateFocusInfo(event)
        }
    }

    override fun onInterrupt() = Unit

    private fun updateFocusInfo(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        val root = rootInActiveWindow
        val eventPackage = normalized(event.packageName?.toString())
        val rootPackage = normalized(root?.packageName?.toString())
        val eventClass = normalized(event.className?.toString())
        val rootClass = normalized(root?.className?.toString())

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val packageName = eventPackage ?: rootPackage ?: return
                val className = eventClass ?: rootClass

                updateStableWindow(packageName, className, now)
                publishFocusInfo(
                    source = sourceLabel(event.eventType),
                    packageName = packageName,
                    className = className,
                    event = event,
                    root = root,
                    timestamp = now,
                )
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val stablePackage = lastWindowPackage ?: return
                if (now - lastWindowTimestamp > STABLE_WINDOW_MAX_AGE_MS) {
                    return
                }

                // Content events are noisy. Only accept them when they still belong
                // to the most recent window-focus package.
                val belongsStable = sequenceOf(eventPackage, rootPackage)
                    .filterNotNull()
                    .any { it == stablePackage }
                if (!belongsStable) {
                    return
                }

                val className = lastWindowClass ?: eventClass ?: rootClass
                publishFocusInfo(
                    source = sourceLabel(event.eventType),
                    packageName = stablePackage,
                    className = className,
                    event = event,
                    root = root,
                    timestamp = now,
                )
            }
        }
    }

    private fun updateStableWindow(packageName: String, className: String?, timestamp: Long) {
        lastWindowPackage = packageName
        lastWindowClass = className
        lastWindowTimestamp = timestamp
    }

    private fun publishFocusInfo(
        source: String,
        packageName: String,
        className: String?,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
        timestamp: Long,
    ) {
        val texts = collectTexts(root)
        event.text
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach { text ->
                if (texts.size < MAX_TEXT_ITEMS && !texts.contains(text)) {
                    texts.add(text)
                }
            }

        val title = normalized(event.contentDescription?.toString())
            ?: texts.firstOrNull()

        val info = FocusInfo(
            source = source,
            packageName = packageName,
            className = className,
            windowTitle = title,
            texts = texts.take(MAX_TEXT_ITEMS),
            timestamp = timestamp,
        )
        FocusInfoRepository.update(info, applicationContext.packageName)
    }

    private fun normalized(value: String?): String? {
        val text = value?.trim().orEmpty()
        return if (text.isBlank()) null else text
    }

    private fun sourceLabel(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "accessibility/window_state"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "accessibility/windows_changed"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "accessibility/window_content"
            else -> "accessibility"
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo?): MutableList<String> {
        if (root == null) {
            return mutableListOf()
        }

        val result = linkedSetOf<String>()
        val budget = intArrayOf(MAX_VISIT_NODES)
        collectTextsRecursively(root, result, budget)
        return result.take(MAX_TEXT_ITEMS).toMutableList()
    }

    private fun collectTextsRecursively(
        node: AccessibilityNodeInfo?,
        result: MutableSet<String>,
        budget: IntArray,
    ) {
        if (node == null || budget[0] <= 0 || result.size >= MAX_TEXT_ITEMS) {
            return
        }

        budget[0] -= 1

        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }

        for (index in 0 until node.childCount) {
            if (budget[0] <= 0 || result.size >= MAX_TEXT_ITEMS) {
                break
            }
            collectTextsRecursively(node.getChild(index), result, budget)
        }
    }

    companion object {
        private const val MAX_VISIT_NODES = 220
        private const val MAX_TEXT_ITEMS = 80
        private const val STABLE_WINDOW_MAX_AGE_MS = 8_000L
    }
}
