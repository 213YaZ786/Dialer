package com.yaz.dialer.core.call

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import android.telecom.Call
import android.telecom.DisconnectCause
import com.yaz.dialer.core.dial.Sisters

/**
 * Calls over the messaging app's end-to-end encrypted line. Android keeps
 * such calls out of its call history itself, so the call history's owner,
 * this app, writes them in when they end, marked with the line they took.
 */
object EncryptedCalls {

    /** A call of the line of our SMS, not of an app that only has a package name like it. */
    fun isEncrypted(context: Context, call: Call): Boolean =
        call.details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED) &&
            isLine(context, call.details.accountHandle?.componentName?.packageName)

    /** The log's mark of a call that went over the encrypted line: SMS's package exactly, or its old one. */
    fun isEncrypted(context: Context, componentName: String?): Boolean {
        val pkg = componentName?.substringBefore('/') ?: return false
        return pkg == OLD_PACKAGE || isLine(context, pkg)
    }

    // The package must be SMS's and signed as this app, as PackageManager.checkSignatures
    // (https://developer.android.com/reference/android/content/pm/PackageManager#checkSignatures(java.lang.String,%20java.lang.String)),
    // through the phone kit's Sisters.
    private fun isLine(context: Context, pkg: String?): Boolean = pkg != null && pkg in Sisters.packages("sms") && Sisters.isOurs(context, pkg)

    /** The call, ended, into Android's call history; true when it was missed. */
    fun log(context: Context, call: Call): Boolean {
        val details = call.details
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val answered = details.connectTimeMillis > 0
        val type = when {
            !incoming -> Calls.OUTGOING_TYPE
            answered -> Calls.INCOMING_TYPE
            details.disconnectCause?.code == DisconnectCause.REJECTED -> Calls.REJECTED_TYPE
            else -> Calls.MISSED_TYPE
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val values = ContentValues().apply {
            put(Calls.NUMBER, number)
            put(Calls.TYPE, type)
            put(Calls.DATE, details.creationTimeMillis)
            put(Calls.DURATION, if (answered) (System.currentTimeMillis() - details.connectTimeMillis) / 1000 else 0L)
            put(Calls.NEW, if (type == Calls.MISSED_TYPE) 1 else 0)
            put(Calls.NUMBER_PRESENTATION, Calls.PRESENTATION_ALLOWED)
            details.accountHandle?.let {
                put(Calls.PHONE_ACCOUNT_COMPONENT_NAME, it.componentName.flattenToString())
                put(Calls.PHONE_ACCOUNT_ID, it.id)
            }
        }
        runCatching { context.contentResolver.insert(Calls.CONTENT_URI, values) }
        return type == Calls.MISSED_TYPE
    }

    /** SMS before its move: calls it logged then keep their mark. */
    private const val OLD_PACKAGE = "com.sms.app"
}
