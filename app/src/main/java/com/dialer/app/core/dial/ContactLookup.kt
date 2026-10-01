package com.dialer.app.core.dial

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.ContextCompat

/**
 * Who a number belongs to, asked of Android's contacts directly: for the
 * moments the app may not be running yet, a call being screened or a
 * missed call to announce.
 */
object ContactLookup {

    fun nameOf(context: Context, number: String): String? {
        if (number.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    fun isContact(context: Context, number: String): Boolean = nameOf(context, number) != null
}
