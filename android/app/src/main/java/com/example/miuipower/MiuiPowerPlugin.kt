package com.example.miuipower

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlin.concurrent.thread
import kotlin.math.roundToInt

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
        private const val ACTION_SETTINGS = Settings.ACTION_SETTINGS
        private const val PKG_SETTINGS = "com.android.settings"
        private const val PKG_MISETTINGS = "com.xiaomi.misettings"
        private const val ACTIVITY_MISETTINGS_REFRESH_RATE =
            "com.xiaomi.misettings.display.RefreshRate.RefreshRateActivity"
        private const val ACTIVITY_SUB_SETTINGS = "com.android.settings.SubSettings"
        private const val PKG_HONOR_OF_KINGS = "com.tencent.tmgp.sgame"
        private const val PKG_FILE_EXPLORER = "com.android.fileexplorer"
        private const val ACTIVITY_FILE_EXPLORER =
            "com.android.fileexplorer.FileExplorerTabActivity"
        private const val ACTIVITY_SCREEN_TIME_MAIN =
            "com.xiaomi.misettings.usagestats.UsageStatsMainActivity"
        private const val PKG_CHROME = "com.android.chrome"
        private const val ACTIVITY_CHROME_MAIN = "com.google.android.apps.chrome.Main"
        private val CHROME_INCOGNITO_ACTIONS = listOf(
            "com.google.android.apps.chrome.ACTION_OPEN_NEW_INCOGNITO_TAB",
            "com.google.android.apps.chrome.action.INCOGNITO",
        )
        private const val PKG_AMAP = "com.autonavi.minimap"
        private const val ACTIVITY_AMAP_SPLASH = "com.autonavi.map.activity.SplashActivity"
        private const val PKG_KEEP = "com.gotokeep.keep"
        private const val ACTIVITY_KEEP_MAIN =
            "com.gotokeep.keep.refactor.business.main.activity.MainActivity"
        private const val PKG_QQMUSIC = "com.tencent.qqmusic"
        private const val ACTIVITY_QQMUSIC_MAIN = "com.tencent.qqmusic.activity.AppStarterActivity"
        private const val PKG_WECHAT = "com.tencent.mm"
        private const val ACTIVITY_WECHAT_MAIN = "com.tencent.mm.ui.LauncherUI"
        private const val PKG_CLASH = "com.github.clash.ninja.meta"
        private const val ACTIVITY_CLASH_MAIN = "com.github.kr328.clash.MainActivity"
        private const val PKG_GALLERY = "com.miui.gallery"
        private const val ACTIVITY_GALLERY_MAIN = "com.miui.gallery.activity.HomePageActivity"
        private const val PKG_BILIBILI = "tv.danmaku.bili"
        private const val ACTIVITY_BILIBILI_MAIN = "tv.danmaku.bili.MainActivityV2"
        private const val PKG_AUTHENTICATOR = "com.google.android.apps.authenticator2"
        private const val ACTIVITY_AUTHENTICATOR_MAIN =
            "com.google.android.apps.authenticator2.main.MainActivity"
        private const val PKG_SOUND_RECORDER = "com.android.soundrecorder"
        private const val ACTIVITY_SOUND_RECORDER_MAIN =
            "com.android.soundrecorder.RecordPreviewActivity"
        private const val PKG_WEATHER = "com.miui.weather2"
        private const val ACTIVITY_WEATHER_MAIN = "com.miui.weather2.ActivityWeatherMain"
        private const val PKG_CAMERA = "com.android.camera"
        private const val ACTIVITY_CAMERA_MAIN = "com.android.camera.Camera"
        private const val PKG_DOUBAO = "com.larus.nova"
        private const val ACTIVITY_DOUBAO_MAIN = "com.larus.bmhome.chat.ChatActivity"
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
    fun openDisplaySettings(call: PluginCall) {
        if (tryOpenDisplaySettings(context)) {
            call.resolve(result(true, "display_settings"))
            return
        }

        if (tryOpenSystemSettings(context)) {
            call.resolve(result(true, "settings"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun getDisplayRefreshRate(call: PluginCall) {
        try {
            val display = resolveCurrentDisplay()
            if (display == null) {
                call.reject("Display is unavailable")
                return
            }

            val currentRefreshRate = display.refreshRate.toDouble()
            if (!currentRefreshRate.isFinite() || currentRefreshRate <= 0.0) {
                call.reject("Invalid refresh rate")
                return
            }

            val roundedRefreshRate = currentRefreshRate.roundToInt()
            if (roundedRefreshRate <= 0) {
                call.reject("Invalid rounded refresh rate")
                return
            }

            val supportedRounded = collectSupportedRefreshRates(display)
            call.resolve(JSObject().apply {
                put("currentRefreshRate", currentRefreshRate)
                put("roundedRefreshRate", roundedRefreshRate)
                put("supportedRefreshRates", JSArray(supportedRounded))
            })
        } catch (e: Exception) {
            Log.w(TAG, "Read display refresh rate failed", e)
            call.reject(e.message ?: "Read refresh rate failed")
        }
    }

    @PluginMethod
    fun getClipboardText(call: PluginCall) {
        try {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager == null) {
                call.resolve(clipboardTextResult(false, false, error = "ClipboardManager unavailable"))
                return
            }

            val primaryClip = clipboardManager.getPrimaryClip()
            if (primaryClip == null || primaryClip.itemCount <= 0) {
                call.resolve(clipboardTextResult(true, false))
                return
            }

            val text = primaryClip.getItemAt(0).coerceToText(context)?.toString()
            if (text.isNullOrBlank()) {
                call.resolve(clipboardTextResult(true, false))
                return
            }

            call.resolve(clipboardTextResult(true, true, text = text))
        } catch (e: Exception) {
            Log.w(TAG, "Read clipboard text failed", e)
            call.resolve(clipboardTextResult(false, false, error = e.message ?: e.javaClass.simpleName))
        }
    }

    @PluginMethod
    fun openHonorOfKings(call: PluginCall) {
        call.resolve(openPackage(PKG_HONOR_OF_KINGS))
    }

    @PluginMethod
    fun openScreenTimePage(call: PluginCall) {
        if (tryOpenComponent(PKG_MISETTINGS, ACTIVITY_SCREEN_TIME_MAIN)) {
            call.resolve(result(true, "screen_time_component"))
            return
        }

        if (tryOpenPackage(context, PKG_MISETTINGS)) {
            call.resolve(JSObject().apply {
                put("ok", true)
                put("method", "package_launch_intent")
                put("installed", true)
                put("packageName", PKG_MISETTINGS)
            })
            return
        }

        if (tryOpenUsageAccessSettings(context)) {
            call.resolve(result(true, "usage_access_settings"))
            return
        }

        call.resolve(result(false, "none"))
    }

    @PluginMethod
    fun openUsageAccessSettings(call: PluginCall) {
        val opened = tryOpenUsageAccessSettings(context)
        call.resolve(result(opened, if (opened) "usage_access_settings" else "none"))
    }

    @PluginMethod
    fun openFileManager(call: PluginCall) {
        if (tryOpenComponent(PKG_FILE_EXPLORER, ACTIVITY_FILE_EXPLORER)) {
            call.resolve(result(true, "file_manager_component"))
            return
        }
        call.resolve(openPackage(PKG_FILE_EXPLORER))
    }

    @PluginMethod
    fun pickFile(call: PluginCall) {
        if (openStandardIntent(
                action = Intent.ACTION_OPEN_DOCUMENT,
                type = "*/*",
                addOpenableCategory = true,
            )
        ) {
            call.resolve(result(true, "open_document"))
            return
        }

        val fallbackOpened = openStandardIntent(
            action = Intent.ACTION_GET_CONTENT,
            type = "*/*",
            addOpenableCategory = true,
        )
        call.resolve(result(fallbackOpened, if (fallbackOpened) "get_content" else "none"))
    }

    @PluginMethod
    fun pickFolder(call: PluginCall) {
        val opened = openStandardIntent(action = Intent.ACTION_OPEN_DOCUMENT_TREE)
        call.resolve(result(opened, if (opened) "open_document_tree" else "none"))
    }

    @PluginMethod
    fun openChrome(call: PluginCall) {
        if (tryOpenComponent(PKG_CHROME, ACTIVITY_CHROME_MAIN)) {
            call.resolve(result(true, "chrome_component"))
            return
        }
        call.resolve(openPackage(PKG_CHROME))
    }

    @PluginMethod
    fun openUrl(call: PluginCall) {
        val normalizedUrl = normalizeUrl(call.getString("url"))
        if (normalizedUrl == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val packageName = call.getString("packageName")
        val packageOpened = openViewUri(normalizedUrl, packageName)
        if (packageOpened) {
            call.resolve(result(true, if (packageName.isNullOrBlank()) "action_view" else "action_view_package").apply {
                put("url", normalizedUrl)
            })
            return
        }

        if (!packageName.isNullOrBlank() && openViewUri(normalizedUrl, null)) {
            call.resolve(result(true, "action_view").apply {
                put("url", normalizedUrl)
            })
            return
        }

        call.resolve(result(false, "none").apply {
            put("url", normalizedUrl)
        })
    }

    @PluginMethod
    fun openChromeIncognitoBestEffort(call: PluginCall) {
        for (action in CHROME_INCOGNITO_ACTIONS) {
            val opened = openStandardIntent(
                action = action,
                packageName = PKG_CHROME,
                extras = { putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true) },
            )
            if (opened) {
                call.resolve(result(true, "incognito_action"))
                return
            }
        }

        if (openViewUri("chrome://newtab", PKG_CHROME)) {
            call.resolve(result(true, "chrome_newtab_fallback"))
            return
        }

        if (tryOpenPackage(context, PKG_CHROME)) {
            call.resolve(JSObject().apply {
                put("ok", true)
                put("method", "chrome_package_fallback")
                put("installed", true)
                put("packageName", PKG_CHROME)
            })
            return
        }

        call.resolve(openPackage(PKG_CHROME))
    }

    @PluginMethod
    fun openAmap(call: PluginCall) {
        if (tryOpenComponent(PKG_AMAP, ACTIVITY_AMAP_SPLASH)) {
            call.resolve(result(true, "amap_component"))
            return
        }
        call.resolve(openPackage(PKG_AMAP))
    }

    @PluginMethod
    fun openMapLocation(call: PluginCall) {
        val lat = call.getDouble("lat")
        val lng = call.getDouble("lng")
        if (lat == null || lng == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val name = call.getString("name").orEmpty()
        val amapUri =
            "androidamap://viewMap?sourceApplication=miui_power&lat=$lat&lon=$lng&dev=0&poiname=${Uri.encode(name)}"
        if (openViewUri(amapUri, PKG_AMAP)) {
            call.resolve(result(true, "amap_viewmap"))
            return
        }

        val label = if (name.isBlank()) "$lat,$lng" else name
        val geoUri = "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"
        val fallbackOpened = openViewUri(geoUri)
        call.resolve(result(fallbackOpened, if (fallbackOpened) "geo_fallback" else "none"))
    }

    @PluginMethod
    fun openNavigation(call: PluginCall) {
        val lat = call.getDouble("lat")
        val lng = call.getDouble("lng")
        if (lat == null || lng == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val name = call.getString("name").orEmpty()
        val amapUri =
            "androidamap://navi?sourceApplication=miui_power&lat=$lat&lon=$lng&dev=0&style=2&keywords=${Uri.encode(name)}"
        if (openViewUri(amapUri, PKG_AMAP)) {
            call.resolve(result(true, "amap_navigation"))
            return
        }

        val geoUri = if (name.isBlank()) {
            "geo:0,0?q=$lat,$lng"
        } else {
            "geo:0,0?q=${Uri.encode(name)}"
        }
        val fallbackOpened = openViewUri(geoUri)
        call.resolve(result(fallbackOpened, if (fallbackOpened) "geo_fallback" else "none"))
    }

    @PluginMethod
    fun openMapSearch(call: PluginCall) {
        val keyword = call.getString("keyword")?.trim()
        if (keyword.isNullOrEmpty()) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val amapUri =
            "androidamap://poi?sourceApplication=miui_power&keywords=${Uri.encode(keyword)}&dev=0"
        if (openViewUri(amapUri, PKG_AMAP)) {
            call.resolve(result(true, "amap_search"))
            return
        }

        val fallbackOpened = openViewUri("geo:0,0?q=${Uri.encode(keyword)}")
        call.resolve(result(fallbackOpened, if (fallbackOpened) "geo_fallback" else "none"))
    }

    @PluginMethod
    fun openKeep(call: PluginCall) {
        if (tryOpenComponent(PKG_KEEP, ACTIVITY_KEEP_MAIN)) {
            call.resolve(result(true, "keep_component"))
            return
        }
        call.resolve(openPackage(PKG_KEEP))
    }

    @PluginMethod
    fun openQQMusic(call: PluginCall) {
        if (tryOpenComponent(PKG_QQMUSIC, ACTIVITY_QQMUSIC_MAIN)) {
            call.resolve(result(true, "qqmusic_component"))
            return
        }
        call.resolve(openPackage(PKG_QQMUSIC))
    }

    @PluginMethod
    fun openMusicLink(call: PluginCall) {
        val normalizedUrl = normalizeUrl(call.getString("url"))
        if (normalizedUrl == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        if (openViewUri(normalizedUrl, PKG_QQMUSIC)) {
            call.resolve(result(true, "action_view_package").apply {
                put("url", normalizedUrl)
            })
            return
        }

        val fallbackOpened = openViewUri(normalizedUrl)
        call.resolve(result(fallbackOpened, if (fallbackOpened) "action_view" else "none").apply {
            put("url", normalizedUrl)
        })
    }

    @PluginMethod
    fun openWeChat(call: PluginCall) {
        if (tryOpenComponent(PKG_WECHAT, ACTIVITY_WECHAT_MAIN)) {
            call.resolve(result(true, "wechat_component"))
            return
        }
        call.resolve(openPackage(PKG_WECHAT))
    }

    @PluginMethod
    fun shareText(call: PluginCall) {
        val text = call.getString("text")?.trim()
        if (text.isNullOrEmpty()) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val opened = openSendText(text = text, chooser = true)
        call.resolve(result(opened, if (opened) "action_send_chooser" else "none"))
    }

    @PluginMethod
    fun shareUrl(call: PluginCall) {
        val normalizedUrl = normalizeUrl(call.getString("url"))
        if (normalizedUrl == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        val opened = openSendText(text = normalizedUrl, chooser = true)
        call.resolve(result(opened, if (opened) "action_send_chooser" else "none").apply {
            put("url", normalizedUrl)
        })
    }

    @PluginMethod
    fun openClash(call: PluginCall) {
        if (tryOpenComponent(PKG_CLASH, ACTIVITY_CLASH_MAIN)) {
            call.resolve(result(true, "clash_component"))
            return
        }
        call.resolve(openPackage(PKG_CLASH))
    }

    @PluginMethod
    fun openGallery(call: PluginCall) {
        if (tryOpenComponent(PKG_GALLERY, ACTIVITY_GALLERY_MAIN)) {
            call.resolve(result(true, "gallery_component"))
            return
        }
        call.resolve(openPackage(PKG_GALLERY))
    }

    @PluginMethod
    fun pickImage(call: PluginCall) {
        val pickOpened = openStandardIntent(
            action = Intent.ACTION_PICK,
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        )
        if (pickOpened) {
            call.resolve(result(true, "action_pick"))
            return
        }

        val fallbackOpened = openStandardIntent(
            action = Intent.ACTION_GET_CONTENT,
            type = "image/*",
            addOpenableCategory = true,
        )
        call.resolve(result(fallbackOpened, if (fallbackOpened) "get_content" else "none"))
    }

    @PluginMethod
    fun pickVideo(call: PluginCall) {
        val pickOpened = openStandardIntent(
            action = Intent.ACTION_PICK,
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
        if (pickOpened) {
            call.resolve(result(true, "action_pick"))
            return
        }

        val fallbackOpened = openStandardIntent(
            action = Intent.ACTION_GET_CONTENT,
            type = "video/*",
            addOpenableCategory = true,
        )
        call.resolve(result(fallbackOpened, if (fallbackOpened) "get_content" else "none"))
    }

    @PluginMethod
    fun openBilibili(call: PluginCall) {
        if (tryOpenComponent(PKG_BILIBILI, ACTIVITY_BILIBILI_MAIN)) {
            call.resolve(result(true, "bilibili_component"))
            return
        }
        call.resolve(openPackage(PKG_BILIBILI))
    }

    @PluginMethod
    fun openBiliUrl(call: PluginCall) {
        val normalizedUrl = normalizeUrl(call.getString("url"))
        if (normalizedUrl == null) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        if (openViewUri(normalizedUrl, PKG_BILIBILI)) {
            call.resolve(result(true, "action_view_package").apply {
                put("url", normalizedUrl)
            })
            return
        }

        val fallbackOpened = openViewUri(normalizedUrl)
        call.resolve(result(fallbackOpened, if (fallbackOpened) "action_view" else "none").apply {
            put("url", normalizedUrl)
        })
    }

    @PluginMethod
    fun openAuthenticator(call: PluginCall) {
        if (tryOpenComponent(PKG_AUTHENTICATOR, ACTIVITY_AUTHENTICATOR_MAIN)) {
            call.resolve(result(true, "authenticator_component"))
            return
        }
        call.resolve(openPackage(PKG_AUTHENTICATOR))
    }

    @PluginMethod
    fun openRecorder(call: PluginCall) {
        if (tryOpenComponent(PKG_SOUND_RECORDER, ACTIVITY_SOUND_RECORDER_MAIN)) {
            call.resolve(result(true, "recorder_component"))
            return
        }
        call.resolve(openPackage(PKG_SOUND_RECORDER))
    }

    @PluginMethod
    fun recordSound(call: PluginCall) {
        val opened = openStandardIntent(action = MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        call.resolve(result(opened, if (opened) "record_sound_action" else "none"))
    }

    @PluginMethod
    fun openWeather(call: PluginCall) {
        if (tryOpenComponent(PKG_WEATHER, ACTIVITY_WEATHER_MAIN)) {
            call.resolve(result(true, "weather_component"))
            return
        }
        call.resolve(openPackage(PKG_WEATHER))
    }

    @PluginMethod
    fun openCamera(call: PluginCall) {
        if (tryOpenComponent(PKG_CAMERA, ACTIVITY_CAMERA_MAIN)) {
            call.resolve(result(true, "camera_component"))
            return
        }
        call.resolve(openPackage(PKG_CAMERA))
    }

    @PluginMethod
    fun takePhoto(call: PluginCall) {
        val opened = openStandardIntent(action = MediaStore.ACTION_IMAGE_CAPTURE)
        call.resolve(result(opened, if (opened) "action_image_capture" else "none"))
    }

    @PluginMethod
    fun takeVideo(call: PluginCall) {
        val opened = openStandardIntent(action = MediaStore.ACTION_VIDEO_CAPTURE)
        call.resolve(result(opened, if (opened) "action_video_capture" else "none"))
    }

    @PluginMethod
    fun openDoubao(call: PluginCall) {
        if (tryOpenComponent(PKG_DOUBAO, ACTIVITY_DOUBAO_MAIN)) {
            call.resolve(result(true, "doubao_component"))
            return
        }
        call.resolve(openPackage(PKG_DOUBAO))
    }

    @PluginMethod
    fun shareToDoubao(call: PluginCall) {
        val text = call.getString("text")?.trim()
        if (text.isNullOrEmpty()) {
            call.resolve(result(false, "invalid_args"))
            return
        }

        if (openSendText(text = text, packageName = PKG_DOUBAO)) {
            call.resolve(result(true, "action_send_package"))
            return
        }

        val fallbackOpened = openSendText(text = text, chooser = true)
        call.resolve(result(fallbackOpened, if (fallbackOpened) "action_send_chooser" else "none"))
    }

    @PluginMethod
    fun openPackage(call: PluginCall) {
        val packageName = call.getString("packageName")?.trim()
        if (packageName.isNullOrEmpty()) {
            call.resolve(result(false, "invalid_args").apply {
                put("error", "packageName is required")
            })
            return
        }
        call.resolve(openPackage(packageName))
    }

    @PluginMethod
    fun launchIntent(call: PluginCall) {
        val action = call.getString("action")?.trim()?.takeIf { it.isNotEmpty() }
        val packageName = call.getString("packageName")?.trim()?.takeIf { it.isNotEmpty() }
        val className = call.getString("className")?.trim()?.takeIf { it.isNotEmpty() }
        val dataUri = call.getString("dataUri")?.trim()?.takeIf { it.isNotEmpty() }
        val mimeType = call.getString("mimeType")?.trim()?.takeIf { it.isNotEmpty() }
        val categories = call.getArray("categories")
        val extras = call.getObject("extras")
        val chooser = call.getBoolean("chooser") ?: false

        if (className != null && packageName == null) {
            call.resolve(result(false, "invalid_args").apply {
                put("error", "className requires packageName")
            })
            return
        }

        if (action == null && packageName == null && className == null && dataUri == null) {
            call.resolve(result(false, "invalid_args").apply {
                put("error", "At least one of action/packageName/className/dataUri is required")
            })
            return
        }

        try {
            val intent = if (action != null) Intent(action) else Intent()

            if (dataUri != null && mimeType != null) {
                intent.setDataAndType(Uri.parse(dataUri), mimeType)
            } else if (dataUri != null) {
                intent.data = Uri.parse(dataUri)
            } else if (mimeType != null) {
                intent.type = mimeType
            }

            if (packageName != null && className != null) {
                intent.component = ComponentName(packageName, className)
            } else if (packageName != null) {
                intent.setPackage(packageName)
            }

            applyCategories(intent, categories)
            applyExtras(intent, extras)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val launchIntent = if (chooser) {
                Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }

            context.startActivity(launchIntent)
            call.resolve(result(true, "custom_intent").apply {
                if (packageName != null) {
                    put("packageName", packageName)
                    put("installed", isPackageInstalled(packageName))
                }
                if (dataUri != null) {
                    put("url", dataUri)
                }
            })
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Custom intent launch failed: action=$action pkg=$packageName cls=$className uri=$dataUri",
                e,
            )
            call.resolve(result(false, "none").apply {
                if (packageName != null) {
                    put("packageName", packageName)
                    put("installed", isPackageInstalled(packageName))
                }
                put("error", e.message ?: "launch_failed")
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

    private fun tryOpenSystemSettings(context: Context): Boolean {
        return try {
            val intent = Intent(ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "System settings fallback launch failed", e)
            false
        }
    }

    private fun resolveCurrentDisplay(): Display? {
        val currentActivity = activity
        if (currentActivity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                currentActivity.display?.let { return it }
            }
            @Suppress("DEPRECATION")
            currentActivity.windowManager?.defaultDisplay?.let { return it }
        }

        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displays = manager?.displays ?: emptyArray()
        if (displays.isEmpty()) {
            return null
        }
        return displays.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY } ?: displays.firstOrNull()
    }

    private fun collectSupportedRefreshRates(display: Display): List<Int> {
        val output = mutableSetOf<Int>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display.supportedModes.forEach { mode ->
                val rounded = mode.refreshRate.roundToInt()
                if (rounded > 0) {
                    output.add(rounded)
                }
            }
        }

        val currentRounded = display.refreshRate.roundToInt()
        if (currentRounded > 0) {
            output.add(currentRounded)
        }

        return output.toList().sorted()
    }

    private fun tryOpenPackage(context: Context, packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Package launch failed: $packageName", e)
            false
        }
    }

    private fun tryOpenComponent(packageName: String, className: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Component launch failed: $packageName/$className", e)
            false
        }
    }

    private fun tryOpenUsageAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Usage-access settings launch failed", e)
            false
        }
    }

    private fun openPackage(packageName: String): JSObject {
        val installed = context.packageManager.getLaunchIntentForPackage(packageName) != null
        val opened = tryOpenPackage(context, packageName)
        return JSObject().apply {
            put("ok", opened)
            put("method", if (opened) "package_launch_intent" else "none")
            put("installed", installed)
            put("packageName", packageName)
        }
    }

    private fun openViewUri(uri: String, packageName: String? = null): Boolean {
        return openStandardIntent(
            action = Intent.ACTION_VIEW,
            uri = Uri.parse(uri),
            packageName = packageName,
        )
    }

    private fun openSendText(
        text: String,
        packageName: String? = null,
        chooser: Boolean = false,
    ): Boolean {
        return openStandardIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            packageName = packageName,
            chooser = chooser,
            extras = { putExtra(Intent.EXTRA_TEXT, text) },
        )
    }

    private fun openStandardIntent(
        action: String,
        uri: Uri? = null,
        type: String? = null,
        packageName: String? = null,
        addOpenableCategory: Boolean = false,
        chooser: Boolean = false,
        extras: (Intent.() -> Unit)? = null,
    ): Boolean {
        return try {
            val intent = Intent(action).apply {
                if (uri != null && type != null) {
                    setDataAndType(uri, type)
                } else if (uri != null) {
                    data = uri
                } else if (type != null) {
                    this.type = type
                }
                if (addOpenableCategory) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                if (!packageName.isNullOrBlank()) {
                    setPackage(packageName)
                }
                extras?.invoke(this)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val launchIntent = if (chooser) {
                Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Intent launch failed: action=$action pkg=$packageName uri=$uri", e)
            false
        }
    }

    private fun applyCategories(intent: Intent, categories: JSArray?) {
        if (categories == null) {
            return
        }
        for (index in 0 until categories.length()) {
            val value = categories.optString(index, "").trim()
            if (value.isNotEmpty()) {
                intent.addCategory(value)
            }
        }
    }

    private fun applyExtras(intent: Intent, extras: JSObject?) {
        if (extras == null) {
            return
        }

        val keys = extras.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = extras.opt(key)
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Number -> intent.putExtra(key, value.toDouble())
                is String -> intent.putExtra(key, value)
                null -> Unit
                else -> intent.putExtra(key, value.toString())
            }
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeUrl(rawUrl: String?): String? {
        val value = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (value.contains("://")) {
            value
        } else {
            "https://$value"
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

    private fun clipboardTextResult(
        ok: Boolean,
        hasText: Boolean,
        text: String? = null,
        error: String? = null,
    ): JSObject {
        return JSObject().apply {
            put("ok", ok)
            put("hasText", hasText)
            put("method", "primary_clip")
            if (text != null) {
                put("text", text)
            }
            if (error != null) {
                put("error", error)
            }
        }
    }
}
