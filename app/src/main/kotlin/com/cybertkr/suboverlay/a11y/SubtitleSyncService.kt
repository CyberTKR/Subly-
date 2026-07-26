package com.cybertkr.suboverlay.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cybertkr.suboverlay.overlay.OverlayService

class SubtitleSyncService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastRead = 0L
    private val poll = object : Runnable {
        override fun run() {
            readOnce()
            handler.postDelayed(this, 250L)
        }
    }

    override fun onServiceConnected() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == "com.netflix.mediaclient") readOnce()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    private fun readOnce() {
        val nowUp = SystemClock.uptimeMillis()
        if (nowUp - lastRead < 120L) return
        lastRead = nowUp
        val root = rootInActiveWindow ?: return
        if (root.packageName != "com.netflix.mediaclient") return
        OverlayService.markNetflixForeground()
        val sb = findByTag(root, "seekbar") ?: return
        val ri = sb.rangeInfo ?: return
        val posMs = ri.current.toLong()
        val totalMs = ri.max.toLong()
        if (posMs < 0 || totalMs <= 0) return
        OverlayService.bridgePosition(posMs, totalMs)
    }

    private fun findByTag(node: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.endsWith(tag) == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findByTag(c, tag)?.let { return it }
        }
        return null
    }
}
