package com.yaz.dialer.feature.call

import android.telephony.TelephonyManager
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
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.glass.LocalGlass
import com.yaz.dialer.ui.glass.glassZone
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.ui.theme.zone
import kotlinx.coroutines.delay
import kotlin.math.hypot

/** How a control answers when it is turned on or used. */
enum class ControlMotion { NONE, TURN, WAVES }

/**
 * One button of the call, as the screen offers it: what it shows, whether it is on, whether it can be used, what
 * it does. The chooser below decides which of them take a pill and which go into More.
 */
private class CallButton(
    val icon: ImageVector,
    val label: String,
    val on: Boolean,
    val enabled: Boolean,
    val motion: ControlMotion = ControlMotion.NONE,
    val onLongPress: (() -> Unit)? = null,
    val extra: @Composable () -> Unit = {},
    val onClick: () -> Unit
)

/**
 * The controls of a call, Google Phone 2025's layout in our glass (the visual validated 2026-11-06): one row of
 * four pills, Keypad, Mute, Speaker and More, then Hang up apart under them as a wide red pill. LineageOS's
 * ButtonChooser, with three slots, picks the three buttons that get a pill; the others (Add call, Hold, the
 * camera of a video call) are listed in More. They rise one after the other when the call screen opens.
 *
 * The row is laid out as LineageOS's incall_button_grid.xml centres its buttons in their cells, here four pills
 * 14 dp apart; the 140 dp of the row above the bottom and the 40 dp under Hang up are the sheet's (CallScreen).
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/res/layout/incall_button_grid.xml#GridLayout
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
    var more by rememberSaveable { mutableStateOf(false) }
    // Only the earpiece and the speaker: one tap switches. Anything more,
    // a Bluetooth device or a headset, and the tap offers the choice.
    val choice = state.routes.any { it.kind == AudioRoute.Kind.BLUETOOTH || it.kind == AudioRoute.Kind.WIRED }
    val route = state.route
    val held = call.phase == CallPhase.HOLDING
    val camera by actions.camera.collectAsState()
    var hangUpAt by remember { mutableStateOf(Offset.Zero) }

    // The chooser of the call's network: the voice network type and phone type of the call's SIM, Wi-Fi from the call.
    // https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/ButtonChooserFactory.java#newButtonChooser
    val context = LocalContext.current
    val chooser = remember(call.subId, call.wifi) {
        val manager = context.getSystemService(TelephonyManager::class.java)
        val sim = if (call.subId >= 0) runCatching { manager.createForSubscriptionId(call.subId) }.getOrNull() ?: manager else manager
        newButtonChooser(
            voiceNetworkType = runCatching { sim.voiceNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN),
            isWiFi = call.wifi,
            phoneType = sim.phoneType
        )
    }

    // The buttons and their rules, as they were (moved from this CallControls):
    // https://github.com/213YaZ786/Dialer/blob/main/app/src/main/java/com/yaz/dialer/feature/call/CallControls.kt#CallControls
    val buttons = linkedMapOf<Int, CallButton>()
    buttons[InCallButtonIds.BUTTON_MUTE] = CallButton(
        icon = if (state.muted) AppIcons.MicOff else AppIcons.Mic,
        label = if (state.muted) "Unmute" else "Mute",
        on = state.muted,
        enabled = call.canMute
    ) { actions.mute(!state.muted) }
    // Tones mean nothing on an encrypted line: no keypad there.
    buttons[InCallButtonIds.BUTTON_DIALPAD] = CallButton(AppIcons.Dialpad, "Keypad", on = false, enabled = !call.encrypted && !call.video, onClick = onKeypad)
    // Sound: a tap switches between the ear and the speaker; with a headset or Bluetooth device about, or held,
    // it offers them all.
    buttons[InCallButtonIds.BUTTON_AUDIO] = CallButton(
        icon = routeIcon(route?.kind),
        label = when {
            route == null -> "Speaker"
            route.kind == AudioRoute.Kind.EARPIECE -> if (choice) "Audio" else "Speaker"
            else -> route.label
        },
        on = route != null && route.kind != AudioRoute.Kind.EARPIECE,
        enabled = true,
        motion = if (route?.kind == AudioRoute.Kind.SPEAKER) ControlMotion.WAVES else ControlMotion.NONE,
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
    if (!call.video) {
        // A second call: the dialpad opens, Telecom holds this one when the new one goes out, and Merge joins them after.
        buttons[InCallButtonIds.BUTTON_ADD_CALL] = CallButton(
            AppIcons.AddCall,
            "Add call",
            on = false,
            enabled = state.secondary == null && !call.encrypted,
            motion = ControlMotion.TURN,
            onClick = onAddCall
        )
        buttons[InCallButtonIds.BUTTON_HOLD] = CallButton(
            icon = if (held) AppIcons.Play else AppIcons.Hold,
            label = if (held) "Resume" else "Hold",
            on = held,
            enabled = call.canHold
        ) { actions.hold(call.id, !held) }
    }
    // A video call's camera, which LineageOS's mapping does not hold (its video screen has its own buttons): after the others.
    val videoButtons = if (call.video) {
        listOf(
            CallButton(
                icon = if (camera.on) AppIcons.Videocam else AppIcons.VideocamOff,
                label = if (camera.on) "Camera off" else "Camera on",
                on = !camera.on,
                enabled = true
            ) { actions.camera(call.id, camera.copy(on = !camera.on)) },
            CallButton(AppIcons.CameraSwitch, "Switch camera", on = false, enabled = camera.on) {
                actions.camera(call.id, camera.copy(front = !camera.front))
            }
        )
    } else emptyList()

    // The three slots: ButtonChooser over the allowed buttons, the disabled ones apart (it does not move them in).
    val allowedButtons = buttons.keys
    val disabledButtons = buttons.filterValues { !it.enabled }.keys
    val numUiButtons = 3
    val placed = chooser.getButtonPlacement(numUiButtons, allowedButtons, disabledButtons)
    // The pills in the visual's order, Keypad, Mute, Speaker; the rest, in the chooser's order, goes to More.
    val visualOrder = listOf(InCallButtonIds.BUTTON_DIALPAD, InCallButtonIds.BUTTON_MUTE, InCallButtonIds.BUTTON_AUDIO)
    val pills = placed.sortedBy { id -> visualOrder.indexOf(id).let { if (it < 0) visualOrder.size else it } }
    val all = chooser.getButtonPlacement(allowedButtons.size, allowedButtons, disabledButtons)
    val rest = ((all + allowedButtons).distinct() - pills.toSet()).mapNotNull { buttons[it] } + videoButtons
    val moreButton = CallButton(
        icon = AppIcons.MoreVert,
        label = "More",
        on = false,
        enabled = true,
        extra = { if (more) MorePopover(rest, onDismiss = { more = false }) }
    ) { more = true }
    val slots = pills.mapNotNull { buttons[it] } + if (rest.isNotEmpty()) listOf(moreButton) else emptyList()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Wide medium icon buttons: leading space, icon, trailing space (MediumIconButtonTokens), 14 dp apart; on a
        // window too narrow for them the pills narrow together.
        val gap = 14.dp
        val wide = MediumIconButtonTokens.WideLeadingSpace + MediumIconButtonTokens.IconSize + MediumIconButtonTokens.WideTrailingSpace
        val width = minOf(wide, (maxWidth - gap * (slots.size - 1)) / slots.size)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                slots.forEachIndexed { index, button ->
                    CallPill(button, width = width, labelWidth = width + gap, order = index)
                }
            }
            // Hang up apart under the row: its top 28 dp below the labels (the row 140 dp, Hang up 40 dp above the bottom).
            Spacer(Modifier.height(28.dp))
            Appear(order = slots.size) {
                EndCallButton(onCenter = { hangUpAt = it }, onClick = { onHangUp(hangUpAt) })
            }
        }
    }
}

/**
 * Google Phone 2025's pill in our glass: the medium wide icon toggle button (56 dp high, [width] wide), round at
 * rest, a rounded rectangle when on, the pressed shape in between (IconToggleButton with its three shapes), the
 * label 12 sp under it as CheckableLabeledButton gives it (the words wrap, two lines at most, a long one shrinks):
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/CheckableLabeledButton.java#init
 * Turned on, light spreads from the centre as CallControl's drop does (iOS's chosen control): nearly white by night,
 * the accent by day.
 */
