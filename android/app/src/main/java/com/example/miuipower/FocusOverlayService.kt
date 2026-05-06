package com.example.miuipower

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class FocusOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var buttonView: TextView? = null
    private var panelView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification())
        showButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (buttonView == null) {
            showButton()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removePanel()
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showButton() {
        if (!Settings.canDrawOverlays(this) || buttonView != null) {
            return
        }

        val button = TextView(this).apply {
            text = "焦点"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(190, 128, 129))
            elevation = 12f
            setOnClickListener { refreshFocusInfo() }
        }

        val params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(160)
        }
        attachDrag(button, params)

        windowManager.addView(button, params)
        buttonView = button
    }

    private fun refreshFocusInfo() {
        Toast.makeText(this, "正在读取 dumpsys window...", Toast.LENGTH_SHORT).show()
        thread(name = "focus-window-reader") {
            val result = FocusWindowReader.read()
            mainHandler.post {
                val text = if (result.ok) {
                    result.lines.joinToString("\n")
                } else {
                    buildString {
                        appendLine(result.error.ifBlank { "读取失败" })
                        result.lines.takeIf { it.isNotEmpty() }?.let {
                            appendLine()
                            append(it.joinToString("\n"))
                        }
                    }.trim()
                }
                copyResult(text)
                showPanel(text.ifBlank { "没有可展示的焦点窗口信息" })
            }
        }
    }

    private fun showPanel(text: String) {
        removePanel()

        val content = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.rgb(41, 37, 40))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val close = TextView(this).apply {
            this.text = "关闭"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(190, 128, 129))
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { removePanel() }
        }
        val scroll = ScrollView(this).apply {
            addView(content)
            setBackgroundColor(Color.rgb(240, 238, 236))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(240, 238, 236))
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                close,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val params = WindowManager.LayoutParams(
            dp(310),
            dp(220),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(236)
        }

        windowManager.addView(panel, params)
        panelView = panel
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
    }

    private fun copyResult(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("window-focus", text))
        Toast.makeText(this, "焦点信息已复制", Toast.LENGTH_SHORT).show()
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) {
                        moved = true
                    }
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Window Focus Overlay",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("焦点窗口悬浮按钮")
            .setContentText("点击悬浮按钮读取 mCurrentFocus / mFocusedApp")
            .setOngoing(true)
            .build()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "focus_overlay"
        private const val NOTIFICATION_ID = 1501
    }
}
