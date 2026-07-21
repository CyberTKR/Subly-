package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineEngineTest {
    private val cues = listOf(
        SubtitleCue(1000, 3000, "a"),
        SubtitleCue(4000, 6000, "b"),
    )
    private val engine = TimelineEngine(cues)

    @Test fun beforeFirstCueReturnsNull() = assertNull(engine.cueAt(500))
    @Test fun insideFirstCue() = assertEquals("a", engine.cueAt(1500)?.text)
    @Test fun atStartInclusive() = assertEquals("a", engine.cueAt(1000)?.text)
    @Test fun atEndExclusive() = assertNull(engine.cueAt(3000))
    @Test fun inGapReturnsNull() = assertNull(engine.cueAt(3500))
    @Test fun insideSecondCue() = assertEquals("b", engine.cueAt(5000)?.text)
    @Test fun afterLastReturnsNull() = assertNull(engine.cueAt(9999))

    @Test fun overlappingReturnsEarliestActive() {
        val e = TimelineEngine(listOf(
            SubtitleCue(1000, 5000, "long"),
            SubtitleCue(3000, 6000, "short"),
        ))
        assertEquals("long", e.cueAt(3500)?.text)
    }
}
