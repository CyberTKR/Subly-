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
        fun bridgePosition(posMs: Long, totalMs: Long) {
            val s = instance ?: return
            if (totalMs > 0L) s.totalMs = totalMs
            val now = android.os.SystemClock.elapsedRealtime()

            if (s.lastReadWall == 0L || now - s.lastReadWall >= 400L) {
                if (s.lastReadWall != 0L) {
                    val advanced = posMs - s.lastReadPos
                    if (advanced in -250L..250L) {
                        if (s.clock.isRunning) s.clock.pause(now)
                    } else {
                        if (!s.clock.isRunning) s.clock.resume(now)
                    }
                }
                s.lastReadPos = posMs
                s.lastReadWall = now
            }

            if (!s.autoSynced) {
                s.clock.start(now, posMs + s.userOffsetMs)
                s.autoSynced = true
            } else if (kotlin.math.abs(posMs + s.userOffsetMs - s.clock.position(now)) > 1000L) {
                s.clock.syncTo(now, posMs + s.userOffsetMs)
            }
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var subtitleView: SubtitleView
    private lateinit var bubble: ControlBubble
    private var totalMs: Long = 0L
    private var autoSynced = false
    private var lastReadPos = 0L
    private var lastReadWall = 0L
    private var userOffsetMs = 0L
    private val settings by lazy { com.cybertkr.suboverlay.SettingsStore(this) }

    val clock = PlaybackClock()
    private var engine = TimelineEngine(emptyList())
    private val ticker = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val pos = clock.position(SystemClock.elapsedRealtime())
            subtitleView.setLine(engine.cueAt(pos)?.text)
            if (this@OverlayService::bubble.isInitialized) {
                bubble.updateTime(pos.coerceIn(0, totalMs), totalMs)
                bubble.setPlaying(clock.isRunning)
            }
            ticker.postDelayed(this, 30)
        }
    }

    fun onSyncStart(atPositionMs: Long) {
        engine = TimelineEngine(SubtitleRepository.cues)
        totalMs = SubtitleRepository.cues.maxOfOrNull { it.endMs } ?: 0L
        clock.start(SystemClock.elapsedRealtime(), atPositionMs)
        autoSynced = true
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
    }

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countingDown = false

    private fun beginCountdown(offsetMs: Long) {
        if (countingDown) return
        countingDown = true
        val steps = listOf("3", "2", "1", "BAŞLA")
        var i = 0
        val r = object : Runnable {
            override fun run() {
                if (i < steps.size) {
                    subtitleView.setLine(steps[i]); i++
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    countingDown = false
                    onSyncStart(offsetMs)
                }
            }
        }
        countdownHandler.post(r)
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
            override fun onSync() { beginCountdown(SubtitleRepository.startOffsetMs) }
            override fun onTogglePlay() {
                val now = SystemClock.elapsedRealtime()
                if (clock.isRunning) clock.pause(now) else clock.resume(now)
                bubble.setPlaying(clock.isRunning)
            }
            override fun onRewind() { clock.nudge(-10_000, SystemClock.elapsedRealtime()) }
            override fun onForward() { clock.nudge(+10_000, SystemClock.elapsedRealtime()) }
            override fun onRewindMin() { clock.nudge(-60_000, SystemClock.elapsedRealtime()) }
            override fun onForwardMin() { clock.nudge(+60_000, SystemClock.elapsedRealtime()) }
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
        bubble.setPlaying(clock.isRunning)
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
        countdownHandler.removeCallbacksAndMessages(null)
        if (this::bubble.isInitialized) wm.removeView(bubble.view())
        if (this::subtitleView.isInitialized) wm.removeView(subtitleView)
        instance = null
        super.onDestroy()
    }
}
