package com.yaz.dialer.feature.dialpad

import org.junit.Assert.assertEquals
import org.junit.Test

// Ported from libphonenumber's AsYouTypeFormatterTest (digit by digit, the formatter's last answer
// is checked): https://github.com/google/libphonenumber/blob/master/java/libphonenumber/test/com/google/i18n/phonenumbers/AsYouTypeFormatterTest.java
class DialFormatTest {

    private fun steps(number: String, iso: String) =
        number.indices.map { DialFormat.asYouType(number.substring(0, it + 1), iso) }

    @Test fun frenchNumberGroupsWhileTyped() {
        val shown = steps("0697541234", "FR")
        assertEquals("06 97 54", shown[5])
        assertEquals("06 97 54 1", shown[6])
        assertEquals("06 97 54 12 34", shown[9])
    }

    @Test fun frenchInternationalNumber() {
        assertEquals("+33 6 12 34 56 78", DialFormat.asYouType("+33612345678", "FR"))
        assertEquals("+33 6 12", DialFormat.asYouType("+33612", "FR"))
    }

    @Test fun ukNumber() {
        assertEquals("020 7946 0958", DialFormat.asYouType("02079460958", "GB"))
        assertEquals("+44 20 7946 0958", DialFormat.asYouType("+442079460958", "GB"))
    }

    @Test fun usNumber() {
        assertEquals("(415) 555-2671", DialFormat.asYouType("4155552671", "US"))
        assertEquals("+1 415-555-2671", DialFormat.asYouType("+14155552671", "US"))
    }

    @Test fun codesAndLettersStayRaw() {
        assertEquals("*#06#", DialFormat.asYouType("*#06#", "FR"))
        assertEquals("*123#", DialFormat.asYouType("*123#", "US"))
        assertEquals("0697,12", DialFormat.asYouType("0697,12", "FR"))
        assertEquals("06a97", DialFormat.asYouType("06a97", "FR"))
        assertEquals("", DialFormat.asYouType("", "FR"))
    }

    @Test fun unknownCountryStaysRaw() {
        assertEquals("0697541234", DialFormat.asYouType("0697541234", ""))
    }
}
