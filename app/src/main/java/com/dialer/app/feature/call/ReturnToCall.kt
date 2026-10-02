package com.dialer.app.feature.call

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.CallInfo
import com.dialer.app.core.call.CallPhase
import com.dialer.app.core.call.CallStore
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.icon.AppIcons
import org.koin.compose.koinInject

/**
 * While a call goes on and the app is open over it (Add call, or a look at
 * a contact), a pane of green glass with the call and its time: a tap goes
 * back to the call screen.
 */
@Composable
fun ReturnToCall(modifier: Modifier = Modifier) {
    val call = ongoingCall()
    AnimatedVisibility(
        visible = call != null,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        modifier = modifier
    ) {
        var last by remember { mutableStateOf<CallInfo?>(null) }
        call?.let { last = it }
        last?.let { ReturnToCallPill(it) }
    }
}

/**
 * The call going on, taken or dialling, or null; null too when the island
 * floats over every app (the user allowed it), so it is not shown twice.
 */
@Composable
fun ongoingCall(): CallInfo? {
    val store: CallStore = koinInject()
    val state by store.state.collectAsState()
    val context = LocalContext.current
    if (android.provider.Settings.canDrawOverlays(context)) return null
    return state.primary?.takeIf { it.phase != CallPhase.ENDED && it.phase != CallPhase.RINGING }
}

/** The call island, inside the app, where the screen's name goes. */
@Composable
fun ReturnToCallPill(call: CallInfo) {
    val context = LocalContext.current
    val store: CallStore = koinInject()
    val state by store.state.collectAsState()
    val book: com.dialer.app.data.contacts.PhoneBook = koinInject()
    val contacts by book.entries.collectAsState()
    val photo = remember(contacts, call.number) { com.dialer.app.data.contacts.PhoneIndex(contacts).find(com.dialer.app.core.dial.T9.clean(call.number))?.photo }
    androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) {
        var opened by remember(call.id) { mutableStateOf(false) }
        CallIsland(state, call, photo, store, onOpenScreen = { openCallScreen(context) }, room = maxWidth, opened = opened, onOpened = { opened = it })
    }
}

