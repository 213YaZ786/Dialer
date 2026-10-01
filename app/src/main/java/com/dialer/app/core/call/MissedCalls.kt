package com.dialer.app.core.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import com.dialer.app.MainActivity
import com.dialer.app.R
import com.dialer.app.core.dial.ContactLookup
import com.dialer.app.core.dial.Numbers

/**
 * The missed call notification. Telecom hands it to the phone app (with
 * READ_PHONE_STATE) and shows its own only when no phone app takes it.
 * One missed call offers Call back and Message; several say how many. A
 * tap opens Recents; once Recents has been seen, Telecom calls back here
 * with no missed call and the notification goes.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        // The number to call back is taken from the call log, the system's
        // own record, rather than from the message alone.
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
            ?.takeIf { count != 1 || MissedCallNotifier.isLastMissed(context, it) }
        MissedCallNotifier(context).show(count, number)
    }
}

class MissedCallNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun show(count: Int, number: String?) {
        if (count <= 0) {
            manager.cancel(ID)
            return
        }
        val name = number?.let { ContactLookup.nameOf(context, it) }
        val who = name ?: number?.takeIf { it.isNotBlank() }?.let { Numbers.format(context, it) }
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dialer)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentTitle(if (count == 1) who ?: "Private number" else "$count missed calls")
            .setContentText(if (count == 1) "Missed call" else who?.let { "Last from $it" })
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setContentIntent(recents())
            .setDeleteIntent(clear())
        // On a locked screen set to hide sensitive content, no name nor number.
        builder.setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_dialer)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setContentTitle(if (count == 1) "Missed call" else "$count missed calls")
                .build()
        )
        if (count == 1 && !number.isNullOrBlank()) {
            builder.addAction(Notification.Action.Builder(null, "Call back", callBack(number)).build())
            builder.addAction(Notification.Action.Builder(null, "Message", message(number)).build())
        }
        manager.notify(ID, builder.build())
    }

    private fun recents(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun callBack(number: String): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, CallActionReceiver::class.java).setAction(CallNotifier.ACTION_CALL_BACK).putExtra(CallNotifier.EXTRA_NUMBER, number),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun message(number: String): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun clear(): PendingIntent = PendingIntent.getBroadcast(
        context, 3,
        Intent(context, CallActionReceiver::class.java).setAction(CallNotifier.ACTION_CLEAR_MISSED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val CHANNEL = "missed_calls"
        private const val ID = 2

        /** True when the newest missed call in the log is from [number]. */
        fun isLastMissed(context: Context, number: String): Boolean = runCatching {
            val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1").build()
            context.contentResolver.query(
                uri, arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                c.moveToFirst() && PhoneNumberUtils.areSamePhoneNumber(c.getString(0).orEmpty(), number, Numbers.countryIso(context).lowercase())
            }
        }.getOrNull() ?: false
    }
}
