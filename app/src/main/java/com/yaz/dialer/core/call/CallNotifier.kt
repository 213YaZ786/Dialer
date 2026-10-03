package com.yaz.dialer.core.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import com.yaz.dialer.R
import com.yaz.dialer.feature.call.CallActivity

/**
 * The call notifications, Android's own call style: the incoming one with
 * Answer and Decline, which opens the call screen over the lock screen, and
 * the ongoing one with Hang up and the timer, which brings the screen back.
 *
 * Both are silent. Telecom rings and vibrates (the service does not claim
 * the ringing), so the contact's own ringtone, Do Not Disturb and the
 * ringer switch all keep working as the system sets them.
 */
class CallNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        )
    }

    /**
     * The notification for [state], or null when no call is left. While the
     * call screen is in front, a ringing call goes on the quiet channel with
     * no full screen intent, so no banner covers the screen's own buttons.
     */
    fun build(state: CallsState, screenShown: Boolean): Notification? {
        val call = state.primary ?: return null
        if (call.phase == CallPhase.ENDED && !state.anyLive) return null
        val person = Person.Builder().setName(call.title).setImportant(true).build()
        val open = screen(OPEN_REQUEST, null, call.id)

        return if (call.phase == CallPhase.RINGING) {
            Notification.Builder(context, if (screenShown) CHANNEL_ONGOING else CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_dialer)
                .setCategory(Notification.CATEGORY_CALL)
                .setStyle(
                    Notification.CallStyle.forIncomingCall(
                        person,
                        action(ACTION_DECLINE, call.id),
                        // Answering opens the call screen too, which also
                        // closes the shade the notification was in.
                        screen(ANSWER_REQUEST, CallActivity.EXTRA_ANSWER, call.id)
                    )
                )
                .setContentText(if (call.encrypted) "Encrypted call" else call.number.takeIf { call.name != null })
                .apply { if (!screenShown) setFullScreenIntent(open, true) }
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_dialer)
                .setCategory(Notification.CATEGORY_CALL)
                .apply {
                    // With the call island over the other apps, a plain notice:
                    // Android's call style would add its own chip to the status
                    // bar, the same call shown twice.
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        setContentTitle(call.title)
                        addAction(Notification.Action.Builder(null, "Hang up", action(ACTION_HANG_UP, call.id)).build())
                    } else {
                        setStyle(Notification.CallStyle.forOngoingCall(person, action(ACTION_HANG_UP, call.id)))
                    }
                }
                .setContentText(statusOf(call))
                .apply {
                    if (call.connectedAt > 0) setWhen(call.connectedAt).setUsesChronometer(true).setShowWhen(true)
                }
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    private fun statusOf(call: CallInfo): String = when (call.phase) {
        CallPhase.DIALING -> if (call.encrypted) "Calling, encrypted" else "Calling"
        CallPhase.HOLDING -> "On hold"
        CallPhase.CHOOSE_SIM -> "Choose a SIM"
        CallPhase.ENDED -> "Call ended"
        else -> if (call.encrypted) "Encrypted call" else "Ongoing call"
    }

    private fun screen(request: Int, extra: String?, id: Int): PendingIntent {
        val intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (extra != null) putExtra(extra, id) }
        return PendingIntent.getActivity(
            context, request + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(action: String, id: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_CALL, id)
        return PendingIntent.getBroadcast(
            context, action.hashCode() + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_ONGOING = "ongoing_calls"
        const val ACTION_DECLINE = "com.yaz.dialer.DECLINE"
        const val ACTION_HANG_UP = "com.yaz.dialer.HANG_UP"
        const val ACTION_CALL_BACK = "com.yaz.dialer.CALL_BACK"
        const val ACTION_CLEAR_MISSED = "com.yaz.dialer.CLEAR_MISSED"
        const val EXTRA_CALL = "call"
        const val EXTRA_NUMBER = "number"
        private const val OPEN_REQUEST = 1000
        private const val ANSWER_REQUEST = 2000
    }
}
