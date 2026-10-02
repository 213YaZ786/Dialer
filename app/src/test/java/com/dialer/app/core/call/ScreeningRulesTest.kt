package com.dialer.app.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun theContactsAreOnlyReadWhenNeeded() {
        var asked = false
        ScreeningRules.verdict(false, false, true, false, silenceUnknown = false, strict = false) { asked = true; false }
        assertFalse(asked)
    }
}
