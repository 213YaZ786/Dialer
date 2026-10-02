package com.dialer.app.core.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBacksTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private var id = 0L

    private fun call(number: String, kind: CallKind, hoursAgo: Long, hidden: Boolean = false) =
        CallEntry(id++, number, null, kind, now - hoursAgo * hour, if (kind == CallKind.MISSED) 0 else 30, false, null, hidden)

    @Test
    fun aMissedCallWaitsUntilAnsweredOrCalledBack() {
        val calls = listOf(
            call("0612345678", CallKind.MISSED, 1),
            call("0612345678", CallKind.MISSED, 3),
            call("0698765432", CallKind.MISSED, 5),
            call("+33698765432", CallKind.OUTGOING, 2),
            call("0123456789", CallKind.MISSED, 4),
            call("0123456789", CallKind.INCOMING, 6)
        )
        val pending = CallBacks.pending(calls, now)
        assertEquals(listOf("0612345678", "0123456789"), pending.map { it.last.number })
        assertEquals(2, pending.first().missed)
    }

    @Test
    fun oldAndHiddenCallsDoNotWait() {
        val calls = listOf(call("0612345678", CallKind.MISSED, 24 * 8), call("", CallKind.MISSED, 1, hidden = true))
        assertTrue(CallBacks.pending(calls, now).isEmpty())
    }
}
