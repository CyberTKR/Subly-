package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtParserTest {
    @Test fun parsesTwoCues() {
        val raw = """
            1
            00:00:01,000 --> 00:00:03,500
            Merhaba dünya

            2
            00:00:04,000 --> 00:00:06,000
            İkinci satır
        """.trimIndent()

        val r = SrtParser.parse(raw)

        assertEquals(2, r.cues.size)
        assertEquals(0, r.skipped)
        assertEquals(1000L, r.cues[0].startMs)
        assertEquals(3500L, r.cues[0].endMs)
        assertEquals("Merhaba dünya", r.cues[0].text)
        assertEquals(4000L, r.cues[1].startMs)
        assertEquals("İkinci satır", r.cues[1].text)
    }

    @Test fun joinsMultiLineTextWithNewline() {
        val raw = "1\n00:00:01,000 --> 00:00:02,000\nsatır a\nsatır b\n"
        val r = SrtParser.parse(raw)
        assertEquals("satır a\nsatır b", r.cues[0].text)
    }
}
