package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtParserRobustnessTest {
    @Test fun handlesBomAndCrlf() {
        val raw = "﻿1\r\n00:00:01,000 --> 00:00:02,000\r\nselam\r\n"
        val r = SrtParser.parse(raw)
        assertEquals(1, r.cues.size)
        assertEquals("selam", r.cues[0].text)
    }

    @Test fun skipsBlockWithoutTimestamp() {
        val raw = "1\nbozuk blok, zaman yok\n\n2\n00:00:03,000 --> 00:00:04,000\ngeçerli"
        val r = SrtParser.parse(raw)
        assertEquals(1, r.cues.size)
        assertEquals(1, r.skipped)
        assertEquals("geçerli", r.cues[0].text)
    }

    @Test fun acceptsDotMillisSeparator() {
        val raw = "1\n00:00:01.250 --> 00:00:02.500\nnokta ayraç"
        val r = SrtParser.parse(raw)
        assertEquals(1250L, r.cues[0].startMs)
        assertEquals(2500L, r.cues[0].endMs)
    }

    @Test fun stripsHtmlAndAssTags() {
        val raw = "1\n00:00:01,000 --> 00:00:02,000\n<i>Merhaba</i> {\\an8}dünya\n\n" +
            "2\n00:00:03,000 --> 00:00:04,000\n<i>tek satır"
        val r = SrtParser.parse(raw)
        assertEquals("Merhaba dünya", r.cues[0].text)
        assertEquals("tek satır", r.cues[1].text)
    }

    @Test fun preservesOverlappingCues() {
        val raw = "1\n00:00:01,000 --> 00:00:05,000\na\n\n2\n00:00:03,000 --> 00:00:06,000\nb"
        val r = SrtParser.parse(raw)
        assertEquals(2, r.cues.size)
        assertEquals(1000L, r.cues[0].startMs)
        assertEquals(3000L, r.cues[1].startMs)
    }
}
