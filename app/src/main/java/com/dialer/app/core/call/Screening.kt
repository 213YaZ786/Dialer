package com.dialer.app.core.call

import android.telecom.Call
import android.telecom.Connection
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.dialer.app.core.dial.SalesCalls
import com.dialer.app.core.dial.ContactLookup
import com.dialer.app.core.system.AdvancedProtection
import com.dialer.app.data.settings.SettingsStore
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

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow)
            return
        }
        val prefs = settings.current
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED ||
            details.handle?.schemeSpecificPart.isNullOrBlank()
        val verdict = ScreeningRules.verdict(
            hidden = hidden,
            spoofed = details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED,
            blockSpoofed = prefs.blockSpoofed,
            blockHidden = prefs.blockHidden,
            silenceUnknown = prefs.silenceUnknown,
            salesCall = prefs.blockSalesCalls && !hidden &&
                SalesCalls.isSalesCall(details.handle.schemeSpecificPart, getSystemService(TelephonyManager::class.java)?.networkCountryIso),
            strict = AdvancedProtection.isOn(this),
            isContact = { ContactLookup.isContact(this, details.handle.schemeSpecificPart) },
            calledAgain = { calledLately(details.handle.schemeSpecificPart) }
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
    val digits = com.dialer.app.core.dial.T9.clean(number).takeLast(9)
    if (digits.length < 6) return@runCatching false
    contentResolver.query(
        android.provider.CallLog.Calls.CONTENT_URI, arrayOf(android.provider.CallLog.Calls.NUMBER),
        "${android.provider.CallLog.Calls.DATE} > ?", arrayOf(since.toString()), null
    )?.use { c ->
        var found = false
        while (!found && c.moveToNext()) found = com.dialer.app.core.dial.T9.clean(c.getString(0).orEmpty()).takeLast(9) == digits
        found
    } ?: false
}.getOrDefault(false)

enum class Verdict { ALLOW, SILENCE, REJECT }

/** What happens to an incoming call, from what is known of it and the user's choices. */
object ScreeningRules {
    /** How soon a second call from an unknown number rings through. */
    const val AGAIN_MS = 3 * 60 * 1000L

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
        calledAgain: () -> Boolean = { false },
        isContact: () -> Boolean
    ): Verdict = when {
        spoofed && (blockSpoofed || strict) -> Verdict.REJECT
        hidden && (blockHidden || strict) -> Verdict.REJECT
        // A number in a sales range, unless the user saved it.
        salesCall && !isContact() -> Verdict.REJECT
        // Not saved, silent, unless they call again within minutes: then it rings.
        !hidden && silenceUnknown && !isContact() && !calledAgain() -> Verdict.SILENCE
        else -> Verdict.ALLOW
    }
}
