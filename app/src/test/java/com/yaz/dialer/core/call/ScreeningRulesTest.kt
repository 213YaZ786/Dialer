package com.yaz.dialer.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningRulesTest {

    private fun verdict(
        hidden: Boolean = false,
        spoofed: Boolean = false,
        blockSpoofed: Boolean = true,
        blockHidden: Boolean = false,
        silenceUnknown: Boolean = false,
        strict: Boolean = false,
        contact: Boolean = false
    ) = ScreeningRules.verdict(hidden, spoofed, blockSpoofed, blockHidden, silenceUnknown, strict) { contact }

    @Test
    fun aFakedNumberIsTurnedAwayByDefault() {
        assertEquals(Verdict.REJECT, verdict(spoofed = true))
    }

    @Test
    fun aFakedNumberRingsWhenTheUserAllowsIt() {
        assertEquals(Verdict.ALLOW, verdict(spoofed = true, blockSpoofed = false))
    }

    @Test
    fun advancedProtectionTurnsAwayFakedAndHiddenWhateverTheChoice() {
        assertEquals(Verdict.REJECT, verdict(spoofed = true, blockSpoofed = false, strict = true))
        assertEquals(Verdict.REJECT, verdict(hidden = true, blockHidden = false, strict = true))
    }

    @Test
    fun aHiddenNumberRingsUnlessBlocked() {
        assertEquals(Verdict.ALLOW, verdict(hidden = true))
        assertEquals(Verdict.REJECT, verdict(hidden = true, blockHidden = true))
    }

    @Test
    fun anUnknownNumberIsSilencedOnlyWhenChosen() {
        assertEquals(Verdict.ALLOW, verdict())
        assertEquals(Verdict.SILENCE, verdict(silenceUnknown = true))
        assertEquals(Verdict.ALLOW, verdict(silenceUnknown = true, contact = true))
    }

    @Test
    fun anUnknownNumberCallingAgainRings() {
        assertEquals(Verdict.ALLOW, ScreeningRules.verdict(false, false, true, false, silenceUnknown = true, strict = false, calledAgain = { true }) { false })
        assertEquals(Verdict.SILENCE, ScreeningRules.verdict(false, false, true, false, silenceUnknown = true, strict = false, calledAgain = { false }) { false })
        // A faked number does not get through by insisting.
        assertEquals(Verdict.REJECT, ScreeningRules.verdict(false, true, true, false, silenceUnknown = true, strict = false, calledAgain = { true }) { false })
    }

    // The tests below follow Android's own: CallScreeningServiceTest
    // (https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/tests/telecom/src/android/telecom/cts/CallScreeningServiceTest.java)
    // and, for numbers written differently, PhoneNumberUtilsTest
    // (https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/tests/telephony/current/src/android/telephony/cts/PhoneNumberUtilsTest.java).
    private fun sales(contact: Boolean = false, hidden: Boolean = false, spoofed: Boolean = false) =
        ScreeningRules.verdict(hidden, spoofed, true, false, silenceUnknown = false, strict = false, salesCall = true) { contact }

    @Test
    fun aSalesCallIsTurnedAwayUnlessSaved() {
        assertEquals(Verdict.REJECT, sales())
        assertEquals(Verdict.ALLOW, sales(contact = true))
        // A faked number is turned away even from a contact in a sales range.
        assertEquals(Verdict.REJECT, sales(contact = true, spoofed = true))
    }

    @Test
    fun anAllowedNumberIsKnownWhateverItsForm() {
        val allowed = setOf("+33270123456")
        assertTrue(ScreeningRules.isAllowed(allowed, "+33270123456"))
        assertTrue(ScreeningRules.isAllowed(allowed, "0270123456"))
        assertTrue(ScreeningRules.isAllowed(allowed, "02 70 12 34 56"))
        assertTrue(ScreeningRules.isAllowed(allowed, "0033270123456"))
        assertTrue(ScreeningRules.isAllowed(allowed, "+33 (0)2 70 12 34 56"))
        assertFalse(ScreeningRules.isAllowed(allowed, "0270123457"))
        assertFalse(ScreeningRules.isAllowed(allowed, "0162123456"))
        assertFalse(ScreeningRules.isAllowed(emptySet(), "0270123456"))
    }

    @Test
    fun tooShortOrEmptyNumbersAreNeverAllowed() {
        assertFalse(ScreeningRules.isAllowed(setOf("", "123"), ""))
        assertFalse(ScreeningRules.isAllowed(setOf("12345"), "12345"))
        assertFalse(ScreeningRules.isAllowed(setOf("+33270123456"), ""))
        assertEquals(emptySet<String>(), ScreeningRules.withAllowed(emptySet(), "123", true))
        assertEquals(emptySet<String>(), ScreeningRules.withAllowed(emptySet(), "", true))
    }

    @Test
    fun allowingAndTakingOutANumber() {
        val one = ScreeningRules.withAllowed(emptySet(), "0270123456", true)
        assertEquals(setOf("0270123456"), one)
        // The same number written another way replaces it, never doubles.
        assertEquals(setOf("+33270123456"), ScreeningRules.withAllowed(one, "+33270123456", true))
        assertEquals(emptySet<String>(), ScreeningRules.withAllowed(one, "+33 2 70 12 34 56", false))
        val two = ScreeningRules.withAllowed(one, "0948123456", true)
        assertEquals(2, two.size)
        assertEquals(setOf("0948123456"), ScreeningRules.withAllowed(two, "0270123456", false))
        // Taking out one that is not there changes nothing.
        assertEquals(two, ScreeningRules.withAllowed(two, "0162123456", false))
    }

    @Test
    fun theContactsAreOnlyReadWhenNeeded() {
        var asked = false
        ScreeningRules.verdict(false, false, true, false, silenceUnknown = false, strict = false) { asked = true; false }
        assertFalse(asked)
    }
}
