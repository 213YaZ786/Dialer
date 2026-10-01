package com.dialer.app.data.calllog

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.dialer.app.core.calllog.CallEntry
import com.dialer.app.core.calllog.CallKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android's call log, the one store of the calls: read for Recents, followed
 * as it changes, and trimmed when the user deletes a line. Nothing is
 * copied anywhere.
 */
class CallHistory(private val context: Context, private val scope: CoroutineScope) {

    private val _entries = MutableStateFlow<List<CallEntry>>(emptyList())
    val entries: StateFlow<List<CallEntry>> = _entries.asStateFlow()

    /** False until the first read is back, so the screen does not say "no calls" too early. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var watching = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun canRead(): Boolean = granted(Manifest.permission.READ_CALL_LOG)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Loads, and from then on follows the log. Does nothing without the permission. */
    fun refresh() {
        if (!canRead()) return
        if (!watching) {
            context.contentResolver.registerContentObserver(Calls.CONTENT_URI, true, observer)
            watching = true
        }
        scope.launch {
            _entries.value = withContext(Dispatchers.IO) { load() }
            _loaded.value = true
        }
    }

    /**
     * The missed calls are seen: their dots go, and so does the system's
     * missed call notification.
     */
    fun markMissedSeen() {
        if (_entries.value.none { it.isNew && it.kind == CallKind.MISSED }) return
        scope.launch(Dispatchers.IO) {
            if (granted(Manifest.permission.WRITE_CALL_LOG)) {
                val seen = ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                }
                runCatching {
                    context.contentResolver.update(Calls.CONTENT_URI, seen, "${Calls.NEW} = 1 AND ${Calls.TYPE} = ?", arrayOf(Calls.MISSED_TYPE.toString()))
                }
            }
            runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        }
    }

    /** Takes these calls out of the log. */
    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty() || !granted(Manifest.permission.WRITE_CALL_LOG)) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                ids.chunked(200).forEach { chunk ->
                    context.contentResolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${chunk.joinToString(",")})", null)
                }
            }
        }
    }

    private fun load(): List<CallEntry> = runCatching {
        val columns = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION,
            Calls.NEW, Calls.GEOCODED_LOCATION, Calls.NUMBER_PRESENTATION
        )
        val uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, LIMIT.toString()).build()
        context.contentResolver.query(uri, columns, null, null, "${Calls.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val kind = when (c.getInt(3)) {
                        Calls.OUTGOING_TYPE -> CallKind.OUTGOING
                        Calls.MISSED_TYPE -> CallKind.MISSED
                        Calls.REJECTED_TYPE -> CallKind.REJECTED
                        Calls.BLOCKED_TYPE -> CallKind.BLOCKED
                        Calls.VOICEMAIL_TYPE -> CallKind.VOICEMAIL
                        else -> CallKind.INCOMING
                    }
                    val number = c.getString(1).orEmpty()
                    add(
                        CallEntry(
                            id = c.getLong(0),
                            number = number,
                            cachedName = c.getString(2)?.takeIf { it.isNotBlank() },
                            kind = kind,
                            date = c.getLong(4),
                            duration = c.getLong(5),
                            isNew = c.getInt(6) == 1,
                            location = c.getString(7)?.takeIf { it.isNotBlank() },
                            hidden = c.getInt(8) != Calls.PRESENTATION_ALLOWED || number.isBlank()
                        )
                    )
                }
            }
        }
    }.getOrNull().orEmpty()

    private companion object {
        /** About a year of calls for most people; older ones stay in the log, unseen here. */
        const val LIMIT = 1000
    }
}
