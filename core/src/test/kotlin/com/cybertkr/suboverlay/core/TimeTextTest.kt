package com.cybertkr.suboverlay.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeTextTest {
    @Test fun parsesMinutesSeconds() {
        assertEquals(2_503_000L, TimeText.parseToMs("41:43"))
    }

    @Test fun parsesHoursMinutesSeconds() {
        assertEquals(3_723_000L, TimeText.parseToMs("1:02:03"))
    }

    @Test fun nonNumericReturnsNull() {
        assertNull(TimeText.parseToMs("abc"))
    }

    @Test fun singlePartReturnsNull() {
        assertNull(TimeText.parseToMs("12"))
    }

    @Test fun emptyReturnsNull() {
        assertNull(TimeText.parseToMs(""))
    }
}
