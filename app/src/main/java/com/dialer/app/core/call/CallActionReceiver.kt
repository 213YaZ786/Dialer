package com.dialer.app.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Decline and Hang up, pressed in a call notification, and Call back on a
 * missed call. Not exported: only our own notifications reach it.
 */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {

    private val calls: CallStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifier.ACTION_CALL_BACK -> {
                intent.getStringExtra(CallNotifier.EXTRA_NUMBER)?.let { Dialing.call(context, it) }
                clearMissed(context)
                return
            }
            CallNotifier.ACTION_CLEAR_MISSED -> {
                clearMissed(context)
                return
            }
        }
        val id = intent.getIntExtra(CallNotifier.EXTRA_CALL, -1)
        if (id < 0) return
        when (intent.action) {
            CallNotifier.ACTION_DECLINE -> calls.decline(id)
            CallNotifier.ACTION_HANG_UP -> calls.hangUp(id)
        }
    }

    /** Telecom keeps its own count of missed calls; this resets it, and the notification goes. */
    private fun clearMissed(context: Context) {
        runCatching { context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        MissedCallNotifier(context).show(0, null)
    }
}
