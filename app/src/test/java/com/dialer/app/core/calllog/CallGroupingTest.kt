package com.dialer.app.core.calllog

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallGroupingTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun at(day: Int, hour: Int): Long =
        LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun call(id: Long, number: String, date: Long, kind: CallKind = CallKind.INCOMING, hidden: Boolean = false) =
        CallEntry(id, number, null, kind, date, 0, false, null, hidden)

    @Test
    fun followingCallsFromOneNumberFold() {
        val sections = CallGrouping.sections(
            listOf(
                call(1, "0612345678", at(1, 10)),
                call(2, "+33 6 12 34 56 78", at(1, 11), CallKind.MISSED),
                call(3, "0698765432", at(1, 12))
            ),
            zone
        )
        assertEquals(1, sections.size)
        assertEquals(listOf(1, 2), sections[0].groups.map { it.count })
        assertEquals(CallKind.MISSED, sections[0].groups[1].first.kind)
    }

    @Test
    fun anotherNumberBetweenBreaksTheFold() {
        val groups = CallGrouping.sections(
            listOf(call(1, "0611111111", at(1, 10)), call(2, "0622222222", at(1, 11)), call(3, "0611111111", at(1, 12))),
            zone
        ).single().groups
        assertEquals(3, groups.size)
    }

    @Test
    fun daysAreSeparateNewestFirst() {
        val sections = CallGrouping.sections(listOf(call(1, "0611111111", at(1, 10)), call(2, "0611111111", at(2, 10))), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)), sections.map { it.day })
    }

    @Test
    fun hiddenNumbersOnlyMatchEachOther() {
        assertTrue(CallGrouping.sameNumber(call(1, "", 0, hidden = true), call(2, "", 0, hidden = true)))
        assertFalse(CallGrouping.sameNumber(call(1, "", 0, hidden = true), call(2, "0611111111", 0)))
    }

    @Test
    fun shortNumbersMustBeEqual() {
        assertTrue(CallGrouping.sameDigits("3179", "3179"))
        assertFalse(CallGrouping.sameDigits("112", "0112"))
        assertFalse(CallGrouping.sameDigits("", ""))
    }
}