@Composable
private fun CallPill(button: CallButton, width: Dp, labelWidth: Dp, order: Int) {
    val haptics = rememberHaptics()
    val glass = LocalGlass.current
    val accent = MaterialTheme.colorScheme.primary
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    val fill by animateFloatAsState(if (button.on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 260f), label = "fill")
    val turn = remember { Animatable(0f) }
    var turns by remember { mutableStateOf(0) }
    LaunchedEffect(turns) { if (turns > 0) turn.animateTo(turns * 90f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
    val alpha = if (button.enabled) 1f else 0.38f
    val shapes = remember {
        IconToggleButtonShapes(
            shape = MediumIconButtonTokens.ContainerShapeRound,
            pressedShape = MediumIconButtonTokens.PressedContainerShape,
            checkedShape = MediumIconButtonTokens.SelectedContainerShapeRound
        )
    }
    val night = glass?.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
    val wash = if (night) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary

    Appear(order) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width)) {
            Box(
                modifier = Modifier
                    .size(width, MediumIconButtonTokens.ContainerHeight)
                    .graphicsLayer {
                        scaleX = sink
                        scaleY = sink
                    }
                    .then(if (button.motion == ControlMotion.WAVES && button.on) Modifier.soundWaves(accent) else Modifier)
            ) {
                IconToggleButton(
                    checked = button.on,
                    onCheckedChange = {
                        if (button.motion == ControlMotion.TURN) {
                            haptics.tick()
                            turns++
                        } else {
                            haptics.toggle(it)
                        }
                        button.onClick()
                    },
                    shapes = shapes,
                    modifier = Modifier.size(width, MediumIconButtonTokens.ContainerHeight),
                    enabled = button.enabled,
                    interactionSource = press,
                    onLongClick = button.onLongPress?.let { long ->
                        {
                            haptics.firm()
                            long()
                        }
                    },
                    label = button.label
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (fill > 0.01f) {
                            drawCircle(wash, radius = hypot(size.width, size.height) / 2f * fill.coerceAtMost(1.05f), alpha = fill.coerceIn(0f, 1f))
                        }
                    }
                    AnimatedContent(
                        targetState = button.icon,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.4f) + fadeOut())
                        },
                        label = "icon"
                    ) { shown ->
                        Icon(
                            shown,
                            contentDescription = button.label,
                            tint = lerpColor(if (night) Color.White else MaterialTheme.colorScheme.primary, if (night) Color(0xFF1B1B1F) else MaterialTheme.colorScheme.onPrimary, fill).copy(alpha = alpha),
                            modifier = Modifier.size(MediumIconButtonTokens.IconSize).graphicsLayer { rotationZ = turn.value }
                        )
                    }
                }
                button.extra()
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(targetState = button.label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "label") { text ->
                // Lines break between words, centred, two at most; a single word still too wide shrinks as the last
                // resort: Compose's TextAutoSize.StepBased, as CallControl's label.
                val style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp, // Dialer_Incall_TextAppearance_Label: 12sp
                    fontWeight = if (button.on) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    textAlign = TextAlign.Center
                )
                BasicText(
                    text,
                    style = style,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.wrapContentWidth(unbounded = true).widthIn(max = labelWidth)
                )
            }
        }
    }
}

