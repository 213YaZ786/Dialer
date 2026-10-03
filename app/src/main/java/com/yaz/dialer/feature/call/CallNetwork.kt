package com.yaz.dialer.feature.call

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.call.CallInfo
import com.yaz.dialer.core.call.CallPhase
import com.yaz.dialer.core.network.CellProtection
import com.yaz.dialer.core.network.CellWatch
import com.yaz.dialer.core.network.NetworkAlert
import com.yaz.dialer.core.network.Protection
import com.yaz.dialer.core.network.Protocol
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.protectionColor
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * The network a phone call goes over, from the call's own SIM; null when
 * the phone network has nothing to do with it (an encrypted call goes
 * over the internet) or when it is not known.
 */
@Composable
fun callProtocol(call: CallInfo): Protocol? {
    if (call.encrypted || call.phase == CallPhase.ENDED) return null
    if (call.wifi) return Protocol.WIFI
    val cell: CellWatch = koinInject()
    val sims by cell.sims.collectAsState()
    val sim = sims.firstOrNull { it.subId == call.subId } ?: sims.firstOrNull { it.isDefaultVoice } ?: sims.firstOrNull()
    return sim?.forCalls?.takeIf { it != Protocol.NONE }
}

/**
 * The call's network going onto 2G, or back from it, as it happens: each
 * change once, null otherwise. [onChange] runs with each one.
 */
@Composable
fun rememberNetworkChange(call: CallInfo, protocol: Protocol?, onChange: (NetworkAlert.Change) -> Unit = {}): NetworkAlert.Change? {
    var before by remember(call.id) { mutableStateOf<Protection?>(null) }
    var change by remember(call.id) { mutableStateOf<NetworkAlert.Change?>(null) }
    val now = protocol?.let(CellProtection::protectionOf)
    LaunchedEffect(call.id, now) {
        if (now == null) return@LaunchedEffect
        NetworkAlert.change(before, now)?.let {
            change = it
            onChange(it)
        }
        before = now
    }
    return change
}

/**
 * Under the network chip on the call screen: the call went onto 2G, felt
 * as a refusal since the phone is often at the ear, and said in a pane of
 * red glass that drops in with the way to turn 2G off; back on a better
 * network, it turns green to say so and goes.
 */
@Composable
fun NetworkAlertBanner(call: CallInfo, protocol: Protocol?) {
    val haptics = rememberHaptics()
    var shown by remember(call.id) { mutableStateOf<NetworkAlert.Change?>(null) }
    rememberNetworkChange(call, protocol) {
        if (it == NetworkAlert.Change.DOWN) {
            haptics.reject()
            shown = it
        } else if (shown != null) {
            haptics.done()
            shown = it
        }
    }
    // Said, then out of the way: the chip stays red while it lasts.
    LaunchedEffect(shown) {
        when (shown) {
            NetworkAlert.Change.DOWN -> { delay(10_000); shown = null }
            NetworkAlert.Change.BACK -> { delay(2_500); shown = null }
            null -> Unit
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 500f)) { -it / 3 } + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.9f) +
            fadeIn(tween(160)) + expandVertically(spring(dampingRatio = 0.9f, stiffness = 600f), expandFrom = Alignment.Top),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(220))
    ) {
        var last by remember { mutableStateOf(NetworkAlert.Change.DOWN) }
        shown?.let { last = it }
        AnimatedContent(last, transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) }, label = "alert") { change ->
            Alert(change, protocol, onClose = {
                haptics.tick()
                shown = null
            })
        }
    }
}

@Composable
private fun Alert(change: NetworkAlert.Change, protocol: Protocol?, onClose: () -> Unit) {
    val context = LocalContext.current
    val down = change == NetworkAlert.Change.DOWN
    val color = if (down) MaterialTheme.colorScheme.error else protectionColor(Protection.PROTECTED)
    val onColor = if (down) MaterialTheme.colorScheme.onError else Color.White
    // The warning beats twice as it lands, like a heart that skipped.
    val beat = remember { Animatable(1f) }
    LaunchedEffect(change) {
        repeat(if (down) 2 else 1) {
            beat.animateTo(1.18f, tween(110))
            beat.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    ZoneSurface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(top = 10.dp).widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Box(Modifier.background(color.copy(alpha = 0.14f)).clickable(onClickLabel = "Close", onClick = onClose)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Box(
                    Modifier.size(40.dp).graphicsLayer { scaleX = beat.value; scaleY = beat.value }.background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (down) AppIcons.Warning else AppIcons.CheckCircle, contentDescription = null, tint = onColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (down) "This call is now on 2G" else "Back on ${protocol?.label ?: "a safer network"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = color
                    )
                    if (down) {
                        Text(
                            "It can be listened to: keep anything sensitive for later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .background(color, RoundedCornerShape(50))
                                .clickable(onClickLabel = "Turn off 2G") {
                                    onClose()
                                    for (action in listOf("android.settings.CELLULAR_NETWORK_SECURITY", Settings.ACTION_NETWORK_OPERATOR_SETTINGS)) {
                                        if (runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Turn off 2G", style = MaterialTheme.typography.labelLarge, color = onColor)
                        }
                    }
                }
            }
        }
    }
}
