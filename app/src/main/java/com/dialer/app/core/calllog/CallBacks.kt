package com.dialer.app.core.calllog

/** Someone who called and is still waiting: their last missed call and how many. */
data class CallBack(val last: CallEntry, val missed: Int)

/**
 * The people still waiting for a call back: a missed call in the last
 * [days] days with no call since, either way, that was answered. Newest
 * first; hidden numbers left out, there is no one to call.
 */
object CallBacks {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun pending(calls: List<CallEntry>, now: Long, days: Int = 7, limit: Int = 10): List<CallBack> {
        val since = now - days * DAY_MS
        val result = ArrayList<CallBack>()
        val done = HashSet<String>()
        for (call in calls.sortedByDescending { it.date }) {
            if (call.hidden) continue
            val who = call.key.removePrefix("+").takeLast(9)
            if (!done.add(who)) continue
            // The newest call with them decides: answered or placed, nothing waits.
            if (call.kind != CallKind.MISSED || call.date < since) continue
            val missed = calls.count { it.kind == CallKind.MISSED && !it.hidden && it.date >= since && CallGrouping.sameDigits(it.key, call.key) }
            result += CallBack(call, missed)
            if (result.size == limit) break
        }
        return result
    }
}