/**
 * Material 3 Expressive's medium icon button, wide: the tokens of the pills and of the pill Hang up is drawn in (the
 * container ContainerShapeRound is CornerFull, the pressed one PressedContainerShape CornerMedium, the selected one
 * SelectedContainerShapeRound CornerLarge).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/MediumIconButtonTokens.kt#MediumIconButtonTokens
 * (ContainerShapeSquare, SelectedContainerShapeSquare, the default and narrow spaces and the outline width are not
 * ported: the pills are wide and round, never square, narrow or outlined.)
 */
private object MediumIconButtonTokens {
    val ContainerHeight = 56.dp
    val IconSize = 24.dp
    val WideLeadingSpace = 24.dp
    val WideTrailingSpace = 24.dp
    val ContainerShapeRound: CornerBasedShape = CircleShape
    val PressedContainerShape: CornerBasedShape = ShapeDefaults.Medium
    val SelectedContainerShapeRound: CornerBasedShape = ShapeDefaults.Large
}

/**
 * The shapes of a toggle button: it morphs between these three depending on the interaction, assuming all of them
 * are corner based shapes.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/IconButton.kt#IconToggleButtonShapes
 *
 * @property shape is the unchecked shape.
 * @property pressedShape is the pressed shape.
 * @property checkedShape is the checked shape.
 */
private class IconToggleButtonShapes(
    val shape: Shape,
    val pressedShape: Shape = shape,
    val checkedShape: Shape = shape
) {
    /** Returns a copy of these shapes, optionally overriding some of the values. */
    fun copy(
        shape: Shape? = this.shape,
        pressedShape: Shape? = this.pressedShape,
        checkedShape: Shape? = this.checkedShape
    ): IconToggleButtonShapes =
        IconToggleButtonShapes(
            shape = shape.takeOrElse { this.shape },
            pressedShape = pressedShape.takeOrElse { this.pressedShape },
            checkedShape = checkedShape.takeOrElse { this.checkedShape }
        )

    fun Shape?.takeOrElse(block: () -> Shape): Shape = this ?: block()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is IconToggleButtonShapes) return false

        if (shape != other.shape) return false
        if (pressedShape != other.pressedShape) return false
        if (checkedShape != other.checkedShape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + pressedShape.hashCode()
        result = 31 * result + checkedShape.hashCode()

        return result
    }
}

private val IconToggleButtonShapes.isCornerBasedShape: Boolean
    get() =
        shape is CornerBasedShape &&
            pressedShape is CornerBasedShape &&
            checkedShape is CornerBasedShape

private val IconToggleButtonShapes.isStatic: Boolean
    get() = shape === pressedShape && shape === checkedShape

/**
 * A toggle button in our glass: Material's IconToggleButton with [shapes], its container drawn as CallControl's
 * drops are (glassZone, or the zone colour without glass) instead of a flat colour and a ripple, plus a long press.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/IconButton.kt#IconToggleButton
 * (not ported: the overloads without [shapes] and the IconToggleButtonColors, the glass has its own colours; the
 * minimum 48 dp touch target, the pills are 56 dp high.)
 *
 * @param checked whether this button is toggled on or off
 * @param onCheckedChange called when this icon button is clicked
 * @param shapes the shapes that the icon toggle button will morph between depending on the user's interaction
 * @param enabled controls the enabled state of this icon button
 * @param interactionSource an optional hoisted source for observing the interactions of this icon button
 * @param content the content of this icon button, typically an Icon
 */
