package com.yaz.dialer.feature.recents

import org.junit.Assert.assertEquals
import org.junit.Test

// JUnit's assertEquals on CallText's labels (https://github.com/junit-team/junit4/blob/main/src/main/java/org/junit/Assert.java).
class CallLabelsTest {

    @Test fun shortAndMinuteDurations() {
        assertEquals("0 s", durationLabel(0))
        assertEquals("45 s", durationLabel(45))
        assertEquals("1 min", durationLabel(60))
        assertEquals("3 min 20 s", durationLabel(200))
        assertEquals("59 min 59 s", durationLabel(3599))
    }

    // JUnit's assertEquals (https://github.com/junit-team/junit4/blob/main/src/main/java/org/junit/Assert.java).
    @Test fun anHourAndMoreReadInHoursAndMinutes() {
        assertEquals("1 h 00 min", durationLabel(3600))
        assertEquals("1 h 02 min", durationLabel(3753))
        assertEquals("27 h 46 min", durationLabel(100_000))
    }

    @Test fun aNegativeDurationReadsZero() {
        assertEquals("0 s", durationLabel(-5))
    }

    @Test fun aGroupCountStopsAtNinetyNinePlus() {
        assertEquals("×2", countLabel(2))
        assertEquals("×99", countLabel(99))
        assertEquals("×99+", countLabel(100))
        assertEquals("×99+", countLabel(5000))
    }
}
