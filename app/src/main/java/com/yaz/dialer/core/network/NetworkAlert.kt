package com.yaz.dialer.core.network

/**
 * When a call's network is worth a word: it went onto 2G (or started on
 * it), where it can be listened to, or it came back from there. Other
 * changes, 5G to 4G and back, are left to the network chip.
 */
object NetworkAlert {

    enum class Change { DOWN, BACK }

    /** The change from [before] (null at the call's start) to [now], if it is worth saying. */
    fun change(before: Protection?, now: Protection): Change? = when {
        now == Protection.UNPROTECTED && before != Protection.UNPROTECTED -> Change.DOWN
        before == Protection.UNPROTECTED && now != Protection.UNPROTECTED && now != Protection.UNKNOWN -> Change.BACK
        else -> null
    }
}
