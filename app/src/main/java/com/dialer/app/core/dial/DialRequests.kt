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
}
