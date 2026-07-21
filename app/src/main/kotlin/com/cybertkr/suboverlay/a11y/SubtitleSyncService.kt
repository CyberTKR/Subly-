package com.cybertkr.suboverlay.a11y

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cybertkr.suboverlay.overlay.OverlayService

class SubtitleSyncService : AccessibilityService() {

    private var lastProcess = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != "com.netflix.mediaclient") return
        val nowUp = SystemClock.uptimeMillis()
        if (nowUp - lastProcess < 150L) return
        lastProcess = nowUp
        val root = rootInActiveWindow ?: return
        val sb = findByTag(root, "seekbar") ?: return
        val ri = sb.rangeInfo ?: return
        val posMs = ri.current.toLong()
        val totalMs = ri.max.toLong()
        if (posMs < 0 || totalMs <= 0) return
        OverlayService.bridgePosition(posMs, totalMs)
    }

    override fun onInterrupt() {}

    private fun findByTag(node: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.endsWith(tag) == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findByTag(c, tag)?.let { return it }
        }
        return null
    }
}
