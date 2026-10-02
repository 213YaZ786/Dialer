package com.dialer.app.core.calllog

import com.dialer.app.core.dial.T9
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What happened to a call, in the words the list needs. */
enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL }

/** One line of Android's call log. */
data class CallEntry(
    val id: Long,
    /** As the log has it, empty when the number was hidden. */
    val number: String,
    /** The name saved in the log when the call happened, if any. */
    val cachedName: String?,
    val kind: CallKind,
    /** When the call started, epoch milliseconds. */
    val date: Long,
    /** Seconds spoken, 0 for a call that was not answered. */
    val duration: Long,
    /** A missed call the user has not seen yet. */
    val isNew: Boolean,
    /** The city or country Android tells from the number, without any network. */
    val location: String?,
    /** Hidden, payphone or unknown: no number to call back. */
    val hidden: Boolean
) {
    /** The digits, to compare numbers written differently. */
    val key: String get() = if (hidden) "hidden" else T9.clean(number)
}

/** Calls in a row with one number on one day: one line in Recents. */
data class CallGroup(val calls: List<CallEntry>) {
    val first: CallEntry get() = calls.first()
    val count: Int get() = calls.size
    val unseen: Boolean get() = calls.any { it.isNew && it.kind == CallKind.MISSED }
    /** The last three kinds, newest first, for the little arrows. */
    val kinds: List<CallKind> get() = calls.take(3).map { it.kind }
}

/** A day of calls under its heading. */
data class DaySection(val day: LocalDate, val groups: List<CallGroup>)

object CallGrouping {

    /**
     * Calls newest first, cut by day; on each day, calls following each
     * other with the same number fold into one line, as phones do.
     */
    fun sections(entries: List<CallEntry>, zone: ZoneId = ZoneId.systemDefault()): List<DaySection> {
        val days = LinkedHashMap<LocalDate, MutableList<CallGroup>>()
        var current: MutableList<CallEntry>? = null
        var currentDay: LocalDate? = null
        for (entry in entries.sortedByDescending(CallEntry::date)) {
            val day = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()
            val run = current
            if (run != null && day == currentDay && sameNumber(run.last(), entry)) {
                run += entry
                continue
            }
            current = mutableListOf(entry)
            currentDay = day
            days.getOrPut(day) { mutableListOf() } += CallGroup(current)
        }
        return days.map { (day, groups) -> DaySection(day, groups) }
    }

    /** Equal digits, or the same last nine: 06 12… and +33 6 12… are one number. */
    fun sameNumber(a: CallEntry, b: CallEntry): Boolean {
        if (a.hidden || b.hidden) return a.hidden && b.hidden
        return sameDigits(a.key, b.key)
    }

    fun sameDigits(a: String, b: String): Boolean = T9.sameDigits(a, b)
}
