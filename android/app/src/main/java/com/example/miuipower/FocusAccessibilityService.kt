package com.example.miuipower

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FocusAccessibilityService : AccessibilityService() {
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
        val packageName = event.packageName?.toString()?.trim().orEmpty()
        if (packageName.isBlank()) {
            return
        }

        val className = event.className?.toString()?.trim().takeUnless { it.isNullOrBlank() }
        val texts = collectTexts(rootInActiveWindow)

        event.text
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach { text ->
                if (texts.size < MAX_TEXT_ITEMS && !texts.contains(text)) {
                    texts.add(text)
                }
            }

        val title = event.contentDescription?.toString()?.trim().takeUnless { it.isNullOrBlank() }
            ?: texts.firstOrNull()

        val info = FocusInfo(
            source = "accessibility",
            packageName = packageName,
            className = className,
            windowTitle = title,
            texts = texts.take(MAX_TEXT_ITEMS),
            timestamp = System.currentTimeMillis(),
        )

        FocusInfoRepository.update(info, applicationContext.packageName)
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
    }
}
