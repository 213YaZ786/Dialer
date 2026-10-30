package com.yaz.dialer.core.call

import com.yaz.dialer.data.settings.BlockedPrefix
import com.yaz.dialer.data.settings.PrefixAction
import com.yaz.dialer.data.settings.Settings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tests follow Android's own for numbers written differently (CTS PhoneNumberUtilsTest,
 * https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/tests/telephony/current/src/android/telephony/cts/PhoneNumberUtilsTest.java)
 * and libphonenumber's PhoneNumberUtilTest for the trunk digit
 * (https://github.com/google/libphonenumber/blob/master/java/libphonenumber/test/com/google/i18n/phonenumbers/PhoneNumberUtilTest.java).
 */
class PrefixRulesTest {

    private val paris = BlockedPrefix("+3328103", "02 81 03")

    /** libphonenumber's own example numbers per type (resources/PhoneNumberMetadata.xml, exampleNumber). */
    @Test
    fun aRangeIsOfferedForFixedAndServiceLinesOnly() {
        // France: fixed 01, internet 09, free 080; mobiles 06 and 07.
        assertTrue(PrefixRules.offersRange("+33123456789", "FR"))
        assertTrue(PrefixRules.offersRange("0912345678", "FR"))
        assertTrue(PrefixRules.offersRange("+33801234567", null))
        assertFalse(PrefixRules.offersRange("+33612345678", "FR"))
        assertFalse(PrefixRules.offersRange("0712345678", "FR"))
        // Germany, the United Kingdom, Spain, Morocco.
        assertTrue(PrefixRules.offersRange("+4930123456", "US"))
        assertFalse(PrefixRules.offersRange("+4915123456789", "US"))
        assertTrue(PrefixRules.offersRange("+441212345678", null))
        assertFalse(PrefixRules.offersRange("+447400123456", null))
        assertTrue(PrefixRules.offersRange("+34810123456", null))
        assertFalse(PrefixRules.offersRange("+34612345678", null))
        assertTrue(PrefixRules.offersRange("+212520123456", null))
        assertFalse(PrefixRules.offersRange("+212650123456", null))
        // The United States: fixed and mobile cannot be told apart; toll free can.
        assertFalse(PrefixRules.offersRange("+12015550123", "US"))
        assertTrue(PrefixRules.offersRange("+18002345678", "US"))
        // Not a number, a national number with no country.
        assertFalse(PrefixRules.offersRange("AMAZON", "FR"))
        assertFalse(PrefixRules.offersRange("0123456789", null))
    }

    @Test
    fun aNationalPrefixIsTheInternationalOne() {
        assertEquals("+3328103", PrefixRules.normalize("02 81 03", "FR"))
        assertEquals("+3328103", PrefixRules.normalize("+33 2 81 03", "FR"))
        assertEquals("+3328103", PrefixRules.normalize("+33 2 81 03", null))
        assertEquals("+3328103", PrefixRules.normalize("0033 2 81 03", "FR"))
        assertEquals("+3328103", PrefixRules.normalize("+33 (0)2 81 03", "FR"))
        assertEquals("+3328103", PrefixRules.normalize("028103", "fr"))
    }

    @Test
    fun theTrunkZeroStaysWhereItIsPartOfTheNumber() {
        // Italy keeps its leading 0.
        assertEquals("+3906123", PrefixRules.normalize("06 123", "IT"))
        assertEquals("+3906123", PrefixRules.normalize("+39 06 123", null))
    }

    @Test
    fun tooShortOrNotANumberHasNoPrefix() {
        assertNull(PrefixRules.normalize("", "FR"))
        assertNull(PrefixRules.normalize("   ", "FR"))
        assertNull(PrefixRules.normalize("02", "FR"))
        assertNull(PrefixRules.normalize("+33 2", "FR"))
        assertNull(PrefixRules.normalize("abc", "FR"))
        // National, and no country to read it in.
        assertNull(PrefixRules.normalize("02 81 03", null))
        assertNull(PrefixRules.normalize("02 81 03", ""))
        // A code that is no country's.
        assertNull(PrefixRules.normalize("+999 123 456", "FR"))
    }

    @Test
    fun aNumberIsMatchedWhateverItsForm() {
        val rules = listOf(paris)
        assertEquals(paris, PrefixRules.match(rules, "0281031234", "FR"))
        assertEquals(paris, PrefixRules.match(rules, "+33281031234", "FR"))
        assertEquals(paris, PrefixRules.match(rules, "+33281031234", "US"))
        assertEquals(paris, PrefixRules.match(rules, "0033281031234", "FR"))
        assertEquals(paris, PrefixRules.match(rules, "02 81 03 12 34", "FR"))
        assertEquals(paris, PrefixRules.match(rules, "+33 (0)2 81 03 12 34", null))
        assertNull(PrefixRules.match(rules, "0281041234", "FR"))
        assertNull(PrefixRules.match(rules, "0381031234", "FR"))
        // The same digits in another country are not the same number.
        assertNull(PrefixRules.match(rules, "+44281031234", "FR"))
        assertNull(PrefixRules.match(rules, "0281031234", "GB"))
        assertNull(PrefixRules.match(emptyList(), "0281031234", "FR"))
        assertNull(PrefixRules.match(rules, "", "FR"))
        assertNull(PrefixRules.match(rules, "0281031234", null))
    }

    @Test
    fun theLongestPrefixWinsAndBlockBeatsSilenceOnATie() {
        val wide = BlockedPrefix("+332", "02", PrefixAction.SILENCE)
        val narrow = BlockedPrefix("+3328103", "02 81 03", PrefixAction.BLOCK)
        assertEquals(narrow, PrefixRules.match(listOf(wide, narrow), "0281031234", "FR"))
        assertEquals(wide, PrefixRules.match(listOf(wide, narrow), "0299991234", "FR"))
        val silence = BlockedPrefix("+3328103", "02 81 03", PrefixAction.SILENCE)
        val block = BlockedPrefix("+3328103", "028103", PrefixAction.BLOCK)
        assertEquals(block, PrefixRules.match(listOf(silence, block), "0281031234", "FR"))
        assertEquals(block, PrefixRules.match(listOf(block, silence), "0281031234", "FR"))
    }

    @Test
    fun addingAPrefixAgainReplacesItAndRemovingIt() {
        val silence = paris.copy(action = PrefixAction.SILENCE)
        val one = PrefixRules.with(listOf(paris), silence)
        assertEquals(listOf(silence), one)
        assertEquals(emptyList<BlockedPrefix>(), PrefixRules.without(one, "+3328103"))
        assertEquals(one, PrefixRules.without(one, "+3399999"))
    }

    private fun verdict(prefix: PrefixAction?, contact: Boolean = false, sales: Boolean = false, hidden: Boolean = false) =
        ScreeningRules.verdict(hidden, false, true, false, silenceUnknown = false, strict = false, salesCall = sales, prefix = prefix) { contact }

    @Test
    fun blockRejectsAndSilenceRingsWithoutSound() {
        assertEquals(Verdict.REJECT, verdict(PrefixAction.BLOCK))
        assertEquals(Verdict.SILENCE, verdict(PrefixAction.SILENCE))
        assertEquals(Verdict.ALLOW, verdict(null))
    }

    @Test
    fun aContactIsNeverBlockedByAPrefix() {
        assertEquals(Verdict.ALLOW, verdict(PrefixAction.BLOCK, contact = true))
        assertEquals(Verdict.ALLOW, verdict(PrefixAction.SILENCE, contact = true))
    }

    @Test
    fun aPrefixIsCheckedBeforeTheSalesRanges() {
        assertEquals(Verdict.SILENCE, verdict(PrefixAction.SILENCE, sales = true))
        assertEquals(Verdict.REJECT, verdict(PrefixAction.BLOCK, sales = true))
        assertEquals(Verdict.REJECT, verdict(null, sales = true))
    }

    @Test
    fun salesCallsAreBlockedByDefaultButAChoiceIsKept() {
        assertTrue(Settings().blockSalesCalls)
        assertTrue(ScreeningPrefs().blockSalesCalls)
        assertFalse(ScreeningPrefs().blockHidden)
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(json.decodeFromString<Settings>("""{"blockSalesCalls": false}""").blockSalesCalls)
        assertTrue(json.decodeFromString<Settings>("""{"blockSalesCalls": true}""").blockSalesCalls)
        // A settings file from before the choice existed.
        assertTrue(json.decodeFromString<Settings>("""{"themeMode": "DARK"}""").blockSalesCalls)
    }
}
