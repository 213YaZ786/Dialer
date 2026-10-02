package com.dialer.app.feature.call

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.widthIn
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
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class Shape { RING, SMALL, OPEN }

/**
 * The call island: the call in progress, kept at the top of the screen
 * while its screen is not in front (in the app, and over the other apps
 * when the user allowed it). A drop of the wallpaper's glass that comes
 * out of the camera: ringing, it holds who calls with Decline and
 * Answer; taken, it folds into the time and the voice's waves; a tap
 * opens it on mute, speaker and hang up, a long press goes back to the
 * call screen; it closes back into the camera when the call ends.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallIsland(state: CallsState, call: CallInfo, photo: String?, actions: CallStore, onOpenScreen: () -> Unit) {
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
    // Out of the camera when it comes.
    val grow = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
    LaunchedEffect(shape) { haptics.tick() }

    val corner = if (shape == Shape.SMALL) 27.dp else 32.dp
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier
            .graphicsLayer {
                scaleX = grow.value
                scaleY = grow.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .clip(RoundedCornerShape(corner))
            // Glass of the wallpaper's tones: a frosted fill and a light rim on top.
            .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.94f), surface.copy(alpha = 0.86f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(corner))
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
            )
    ) {
      // Its own window over other apps provides no text colour: given here, for dark mode too.
      androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // One spring for the size, the content fading across it.
        AnimatedContent(
            shape,
            transitionSpec = {
                ((fadeIn(tween(160, delayMillis = 60)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(90)))
                    .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.78f, stiffness = 700f) })
            },
            label = "island"
        ) { now ->
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

@Composable
private fun Ringing(call: CallInfo, photo: String?, onDecline: () -> Unit, onAnswer: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 300.dp, max = 380.dp).padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
    ) {
        ContactAvatar(call.title, photo, 46.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (call.encrypted) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (call.encrypted) (if (call.video) "Encrypted video" else "Encrypted") else if (call.video) "Video" else "Mobile",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(call.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Round(AppIcons.CallEnd, "Decline", HangUpRed, onDecline)
        Spacer(Modifier.width(10.dp))
        Round(if (call.video) AppIcons.Videocam else AppIcons.Call, "Answer", AnswerGreen, onAnswer)
    }
}

@Composable
private fun Small(call: CallInfo, photo: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(284.dp).height(54.dp).padding(start = 10.dp, end = 14.dp)
    ) {
        ContactAvatar(call.title, photo, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(call.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (call.encrypted) AppIcons.Lock else AppIcons.Call, contentDescription = if (call.encrypted) "Encrypted" else null, tint = AnswerGreen, modifier = Modifier.size(13.dp))
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(300.dp).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(call.title, photo, 28.dp)
            Text(
                call.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text(clockOf(call), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AnswerGreen)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
private fun Round(icon: ImageVector, label: String, color: Color?, onClick: () -> Unit) = Round(icon, label, color, on = false, onClick = onClick)

@Composable
private fun Round(icon: ImageVector, label: String, color: Color?, on: Boolean, onClick: () -> Unit) {
    val fill = color ?: if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val tint = if (color != null || on) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(fill).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * The voice's waves: thin bars, from the call's green to a warm yellow,
 * each moving on its own slow rhythm so the whole breathes like a voice
 * (Android gives no app a phone call's sound, so they move with the call,
 * not with the words); low and still while the call waits.
 */
@Composable
private fun Waves(live: Boolean) {
    val t by rememberInfiniteTransition(label = "waves").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val colors = remember { listOf(AnswerGreen, Color(0xFF9BE15D), Color(0xFFE6E85C), Color(0xFFFFC857)) }
    Canvas(Modifier.width(72.dp).height(24.dp)) {
        val n = 18
        val bar = 2.2.dp.toPx()
        val gap = (size.width - n * bar) / (n - 1)
        for (i in 0 until n) {
            // Three slow waves per bar at its own speeds: never in step, never still.
            val x = i.toFloat()
            val wave = 0.5f + 0.5f * (sin(t * 6.1f + x * 0.9f) * 0.5f + sin(t * 9.7f + x * 1.7f) * 0.3f + sin(t * 3.3f + x * 0.4f) * 0.2f)
            val level = if (live) 0.18f + 0.82f * wave * wave else 0.14f
            val h = (size.height * level).coerceAtLeast(bar)
            val p = i / (n - 1f) * (colors.size - 1)
            val k = p.toInt().coerceAtMost(colors.size - 2)
            val color = androidx.compose.ui.graphics.lerp(colors[k], colors[k + 1], p - k)
            drawRoundRect(color, Offset(i * (bar + gap), (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

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
        else -> "Calling"
    }
}

/** Back to the call's screen. */
fun openCallScreen(context: Context) {
    context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
