package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {
    @Test fun startsAtZeroAndAdvancesWithTime() {
        val c = PlaybackClock()
        c.start(nowMs = 10_000, atPositionMs = 0)
        assertTrue(c.isRunning)
        assertEquals(0L, c.position(10_000))
        assertEquals(2_000L, c.position(12_000))
    }

    @Test fun startWithOffsetForMidMovie() {
        val c = PlaybackClock()
        c.start(nowMs = 10_000, atPositionMs = 750_000)
        assertEquals(750_000L, c.position(10_000))
        assertEquals(751_000L, c.position(11_000))
    }

    @Test fun pauseFreezesResumeContinues() {
        val c = PlaybackClock()
        c.start(nowMs = 0, atPositionMs = 0)
        c.pause(nowMs = 5_000)
        assertFalse(c.isRunning)
        assertEquals(5_000L, c.position(9_999))
        c.resume(nowMs = 20_000)
        assertEquals(5_000L, c.position(20_000))
        assertEquals(6_000L, c.position(21_000))
    }

    @Test fun nudgeShiftsPositionForward() {
        val c = PlaybackClock()
        c.start(nowMs = 0, atPositionMs = 0)
        assertEquals(1_000L, c.position(1_000))
        c.nudge(deltaMs = 100, nowMs = 1_000)
        assertEquals(1_100L, c.position(1_000))
        assertEquals(1_600L, c.position(1_500))
    }

    @Test fun nudgeWorksWhilePaused() {
        val c = PlaybackClock()
        c.start(nowMs = 0, atPositionMs = 3_000)
        c.pause(nowMs = 0)
        c.nudge(deltaMs = -100, nowMs = 0)
        assertEquals(2_900L, c.position(0))
    }

    @Test fun syncToSetsAbsolutePositionAndKeepsRunning() {
        val c = PlaybackClock()
        c.start(nowMs = 0, atPositionMs = 0)
        assertEquals(5_000L, c.position(5_000))
        c.syncTo(nowMs = 5_000, positionMs = 60_000)
        assertEquals(60_000L, c.position(5_000))
        assertTrue(c.isRunning)
        assertEquals(61_000L, c.position(6_000))
    }

    @Test fun syncToWhilePausedKeepsPositionFrozen() {
        val c = PlaybackClock()
        c.start(nowMs = 0, atPositionMs = 0)
        c.pause(nowMs = 1_000)
        c.syncTo(nowMs = 1_000, positionMs = 42_000)
        assertFalse(c.isRunning)
        assertEquals(42_000L, c.position(5_000))
    }
}
