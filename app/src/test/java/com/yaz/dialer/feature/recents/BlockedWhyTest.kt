package com.yaz.dialer.feature.recents

import android.provider.CallLog.Calls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Follows AOSP's own CallLog block reasons
// (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/CallLog.java).
class BlockedWhyTest {

    private val screening = Calls.BLOCK_REASON_CALL_SCREENING_SERVICE

    @Test
    fun aCallThisAppTurnedAwayIsToldFromItsNumber() {
        assertEquals("hidden number", blockedWhy(screening, ours = true, hidden = true, salesRange = false))
        assertEquals("hidden number", blockedWhy(screening, ours = true, hidden = true, salesRange = true))
        assertEquals("sales call", blockedWhy(screening, ours = true, hidden = false, salesRange = true))
        assertEquals("faked number", blockedWhy(screening, ours = true, hidden = false, salesRange = false))
    }

    @Test
    fun aCallAnotherAppTurnedAwayNamesTheApp() {
        assertEquals("by Spam Blocker", blockedWhy(screening, ours = false, hidden = false, salesRange = true, otherApp = "Spam Blocker"))
        assertEquals("screening app", blockedWhy(screening, ours = false, hidden = false, salesRange = false))
    }

    @Test
    fun theSystemsOwnReasonsAreTold() {
        assertEquals("blocked number", blockedWhy(Calls.BLOCK_REASON_BLOCKED_NUMBER, false, false, false))
        assertEquals("unknown number", blockedWhy(Calls.BLOCK_REASON_UNKNOWN_NUMBER, false, false, false))
        assertEquals("hidden number", blockedWhy(Calls.BLOCK_REASON_RESTRICTED_NUMBER, false, true, false))
        assertEquals("payphone", blockedWhy(Calls.BLOCK_REASON_PAY_PHONE, false, false, false))
        assertEquals("not in contacts", blockedWhy(Calls.BLOCK_REASON_NOT_IN_CONTACTS, false, false, false))
    }

    @Test
    fun noReasonSaysNothing() {
        assertNull(blockedWhy(Calls.BLOCK_REASON_NOT_BLOCKED, false, false, false))
        assertNull(blockedWhy(Calls.BLOCK_REASON_DIRECT_TO_VOICEMAIL, false, false, false))
        assertNull(blockedWhy(-1, true, true, true))
        assertNull(blockedWhy(99, true, false, true))
    }
}
