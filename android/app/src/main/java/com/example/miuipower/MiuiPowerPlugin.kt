package com.example.miuipower

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlin.concurrent.thread

@CapacitorPlugin(name = "MiuiPower")
class MiuiPowerPlugin : Plugin() {

    companion object {
        private const val TAG = "MiuiPowerPlugin"
        private const val PKG_SECURITY_CENTER = "com.miui.securitycenter"
        private const val ACTIVITY_BOOT_SHUTDOWN =
            "com.miui.powercenter.bootshutdown.PowerShutdownOnTime"
        private const val ACTION_BOOT_SHUTDOWN =
            "miui.powercenter.intent.action.BOOT_SHUTDOWN_ONTIME"
        private const val ACTION_POWER_MANAGER = "miui.intent.action.POWER_MANAGER"
        private const val ACTION_MIUI_SCREEN_REFRESH_RATE = "miui.intent.action.DISPLAY_REFRESH_RATE"
        private const val ACTION_DISPLAY_SETTINGS = Settings.ACTION_DISPLAY_SETTINGS
        private const val PKG_SETTINGS = "com.android.settings"
        private const val PKG_MISETTINGS = "com.xiaomi.misettings"
        private const val ACTIVITY_MISETTINGS_REFRESH_RATE =
            "com.xiaomi.misettings.display.RefreshRate.RefreshRateActivity"
        private const val ACTIVITY_SUB_SETTINGS = "com.android.settings.SubSettings"
        private const val PKG_HONOR_OF_KINGS = "com.tencent.tmgp.sgame"
        private const val EXTRA_SHOW_FRAGMENT = ":settings:show_fragment"
        private const val EXTRA_SHOW_FRAGMENT_TITLE = ":settings:show_fragment_title"
        private const val EXTRA_SOURCE_METRICS = ":settings:source_metrics"
        private const val FRAGMENT_WIRELESS_DEBUGGING =
            "com.android.settings.development.WirelessDebuggingFragment"
        private val FRAGMENTS_SCREEN_REFRESH_RATE = listOf(
            "com.android.settings.display.RefreshRateSettings",
            "com.android.settings.display.ScreenRefreshRateFragment",
            "com.android.settings.display.RefreshRateFragment",
            "com.android.settings.display.SmoothDisplayFragment",
        )
        private const val TITLE_WIRELESS_DEBUGGING = "无线调试"
        private const val TITLE_SCREEN_REFRESH_RATE = "屏幕刷新率"
        private const val SOURCE_METRICS_WIRELESS_DEBUGGING = 1839
        private const val SOURCE_METRICS_SCREEN_REFRESH_RATE = 746
    }

