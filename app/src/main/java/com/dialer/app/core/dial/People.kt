package com.dialer.app.core.dial

import com.dialer.app.core.calllog.CallEntry
import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.calllog.CallKind
import java.text.Normalizer

/** A contact with all its numbers, the first one the one called by default. */
data class Person(val id: Long, val name: String, val photo: String?, val starred: Boolean, val numbers: List<PhoneEntry>) {
    val number: String get() = numbers.first().number
}

/** Someone called often, saved or not. */
data class Frequent(val number: String, val person: Person?, val calls: Int)

object People {

    /** The numbers of the phone book folded into one entry per contact, in the book's order. */
    fun of(entries: List<PhoneEntry>): List<Person> =
        entries.groupBy { it.contactId }.map { (id, numbers) ->
            val first = numbers.first()
            Person(id, first.name, numbers.firstNotNullOfOrNull { it.photo }, numbers.any { it.starred }, numbers)
        }

    /** People whose name has a word starting with [query], or whose number holds its digits. */
    fun search(people: List<Person>, query: String): List<Person> {
        val q = plain(query.trim())
        if (q.isEmpty()) return people
        val digits = query.filter(Char::isDigit)
        return people.filter { p ->
            plain(p.name).split(' ', '-', '.').any { it.startsWith(q) } || plain(p.name).startsWith(q) ||
                (digits.length >= 2 && p.numbers.any { it.digits.contains(digits) })
        }
    }

    /**
     * The numbers talked with most over the last [days] days, calls that
     * were answered either way; favourites are left out, they have their
     * own place.
     */
    fun frequents(calls: List<CallEntry>, people: List<Person>, now: Long, days: Int = 60, limit: Int = 6): List<Frequent> {
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

    /** Lower case, accents gone: "Joëlle" is found by "joe". */
    fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
