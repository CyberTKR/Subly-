package com.cybertkr.suboverlay.core

class PlaybackClock {
    private var basePos = 0L
    private var anchorNow = 0L
    private var running = false

    val isRunning: Boolean get() = running

    fun start(nowMs: Long, atPositionMs: Long) {
        basePos = atPositionMs; anchorNow = nowMs; running = true
    }

    fun pause(nowMs: Long) {
        basePos = position(nowMs); anchorNow = nowMs; running = false
    }

    fun resume(nowMs: Long) {
        anchorNow = nowMs; running = true
    }

    fun nudge(deltaMs: Long, nowMs: Long) {
        basePos = position(nowMs) + deltaMs; anchorNow = nowMs
    }

    fun position(nowMs: Long): Long =
        if (running) basePos + (nowMs - anchorNow) else basePos

    fun syncTo(nowMs: Long, positionMs: Long) {
        basePos = positionMs
        anchorNow = nowMs
    }
}
