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
import androidx.compose.material3.ShapeDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
 * The controls of a call, each its own drop of glass, standing free, in
 * LineageOS's grid of three: Speaker, Mute, Keypad, then Add call, Hold, and
 * Hang up apart under them. They rise one after the other when the call
 * screen opens.
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

    // LineageOS Dialer's button grid (2026-11-03): a GridLayout of 3 columns of equal weight, the buttons centred
    // in their cell, the second row incall_button_vertical_padding lower, then the end call apart, 36 dp under it
    // (incall_end_call's marginTop). The 24 dp window margins are the sheet's padding.
    // https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/res/layout/incall_button_grid.xml#GridLayout
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = maxWidth / IncallButtonGridColumnCount
        // incall_button_vertical_padding: values/dimens.xml 8 dp, values-w260dp-h580dp 16 dp, values-w300dp-h620dp 32 dp
        // (the qualifiers read from the window's size in dp, as the resources are):
        // https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/res/values-w300dp-h620dp/dimens.xml#incall_button_vertical_padding
        val windowSize = androidx.compose.ui.platform.LocalConfiguration.current
        val incallButtonVerticalPadding = when {
            windowSize.screenWidthDp >= 300 && windowSize.screenHeightDp >= 620 -> 32.dp
            windowSize.screenWidthDp >= 260 && windowSize.screenHeightDp >= 580 -> 16.dp
            else -> 8.dp
        }
        val mute: @Composable (Int) -> Unit = { order ->
            CheckableLabeledButton(
                cell = cell,
                icon = if (state.muted) AppIcons.MicOff else AppIcons.Mic,
                label = if (state.muted) "Unmute" else "Mute",
                on = state.muted,
                enabled = call.canMute,
                order = order
            ) { actions.mute(!state.muted) }
        }
        // Sound: a tap switches between the ear and the speaker; with a
        // headset or Bluetooth device about, or held, it offers them all.
        val speaker: @Composable () -> Unit = {
            CheckableLabeledButton(
                cell = cell,
                icon = routeIcon(route?.kind),
                label = when {
                    route == null -> "Speaker"
                    route.kind == AudioRoute.Kind.EARPIECE -> if (choice) "Audio" else "Speaker"
                    else -> route.label
                },
                on = route != null && route.kind != AudioRoute.Kind.EARPIECE,
                motion = if (route?.kind == AudioRoute.Kind.SPEAKER) ControlMotion.WAVES else ControlMotion.NONE,
                order = 0,
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // The same controls and rules as before, in LineageOS's slots (incall_button_grid.xml: first to sixth
            // button, rows centred), moved from this CallControls:
            // https://github.com/213YaZ786/Dialer/blob/main/app/src/main/java/com/yaz/dialer/feature/call/CallControls.kt#CallControls
            // Row 1: Speaker, Mute, Keypad; a video call: Speaker, Camera, Mute.
            Row(verticalAlignment = Alignment.Top) {
                speaker()
                if (call.video) {
                    // A video call: the camera in the middle.
                    val camera by actions.camera.collectAsState()
                    CheckableLabeledButton(
                        cell = cell,
                        icon = if (camera.on) AppIcons.Videocam else AppIcons.VideocamOff,
                        label = if (camera.on) "Camera off" else "Camera on",
                        on = !camera.on,
                        order = 1
                    ) { actions.camera(call.id, camera.copy(on = !camera.on)) }
                    mute(2)
                } else {
                    mute(1)
                    // Tones mean nothing on an encrypted line: no keypad there.
                    CheckableLabeledButton(cell, AppIcons.Dialpad, "Keypad", on = false, enabled = !call.encrypted && !call.video, order = 2, onClick = onKeypad)
                }
            }
            // Row 2 (layout_marginTop incall_button_vertical_padding), centred: Add call, Hold;
            // a video call: Switch camera, Keypad.
            Spacer(Modifier.height(incallButtonVerticalPadding))
            Row(verticalAlignment = Alignment.Top) {
                if (call.video) {
                    val camera by actions.camera.collectAsState()
                    CheckableLabeledButton(cell, AppIcons.CameraSwitch, "Switch camera", on = false, enabled = camera.on, order = 3) {
                        actions.camera(call.id, camera.copy(front = !camera.front))
                    }
                    CheckableLabeledButton(cell, AppIcons.Dialpad, "Keypad", on = false, enabled = !call.encrypted && !call.video, order = 4, onClick = onKeypad)
                } else {
                    // A second call: the dialpad opens, Telecom holds this one when
                    // the new one goes out, and Merge joins them after.
                    CheckableLabeledButton(
                        cell,
                        AppIcons.AddCall,
                        "Add call",
                        on = false,
                        enabled = state.secondary == null && !call.encrypted,
                        motion = ControlMotion.TURN,
                        order = 3,
                        onClick = onAddCall
                    )
                    CheckableLabeledButton(
                        cell = cell,
                        icon = if (held) AppIcons.Play else AppIcons.Hold,
                        label = if (held) "Resume" else "Hold",
                        on = held,
                        enabled = call.canHold,
                        order = 4
                    ) { actions.hold(call.id, !held) }
                }
            }
            // incall_end_call (frag_incall_voice.xml): apart under the grid, layout_marginTop 36dp; its
            // marginBottom 36dp is the sheet's.
            Spacer(Modifier.height(IncallEndCallMarginTop))
            Appear(order = 5) {
                EndCallButton(onCenter = { hangUpAt = it }, onClick = { onHangUp(hangUpAt) })
            }
        }
    }
}

