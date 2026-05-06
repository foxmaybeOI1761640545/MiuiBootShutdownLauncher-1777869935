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
        private const val PKG_SETTINGS = "com.android.settings"
        private const val ACTIVITY_SUB_SETTINGS = "com.android.settings.SubSettings"
        private const val EXTRA_SHOW_FRAGMENT = ":settings:show_fragment"
        private const val EXTRA_SHOW_FRAGMENT_TITLE = ":settings:show_fragment_title"
        private const val EXTRA_SOURCE_METRICS = ":settings:source_metrics"
        private const val FRAGMENT_WIRELESS_DEBUGGING =
            "com.android.settings.development.WirelessDebuggingFragment"
        private const val TITLE_WIRELESS_DEBUGGING = "无线调试"
        private const val SOURCE_METRICS_WIRELESS_DEBUGGING = 1839
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
    fun hasOverlayPermission(call: PluginCall) {
        call.resolve(JSObject().apply {
            put("granted", canDrawOverlays(context))
        })
    }

    @PluginMethod
    fun openOverlayPermissionSettings(call: PluginCall) {
        val opened = tryOpenOverlayPermissionSettings(context)
        call.resolve(result(opened, if (opened) "manage_overlay_permission" else "none"))
    }

    @PluginMethod
    fun startFocusOverlay(call: PluginCall) {
        val ctx = context
        if (!canDrawOverlays(ctx)) {
            tryOpenOverlayPermissionSettings(ctx)
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "overlay_permission_required")
                put("permissionRequired", true)
            })
            return
        }

        val intent = Intent(ctx, FocusOverlayService::class.java)
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
            })
        } catch (e: Exception) {
            Log.w(TAG, "Focus overlay launch failed", e)
            call.resolve(JSObject().apply {
                put("ok", false)
                put("method", "none")
                put("permissionRequired", false)
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
            val info = FocusWindowReader.read()
            call.resolve(info.toJSObject())
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
