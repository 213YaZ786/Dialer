package com.dialer.app.core.dial

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

    fun open(number: String) {
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

    companion object {
        private const val MAX_NUMBER = 64

        /**
         * A number from a link, kept to what can be dialled and to a sane
         * length. It only fills the dialpad: the user reads it and presses Call.
         */
        fun fromLink(raw: String?): String =
            raw.orEmpty().filter { it.isDigit() || it in "+*#,;" }.take(MAX_NUMBER)
    }
}
