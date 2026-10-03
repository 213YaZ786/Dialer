package com.yaz.dialer

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract

/**
 * Debug builds only: writes the Contacts app's "look" row of a data row
 * already there, for testing what Dialer reads from it.
 * adb shell am broadcast -n com.yaz.dialer.debug/com.yaz.dialer.DebugLookReceiver --el row 102 --es json '{"bypass":true}'
 */
class DebugLookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.getLongExtra("row", -1)
        val json = intent.getStringExtra("json") ?: return
        if (row < 0) return
        runCatching {
            context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply { put(ContactsContract.Data.DATA1, json) },
                "${ContactsContract.Data._ID} = ?",
                arrayOf(row.toString())
            )
        }
    }
}
