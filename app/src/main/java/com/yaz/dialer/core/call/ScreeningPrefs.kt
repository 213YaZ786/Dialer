package com.yaz.dialer.core.call

import android.content.Context
import com.yaz.dialer.data.settings.Settings

/**
 * What Screening decides with. A copy of these four choices is kept in the
 * device-protected storage, the only one Android lets an app read before the
 * first unlock after a boot (GrapheneOS reboots an idle phone by itself): a
 * call that comes in then is still screened.
 * Source: developer.android.com/training/articles/direct-boot ("Access
 * device encrypted storage": createDeviceProtectedStorageContext). Only
 * switches are kept there, never a number.
 */
data class ScreeningPrefs(
    val blockSpoofed: Boolean = true,
    val blockHidden: Boolean = false,
    val blockSalesCalls: Boolean = false,
    val silenceUnknown: Boolean = false
) {
    companion object {
        private const val FILE = "screening"

        fun of(settings: Settings) = ScreeningPrefs(
            blockSpoofed = settings.blockSpoofed,
            blockHidden = settings.blockHidden,
            blockSalesCalls = settings.blockSalesCalls,
            silenceUnknown = settings.silenceUnknown
        )

        private fun file(context: Context) =
            context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** Written whenever the choices change, and when the settings are first read after an unlock. */
        fun keep(context: Context, prefs: ScreeningPrefs) {
            runCatching {
                file(context).edit()
                    .putBoolean("spoofed", prefs.blockSpoofed)
                    .putBoolean("hidden", prefs.blockHidden)
                    .putBoolean("sales", prefs.blockSalesCalls)
                    .putBoolean("silence", prefs.silenceUnknown)
                    .apply()
            }
        }

        /** The kept copy, or the defaults when none was written yet. */
        fun kept(context: Context): ScreeningPrefs = runCatching {
            val f = file(context)
            ScreeningPrefs(
                blockSpoofed = f.getBoolean("spoofed", true),
                blockHidden = f.getBoolean("hidden", false),
                blockSalesCalls = f.getBoolean("sales", false),
                silenceUnknown = f.getBoolean("silence", false)
            )
        }.getOrDefault(ScreeningPrefs())
    }
}