@Composable
private fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: IconToggleButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    label: String,
    content: @Composable BoxScope.() -> Unit
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    IconToggleButtonImpl(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shape = shapeForInteraction(checked, shapes, interactionSource),
        interactionSource = interactionSource,
        onLongClick = onLongClick,
        label = label,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconToggleButtonImpl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape,
    onLongClick: (() -> Unit)? = null,
    label: String,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlass.current
    val base = modifier.clip(shape)
    Box(
        modifier = (if (glass != null) base.glassZone(shape, glass, lens = 1.3f) else base.background(MaterialTheme.colorScheme.zone))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onLongClick = onLongClick,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun shapeForInteraction(
    checked: Boolean,
    shapes: IconToggleButtonShapes,
    interactionSource: MutableInteractionSource
): Shape {
    if (shapes.isStatic) {
        return shapes.shape
    }
    // The expressive scheme's default effects spring (damping 1, stiffness 1600): no bounce in this component.
    val defaultAnimationSpec = spring<Float>(dampingRatio = 1f, stiffness = 1600f)
    val pressed by interactionSource.collectIsPressedAsState()

    return shapeByInteraction(shapes, pressed, checked, defaultAnimationSpec)
}

@Composable
private fun shapeByInteraction(
    shapes: IconToggleButtonShapes,
    pressed: Boolean,
    checked: Boolean,
    animationSpec: FiniteAnimationSpec<Float>
): Shape {
    val shape =
        if (pressed) {
            shapes.pressedShape
        } else if (checked) {
            shapes.checkedShape
        } else shapes.shape

    if (shapes.isCornerBasedShape) {
        return key(shapes) { rememberAnimatedShape(shape as CornerBasedShape, animationSpec) }
    }
    return shape
}

/**
 * A state class that manages the animation between different corner based shapes: the progress of the morph, the
 * start and the target shapes; it reverses smoothly when the target goes back to the start before the end.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/AnimatedShape.kt#AnimatedShapeState
 */
@Stable
private class AnimatedShapeState(
    initialShape: CornerBasedShape,
    val spec: FiniteAnimationSpec<Float>
) {
    var startShape: CornerBasedShape = initialShape
    var targetShape: CornerBasedShape = initialShape
    val progress = Animatable(1f)

    private var cachedProgress: Float = -1f
    private var cachedMorphedShape: CornerBasedShape? = null

    /** The shape between start and target at [progress], kept while it does not move. */
    fun getMorphedShape(): CornerBasedShape {
        val currentProgress = progress.value
        val cached = cachedMorphedShape

        if (currentProgress == cachedProgress && cached != null) {
            return cached
        }

        val morphed = lerpShape(startShape, targetShape, currentProgress)
        cachedProgress = currentProgress
        cachedMorphedShape = morphed
        return morphed
    }

    suspend fun animateToShape(newTarget: CornerBasedShape) {
        if (targetShape == newTarget) return

        if (newTarget == startShape) {
            // The user reversed their action before the animation finished: flip the progress, reverse the velocity.
            startShape = targetShape
            targetShape = newTarget
            cachedMorphedShape = null

            val p = progress.value
            val v = progress.velocity

            progress.snapTo(1f - p)
            progress.animateTo(1f, spec, initialVelocity = -v)
        } else {
            // A new target: freeze the shape visible now and morph from it.
            val currentProgress = progress.value
            val capturedStart = startShape
            val capturedTarget = targetShape

            startShape =
                when (currentProgress) {
                    1f -> capturedTarget
                    0f -> capturedStart
                    else -> lerpShape(capturedStart, capturedTarget, currentProgress)
                }

            targetShape = newTarget
            cachedMorphedShape = null
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
}

/** The corners between two shapes (Interpolatable.lerp of a corner based shape), each corner resolved at the shape's size. */
private fun lerpShape(start: CornerBasedShape, stop: CornerBasedShape, fraction: Float): CornerBasedShape =
    RoundedCornerShape(
        topStart = lerpCorner(start.topStart, stop.topStart, fraction),
        topEnd = lerpCorner(start.topEnd, stop.topEnd, fraction),
        bottomEnd = lerpCorner(start.bottomEnd, stop.bottomEnd, fraction),
        bottomStart = lerpCorner(start.bottomStart, stop.bottomStart, fraction)
    )

private fun lerpCorner(start: CornerSize, stop: CornerSize, fraction: Float): CornerSize = object : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        androidx.compose.ui.util.lerp(start.toPx(shapeSize, density), stop.toPx(shapeSize, density), fraction)
}

@Composable
private fun rememberAnimatedShape(state: AnimatedShapeState): Shape {
    return remember(state) {
        // (not ported: offset and CenterOpticallyCoefficient, the optical centring of the content, internal to material3)
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                // The cached shape, or a new one when the progress advanced
                val morphedShape = state.getMorphedShape()

                // The morphed shape draws the outline
                return morphedShape.createOutline(size, layoutDirection, density)
            }
        }
    }
}

/**
 * Resolves and remembers a shape that smoothly morphs between corner based shapes.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/AnimatedShape.kt#rememberAnimatedShape
 */
@Composable
private fun rememberAnimatedShape(
    currentShape: CornerBasedShape,
    animationSpec: FiniteAnimationSpec<Float>
): Shape {
    val state =
        remember(animationSpec) {
            AnimatedShapeState(initialShape = currentShape, spec = animationSpec)
        }

    LaunchedEffect(currentShape, state) { state.animateToShape(currentShape) }

    return rememberAnimatedShape(state)
}

/**
 * Hang up apart under the pills: the visual's wide red pill (200 x 72 dp, the icon 32 dp, the visual validated
 * 2026-11-06), round at rest (CornerFull) and the medium corner pressed (MediumIconButtonTokens), in GlassCallButton's
 * red glass (its tint, glow and onCenter, so the wave of glass starts at its centre). A sibling of GlassCallButton,
 * which is kept for the incoming call's buttons (not ported: GlassCallButton, it stays in IncomingChoice):
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
    val width = 200.dp
    val height = 72.dp
    // Round at rest (CornerFull: half the height), the medium corner while pressed, the change sprung.
    val container = with(density) { Size(width.toPx(), height.toPx()) }
    val corner by animateFloatAsState(
        if (pressed) MediumIconButtonTokens.PressedContainerShape.topStart.toPx(container, density) else container.height / 2f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "corner"
    )
    val shape = if (corner >= container.height / 2f - 0.5f) MediumIconButtonTokens.ContainerShapeRound else RoundedCornerShape(with(density) { corner.toDp() })
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .then(if (onCenter != null) Modifier.onGloballyPositioned { onCenter(it.boundsInRoot().center) } else Modifier)
            // The halo is drawn around the button, unclipped, stretched along the pill.
            .drawBehind {
                val r = this.size.height / 2f
                withTransform({ scale(this@drawBehind.size.width / this@drawBehind.size.height, 1f, pivot = center) }) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.55f * glow), color.copy(alpha = 0f)),
                            center = center,
                            radius = r * 1.9f
                        ),
                        radius = r * 1.9f
                    )
                }
            }
    ) {
        val base = Modifier.size(width, height).clip(shape)
        Box(
            contentAlignment = Alignment.Center,
            modifier = (if (tinted != null) base.glassZone(shape, tinted, lens = 1.6f) else base.background(color))
                .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = "Hang up", onClick = onClick)
        ) {
            Icon(AppIcons.CallEnd, contentDescription = "Hang up", tint = Color.White, modifier = Modifier.size(32.dp))
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
                    // the last resort: Compose's TextAutoSize.StepBased, as in PersonActionTile. The width is
                    // bounded only in the grid (maxWidth).
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
 * A pane of glass rising above the button that opened it: Material 3's extra large corner, centred above its
 * anchor, kept on screen, dismissed by a tap outside. The pane of Where the sound goes and of More.
 */
@Composable
private fun PanePopover(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
            }
        }
    }
}