/** incall_button_grid.xml: columnCount 3. */
private const val IncallButtonGridColumnCount = 3
/** frag_incall_voice.xml, incall_end_call: layout_marginTop. */
private val IncallEndCallMarginTop = 36.dp

/**
 * A button of the call screen's grid: CallControl in LineageOS's CheckableLabeledButton measures (incall), centred
 * in its [cell], the label wrapping between words inside it (two lines at most; LineageOS's is one line cut by an
 * ellipsis at 9 ems, ours never breaks with another language or the 200 % font).
 * https://github.com/213YaZ786/Dialer/blob/main/app/src/main/java/com/yaz/dialer/feature/call/CallControls.kt#CallControl
 */
@Composable
private fun CheckableLabeledButton(
    cell: androidx.compose.ui.unit.Dp,
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
    Box(Modifier.width(cell), contentAlignment = Alignment.TopCenter) {
        CallControl(icon, label, on, enabled, motion, order, onLongPress, extra, incall = true, maxWidth = cell, onClick = onClick)
    }
}

/**
 * Material 3 Expressive's medium icon button, wide: the tokens of the pill Hang up is drawn in (the container
 * ContainerShapeRound is CornerFull, the pressed one PressedContainerShape CornerMedium).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/MediumIconButtonTokens.kt#MediumIconButtonTokens
 */
private object MediumIconButtonTokens {
    val ContainerHeight = 56.dp
    val IconSize = 24.dp
    val WideLeadingSpace = 24.dp
    val WideTrailingSpace = 24.dp
    val ContainerShapeRound: androidx.compose.ui.graphics.Shape = CircleShape
    val PressedContainerShape: androidx.compose.foundation.shape.CornerBasedShape = ShapeDefaults.Medium
}

/**
 * Hang up under the grid: the wide medium icon button of [MediumIconButtonTokens] (56 dp high, a 24 dp icon, 24 dp
 * each side), round at rest and the medium corner pressed, in GlassCallButton's red glass (its tint, glow and
 * onCenter, so the wave of glass starts at its centre). A sibling of GlassCallButton, which is kept for the
 * incoming call's buttons:
 * https://github.com/213YaZ786/Dialer/blob/main/app/src/main/java/com/yaz/dialer/feature/call/IncomingChoice.kt#GlassCallButton
 */
