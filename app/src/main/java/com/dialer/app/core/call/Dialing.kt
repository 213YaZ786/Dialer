package com.dialer.app.core.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Places a call through Telecom, as the phone app. Telecom picks the SIM
 * (asking through the call screen when there are two and no default),
 * handles emergency numbers, and hands the call back to the call screen.
 */
object Dialing {

    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /**
     * The codes a phone answers itself rather than calling: *#*#4636#*#*
     * and the like go to the app that listens for them, and *#06#, the
     * IMEI, which Android only shows to its own apps, opens About phone
     * where it is written. True when [number] was one of them.
     */
    fun special(context: Context, number: String): Boolean {
        val code = Regex("""\*#\*#(\d+)#\*#\*""").matchEntire(number)?.groupValues?.get(1)
        if (code != null) {
            return runCatching { context.getSystemService(TelephonyManager::class.java).sendDialerSpecialCode(code) }.isSuccess
        }
        if (number == "*#06#") {
            return runCatching {
                context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
        return false
    }

    /** False when the number is empty or the permission is missing. */
    fun call(context: Context, number: String): Boolean {
        val dialable = number.filter { it.isDigit() || it in "+*#,;" }
        if (dialable.isEmpty() || !canCall(context)) return false
        return runCatching {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", dialable, null), Bundle())
        }.isSuccess
    }

    /**
     * Calls the voicemail of the default SIM: Telecom knows its number, so
     * Dialer needs no access to the SIM for it.
     */
    fun voicemail(context: Context): Boolean {
        if (!canCall(context)) return false
        return runCatching {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), Bundle())
        }.isSuccess
    }
}
