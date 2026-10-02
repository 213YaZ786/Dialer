package com.dialer.app.feature.call

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import com.dialer.app.ui.glass.glassFloating
import com.dialer.app.ui.glass.LocalGlassBackdrop
import com.dialer.app.ui.glass.LocalGlass
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.AudioRoute
import com.dialer.app.core.call.CallInfo
import com.dialer.app.core.call.CallPhase
import com.dialer.app.core.call.CallStore
import com.dialer.app.core.call.CallsState
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.icon.AppIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay

private enum class Shape { RING, SMALL, OPEN }

/** The island's corners: one radius for every shape, the one Android's blur behind it takes too. */
val IslandCorner = 30.dp

/**
 * The call island: the call in progress, kept at the top of the screen
 * while its screen is not in front (in the app, and over the other apps
 * when the user allowed it). A pane of glass in the wallpaper's tones
 * that comes out of the camera: ringing, it holds who calls with Decline
 * and Answer; taken, it folds into the time and the voice's waves; a tap
 * goes back to the call screen, a long press opens mute, speaker and hang
 * up; it closes back into the camera when the call ends ([leaving]).
 *
 * [room] is the width it may take; [blurred] says Android blurs what lies
 * behind its window, so the glass can be clear. Inside the app, it is the
 * app's own glass ([overlay] false).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallIsland(
    state: CallsState,
    call: CallInfo,
    photo: String?,
    actions: CallStore,
    onOpenScreen: () -> Unit,
    room: Dp,
    overlay: Boolean = false,
    blurred: Boolean = false,
    leaving: Boolean = false,
    onGone: () -> Unit = {},
    onGlass: (Float) -> Unit = {}
) {
    val haptics = rememberHaptics()
    var opened by remember(call.id) { mutableStateOf(false) }
    val shape = when {
        call.phase == CallPhase.RINGING -> Shape.RING
        opened -> Shape.OPEN
        else -> Shape.SMALL
    }
    // Opened, it folds back by itself after a few quiet seconds.
    LaunchedEffect(opened) {
        if (opened) {
            delay(4500)
            opened = false
        }
    }
    LaunchedEffect(shape) { haptics.tick() }

    // Sizes follow the screen: as wide as it allows, taller when it asks
    // something. Over other apps the width never changes: only a window's
    // height can move with no jolt, its top staying put.
    val wide = min(room * 0.94f, 460.dp)
    fun sizeOf(s: Shape) = when (s) {
        Shape.RING -> DpSize(wide, 84.dp)
        Shape.OPEN -> DpSize(wide, 152.dp)
        Shape.SMALL -> DpSize(if (overlay) wide else min(room, 360.dp), 60.dp)
    }
    val target = sizeOf(shape)
    // It drops out of the top: the glass clears in while it lengthens, quick and without bounce.
    val width = remember { Animatable(if (overlay) target.width.value else target.width.value * 0.42f) }
    val height = remember { Animatable(60f) }
    val shown = remember { Animatable(0f) }
    val motion = spring<Float>(dampingRatio = 0.9f, stiffness = 700f)
    LaunchedEffect(target, leaving) {
        if (leaving) {
            launch { height.animateTo(60f, motion) }
            shown.animateTo(0f, tween(170, easing = FastOutLinearInEasing))
            onGone()
        } else {
            launch { shown.animateTo(1f, tween(190, easing = LinearOutSlowInEasing)) }
            launch { height.animateTo(target.height.value, motion) }
            width.animateTo(target.width.value, motion)
        }
    }
    // Android's blur behind the window follows the glass in and out.
    LaunchedEffect(Unit) { snapshotFlow { shown.value }.collect(onGlass) }

    val corner = RoundedCornerShape(IslandCorner)
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width.value.dp, height.value.dp)
            .graphicsLayer { alpha = shown.value }
            .clip(corner)
            .then(
                if (!overlay && look != null && backdrop != null) Modifier.glassFloating(backdrop, corner, look, tint = look.zoneTint, lens = 1.2f)
                else Modifier.islandGlass(scheme, clear = overlay && blurred)
            )
            .islandRim(scheme)
            // A tap: back to the call. Held: its quick controls, without leaving the app in front.
            .combinedClickable(
                onClick = {
                    haptics.tick()
                    onOpenScreen()
                },
                onLongClick = {
                    if (shape != Shape.RING) {
                        haptics.firm()
                        opened = !opened
                    }
                },
                onClickLabel = "Back to the call",
                onLongClickLabel = "The call's controls"
            ),
        contentAlignment = Alignment.Center
    ) {
      // Its own window over other apps provides no text colour: given here, for dark mode too.
      androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
        // The glass changes size; what it holds fades across, each laid out at its own final size.
        AnimatedContent(
            shape,
            transitionSpec = {
                (fadeIn(tween(150, delayMillis = 70)) + scaleIn(tween(220, delayMillis = 40), initialScale = 0.96f)) togetherWith fadeOut(tween(70)) using
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
            },
            modifier = Modifier.wrapContentSize(unbounded = true),
            label = "island"
        ) { now ->
            Box(Modifier.requiredSize(sizeOf(now)), contentAlignment = Alignment.Center) {
                when (now) {
                    Shape.RING -> Ringing(call, photo, onDecline = {
                        haptics.reject()
                        actions.decline(call.id)
                    }, onAnswer = {
                        haptics.firm()
                        actions.answer(call.id)
                    })
                    Shape.SMALL -> Small(call, photo)
                    Shape.OPEN -> Open(state, call, photo, actions)
                }
            }
        }
      }
    }
}

/**
 * The glass's body: the wallpaper's surface tone touched by its accent,
 * clear when Android blurs what lies behind, nearly solid when it cannot
 * (battery saver, or blur off), a light from above and a glow of the
 * accent from below.
 */