@Composable
internal fun EndCallButton(
    onCenter: ((Offset) -> Unit)? = null,
    onClick: () -> Unit
) {
    val color = HangUpRed
    val glow = 0.35f
    val glass = LocalGlass.current
    val tinted = rememberTinted(glass, color.copy(alpha = 0.66f))
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "sink")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = MediumIconButtonTokens.WideLeadingSpace + MediumIconButtonTokens.IconSize + MediumIconButtonTokens.WideTrailingSpace
    val height = MediumIconButtonTokens.ContainerHeight
    // Round at rest (CornerFull: half the height), the medium corner while pressed, the change sprung.
    val container = with(density) { Size(width.toPx(), height.toPx()) }
    val corner by animateFloatAsState(
        if (pressed) MediumIconButtonTokens.PressedContainerShape.topStart.toPx(container, density) else container.height / 2f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "corner"
    )
    val shape = if (corner >= container.height / 2f - 0.5f) MediumIconButtonTokens.ContainerShapeRound else androidx.compose.foundation.shape.RoundedCornerShape(with(density) { corner.toDp() })
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .then(if (onCenter != null) Modifier.onGloballyPositioned { onCenter(it.boundsInRoot().center) } else Modifier)
            // The halo is drawn around the button, unclipped.
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.55f * glow), color.copy(alpha = 0f)),
                        center = center,
                        radius = r * 1.9f
                    ),
                    radius = r * 1.9f
                )
            }
    ) {
        val base = Modifier.size(width, height).clip(shape)
        Box(
            contentAlignment = Alignment.Center,
            modifier = (if (tinted != null) base.glassZone(shape, tinted, lens = 1.6f) else base.background(color))
                .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = "Hang up", onClick = onClick)
        ) {
            Icon(AppIcons.CallEnd, contentDescription = "Hang up", tint = Color.White, modifier = Modifier.size(MediumIconButtonTokens.IconSize))
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
    diameter: androidx.compose.ui.unit.Dp = ControlSize,
    labelShown: Boolean = true,
    maxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    incall: Boolean = false,
    onClick: () -> Unit
) {
    // CheckableLabeledButton.init, for the call screen's grid (incall): paddingSize 4 dp around, the round iconSize
    // (incall_labeled_button_size: 48 dp, 64 dp from w260dp-h580dp, read from the window's size in dp as the resource
    // qualifiers are) holding the icon imageSize (36 dp); the label labelParams.topMargin 8 dp under it.
    // https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/CheckableLabeledButton.java#init
    val paddingSize = if (incall) 4.dp else 0.dp
    val windowSize = androidx.compose.ui.platform.LocalConfiguration.current
    val iconSize = if (!incall) diameter else if (windowSize.screenWidthDp >= 260 && windowSize.screenHeightDp >= 580) 64.dp else 48.dp
    val imageSize = if (incall) 36.dp else iconSize * 0.36f
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
        // At least the drop and a margin wide, never a fixed width around words: the label wraps inside [maxWidth].
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = iconSize + if (labelShown) 16.dp else 0.dp, max = maxWidth).padding(paddingSize)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = sink
                        scaleY = sink
                    }
                    .then(if (motion == ControlMotion.WAVES && on) Modifier.soundWaves(accent) else Modifier)
            ) {
                val base = Modifier.size(iconSize).clip(CircleShape)
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
                    // Light spreading from the centre, under the icon (iOS's chosen control):
                    // nearly white by night, the accent by day.
                    val night = glass?.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
                    val wash = if (night) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary
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
                            tint = lerpColor(if (night) Color.White else MaterialTheme.colorScheme.primary, if (night) Color(0xFF1B1B1F) else MaterialTheme.colorScheme.onPrimary, fill).copy(alpha = alpha),
                            modifier = Modifier.size(imageSize).graphicsLayer { rotationZ = turn.value }
                        )
                    }
                }
                extra()
            }
            if (labelShown) {
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState = label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "label") { text ->
                    // Lines break between words, centred, two at most; a single word still too wide shrinks as
                    // the last resort: TextAutoSize.StepBased (https://developer.android.com/develop/ui/compose/text/autosize),
                    // as in PersonActionTile. The width is bounded only in the grid (maxWidth).
                    val style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textAlign = TextAlign.Center
                    ).let { if (incall) it.copy(fontSize = 12.sp) else it } // Dialer_Incall_TextAppearance_Label: 12sp
                    BasicText(
                        text,
                        style = style,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = if (maxWidth.isSpecified) TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp) else null
                    )
                }
            }
        }
    }
}

/** Arcs of sound leaving the button on both sides, one after the other, while the speaker is on (and the face while dialing). */
@Composable
internal fun Modifier.soundWaves(color: Color): Modifier {
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
internal fun RoutePopover(state: CallsState, onPick: (AudioRoute) -> Unit, onDismiss: () -> Unit) {
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
            // A pane over the call: Material 3's extra large corner, its rows the large one (ShapeDefaults,
            // https://developer.android.com/reference/kotlin/androidx/compose/material3/ShapeDefaults).
            ZoneSurface(shape = ShapeDefaults.ExtraLarge, shadowElevation = 6.dp, modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.routes.forEach { option ->
                        val chosen = option == state.route
                        val wash = LocalGlass.current?.accentTint ?: MaterialTheme.colorScheme.secondaryContainer
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(ShapeDefaults.Large)
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
                                    // A device name: two lines, then an ellipsis.
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
