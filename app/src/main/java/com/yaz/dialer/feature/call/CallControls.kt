package com.yaz.dialer.feature.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yaz.dialer.ui.component.ZoneSurface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.call.AudioRoute
import com.yaz.dialer.core.call.CallInfo
import com.yaz.dialer.core.call.CallPhase
import com.yaz.dialer.core.call.CallStore
import com.yaz.dialer.core.call.CallsState
import com.yaz.dialer.ui.component.ZoneAlertDialog
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.glass.LocalGlass
import com.yaz.dialer.ui.glass.glassZone
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.ui.theme.zone
import kotlinx.coroutines.delay

/** How a control answers when it is turned on or used. */
enum class ControlMotion { NONE, TURN, WAVES }

/**
 * The controls of a call, each its own drop of glass, standing free: Mute,
 * Keypad, Speaker, then Hold and Add call, and Hang up below. They rise one
 * after the other when the call screen opens.
 */
@Composable
fun CallControls(
    state: CallsState,
    call: CallInfo,
    actions: CallStore,
    onKeypad: () -> Unit,
    onAddCall: () -> Unit,
    onHangUp: (Offset) -> Unit
) {
    var routes by rememberSaveable { mutableStateOf(false) }
    // Only the earpiece and the speaker: one tap switches. Anything more,
    // a Bluetooth device or a headset, and the tap offers the choice.
    val choice = state.routes.any { it.kind == AudioRoute.Kind.BLUETOOTH || it.kind == AudioRoute.Kind.WIRED }
    val route = state.route
    val held = call.phase == CallPhase.HOLDING
    var hangUpAt by remember { mutableStateOf(Offset.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap), verticalAlignment = Alignment.Top) {
            CallControl(
                icon = if (state.muted) AppIcons.MicOff else AppIcons.Mic,
                label = if (state.muted) "Unmute" else "Mute",
                on = state.muted,
                enabled = call.canMute,
                order = 0
            ) { actions.mute(!state.muted) }
            if (call.video) {
                // A video call: the camera in the keypad's place.
                val camera by actions.camera.collectAsState()
                CallControl(
                    icon = if (camera.on) AppIcons.Videocam else AppIcons.VideocamOff,
                    label = if (camera.on) "Camera off" else "Camera on",
                    on = !camera.on,
                    order = 1
                ) { actions.camera(call.id, camera.copy(on = !camera.on)) }
            } else {
                // Tones mean nothing on an encrypted line: no keypad there.
                CallControl(AppIcons.Dialpad, "Keypad", on = false, enabled = !call.encrypted, order = 1, onClick = onKeypad)
            }
            // Sound: a tap switches between the ear and the speaker; with a
            // headset or Bluetooth device about, or held, it offers them all.
            CallControl(
                icon = routeIcon(route?.kind),
                label = when {
                    route == null -> "Speaker"
                    route.kind == AudioRoute.Kind.EARPIECE -> if (choice) "Audio" else "Speaker"
                    else -> route.label
                },
                on = route != null && route.kind != AudioRoute.Kind.EARPIECE,
                motion = if (route?.kind == AudioRoute.Kind.SPEAKER) ControlMotion.WAVES else ControlMotion.NONE,
                order = 2,
                onLongPress = { routes = true },
                extra = {
                    if (routes) {
                        RoutePopover(state, onPick = {
                            actions.route(it)
                            routes = false
                        }, onDismiss = { routes = false })
                    }
                }
            ) {
                if (choice) {
                    routes = true
                } else {
                    val target = if (route?.kind == AudioRoute.Kind.SPEAKER) AudioRoute.Kind.EARPIECE else AudioRoute.Kind.SPEAKER
                    state.routes.firstOrNull { it.kind == target }?.let(actions::route)
                }
            }
        }
        Spacer(Modifier.height(ControlGap))
        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap), verticalAlignment = Alignment.Top) {
            if (call.video) {
                val camera by actions.camera.collectAsState()
                CallControl(AppIcons.CameraSwitch, "Switch camera", on = false, enabled = camera.on, order = 3) {
                    actions.camera(call.id, camera.copy(front = !camera.front))
                }
            } else CallControl(
                icon = if (held) AppIcons.Play else AppIcons.Hold,
                label = if (held) "Resume" else "Hold",
                on = held,
                enabled = call.canHold,
                order = 3
            ) { actions.hold(call.id, !held) }
            // A second call: the dialpad opens, Telecom holds this one when
            // the new one goes out, and Merge joins them after.
            CallControl(
                AppIcons.AddCall,
                "Add call",
                on = false,
                enabled = state.secondary == null && !call.encrypted,
                motion = ControlMotion.TURN,
                order = 4,
                onClick = onAddCall
            )
        }
        Spacer(Modifier.height(32.dp))
        Appear(order = 5) {
            GlassCallButton(
                icon = AppIcons.CallEnd,
                label = "Hang up",
                color = HangUpRed,
                size = 80.dp,
                glow = 0.35f,
                onCenter = { hangUpAt = it },
                onClick = { onHangUp(hangUpAt) }
            )
        }
    }

}