/** One row of a pane: its icon, its words, and a check when it is the one in use. */
@Composable
private fun PaneRow(icon: ImageVector, label: String, chosen: Boolean, enabled: Boolean = true, check: Boolean = chosen, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val wash = LocalGlass.current?.accentTint ?: MaterialTheme.colorScheme.secondaryContainer
    val alpha = if (enabled) 1f else 0.38f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(ShapeDefaults.Large)
            .background(if (chosen) wash else Color.Transparent)
            .clickable(enabled = enabled) {
                haptics.tick()
                onClick()
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                // A device name: two lines, then an ellipsis.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 14.dp)
            )
            if (check) Icon(AppIcons.CheckCircle, contentDescription = "In use", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Where the sound goes, offered on a pane of glass rising above the
 * button: the phone at the ear, the speaker, each Bluetooth device by its
 * name, a wired headset. The one in use carries the accent.
 */
@Composable
internal fun RoutePopover(state: CallsState, onPick: (AudioRoute) -> Unit, onDismiss: () -> Unit) {
    PanePopover(onDismiss) {
        state.routes.forEach { option ->
            PaneRow(
                icon = if (option.kind == AudioRoute.Kind.EARPIECE) AppIcons.Smartphone else routeIcon(option.kind),
                label = option.label,
                chosen = option == state.route
            ) { onPick(option) }
        }
    }
}

/**
 * The buttons that did not get a pill, on the same pane as the routes, each a row with its icon and its word, in
 * the order ButtonChooser gives them; a button that cannot be used now stays, greyed. The one that is on (a call
 * held) carries the accent.
 */
@Composable
private fun MorePopover(buttons: List<CallButton>, onDismiss: () -> Unit) {
    PanePopover(onDismiss) {
        buttons.forEach { button ->
            PaneRow(icon = button.icon, label = button.label, chosen = button.on, enabled = button.enabled, check = false) {
                button.onClick()
                onDismiss()
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

/**
 * Ids for buttons in the in call UI.
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/protocol/InCallButtonIds.java
 */
private object InCallButtonIds {
    const val BUTTON_AUDIO = 0
    const val BUTTON_MUTE = 1
    const val BUTTON_DIALPAD = 2
    const val BUTTON_HOLD = 3
    const val BUTTON_SWAP = 4
    const val BUTTON_UPGRADE_TO_VIDEO = 5
    const val BUTTON_SWITCH_CAMERA = 6
    const val BUTTON_DOWNGRADE_TO_AUDIO = 7
    const val BUTTON_ADD_CALL = 8
    const val BUTTON_MERGE = 9
    const val BUTTON_PAUSE_VIDEO = 10
    const val BUTTON_MANAGE_VIDEO_CONFERENCE = 11
    const val BUTTON_MANAGE_VOICE_CONFERENCE = 12
    const val BUTTON_SWITCH_TO_SECONDARY = 13
    const val BUTTON_SWAP_SIM = 14
    const val BUTTON_RECORD_CALL = 15
    const val BUTTON_UPGRADE_TO_RTT = 16
    const val BUTTON_COUNT = 17
}

/**
 * Holds information about button mapping.
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/MappedButtonConfig.java#MappingInfo
 */
private class MappingInfo(
    /** The Ui slot into which a given button desires to be placed. */
    val slot: Int,
    /**
     * Used to determine which button is chosen for a slot when multiple buttons desire to be placed in the same
     * slot: a lower slotOrder results in the button being chosen.
     */
    val slotOrder: Int,
    /**
     * Used to determine the order in which buttons that weren't chosen for their desired slot are placed into the
     * Ui: a lower conflictOrder results in the button being chosen.
     */
    val conflictOrder: Int,
    /** A button for which the given button conflicts; if it is chosen, the associated button is never chosen. */
    val mutuallyExclusiveButton: Int
) {
    /** Class used to build instances of [MappingInfo]. */
    class Builder(private var slot: Int) {
        private var slotOrder = Int.MAX_VALUE
        private var conflictOrder = Int.MAX_VALUE
        private var mutuallyExclusiveButton = NO_MUTUALLY_EXCLUSIVE_BUTTON_SET

        fun setSlot(slot: Int) = apply { this.slot = slot }
        fun setSlotOrder(slotOrder: Int) = apply { this.slotOrder = slotOrder }
        fun setConflictOrder(conflictOrder: Int) = apply { this.conflictOrder = conflictOrder }
        fun setMutuallyExclusiveButton(button: Int) = apply { this.mutuallyExclusiveButton = button }
        fun build() = MappingInfo(slot, slotOrder, conflictOrder, mutuallyExclusiveButton)
    }

    companion object {
        const val NO_MUTUALLY_EXCLUSIVE_BUTTON_SET = -1

        fun builder(slot: Int) = Builder(slot)
    }
}

/**
 * Determines logical button slot and ordering based on a provided mapping.
 *
 * The provided mapping is declared with the following pieces of information: key, the button for which the mapping
 * applies; [MappingInfo.slot], the arbitrarily indexed slot into which the button desires to be placed;
 * [MappingInfo.slotOrder], used to choose the correct button when multiple desire to be placed in the same slot; and
 * [MappingInfo.conflictOrder], used to determine the overall order for buttons that weren't chosen for their
 * desired slot.
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/MappedButtonConfig.java#MappedButtonConfig
 * (not ported: the AutoValue builder machinery, MappingInfo is a plain class; InCallButtonIdsExtension.toString in
 * the error message, the id is named instead.)
 */
private class MappedButtonConfig(mapping: Map<Int, MappingInfo>) {
    // Android's ArrayMap iterates its integer keys in ascending order: a sorted map keeps that order.
    private val mapping: java.util.TreeMap<Int, MappingInfo> = java.util.TreeMap(mapping)
    private val orderedMappedSlots: List<Int> = findOrderedMappedSlots()

    private fun findOrderedMappedSlots(): List<Int> {
        val slots = HashSet<Int>()
        for (entry in mapping.entries) {
            slots.add(entry.value.slot)
        }
        val orderedSlots = ArrayList(slots)
        orderedSlots.sort()
        return orderedSlots
    }

    /** Returns the slots for which this class has button mapping. */
    fun getOrderedMappedSlots(): List<Int> {
        if (mapping.isEmpty()) {
            return emptyList()
        }
        return orderedMappedSlots
    }

    /** The buttons configured to be placed in the given ui slot. */
    fun getButtonsForSlot(slot: Int): MutableList<Int> {
        val buttons = ArrayList<Int>()
        for (entry in mapping.entries) {
            if (entry.value.slot == slot) {
                buttons.add(entry.key)
            }
        }
        return buttons
    }

    /** Orders buttons that are configured to be placed in the same slot by the natural ordering of the slotOrder. */
    fun getSlotComparator(): Comparator<Int> {
        return Comparator { lhs, rhs ->
            val lhsInfo = lookupMappingInfo(lhs)
            val rhsInfo = lookupMappingInfo(rhs)
            if (lhsInfo.slot != rhsInfo.slot) {
                throw IllegalArgumentException("lhs and rhs don't go in the same slot")
            }
            lhsInfo.slotOrder - rhsInfo.slotOrder
        }
    }

    /** Orders buttons by their conflict score, the natural ordering of the conflictOrder. */
    fun getConflictComparator(): Comparator<Int> {
        return Comparator { lhs, rhs ->
            val lhsInfo = lookupMappingInfo(lhs)
            val rhsInfo = lookupMappingInfo(rhs)
            lhsInfo.conflictOrder - rhsInfo.conflictOrder
        }
    }

    fun lookupMappingInfo(button: Int): MappingInfo {
        return mapping[button] ?: throw IllegalArgumentException("Unknown InCallButtonId: $button")
    }
}

/**
 * Determines where logical buttons should be placed based on the provided mapping.
 *
 * The button placement returned by a call to [getButtonPlacement] is created as follows: one button is placed at
 * each UI slot, using the provided mapping to resolve conflicts. Any allowed buttons that were not chosen for their
 * desired slot are filled in at the end of the list until it becomes the proper size.
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/ButtonChooser.java#ButtonChooser
 */
private class ButtonChooser(private val config: MappedButtonConfig) {

    /**
     * Returns the buttons that should be shown, ordered appropriately.
     *
     * @param numUiButtons the number of ui buttons available.
     * @param allowedButtons the buttons that can be shown.
     * @param disabledButtons the buttons that can be shown but in disabled state.
     * @return a list whose size is at most [numUiButtons], containing the buttons to show.
     */
    fun getButtonPlacement(
        numUiButtons: Int,
        allowedButtons: Set<Int>,
        disabledButtons: Set<Int>
    ): List<Int> {
        require(numUiButtons >= 0)

        if (numUiButtons == 0 || allowedButtons.isEmpty()) {
            return emptyList()
        }

        val placedButtons = ArrayList<Int>()
        val conflicts = ArrayList<Int>()
        placeButtonsInSlots(numUiButtons, allowedButtons, placedButtons, conflicts)
        placeConflictsInOpenSlots(numUiButtons, allowedButtons, disabledButtons, placedButtons, conflicts)
        return placedButtons.toList()
    }

    private fun placeButtonsInSlots(
        numUiButtons: Int,
        allowedButtons: Set<Int>,
        placedButtons: MutableList<Int>,
        conflicts: MutableList<Int>
    ) {
        val configuredSlots = config.getOrderedMappedSlots()
        var i = 0
        while (i < configuredSlots.size && placedButtons.size < numUiButtons) {
            val slotNumber = configuredSlots[i]
            val potentialButtons = config.getButtonsForSlot(slotNumber)
            potentialButtons.sortWith(config.getSlotComparator())
            for (j in potentialButtons.indices) {
                if (allowedButtons.contains(potentialButtons[j])) {
                    placedButtons.add(potentialButtons[j])
                    conflicts.addAll(potentialButtons.subList(j + 1, potentialButtons.size))
                    break
                }
            }
            ++i
        }
    }

    private fun placeConflictsInOpenSlots(
        numUiButtons: Int,
        allowedButtons: Set<Int>,
        disabledButtons: Set<Int>,
        placedButtons: MutableList<Int>,
        conflicts: MutableList<Int>
    ) {
        conflicts.sortWith(config.getConflictComparator())
        for (conflict in conflicts) {
            if (placedButtons.size >= numUiButtons) {
                return
            }

            // If the conflict button is allowed but disabled, don't place it since it probably will
            // move when it's enabled.
            if (!allowedButtons.contains(conflict) || disabledButtons.contains(conflict)) {
                continue
            }

            if (isMutuallyExclusiveButtonAvailable(
                    config.lookupMappingInfo(conflict).mutuallyExclusiveButton, allowedButtons)
            ) {
                continue
            }
            placedButtons.add(conflict)
        }
    }

    private fun isMutuallyExclusiveButtonAvailable(
        mutuallyExclusiveButton: Int,
        allowedButtons: Set<Int>
    ): Boolean {
        if (mutuallyExclusiveButton == MappingInfo.NO_MUTUALLY_EXCLUSIVE_BUTTON_SET) {
            return false
        }
        if (allowedButtons.contains(mutuallyExclusiveButton)) {
            return true
        }
        return false
    }
}

/**
 * Creates the appropriate [ButtonChooser] based on the given information.
 *
 * @param voiceNetworkType the result of a call to TelephonyManager.getVoiceNetworkType().
 * @param isWiFi true if the call is made over WiFi, false otherwise.
 * @param phoneType the result of a call to TelephonyManager.getPhoneType().
 * https://github.com/LineageOS/android_packages_apps_Dialer/blob/lineage-22.2/java/com/android/incallui/incall/impl/ButtonChooserFactory.java#newButtonChooser
 */
private fun newButtonChooser(voiceNetworkType: Int, isWiFi: Boolean, phoneType: Int): ButtonChooser {
    if (voiceNetworkType == TelephonyManager.NETWORK_TYPE_LTE || isWiFi) {
        return newImsAndWiFiButtonChooser()
    }

    if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
        return newCdmaButtonChooser()
    }

    if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
        return newGsmButtonChooser()
    }

    return newImsAndWiFiButtonChooser()
}

private fun newImsAndWiFiButtonChooser(): ButtonChooser {
    val mapping = createCommonMapping()
    mapping[InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE] = MappingInfo.builder(4).setSlotOrder(0).build()
    // RTT call is only supported on IMS and WiFi.
    mapping[InCallButtonIds.BUTTON_UPGRADE_TO_RTT] = MappingInfo.builder(3).setSlotOrder(0).build()
    mapping[InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO] = MappingInfo.builder(4).setSlotOrder(10).build()
    mapping[InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY] = MappingInfo.builder(5).setSlotOrder(0).build()
    mapping[InCallButtonIds.BUTTON_HOLD] = MappingInfo.builder(5).setSlotOrder(10).build()

    return ButtonChooser(MappedButtonConfig(mapping))
}

private fun newCdmaButtonChooser(): ButtonChooser {
    val mapping = createCommonMapping()
    mapping[InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE] = MappingInfo.builder(4).setSlotOrder(0).build()
    mapping[InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO] = MappingInfo.builder(4).setSlotOrder(10).build()
    mapping[InCallButtonIds.BUTTON_SWAP] = MappingInfo.builder(5).setSlotOrder(0).build()
    // For multi-sim devices, the first sim's phoneType is used so hold button might be not
    // available for CDMA + GSM devices calling with GSM sim. Adding hold button as low priority
    // here to let telecom control whether it should be shown.
    mapping[InCallButtonIds.BUTTON_HOLD] = MappingInfo.builder(5).setSlotOrder(5).build()
    mapping[InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY] =
        MappingInfo.builder(5)
            .setSlotOrder(Int.MAX_VALUE)
            .setMutuallyExclusiveButton(InCallButtonIds.BUTTON_SWAP)
            .build()

    return ButtonChooser(MappedButtonConfig(mapping))
}

private fun newGsmButtonChooser(): ButtonChooser {
    val mapping = createCommonMapping()
    mapping[InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY] = MappingInfo.builder(4).setSlotOrder(0).build()
    mapping[InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO] = MappingInfo.builder(4).setSlotOrder(10).build()

    // Unlike the other configurations, MANAGE_VOICE_CONFERENCE shares a spot with HOLD for GSM.
    // On GSM, pressing hold while there's a background call just swaps to the background call. It
    // doesn't make sense to show both SWITCH_TO_SECONDARY and HOLD when they do the same thing, so
    // we show MANAGE_VOICE_CONFERENCE instead. Previously MANAGE_VOICE_CONFERENCE would not show.
    mapping[InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE] = MappingInfo.builder(5).setSlotOrder(0).build()
    mapping[InCallButtonIds.BUTTON_HOLD] = MappingInfo.builder(5).setSlotOrder(5).build()

    return ButtonChooser(MappedButtonConfig(mapping))
}

private fun createCommonMapping(): MutableMap<Int, MappingInfo> {
    val mapping = java.util.TreeMap<Int, MappingInfo>()
    mapping[InCallButtonIds.BUTTON_MUTE] = MappingInfo.builder(0).build()
    mapping[InCallButtonIds.BUTTON_DIALPAD] = MappingInfo.builder(1).build()
    mapping[InCallButtonIds.BUTTON_AUDIO] = MappingInfo.builder(2).build()
    mapping[InCallButtonIds.BUTTON_RECORD_CALL] = MappingInfo.builder(3).build()
    mapping[InCallButtonIds.BUTTON_MERGE] = MappingInfo.builder(4).setSlotOrder(5).build()
    mapping[InCallButtonIds.BUTTON_ADD_CALL] = MappingInfo.builder(4).build()
    mapping[InCallButtonIds.BUTTON_SWAP_SIM] = MappingInfo.builder(5).build()
    return mapping
}

val ControlSize = 72.dp
val ControlGap = 28.dp
private const val STAGGER_MS = 45L
