package com.cybertkr.suboverlay.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ControlBubble(
    private val context: Context,
    private val wm: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onNudge(deltaMs: Long); fun onClose()
    }

    private val density = context.resources.displayMetrics.density
    private fun px(v: Float) = (v * density).toInt()

    private val fabSize = px(46f)
    private val petSize = px(38f)
    private val radius = px(62f)
    private val boxW = radius + petSize + px(10f)
    private val boxH = radius * 2 + petSize

    private var isOpen = false

    private var idleFabAlpha = 0.4f
    private var idleDelayMs = 4000L

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { if (isOpen) collapse() }
    private val idleFade = Runnable {
        if (!isOpen) {
            fab.animate().alpha(idleFabAlpha).setDuration(400).start()
            time.animate().alpha(0f).setDuration(400).start()
        }
    }

    private val root = FrameLayout(context).apply {
        minimumWidth = boxW; minimumHeight = boxH; clipChildren = false
    }
    private val cx get() = px(5f) + fabSize / 2f
    private val cy get() = boxH / 2f

    private val time = TextView(context).apply {
        textSize = 9f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK); text = "00:00 / -00:00"
    }
    private val fab = android.widget.ImageView(context).apply {
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2E7CF6"), Color.parseColor("#14B8A6"))
        ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        setImageResource(com.cybertkr.suboverlay.R.drawable.ic_flow_fab)
        val p = px(6f); setPadding(p, p, p, p)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        isClickable = true
    }
    private fun petal(label: String, color: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = circle(color); visibility = View.GONE
        setOnClickListener {
            animate().scaleX(0.8f).scaleY(0.8f).setDuration(80).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            onClick(); scheduleHide()
        }
    }
    private val pMinus = petal("−", Color.argb(235, 55, 55, 55)) { callbacks.onNudge(-100) }
    private val pPlus  = petal("+", Color.argb(235, 55, 55, 55)) { callbacks.onNudge(+100) }
    private val pClose = petal("✕", Color.argb(235, 190, 55, 55)) { callbacks.onClose() }
    private val petals = listOf(pMinus, pPlus, pClose)
    private val angles = listOf(-45.0, 0.0, 45.0)

    init {
        params.width = boxW
        params.height = boxH
        root.addView(time, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        time.x = px(2f).toFloat(); time.y = cy - fabSize / 2f - px(22f)
        for (p in petals) {
            root.addView(p, FrameLayout.LayoutParams(petSize, petSize))
            p.x = cx - petSize / 2f; p.y = cy - petSize / 2f
        }
        root.addView(fab, FrameLayout.LayoutParams(fabSize, fabSize))
        fab.x = cx - fabSize / 2f; fab.y = cy - fabSize / 2f
        fab.alpha = 1f
        fab.setOnClickListener { if (isOpen) collapse() else expand() }
        enableDrag(fab)
        scheduleIdleFade()
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun expand() {
        isOpen = true; fab.alpha = 1f; time.alpha = 1f
        hideHandler.removeCallbacks(idleFade)
        for (i in petals.indices) {
            val a = Math.toRadians(angles[i])
            val tx = cx + radius * cos(a) - petSize / 2f
            val ty = cy + radius * sin(a) - petSize / 2f
            val p = petals[i]
            p.visibility = View.VISIBLE; p.alpha = 0f
            p.x = cx - petSize / 2f; p.y = cy - petSize / 2f
            p.animate().x(tx.toFloat()).y(ty.toFloat()).alpha(1f).setDuration(180).start()
        }
        scheduleHide()
    }

    private fun collapse() {
        isOpen = false
        for (p in petals) {
            p.animate().x(cx - petSize / 2f).y(cy - petSize / 2f).alpha(0f).setDuration(150)
                .withEndAction { p.visibility = View.GONE }.start()
        }
        hideHandler.removeCallbacks(hideRunnable)
        scheduleIdleFade()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000)
    }

    private fun scheduleIdleFade() {
        hideHandler.removeCallbacks(idleFade)
        if (idleDelayMs <= 0L) { wake(); return }
        hideHandler.postDelayed(idleFade, idleDelayMs)
    }

    fun applyIdleConfig(alphaPercent: Int, fadeSeconds: Int) {
        idleFabAlpha = alphaPercent.coerceIn(0, 100) / 100f
        idleDelayMs = fadeSeconds.coerceAtLeast(0) * 1000L
        if (isOpen) return
        hideHandler.removeCallbacks(idleFade)
        fab.animate().alpha(1f).setDuration(150).start()
        time.animate().alpha(1f).setDuration(150).start()
        if (idleDelayMs > 0L) scheduleIdleFade()
    }

    private fun wake() {
        hideHandler.removeCallbacks(idleFade)
        fab.animate().alpha(1f).setDuration(120).start()
        time.animate().alpha(1f).setDuration(120).start()
    }

    fun updateTime(posMs: Long, totalMs: Long) {
        val remaining = (totalMs - posMs).coerceAtLeast(0)
        time.text = "${fmt(posMs)} / -${fmt(remaining)}"
    }

    fun reclampToEdge() {
        params.x = clampX(params.x); params.y = clampY(params.y)
        if (root.isAttachedToWindow) wm.updateViewLayout(root, params)
    }
    private fun clampX(x: Int) = x.coerceIn(0, (context.resources.displayMetrics.widthPixels - boxW).coerceAtLeast(0))
    private fun clampY(y: Int) = y.coerceIn(0, (context.resources.displayMetrics.heightPixels - boxH).coerceAtLeast(0))

    private fun fmt(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(target: View) {
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        target.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { initX = params.x; initY = params.y; touchX = e.rawX; touchY = e.rawY; moved = false; wake(); false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - touchX) > 22 || abs(e.rawY - touchY) > 22) moved = true
                    params.x = clampX(initX + (e.rawX - touchX).toInt())
                    params.y = clampY(initY + (e.rawY - touchY).toInt())
                    if (root.isAttachedToWindow) wm.updateViewLayout(root, params)
                    if (moved && isOpen) collapse()
                    moved
                }
                MotionEvent.ACTION_UP -> { if (moved) scheduleIdleFade(); moved }
                else -> false
            }
        }
    }

    fun view(): View = root
}
