package com.yaz.dialer.core.call

import android.telecom.Call
import android.telecom.Connection
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.os.UserManager
import com.yaz.dialer.core.dial.SalesCalls
import com.yaz.dialer.core.dial.ContactLookup
import com.yaz.dialer.core.system.AdvancedProtection
import com.yaz.dialer.data.settings.PrefixAction
import com.yaz.dialer.data.settings.PrefixStore
import com.yaz.dialer.data.settings.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Looks at each incoming call before it rings, which Android lets the phone
 * app do. Numbers the network found to be faked (STIR/SHAKEN failed) are
 * turned away, on by default; hidden numbers are turned away and numbers
 * not in the contacts ring without a sound (the call still shows and can be
 * answered), both off by default. Everything is decided on the
 * phone, from the contacts; no number is sent anywhere.
 */
class Screening : CallScreeningService(), KoinComponent {

    private val settings: SettingsStore by inject()
    private val prefixes: PrefixStore by inject()

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow)
            return
        }
        // Telecom binds this service before the first unlock after a boot too (it is direct boot
        // aware, as the LineageOS Dialer's call service is): the settings and the contacts live in
        // storage that is still locked then, so only the copy of the four choices is read, and a
        // number is never taken for an unknown one.
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val prefs = if (unlocked) ScreeningPrefs.of(settings.current) else ScreeningPrefs.kept(this)
        val allowedNumbers = if (unlocked) settings.current.salesAllowed else emptySet()
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED ||
            details.handle?.schemeSpecificPart.isNullOrBlank()
        // The user's blocked prefixes live in the app's files, locked before the first unlock like the contacts.
        val country = SalesCalls.country(this, details.accountHandle)
        val prefix = if (unlocked && !hidden) {
            runCatching { PrefixRules.match(prefixes.current, details.handle.schemeSpecificPart, country)?.action }.getOrNull()
        } else {
            null
        }
        val verdict = ScreeningRules.verdict(
            prefix = prefix,
            hidden = hidden,
            spoofed = details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED,
            blockSpoofed = prefs.blockSpoofed,
            blockHidden = prefs.blockHidden,
            silenceUnknown = prefs.silenceUnknown && unlocked,
            salesCall = prefs.blockSalesCalls && !hidden &&
                SalesCalls.isSalesCall(details.handle.schemeSpecificPart, country) &&
                !ScreeningRules.isAllowed(allowedNumbers, details.handle.schemeSpecificPart),
            strict = AdvancedProtection.isOn(this),
            isContact = { unlocked && ContactLookup.isContact(this, details.handle.schemeSpecificPart) },
            calledAgain = { unlocked && calledLately(details.handle.schemeSpecificPart) }
        )
        val response = when (verdict) {
            Verdict.REJECT -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
            Verdict.SILENCE -> CallResponse.Builder().setSilenceCall(true).build()
            Verdict.ALLOW -> allow
        }
        respondToCall(details, response)
    }
}

/** The same number called within the last minutes: someone who insists may need you. */
private fun android.content.Context.calledLately(number: String): Boolean = runCatching {
    val since = System.currentTimeMillis() - ScreeningRules.AGAIN_MS
    val digits = com.yaz.dialer.core.dial.T9.clean(number).takeLast(9)
    if (digits.length < 6) return@runCatching false
    contentResolver.query(
        android.provider.CallLog.Calls.CONTENT_URI, arrayOf(android.provider.CallLog.Calls.NUMBER),
        "${android.provider.CallLog.Calls.DATE} > ?", arrayOf(since.toString()), null
    )?.use { c ->
        var found = false
        while (!found && c.moveToNext()) found = com.yaz.dialer.core.dial.T9.clean(c.getString(0).orEmpty()).takeLast(9) == digits
        found
    } ?: false
}.getOrDefault(false)

enum class Verdict { ALLOW, SILENCE, REJECT }

/** What happens to an incoming call, from what is known of it and the user's choices. */
object ScreeningRules {
    /** How soon a second call from an unknown number rings through. */
    const val AGAIN_MS = 3 * 60 * 1000L

    /**
     * Whether [number] is one the user said is not a sales call. Numbers are
     * compared by their last nine digits, as [calledLately] does, so +33 2 70…
     * and 02 70… are the same: Android's PhoneNumberUtils.compare, which the
     * blocked list uses, reads the digits from the right too
     * (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/common/android/telephony/PhoneNumberUtils.java).
     */
    fun isAllowed(allowed: Set<String>, number: String): Boolean {
        val key = com.yaz.dialer.core.dial.T9.clean(number).takeLast(9)
        if (key.length < 6) return false
        return allowed.any { com.yaz.dialer.core.dial.T9.clean(it).takeLast(9) == key }
    }

    /**
     * The allowed numbers with [number] added or taken out, a number written
     * another way replacing the one kept (same comparison as [isAllowed],
     * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/common/android/telephony/PhoneNumberUtils.java).
     */
    fun withAllowed(allowed: Set<String>, number: String, allow: Boolean): Set<String> {
        val key = com.yaz.dialer.core.dial.T9.clean(number).takeLast(9)
        if (key.length < 6) return allowed
        val others = allowed.filterNot { com.yaz.dialer.core.dial.T9.clean(it).takeLast(9) == key }.toSet()
        return if (allow) others + number else others
    }

    /**
     * A faked number, then a hidden one, are turned away when the user
     * chose so or Android's Advanced Protection is on; [salesCall] (the user
     * chose to block them and the number is in one) too; a number not in the
     * contacts rings without sound when chosen. [isContact] is only asked
     * when it matters.
     */
    fun verdict(
        hidden: Boolean,
        spoofed: Boolean,
        blockSpoofed: Boolean,
        blockHidden: Boolean,
        silenceUnknown: Boolean,
        strict: Boolean,
        salesCall: Boolean = false,
        prefix: PrefixAction? = null,
        calledAgain: () -> Boolean = { false },
        isContact: () -> Boolean
    ): Verdict = when {
        spoofed && (blockSpoofed || strict) -> Verdict.REJECT
        hidden && (blockHidden || strict) -> Verdict.REJECT
        // A prefix the user blocks or silences, never for a saved contact; before the sales ranges.
        prefix != null && !isContact() -> if (prefix == PrefixAction.BLOCK) Verdict.REJECT else Verdict.SILENCE
        // A number in a sales range, unless the user saved it.
        salesCall && !isContact() -> Verdict.REJECT
        // Not saved, silent, unless they call again within minutes: then it rings.
        !hidden && silenceUnknown && !isContact() && !calledAgain() -> Verdict.SILENCE
        else -> Verdict.ALLOW
    }
}
