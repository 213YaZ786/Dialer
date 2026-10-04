package com.yaz.dialer.feature.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yaz.dialer.core.call.EFFECTS
import com.yaz.dialer.ui.effects.ScreenEffect
import com.yaz.dialer.ui.effects.ScreenKind
import com.yaz.dialer.core.call.AudioRoute
import com.yaz.dialer.core.call.CallInfo
import com.yaz.dialer.core.call.CallPhase
import com.yaz.dialer.core.call.CallStore
import com.yaz.dialer.core.call.CallsState
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons

/**
 * A video call over its pictures, keeping out of the way of the person:
 * one line at the top (the lock, who, how long), and at the bottom right
 * Hang up beside one button that unfolds the other controls upwards.
 */
@Composable
fun VideoCallLayer(
    state: CallsState,
    call: CallInfo,
    actions: CallStore,
    onAddCall: () -> Unit,
    onHangUp: (Offset) -> Unit
) {
    val haptics = rememberHaptics()
    var open by rememberSaveable(call.id) { mutableStateOf(false) }
    // The effect playing over the call: chosen here, or sent from the other side.
    var playing by remember(call.id) { mutableStateOf<Pair<ScreenKind, Long>?>(null) }
    LaunchedEffect(call.id) {
        actions.effects.collect { (id, kind) -> if (id == call.id) playing = kind to System.nanoTime() }
    }
    Box(Modifier.fillMaxSize()) {
        playing?.let { (kind, at) ->
            androidx.compose.runtime.key(at) { ScreenEffect(kind, "", null) { playing = null } }
        }
        // Unfolded, a touch on the picture folds the controls away.
        if (open) Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.tick()
                open = false
            }
        )
        Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp, start = 24.dp, end = 24.dp)) { TopLine(call) }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            var hangUpAt by remember { mutableStateOf(Offset.Zero) }
            Appear(order = 0) {
                GlassCallButton(
                    icon = AppIcons.CallEnd,
                    label = "Hang up",
                    color = HangUpRed,
                    size = VideoButton,
                    glow = 0.35f,
                    labelShown = false,
                    onCenter = { hangUpAt = it },
                    onClick = { onHangUp(hangUpAt) }
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = open,
                    enter = fadeIn() + expandVertically(spring(dampingRatio = 0.75f, stiffness = 500f), expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 14.dp)
                    ) { Unfolded(state, call, actions, onAddCall) }
                }
                Appear(order = 1) {
                    CallControl(
                        icon = if (open) AppIcons.Close else AppIcons.MoreVert,
                        label = if (open) "Fewer controls" else "Controls",
                        on = open,
                        diameter = VideoButton,
                        labelShown = false
                    ) { open = !open }
                }
            }
        }
    }
}

