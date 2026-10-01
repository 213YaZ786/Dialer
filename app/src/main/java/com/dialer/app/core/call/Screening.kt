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
        val strict = AdvancedProtection.isOn(this)
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED ||
            details.handle?.schemeSpecificPart.isNullOrBlank()
        val spoofed = details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val response = when {
            spoofed && (prefs.blockSpoofed || strict) -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
            hidden && (prefs.blockHidden || strict) -> CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
            !hidden && prefs.silenceUnknown && !ContactLookup.isContact(this, details.handle.schemeSpecificPart) ->
                CallResponse.Builder().setSilenceCall(true).build()
            else -> allow
        }
        respondToCall(details, response)
    }
}
