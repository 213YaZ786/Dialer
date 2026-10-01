package com.dialer.app.data.contacts

import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.dial.PhoneEntry

/**
 * The contacts by number, to put a name and a face on a call: the same
 * number written 06… or +33 6… finds the same contact.
 */
class PhoneIndex(entries: List<PhoneEntry>) {

    private val byTail = HashMap<String, PhoneEntry>()
    private val exact = HashMap<String, PhoneEntry>()

    init {
        for (entry in entries) {
            val digits = entry.digits.removePrefix("+")
            exact.putIfAbsent(digits, entry)
            if (digits.length >= 9) byTail.putIfAbsent(digits.takeLast(9), entry)
        }
    }

    fun find(digits: String): PhoneEntry? {
        val d = digits.removePrefix("+")
        if (d.isEmpty()) return null
        return exact[d] ?: if (d.length >= 9) byTail[d.takeLast(9)] else null
    }

    /** All the numbers of one contact match the same way, for the call screen. */
    fun matches(entry: PhoneEntry, digits: String): Boolean = CallGrouping.sameDigits(entry.digits, digits)
}
