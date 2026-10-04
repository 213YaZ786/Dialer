package com.yaz.dialer.core.dial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A number to show in the dialpad, dropped off by whatever opened the app
 * for it: a tel: link, the phone button of another app, Add call during a
 * call. The main screen opens the dialpad with it and takes it.
 */
class DialRequests {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Opened by another app (a messaging app's call button, a link): closing the dialpad goes back there. */
    var fromOutside = false
        private set

    fun open(number: String, outside: Boolean = false) {
        fromOutside = outside
        _pending.value = number
    }

    fun consume() {
        _pending.value = null
    }

    /** Recents to the front: the missed call notification, a "call history" link. */
    private val _recents = MutableStateFlow(false)
    val recents: StateFlow<Boolean> = _recents.asStateFlow()

    fun showRecents() {
        _recents.value = true
    }

    fun recentsShown() {
        _recents.value = false
    }

    /** A number's page to the front, asked by the contacts or messaging app. */
    private val _numberPage = MutableStateFlow<String?>(null)
    val numberPage: StateFlow<String?> = _numberPage.asStateFlow()

    fun showNumber(number: String) {
        if (number.isNotEmpty()) _numberPage.value = number
    }

    fun numberShown() {
        _numberPage.value = null
    }

    companion object {
        /**
         * The calls with a number, asked by another app (the contacts app's
         * Calls or Block, the messaging app's calls): it only shows the
         * page, the user acts there.
         */
        const val ACTION_SHOW_NUMBER = NumberActions.ACTION_SHOW_NUMBER

        private const val MAX_NUMBER = 64

        /**
         * A number from a link, kept to what can be dialled and to a sane
         * length. It only fills the dialpad: the user reads it and presses Call.
         */
        fun fromLink(raw: String?): String =
            raw.orEmpty().filter { it.isDigit() || it in "+*#,;" }.take(MAX_NUMBER)
    }
}