    @PluginMethod
    fun openBootShutdownPage(call: PluginCall) {
        val ctx = context

        if (tryOpenByAction(ctx)) {
            call.resolve(result(true, "action"))
            return
        }

        if (tryOpenByComponent(ctx)) {
            call.resolve(result(true, "component"))
            return
        }

        if (tryOpenPowerManager(ctx)) {
            call.resolve(result(true, "fallback"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun openWirelessDebuggingPage(call: PluginCall) {
        val ctx = context

        if (tryOpenWirelessDebugging(ctx)) {
            call.resolve(result(true, "wireless_debugging_fragment"))
            return
        }

        if (tryOpenDeveloperOptions(ctx)) {
            call.resolve(result(true, "developer_options"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun openDeveloperOptions(call: PluginCall) {
        val ctx = context

        if (tryOpenDeveloperOptions(ctx)) {
            call.resolve(result(true, "application_development_settings"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun openScreenRefreshRatePage(call: PluginCall) {
        val ctx = context

        if (tryOpenScreenRefreshRateByMiSettingsActivity(ctx)) {
            call.resolve(result(true, "xiaomi_misettings_refresh_rate_activity"))
            return
        }

        if (tryOpenScreenRefreshRateByAction(ctx)) {
            call.resolve(result(true, "miui_refresh_rate_action"))
            return
        }

        if (tryOpenScreenRefreshRateByFragment(ctx)) {
            call.resolve(result(true, "refresh_rate_fragment"))
            return
        }

        if (tryOpenDisplaySettings(ctx)) {
            call.resolve(result(true, "display_settings"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun openHonorOfKings(call: PluginCall) {
        val ctx = context
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(PKG_HONOR_OF_KINGS)

        if (launchIntent == null) {
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "none")
                put("installed", false)
                put("packageName", PKG_HONOR_OF_KINGS)
            })
            return
        }

        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launchIntent)
            call.resolve(JSObject().apply {
                put("ok", true)
                put("method", "package_launch_intent")
                put("installed", true)
                put("packageName", PKG_HONOR_OF_KINGS)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Honor of Kings launch failed", e)
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "none")
                put("installed", true)
                put("packageName", PKG_HONOR_OF_KINGS)
                put("error", e.message ?: e.javaClass.simpleName)
            })
        }
    }

    @PluginMethod
    fun hasOverlayPermission(call: PluginCall) {
        call.resolve(JSObject().apply {
            put("granted", canDrawOverlays(context))
        })
    }

    @PluginMethod
    fun hasAccessibilityPermission(call: PluginCall) {
        call.resolve(JSObject().apply {
            put("granted", FocusAccessibilityHelper.isServiceEnabled(context))
        })
    }

    @PluginMethod
    fun openOverlayPermissionSettings(call: PluginCall) {
        val opened = tryOpenOverlayPermissionSettings(context)
        call.resolve(result(opened, if (opened) "manage_overlay_permission" else "none"))
    }

    @PluginMethod
    fun openAccessibilitySettings(call: PluginCall) {
        val opened = FocusAccessibilityHelper.openAccessibilitySettings(context)
        call.resolve(result(opened, if (opened) "accessibility_settings" else "none"))
    }

    @PluginMethod
    fun startFocusOverlay(call: PluginCall) {
        val ctx = context
        if (!canDrawOverlays(ctx)) {
            val overlayOpened = tryOpenOverlayPermissionSettings(ctx)
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "overlay_permission_required")
                put("permissionRequired", true)
                put("overlayPermissionRequired", true)
                put("overlaySettingsOpened", overlayOpened)
                put("accessibilityPermissionRequired", false)
            })
            return
        }

        val intent = Intent(ctx, FocusOverlayService::class.java)
        val accessibilityEnabled = FocusAccessibilityHelper.isServiceEnabled(ctx)
        val accessibilitySettingsOpened = if (!accessibilityEnabled) {
            FocusAccessibilityHelper.openAccessibilitySettings(ctx)
        } else {
            false
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, intent)
            } else {
                ctx.startService(intent)
            }
            call.resolve(JSObject().apply {
                put("ok", true)
                put("method", "focus_overlay_service")
                put("permissionRequired", false)
                put("overlayPermissionRequired", false)
                put("accessibilityEnabled", accessibilityEnabled)
                put("accessibilityPermissionRequired", !accessibilityEnabled)
                put("accessibilitySettingsOpened", accessibilitySettingsOpened)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Focus overlay launch failed", e)
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "none")
                put("permissionRequired", false)
                put("overlayPermissionRequired", false)
                put("accessibilityPermissionRequired", false)
                put("error", e.message ?: e.javaClass.simpleName)
            })
        }
    }

    @PluginMethod
    fun stopFocusOverlay(call: PluginCall) {
        val intent = Intent(context, FocusOverlayService::class.java)
        val stopped = context.stopService(intent)
        call.resolve(JSObject().apply {
            put("ok", stopped)
            put("method", if (stopped) "focus_overlay_service" else "none")
        })
    }

    @PluginMethod
    fun getWindowFocusInfo(call: PluginCall) {
        thread(name = "focus-window-reader-plugin") {
            FocusInfoRepository.readLatestExternal()?.let { info ->
                call.resolve(info.toWindowFocusResult())
                return@thread
            }

            val latestSeen = FocusInfoRepository.readLatestSeen()
            val shellInfo = FocusWindowReader.read()
            val result = shellInfo.toJSObject().apply {
                put("source", "shell_dumpsys")
                put("accessibilityEnabled", FocusAccessibilityHelper.isServiceEnabled(context))
                put("latestSeenPackage", latestSeen?.packageName ?: "")
                put("latestSeenClass", latestSeen?.className ?: "")
            }

            val shellError = shellInfo.error.takeIf { it.isNotBlank() }
            if (shellError != null) {
                val message = buildString {
                    append("无障碍缓存中暂无可用外部窗口")
                    if (!FocusAccessibilityHelper.isServiceEnabled(context)) {
                        append("，请先开启无障碍服务")
                    }
                    append("；shell 调试信息：")
                    append(shellError)
                }
                result.put("error", message)
            }

            call.resolve(result)
        }
    }

    private fun tryOpenByAction(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_BOOT_SHUTDOWN).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Action not found", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Action blocked by system", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Action launch failed", e)
            false
        }
    }

    private fun tryOpenByComponent(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(PKG_SECURITY_CENTER, ACTIVITY_BOOT_SHUTDOWN)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Component not found", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Component blocked by system", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Component launch failed", e)
            false
        }
    }

    private fun tryOpenPowerManager(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_POWER_MANAGER).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallback launch failed", e)
            false
        }
    }

    private fun tryOpenWirelessDebugging(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(PKG_SETTINGS, ACTIVITY_SUB_SETTINGS)
                putExtra(EXTRA_SHOW_FRAGMENT, FRAGMENT_WIRELESS_DEBUGGING)
                putExtra(EXTRA_SHOW_FRAGMENT_TITLE, TITLE_WIRELESS_DEBUGGING)
                putExtra(EXTRA_SOURCE_METRICS, SOURCE_METRICS_WIRELESS_DEBUGGING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Wireless debugging fragment not found", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Wireless debugging blocked by system", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Wireless debugging launch failed", e)
            false
        }
    }

    private fun tryOpenDeveloperOptions(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Developer options fallback launch failed", e)
            false
        }
    }

    private fun tryOpenScreenRefreshRateByAction(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_MIUI_SCREEN_REFRESH_RATE).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "MIUI refresh-rate action launch failed", e)
            false
        }
    }

    private fun tryOpenScreenRefreshRateByMiSettingsActivity(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(PKG_MISETTINGS, ACTIVITY_MISETTINGS_REFRESH_RATE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "MiSettings refresh-rate activity launch failed", e)
            false
        }
    }

    private fun tryOpenScreenRefreshRateByFragment(context: Context): Boolean {
        for (fragment in FRAGMENTS_SCREEN_REFRESH_RATE) {
            val opened = try {
                val intent = Intent().apply {
                    component = ComponentName(PKG_SETTINGS, ACTIVITY_SUB_SETTINGS)
                    putExtra(EXTRA_SHOW_FRAGMENT, fragment)
                    putExtra(EXTRA_SHOW_FRAGMENT_TITLE, TITLE_SCREEN_REFRESH_RATE)
                    putExtra(EXTRA_SOURCE_METRICS, SOURCE_METRICS_SCREEN_REFRESH_RATE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Refresh-rate fragment launch failed: $fragment", e)
                false
            }

            if (opened) {
                return true
            }
        }
        return false
    }

    private fun tryOpenDisplaySettings(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Display settings fallback launch failed", e)
            false
        }
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    private fun tryOpenOverlayPermissionSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Overlay permission settings launch failed", e)
            false
        }
    }

    private fun result(ok: Boolean, method: String): JSObject {
        return JSObject().apply {
            put("ok", ok)
            put("method", method)
        }
    }
}
