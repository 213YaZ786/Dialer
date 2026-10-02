package com.dialer.app.core.calllog

import java.time.Instant
import java.time.ZoneId

/** One week of calls with someone, by what they were. */
data class WeekCalls(val talked: Int, val missed: Int) {
    val total: Int get() = talked + missed
}

/**
 * The calls with one person seen as a whole: how many, how long spoken
 * in all, how often week after week, and the hour they usually happen
 * at, when there is one. Read from the call log on the phone only.
 */
data class Rhythm(
    val calls: Int,
    val talkedSeconds: Long,
    val weeks: List<WeekCalls>,
    /** Start of the two hours most answered calls fall in, or null. */
    val usualHour: Int?
)

object Rhythms {

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** At least this many answered calls before an hour is called usual. */
    private const val MIN_FOR_HOUR = 5

    fun of(calls: List<CallEntry>, now: Long, zone: ZoneId = ZoneId.systemDefault(), weekCount: Int = 12): Rhythm {
        val weeks = MutableList(weekCount) { WeekCalls(0, 0) }
        for (call in calls) {
            val ago = ((now - call.date) / WEEK_MS).toInt()
            if (call.date > now || ago >= weekCount) continue
            val i = weekCount - 1 - ago
            val w = weeks[i]
            weeks[i] = if (call.duration > 0) w.copy(talked = w.talked + 1) else w.copy(missed = w.missed + 1)
        }
        val answered = calls.filter { it.duration > 0 }
        return Rhythm(
            calls = calls.size,
            talkedSeconds = answered.sumOf { it.duration },
            weeks = weeks,
            usualHour = usualHour(answered.map { Instant.ofEpochMilli(it.date).atZone(zone).hour })
        )
    }

    /**
     * The two hours of the day holding the most of [hours], if they hold
     * at least four in ten of them: a habit, not a coincidence.
     */
    internal fun usualHour(hours: List<Int>): Int? {
        if (hours.size < MIN_FOR_HOUR) return null
        val count = IntArray(24)
        hours.forEach { count[it]++ }
        val best = (0 until 24).maxBy { count[it] + count[(it + 1) % 24] }
        val held = count[best] + count[(best + 1) % 24]
        return best.takeIf { held * 10 >= hours.size * 4 }
    }
}
