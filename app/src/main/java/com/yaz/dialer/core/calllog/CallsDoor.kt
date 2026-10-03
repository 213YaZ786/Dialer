package com.yaz.dialer.core.calllog

import android.content.Context
import android.provider.CallLog
import com.yaz.dialer.core.dial.PeopleDoor

/** The Contacts app's question about calls: answered from Android's call log. */
class CallsDoor : PeopleDoor() {
    override fun exchanges(context: Context, since: Long, visit: (number: String, at: Long) -> Boolean) {
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c -> while (c.moveToNext()) if (!visit(c.getString(0).orEmpty(), c.getLong(1))) break }
        }
    }
}