/** The controls a video call keeps folded: the nearest to the thumb first, from the bottom up. */
@Composable
private fun Unfolded(state: CallsState, call: CallInfo, actions: CallStore, onAddCall: () -> Unit) {
    val camera by actions.camera.collectAsState()
    var routes by remember { mutableStateOf(false) }
    val choice = state.routes.any { it.kind == AudioRoute.Kind.BLUETOOTH || it.kind == AudioRoute.Kind.WIRED }
    val route = state.route
    // Built top to bottom; each rises after the one below it.
    // Over the messaging app's encrypted line, a screen effect played on both phones.
    if (call.encrypted) {
        var choosing by remember { mutableStateOf(false) }
        CallControl(
            AppIcons.Sparkles, "Effects", on = choosing, order = 5, diameter = SmallButton, labelShown = false,
            extra = {
                if (choosing) EffectChoices(onPick = { kind ->
                    choosing = false
                    actions.effect(call.id, kind)
                }, onDismiss = { choosing = false })
            }
        ) { choosing = !choosing }
    }
    if (state.secondary == null && !call.encrypted) {
        CallControl(AppIcons.AddCall, "Add call", on = false, motion = ControlMotion.TURN, order = 4, diameter = SmallButton, labelShown = false, onClick = onAddCall)
    }
    if (camera.on) {
        CallControl(AppIcons.CameraSwitch, "Switch camera", on = false, motion = ControlMotion.TURN, order = 3, diameter = SmallButton, labelShown = false) {
            actions.camera(call.id, camera.copy(front = !camera.front))
        }
    }
    CallControl(
        icon = routeIcon(route?.kind),
        label = route?.label ?: "Speaker",
        on = route != null && route.kind != AudioRoute.Kind.EARPIECE,
        motion = if (route?.kind == AudioRoute.Kind.SPEAKER) ControlMotion.WAVES else ControlMotion.NONE,
        order = 2,
        diameter = SmallButton,
        labelShown = false,
        onLongPress = { routes = true },
        extra = {
            if (routes) RoutePopover(state, onPick = {
                actions.route(it)
                routes = false
            }, onDismiss = { routes = false })
        }
    ) {
        if (choice) {
            routes = true
        } else {
            val target = if (route?.kind == AudioRoute.Kind.SPEAKER) AudioRoute.Kind.EARPIECE else AudioRoute.Kind.SPEAKER
            state.routes.firstOrNull { it.kind == target }?.let(actions::route)
        }
    }
    CallControl(
        icon = if (camera.on) AppIcons.Videocam else AppIcons.VideocamOff,
        label = if (camera.on) "Camera off" else "Camera on",
        on = !camera.on,
        order = 1,
        diameter = SmallButton,
        labelShown = false
    ) { actions.camera(call.id, camera.copy(on = !camera.on)) }
    CallControl(
        icon = if (state.muted) AppIcons.MicOff else AppIcons.Mic,
        label = if (state.muted) "Unmute" else "Mute",
        on = state.muted,
        enabled = call.canMute,
        order = 0,
        diameter = SmallButton,
        labelShown = false
    ) { actions.mute(!state.muted) }
}

/** Who, and how long, on one small pane: the lock in front when the line is encrypted. */
@Composable
private fun TopLine(call: CallInfo) {
    Appear(order = 0) {
        ZoneSurface(shape = CircleShape, modifier = Modifier.widthIn(max = 420.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (call.encrypted) {
                    Icon(AppIcons.Lock, contentDescription = "End-to-end encrypted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    call.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("  ·  ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (call.phase == CallPhase.ACTIVE) RollingText(elapsed(call.connectedAt), MaterialTheme.typography.titleMedium)
                else Text("Calling", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val VideoButton = 60.dp
private val SmallButton = 52.dp

/** The effects offered, on a pane of glass beside the button: each a glyph and its name. */
@Composable
private fun EffectChoices(onPick: (ScreenKind) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember { Beside(margin) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 0.5f))
        ) {
            ZoneSurface(shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, modifier = Modifier.width(200.dp)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EFFECTS.forEach { kind ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    haptics.done()
                                    onPick(kind)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(glyphOf(kind), style = MaterialTheme.typography.titleLarge)
                            Text(nameOf(kind), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun glyphOf(kind: ScreenKind) = when (kind) {
    ScreenKind.CONFETTI -> "\uD83C\uDF8A"
    ScreenKind.BALLOONS -> "\uD83C\uDF88"
    ScreenKind.FIREWORKS -> "\uD83C\uDF86"
    ScreenKind.LOVE -> "\u2764\uFE0F"
    ScreenKind.STARS -> "\u2728"
    ScreenKind.CELEBRATION -> "\uD83C\uDF89"
    ScreenKind.LASERS -> "\u26A1"
    else -> ""
}

private fun nameOf(kind: ScreenKind) = when (kind) {
    ScreenKind.CONFETTI -> "Confetti"
    ScreenKind.BALLOONS -> "Balloons"
    ScreenKind.FIREWORKS -> "Fireworks"
    ScreenKind.LOVE -> "Love"
    ScreenKind.STARS -> "Stars"
    ScreenKind.CELEBRATION -> "Celebration"
    ScreenKind.LASERS -> "Lasers"
    else -> ""
}

/** A popup to the left of its anchor, its bottom at the anchor's bottom, kept on screen. */
private class Beside(val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left - popupContentSize.width - margin
        val y = anchorBounds.bottom - popupContentSize.height
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        )
    }
}
