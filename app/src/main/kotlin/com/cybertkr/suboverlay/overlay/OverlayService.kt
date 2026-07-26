package com.cybertkr.suboverlay.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cybertkr.suboverlay.MainActivity
import com.cybertkr.suboverlay.R
import com.cybertkr.suboverlay.SubtitleRepository
import com.cybertkr.suboverlay.core.PlaybackClock
import com.cybertkr.suboverlay.core.TimelineEngine

class OverlayService : LifecycleService() {

    companion object {
        @Volatile private var instance: OverlayService? = null

        fun applyLiveConfig(alphaPercent: Int, fadeSeconds: Int) {
            val s = instance ?: return
            s.mainHandler.post {
                if (s::bubble.isInitialized) s.bubble.applyIdleConfig(alphaPercent, fadeSeconds)
            }
        }

        fun markNetflixForeground() {
            instance?.lastNetflixSeen = android.os.SystemClock.elapsedRealtime()
        }

        fun bridgePosition(posMs: Long, totalMs: Long) {
            val s = instance ?: return
            if (totalMs > 0L) s.totalMs = totalMs
            val now = android.os.SystemClock.elapsedRealtime()
            val target = posMs + s.userOffsetMs
            if (!s.autoSynced) {
                s.clock.start(now, target)
                s.autoSynced = true
            } else if (kotlin.math.abs(target - s.clock.position(now)) > 1000L) {
                s.clock.syncTo(now, target)
            }
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var subtitleView: SubtitleView
    private lateinit var bubble: ControlBubble
    private var totalMs: Long = 0L
    private var autoSynced = false
    @Volatile private var lastNetflixSeen = 0L
    private var userOffsetMs = 0L
    private val settings by lazy { com.cybertkr.suboverlay.SettingsStore(this) }

    val clock = PlaybackClock()
    private var engine = TimelineEngine(emptyList())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ticker = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val pos = clock.position(now)
            val hidden = now - lastNetflixSeen >= 3000L
            subtitleView.setLine(if (hidden) null else engine.cueAt(pos)?.text)
            if (this@OverlayService::bubble.isInitialized) {
                bubble.updateTime(pos.coerceIn(0, totalMs), totalMs)
            }
            ticker.postDelayed(this, 30)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        userOffsetMs = com.cybertkr.suboverlay.SubtitleRepository.savedOffsetMs
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addSubtitleWindow()
        engine = TimelineEngine(SubtitleRepository.cues)
        totalMs = SubtitleRepository.cues.maxOfOrNull { it.endMs } ?: 0L
        ticker.post(tickRunnable)
        addBubble()
        lifecycleScope.launch {
            val alpha = settings.getIdleAlphaPercent()
            val secs = settings.getFadeSeconds()
            if (this@OverlayService::bubble.isInitialized) bubble.applyIdleConfig(alpha, secs)
        }
    }

    private fun addBubble() {
        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 160 }

        bubble = ControlBubble(this, wm, bubbleParams, object : ControlBubble.Callbacks {
            override fun onNudge(deltaMs: Long) {
                val now = android.os.SystemClock.elapsedRealtime()
                userOffsetMs += deltaMs
                clock.nudge(deltaMs, now)
                val t = com.cybertkr.suboverlay.SubtitleRepository.title
                if (t != null) lifecycleScope.launch { settings.setOffset(t, userOffsetMs) }
            }
            override fun onClose() { stopSelf() }
        })
        wm.addView(bubble.view(), bubbleParams)
    }

    private fun addSubtitleWindow() {
        subtitleView = SubtitleView(this)
        val subtitleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 160
        }
        wm.addView(subtitleView, subtitleParams)
    }

    private fun startAsForeground() {
        val channelId = "suboverlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "SubOverlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (this::bubble.isInitialized) bubble.reclampToEdge()
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tickRunnable)
        if (this::bubble.isInitialized) wm.removeView(bubble.view())
        if (this::subtitleView.isInitialized) wm.removeView(subtitleView)
        instance = null
        super.onDestroy()
    }
}