private fun Modifier.islandGlass(scheme: androidx.compose.material3.ColorScheme, clear: Boolean) = drawBehind {
    val dark = scheme.surface.luminance() < 0.5f
    val body = androidx.compose.ui.graphics.lerp(scheme.surfaceContainerHigh, scheme.primary, if (dark) 0.16f else 0.14f)
    drawRect(body.copy(alpha = if (clear) (if (dark) 0.50f else 0.42f) else 0.95f))
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.12f else 0.34f), 0.5f to Color.Transparent))
    drawRect(
        Brush.radialGradient(
            listOf(scheme.primary.copy(alpha = if (dark) 0.26f else 0.18f), Color.Transparent),
            center = Offset(size.width / 2, size.height * 1.1f),
            radius = size.width * 0.55f
        )
    )
    // The glass's thickness: light caught inside its edges, strong above, a reflection below.
    val edge = 5.dp.toPx()
    val r = IslandCorner.toPx().coerceAtMost(size.height / 2)
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.22f else 0.5f),
            0.4f to Color.Transparent,
            1f to Color.White.copy(alpha = if (dark) 0.10f else 0.22f)
        ),
        topLeft = Offset(edge / 2, edge / 2),
        size = Size(size.width - edge, size.height - edge),
        cornerRadius = CornerRadius(r - edge / 2),
        style = Stroke(edge)
    )
}

/** The rim: light where the glass faces up, the accent where it turns down. */
private fun Modifier.islandRim(scheme: androidx.compose.material3.ColorScheme) = drawWithContent {
    drawContent()
    val w = 1.2.dp.toPx()
    val r = IslandCorner.toPx().coerceAtMost(size.height / 2)
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.45f to Color.White.copy(alpha = 0.08f),
            1f to scheme.primary.copy(alpha = 0.45f),
            start = Offset.Zero,
            end = Offset(size.width * 0.35f, size.height * 1.4f)
        ),
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r - w / 2),
        style = Stroke(w)
    )
}

@Composable
private fun Ringing(call: CallInfo, photo: String?, onDecline: () -> Unit, onAnswer: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp)
    ) {
        ContactAvatar(call.title, photo, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (call.encrypted) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (call.encrypted) (if (call.video) "Encrypted video" else "Encrypted") else if (call.video) "Video" else "Mobile",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(call.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Round(AppIcons.CallEnd, "Decline", HangUpRed, size = 52.dp, onClick = onDecline)
        Spacer(Modifier.width(12.dp))
        Round(if (call.video) AppIcons.Videocam else AppIcons.Call, "Answer", AnswerGreen, size = 52.dp, onClick = onAnswer)
    }
}

@Composable
private fun Small(call: CallInfo, photo: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 18.dp)
    ) {
        ContactAvatar(call.title, photo, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(call.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (call.encrypted) AppIcons.Lock else AppIcons.Call, contentDescription = if (call.encrypted) "Encrypted" else null, tint = AnswerGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(clockOf(call), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = AnswerGreen)
            }
        }
        Waves(live = call.phase == CallPhase.ACTIVE)
    }
}

