package com.dialer.app.core.dial

import com.dialer.app.core.calllog.CallEntry
import com.dialer.app.core.calllog.CallKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleTest {

    private fun entry(id: Long, name: String, number: String, starred: Boolean = false) =
        PhoneEntry(id, name, number, T9.clean(number), null, starred)

    private val book = People.of(
        listOf(
            entry(1, "Joëlle Martin", "06 98 76 54 32"),
            entry(1, "Joëlle Martin", "01 23 45 67 89"),
            entry(2, "John Smith", "+33 6 12 34 56 78", starred = true)
        )
    )

    @Test
    fun numbersFoldIntoOnePerson() {
        assertEquals(2, book.size)
        assertEquals(2, book.first { it.id == 1L }.numbers.size)
    }

    @Test
    fun searchIgnoresAccentsAndFindsDigits() {
        assertEquals(listOf(1L), People.search(book, "joel").map { it.id })
        assertEquals(listOf(1L), People.search(book, "mart").map { it.id })
        assertEquals(listOf(2L), People.search(book, "3361").map { it.id })
    }

    @Test
    fun frequentsSkipFavouritesAndSingleCalls() {
        val now = 1_000_000_000_000L
        fun call(id: Long, number: String) = CallEntry(id, number, null, CallKind.OUTGOING, now - id * 1000, 30, false, null, false)
        val calls = listOf(call(1, "0698765432"), call(2, "+33698765432"), call(3, "0612345678"), call(4, "0612345678"), call(5, "0700000000"))
        val frequents = People.frequents(calls, book, now)
        assertEquals(1, frequents.size)
        assertEquals(1L, frequents.single().person?.id)
        assertEquals(2, frequents.single().calls)
    }
}
