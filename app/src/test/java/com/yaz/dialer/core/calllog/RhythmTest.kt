package com.yaz.dialer.core.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RhythmTest {

    private val now = LocalDateTime.of(2026, 10, 2, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun call(daysAgo: Long, seconds: Long, hour: Int = 12): CallEntry {
        val at = LocalDateTime.of(2026, 10, 2, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli() - daysAgo * day
        return CallEntry(daysAgo, "0612345678", null, if (seconds > 0) CallKind.INCOMING else CallKind.MISSED, at, seconds, false, null, false)
    }

    @Test
    fun countsAndTimeSpoken() {
        val r = Rhythms.of(listOf(call(1, 60), call(2, 0), call(3, 120)), now, ZoneOffset.UTC)
        assertEquals(3, r.calls)
        assertEquals(180L, r.talkedSeconds)
    }

    @Test
    fun weeksRunFromOldestToThisOne() {
        val r = Rhythms.of(listOf(call(0, 30), call(1, 0), call(8, 30), call(200, 30)), now, ZoneOffset.UTC)
        assertEquals(12, r.weeks.size)
        assertEquals(WeekCalls(1, 1), r.weeks[11])
        assertEquals(WeekCalls(1, 0), r.weeks[10])
        assertEquals(3, r.weeks.sumOf { it.total })
    }

    @Test
    fun aHabitIsFoundOnlyWhenThereIsOne() {
        assertEquals(18, Rhythms.usualHour(listOf(18, 19, 18, 19, 18, 9, 12)))
        assertNull(Rhythms.usualHour(listOf(18, 19, 18)))
        assertNull(Rhythms.usualHour(listOf(1, 4, 7, 10, 13, 16, 19, 22)))
    }

    @Test
    fun aHabitMayCrossMidnight() {
        assertEquals(23, Rhythms.usualHour(listOf(23, 0, 23, 0, 23)))
    }
}