@Composable
private fun Open(state: CallsState, call: CallInfo, photo: String?, actions: CallStore) {
    val haptics = rememberHaptics()
    val speaker = state.route?.kind == AudioRoute.Kind.SPEAKER
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(call.title, photo, 36.dp)
            Text(
                call.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Waves(live = call.phase == CallPhase.ACTIVE)
            Spacer(Modifier.width(10.dp))
            Text(clockOf(call), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AnswerGreen)
        }
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Round(if (state.muted) AppIcons.MicOff else AppIcons.Mic, if (state.muted) "Unmute" else "Mute", null, on = state.muted) {
                haptics.tick()
                actions.mute(!state.muted)
            }
            Round(AppIcons.Speaker, if (speaker) "Earpiece" else "Speaker", null, on = speaker) {
                haptics.tick()
                val target = if (speaker) AudioRoute.Kind.EARPIECE else AudioRoute.Kind.SPEAKER
                state.routes.firstOrNull { it.kind == target }?.let(actions::route)
            }
            Round(AppIcons.CallEnd, "Hang up", HangUpRed) {
                haptics.reject()
                actions.hangUp(call.id)
            }
        }
    }
}

/** A round button of the island: filled with its colour, or glass that takes the accent when on. */
@Composable
private fun Round(icon: ImageVector, label: String, color: Color?, size: Dp = 54.dp, on: Boolean = false, onClick: () -> Unit) {
    val fill = color ?: if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val tint = when {
        color != null -> Color.White
        on -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(fill).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * The voice's waves: a few rounded bars, each lit from the call's green
 * at its foot to a light yellow at its top, rising and falling on its own
 * beat so the whole sways like speech (Android gives no app a phone
 * call's sound, so they move with the call, not with the words); low and
 * still while the call waits.
 */
@Composable
private fun Waves(live: Boolean) {
    val t by rememberInfiniteTransition(label = "waves").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val calm by animateFloatAsState(if (live) 1f else 0f, tween(400), label = "calm")
    Canvas(Modifier.width(52.dp).height(22.dp)) {
        val bar = 3.dp.toPx()
        val gap = (size.width - Beats.size * bar) / (Beats.size - 1)
        val low = 4.dp.toPx()
        val brush = Brush.verticalGradient(listOf(Color(0xFFD7F56B), AnswerGreen), startY = 0f, endY = size.height)
        Beats.forEachIndexed { i, (delayS, periodS) ->
            // Eased up and down, like the first preview's bars.
            val phase = ((t + delayS) / periodS) % 1f
            val ease = 0.5f - 0.5f * cos(phase * 2f * PI.toFloat())
            val h = low + (size.height - low) * ease * calm
            drawRoundRect(brush, Offset(i * (bar + gap), (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

/** Each bar's offset and beat, in seconds. */
private val Beats = listOf(0f to 0.9f, 0.12f to 0.96f, 0.3f to 0.88f, 0.05f to 1.02f, 0.22f to 0.92f, 0.4f to 0.98f, 0.16f to 0.9f, 0.34f to 1f)

/** 0:44, 12:03; "Calling" before it is taken, "On hold" while held. */
@Composable
private fun clockOf(call: CallInfo): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.phase) {
        while (call.phase == CallPhase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return when (call.phase) {
        CallPhase.ACTIVE -> if (call.connectedAt > 0) ((now - call.connectedAt) / 1000).coerceAtLeast(0).let { "%d:%02d".format(it / 60, it % 60) } else "0:00"
        CallPhase.HOLDING -> "On hold"
        CallPhase.ENDED -> "Ended"
        else -> "Calling"
    }
}

/** Back to the call's screen. */
fun openCallScreen(context: Context) {
    context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
