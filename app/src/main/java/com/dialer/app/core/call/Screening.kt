package com.dialer.app.core.call

import android.telecom.Call
import android.telecom.Connection
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
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
            strict = AdvancedProtection.isOn(this),
            isContact = { ContactLookup.isContact(this, details.handle.schemeSpecificPart) }
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

enum class Verdict { ALLOW, SILENCE, REJECT }

/** What happens to an incoming call, from what is known of it and the user's choices. */
object ScreeningRules {
    /**
     * A faked number, then a hidden one, are turned away when the user
     * chose so or Android's Advanced Protection is on; a number not in the
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
        isContact: () -> Boolean
    ): Verdict = when {
        spoofed && (blockSpoofed || strict) -> Verdict.REJECT
        hidden && (blockHidden || strict) -> Verdict.REJECT
        !hidden && silenceUnknown && !isContact() -> Verdict.SILENCE
        else -> Verdict.ALLOW
    }
}
