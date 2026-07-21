package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtTextTest {
    @Test fun decodesPlainAsciiAsUtf8() {
        assertEquals("Hello", SrtText.decode("Hello".toByteArray(Charsets.UTF_8)))
    }

    @Test fun decodesValidUtf8Turkish() {
        val s = "çğşİ"
        assertEquals(s, SrtText.decode(s.toByteArray(Charsets.UTF_8)))
    }

    @Test fun fallsBackToWindows1254WhenNotUtf8() {
        val bytes = byteArrayOf(0xDD.toByte(), 0x79, 0x69)
        assertEquals("İyi", SrtText.decode(bytes))
    }

    @Test fun fallbackDecodesCommonTurkishLetters() {
        val bytes = byteArrayOf(0xE7.toByte(), 0xFE.toByte(), 0xF0.toByte(),
                                0xFD.toByte(), 0xF6.toByte(), 0xFC.toByte())
        assertEquals("çşğıöü", SrtText.decode(bytes))
    }
}
