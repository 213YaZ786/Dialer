package com.yaz.dialer.core.dial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Numbers.format in the candidate countries (network, SIM, language), after AOSP Dialer's PhoneNumberHelper
 * (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/master/java/com/android/dialer/phonenumberutil/PhoneNumberHelper.java);
 * the expected strings are libphonenumber's own formats (https://github.com/google/libphonenumber, PhoneNumberUtil.java).
 */
class NumbersTest {

    @Test fun frenchNumberOnAnAmericanNetworkIsShownInFull() {
        assertEquals("+33 2 81 03 77 77", Numbers.format("0281037777", listOf("US", "FR")))
    }

    @Test fun frenchNumberInFranceIsNational() {
        assertEquals("02 81 03 77 77", Numbers.format("0281037777", listOf("FR")))
    }

    @Test fun internationalNumberOfAnotherCountryStaysInternational() {
        assertEquals("+33 2 81 03 77 77", Numbers.format("+33281037777", listOf("US")))
    }

    @Test fun internationalNumberOfTheOwnCountryBecomesNational() {
        assertEquals("02 81 03 77 77", Numbers.format("+33281037777", listOf("FR")))
    }

    @Test fun americanNumberInTheUsIsNational() {
        assertEquals("(415) 555-2671", Numbers.format("4155552671", listOf("US")))
    }

    @Test fun codesShortNumbersEmptyAndSipStayAsTyped() {
        for (typed in listOf("*123#", "#31#0281037777", "3615", "", "   ", "sip:bob@example.org", "1-800-FLOWERS", "bob")) {
            assertEquals(typed, Numbers.format(typed, listOf("US", "FR")))
        }
    }

    @Test fun invalidEverywhereAndNoCountryStayAsTyped() {
        assertEquals("12345678901234567890", Numbers.format("12345678901234567890", listOf("US", "FR")))
        assertEquals("0281037777", Numbers.format("0281037777", emptyList()))
        assertEquals("0281037777", Numbers.format("0281037777", listOf("", "XX")))
    }

    @Test fun plusNumberWithoutCountryIsInternational() {
        assertEquals("+33 2 81 03 77 77", Numbers.format("+33281037777", emptyList()))
    }
}