/** Rises into place, a little after the controls before it. */
@Composable
fun Appear(order: Int, content: @Composable () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * STAGGER_MS)
        rise.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
    }
    Box(
        Modifier.graphicsLayer {
            val p = rise.value
            alpha = p.coerceIn(0f, 1f)
            val s = 0.7f + 0.3f * p
            scaleX = s
            scaleY = s
            translationY = (1f - p) * 24.dp.toPx()
        }
    ) { content() }
}

/**
 * One control: a round drop of glass that sinks under the finger. Turned
 * on, the accent fills it from the centre like a drop spreading, and its
 * icon turns into the other one; the speaker, while on, sends out waves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallControl(
    icon: ImageVector,
    label: String,
    on: Boolean,
    enabled: Boolean = true,
    motion: ControlMotion = ControlMotion.NONE,
    order: Int = 0,
    onLongPress: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    val glass = LocalGlass.current
    val accent = MaterialTheme.colorScheme.primary
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    val fill by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 260f), label = "fill")
    val turn = remember { Animatable(0f) }
    var turns by remember { mutableStateOf(0) }
    LaunchedEffect(turns) { if (turns > 0) turn.animateTo(turns * 90f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
    val alpha = if (enabled) 1f else 0.38f

    Appear(order) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(ControlSize + 16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ControlSize)
                    .graphicsLayer {
                        scaleX = sink
                        scaleY = sink
                    }
                    .then(if (motion == ControlMotion.WAVES && on) Modifier.soundWaves(accent) else Modifier)
            ) {
                val base = Modifier.size(ControlSize).clip(CircleShape)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = (if (glass != null) base.glassZone(CircleShape, glass, lens = 1.3f) else base.background(MaterialTheme.colorScheme.zone))
                        .combinedClickable(
                            interactionSource = press,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = label,
                            onLongClick = onLongPress?.let { long ->
                                {
                                    haptics.firm()
                                    long()
                                }
                            },
                            onClick = {
                                if (motion == ControlMotion.TURN) {
                                    haptics.tick()
                                    turns++
                                } else {
                                    haptics.toggle(!on)
                                }
                                onClick()
                            }
                        )
                ) {
                    // The accent spreading from the centre, under the icon.
                    val wash = glass?.accentTint ?: MaterialTheme.colorScheme.primaryContainer
                    Canvas(Modifier.fillMaxSize()) {
                        if (fill > 0.01f) {
                            drawCircle(wash, radius = size.minDimension / 2f * fill.coerceAtMost(1.05f), alpha = fill.coerceIn(0f, 1f))
                        }
                    }
                    AnimatedContent(
                        targetState = icon,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.4f) + fadeOut())
                        },
                        label = "icon"
                    ) { shown ->
                        Icon(
                            shown,
                            contentDescription = label,
                            tint = lerpColor(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer, fill).copy(alpha = alpha),
                            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = turn.value }
                        )
                    }
                }
                extra()
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(targetState = label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "label") { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1
                )
            }
        }
    }
}

/** Arcs of sound leaving the button on both sides, one after the other, while the speaker is on. */
@Composable
private fun Modifier.soundWaves(color: Color): Modifier {
    val waves = rememberInfiniteTransition(label = "waves")
    val t by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    return drawBehind {
        val r = size.minDimension / 2f
        for (k in 0 until 2) {
            val p = (t + k / 2f) % 1f
            val radius = r * (1.05f + 0.45f * p)
            val a = (1f - p) * (1f - p) * 0.6f
            val stroke = Stroke(width = 2.5.dp.toPx() * (1f - 0.5f * p))
            val box = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(color.copy(alpha = a), -35f, 70f, false, topLeft, box, style = stroke)
            drawArc(color.copy(alpha = a), 145f, 70f, false, topLeft, box, style = stroke)
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

fun routeIcon(kind: AudioRoute.Kind?): ImageVector = when (kind) {
    AudioRoute.Kind.BLUETOOTH -> AppIcons.Bluetooth
    AudioRoute.Kind.WIRED -> AppIcons.Headset
    else -> AppIcons.Speaker
}

/**
 * Where the sound goes, offered on a pane of glass rising above the
 * button: the phone at the ear, the speaker, each Bluetooth device by its
 * name, a wired headset. The one in use carries the accent.
 */
@Composable
private fun RoutePopover(state: CallsState, onPick: (AudioRoute) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember { Above(margin) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                expandVertically(tween(200), expandFrom = Alignment.Bottom)
        ) {
            ZoneSurface(shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.routes.forEach { option ->
                        val chosen = option == state.route
                        val wash = LocalGlass.current?.accentTint ?: MaterialTheme.colorScheme.secondaryContainer
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (chosen) wash else Color.Transparent)
                                .clickable {
                                    haptics.tick()
                                    onPick(option)
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Icon(
                                    if (option.kind == AudioRoute.Kind.EARPIECE) AppIcons.Smartphone else routeIcon(option.kind),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                                )
                                if (chosen) Icon(AppIcons.CheckCircle, contentDescription = "In use", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Centres a popup above its anchor, kept on screen. */
private class Above(val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val y = anchorBounds.top - popupContentSize.height - margin
        return IntOffset(
            x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            y.coerceAtLeast(margin)
        )
    }
}

val ControlSize = 72.dp
val ControlGap = 28.dp
private const val STAGGER_MS = 45L
