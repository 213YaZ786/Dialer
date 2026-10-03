package com.yaz.dialer.core.calllog

import com.yaz.dialer.core.dial.Person

/** Someone called often, saved or not. */
data class Frequent(val number: String, val person: Person?, val calls: Int)

object Frequents {

    /**
     * The numbers talked with most over the last [days] days, calls that
     * were answered either way; favourites are left out, they have their
     * own place.
     */
    fun of(calls: List<CallEntry>, people: List<Person>, now: Long, days: Int = 60, limit: Int = 6): List<Frequent> {
        val since = now - days * 86_400_000L
        val counted = calls
            .filter { it.date >= since && !it.hidden && it.duration > 0 && (it.kind == CallKind.INCOMING || it.kind == CallKind.OUTGOING) }
            .groupBy { it.key.removePrefix("+").takeLast(9) }
            .map { (_, group) -> group }
            .sortedByDescending { it.size }
        return counted.mapNotNull { group ->
            val number = group.first().number
            val person = people.firstOrNull { p -> p.numbers.any { CallGrouping.sameDigits(it.digits, group.first().key) } }
            if (person?.starred == true) null else Frequent(number, person, group.size)
        }.filter { it.calls >= 2 }.take(limit)
    }
}
