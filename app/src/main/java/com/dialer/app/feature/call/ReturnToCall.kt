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
import com.dialer.app.ui.icon.DialerIcons
import org.koin.compose.koinInject

/**
 * While a call goes on and the app is open over it (Add call, or a look at
 * a contact), a pane of green glass with the call and its time: a tap goes
 * back to the call screen.
 */
@Composable
fun ReturnToCall(modifier: Modifier = Modifier) {
    val store: CallStore = koinInject()
    val state by store.state.collectAsState()
    val call = state.primary?.takeIf { it.phase != CallPhase.ENDED && it.phase != CallPhase.RINGING }
    AnimatedVisibility(
        visible = call != null,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        modifier = modifier
    ) {
        var last by remember { mutableStateOf<CallInfo?>(null) }
        call?.let { last = it }
        last?.let { Pill(it) }
    }
}

@Composable
private fun Pill(call: CallInfo) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val green = rememberTinted(LocalGlass.current, AnswerGreen.copy(alpha = 0.30f))
    CompositionLocalProvider(LocalGlass provides (green ?: LocalGlass.current)) {
        ZoneSurface(
            shape = CircleShape,
            color = AnswerGreen.copy(alpha = 0.18f),
            onClick = {
                haptics.tick()
                context.startActivity(
                    Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Icon(DialerIcons.Call, contentDescription = null, tint = AnswerGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Return to call · " + when (call.phase) {
                        CallPhase.ACTIVE -> elapsed(call.connectedAt)
                        CallPhase.HOLDING -> "On hold"
                        CallPhase.CHOOSE_SIM -> "Choose a SIM"
                        else -> "Calling"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
