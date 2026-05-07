package com.example.miuipower

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object FocusAccessibilityHelper {
    fun isServiceEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) {
            return false
        }

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val componentName = ComponentName(context, FocusAccessibilityService::class.java)
        val expected = componentName.flattenToString()
        val expectedShort = componentName.flattenToShortString()

        return enabledServices
            .split(':')
            .any { item -> item.equals(expected, ignoreCase = true) || item.equals(expectedShort, ignoreCase = true) }
    }

    fun openAccessibilitySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

