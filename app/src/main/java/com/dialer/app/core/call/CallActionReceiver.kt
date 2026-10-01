package com.dialer.app.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Decline and Hang up, pressed in a call notification. Not exported: only our own notifications reach it. */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {

    private val calls: CallStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(CallNotifier.EXTRA_CALL, -1)
        if (id < 0) return
        when (intent.action) {
            CallNotifier.ACTION_DECLINE -> calls.decline(id)
            CallNotifier.ACTION_HANG_UP -> calls.hangUp(id)
        }
    }
}
