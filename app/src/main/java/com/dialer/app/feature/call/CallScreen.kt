package com.dialer.app.feature.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.app.core.call.AudioRoute
import com.dialer.app.core.call.CallInfo
import com.dialer.app.core.call.CallPhase
import com.dialer.app.core.call.CallStore
import com.dialer.app.core.call.CallsState
import com.dialer.app.ui.component.BoldButton
import com.dialer.app.ui.component.QuietButton
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.GlassLook
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import com.dialer.app.ui.icon.DialerIcons
import com.dialer.app.ui.theme.zone
import kotlinx.coroutines.delay


@Composable
fun CallScreen(
    state: CallsState,
    keypadOpen: Boolean,
    onKeypad: (Boolean) -> Unit,
    actions: CallStore
) {
    val call = state.primary ?: return
    val compact = keypadOpen && call.phase != CallPhase.ENDED

    // The wave of glass let go by Answer or Decline, spread over the whole
    // window. Answering lets it settle away into the call; declining keeps
    // it until the screen closes.
    var wave by remember(call.id) { mutableStateOf<GlassWave?>(null) }
    val waveProgress = remember(call.id) { Animatable(0f) }
    val waveAlpha = remember(call.id) { Animatable(1f) }
    LaunchedEffect(wave) {
        val w = wave ?: return@LaunchedEffect
        waveProgress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        if (w.color == AnswerGreen) waveAlpha.animateTo(0f, tween(520))
    }
    // At least the screen's height, so the buttons sit at the bottom, and
    // scrolling when two calls and the keypad need more: Hang up is never
    // pushed out of reach.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        StatusPill(call)
        Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
        Caller(call, compact = compact)
        state.secondary?.let { other ->
            Spacer(Modifier.height(16.dp))
            // Joining or switching only makes sense once both calls are taken.
            val taken = call.phase != CallPhase.RINGING
            OtherCall(
                other,
                onSwap = { actions.swap(call.id) }.takeIf { taken },
                onMerge = { actions.merge(call.id) }.takeIf { taken && call.canMerge }
            )
        }
        Spacer(Modifier.weight(1f))

        // A readable column on a tablet, the whole width on a phone.
        Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            AnimatedContent(
                targetState = when {
                    call.phase == CallPhase.ENDED -> Panel.ENDED
                    call.phase == CallPhase.RINGING -> Panel.RINGING
                    call.phase == CallPhase.CHOOSE_SIM -> Panel.SIM
                    keypadOpen -> Panel.KEYPAD
                    else -> Panel.CONTROLS
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "call panel"
            ) { panel ->
                when (panel) {
                    Panel.RINGING -> IncomingChoice(
                        onAnswer = { actions.answer(call.id) },
                        onDecline = { actions.decline(call.id) },
                        onWave = { wave = it }
                    )
                    Panel.SIM -> SimChooser(call, actions)
                    Panel.KEYPAD -> InCallKeypad(
                        onTone = { key -> actions.tone(call.id, key) },
                        onHide = { onKeypad(false) },
                        onHangUp = { actions.hangUp(call.id) }
                    )
                    Panel.CONTROLS -> Controls(state, call, actions, onKeypad = { onKeypad(true) })
                    Panel.ENDED -> Ended(call)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    wave?.let { w ->
        Canvas(Modifier.matchParentSize()) { drawGlassWave(w, waveProgress.value, waveAlpha.value) }
    }
    }
}

private enum class Panel { RINGING, SIM, KEYPAD, CONTROLS, ENDED }

/** One small pane at the top: what is happening, or how long it has lasted. */
@Composable
private fun StatusPill(call: CallInfo) {
    val text = when (call.phase) {
        CallPhase.RINGING -> "Incoming call"
        CallPhase.DIALING -> "Calling"
        CallPhase.CHOOSE_SIM -> "Choose a SIM"
        CallPhase.HOLDING -> "On hold"
        CallPhase.ENDED -> "Call ended"
        CallPhase.ACTIVE -> elapsed(call.connectedAt)
    }
    ZoneSurface(shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

/** Minutes and seconds since [since], ticking once a second. */
@Composable
private fun elapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    if (since <= 0) return "Connecting"
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The person on the line: a round pane with their initial, their name, their number. */
@Composable
private fun Caller(call: CallInfo, compact: Boolean) {
    if (compact) {
        // With the keypad open, the name alone: the keys need the room.
        Text(call.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val size = 120.dp
    ZoneSurface(shape = CircleShape, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            val initial = call.name?.firstOrNull { it.isLetter() }?.uppercaseChar()
            if (initial != null) {
                Text(
                    initial.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    DialerIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        call.title,
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    if (call.name != null && !call.isConference) {
        Spacer(Modifier.height(6.dp))
        Text(call.number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The other call, still going on or on hold, with the way to switch to it. */
@Composable
private fun OtherCall(call: CallInfo, onSwap: (() -> Unit)?, onMerge: (() -> Unit)?) {
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (call.phase == CallPhase.HOLDING) "On hold" else "In call",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(call.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onMerge != null) QuietButton(onClick = onMerge, modifier = Modifier.padding(start = 8.dp)) { Text("Merge") }
            if (onSwap != null) BoldButton(onClick = onSwap, modifier = Modifier.padding(start = 8.dp)) { Text("Switch") }
        }
    }
}

/** The SIMs Telecom offers for this call, when no default is set. */
@Composable
private fun SimChooser(call: CallInfo, actions: CallStore) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                call.sims.forEach { sim ->
                    Text(
                        sim.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actions.chooseSim(call.id, sim) }
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton(DialerIcons.CallEnd, "Cancel", HangUpRed) {
            actions.hangUp(call.id)
        }
    }
}

@Composable
private fun Controls(state: CallsState, call: CallInfo, actions: CallStore, onKeypad: () -> Unit) {
    var routes by rememberSaveable { mutableStateOf(false) }
    // Only the earpiece and the speaker: one tap switches. Anything more,
    // a Bluetooth device or a headset, and the tap offers the choice.
    val choice = state.routes.any { it.kind == AudioRoute.Kind.BLUETOOTH || it.kind == AudioRoute.Kind.WIRED }
    val route = state.route
    val routeIcon = if (route?.kind == AudioRoute.Kind.BLUETOOTH) DialerIcons.Bluetooth else DialerIcons.Speaker
    val routeLabel = when {
        choice -> route?.label ?: "Audio"
        else -> "Speaker"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ZoneSurface(shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton(DialerIcons.MicOff, "Mute", on = state.muted, enabled = call.canMute) {
                        actions.mute(!state.muted)
                    }
                    ToggleButton(DialerIcons.Dialpad, "Keypad", on = false, onClick = onKeypad)
                    ToggleButton(routeIcon, routeLabel, on = route != null && route.kind != AudioRoute.Kind.EARPIECE) {
                        if (choice) {
                            routes = true
                        } else {
                            val target = if (route?.kind == AudioRoute.Kind.SPEAKER) AudioRoute.Kind.EARPIECE else AudioRoute.Kind.SPEAKER
                            state.routes.firstOrNull { it.kind == target }?.let(actions::route)
                        }
                    }
                    ToggleButton(DialerIcons.Hold, "Hold", on = call.phase == CallPhase.HOLDING, enabled = call.canHold) {
                        actions.hold(call.id, call.phase != CallPhase.HOLDING)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        BigButton(DialerIcons.CallEnd, "Hang up", HangUpRed) {
            actions.hangUp(call.id)
        }
    }

    if (routes) {
        ZoneAlertDialog(
            onDismissRequest = { routes = false },
            title = { Text("Audio") },
            text = {
                Column {
                    state.routes.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    actions.route(option)
                                    routes = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Icon(
                                if (option.kind == AudioRoute.Kind.BLUETOOTH) DialerIcons.Bluetooth else DialerIcons.Speaker,
                                contentDescription = null,
                                tint = if (option == route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (option == route) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { routes = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun Ended(call: CallInfo) {
    Text(
        call.endedReason ?: "",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp)
    )
}

/** The letters under each key, as on every phone. */
private val KeyLetters = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+"
)

/** The keypad during a call: each key its own pane of glass, sending its tone while held. */
@Composable
private fun InCallKeypad(onTone: (Char?) -> Unit, onHide: () -> Unit, onHangUp: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            typed.ifEmpty { " " },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("123", "456", "789", "*0#").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { key ->
                        KeypadKey(key) {
                            typed += key
                            onTone(it)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            Spacer(Modifier.size(80.dp))
            BigButton(DialerIcons.CallEnd, "Hang up", HangUpRed, onClick = onHangUp)
            ToggleButton(DialerIcons.Dialpad, "Hide", on = true, onClick = onHide)
        }
    }
}

/** One key: pressed sends [key] to [onTone], released sends null to stop the tone. */
@Composable
private fun KeypadKey(key: Char, onTone: (Char?) -> Unit) {
    val haptics = rememberHaptics()
    val glass = LocalGlass.current
    val shape = CircleShape
    val base = Modifier.size(72.dp).clip(shape)
    Box(
        contentAlignment = Alignment.Center,
        modifier = (if (glass != null) base.glassZone(shape, glass, lens = 1f) else base.background(MaterialTheme.colorScheme.zone))
            .semantics {
                role = Role.Button
                contentDescription = key.toString()
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.tick()
                    onTone(key)
                    waitForUpOrCancellation()
                    onTone(null)
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.toString(), fontSize = 30.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp)
            KeyLetters[key]?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A round control of the call: a small pane of glass, washed with the accent when on. */
@Composable
private fun ToggleButton(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val glass = LocalGlass.current
    val alpha = if (enabled) 1f else 0.38f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) {
        val shape = CircleShape
        val base = Modifier.size(64.dp).clip(shape)
        val accent = rememberTinted(glass, glass?.accentTint)
        val look = when {
            glass != null && on -> base.glassZone(shape, accent!!, lens = 1f)
            glass != null -> base.glassZone(shape, glass, lens = 1f)
            on -> base.background(MaterialTheme.colorScheme.secondaryContainer)
            else -> base.background(MaterialTheme.colorScheme.zone)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = look.clickable(enabled = enabled, role = Role.Button, onClickLabel = label) {
                haptics.toggle(!on)
                onClick()
            }
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = (if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary).copy(alpha = alpha)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), maxLines = 1)
    }
}

/** Hang up and cancel: the same deep glass as the incoming call's buttons. */
@Composable
private fun BigButton(icon: ImageVector, label: String, fill: Color, size: Dp = 80.dp, onClick: () -> Unit) {
    GlassCallButton(icon = icon, label = label, color = fill, size = size, glow = 0.35f, onClick = onClick)
}
