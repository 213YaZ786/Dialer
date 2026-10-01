package com.dialer.app.feature.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.GlassLook
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import com.dialer.app.ui.icon.DialerIcons
import kotlin.math.hypot
import kotlinx.coroutines.launch

/** Answer green and hang up red: the colours every phone uses, kept whatever the wallpaper. */
internal val AnswerGreen = Color(0xFF1E8E3E)
internal val HangUpRed = Color(0xFFC5221F)

/** A wave of tinted glass leaving a button: its colour and where it starts, in the window. */
class GlassWave(val color: Color, val origin: Offset)

/**
 * Answer or decline an incoming call: two deep panes of glass, tapped like
 * any button, so nothing has to be learnt.
 *
 * While the phone rings, Answer breathes and sends rings of glass out
 * around it, like ripples on water. Tapped, a button swells and lets a wave
 * of its colour go, which the call screen spreads over the whole window
 * (see [GlassWave] and [drawGlassWave]); the other button melts away.
 */
@Composable
fun IncomingChoice(onAnswer: () -> Unit, onDecline: () -> Unit, onWave: (GlassWave) -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val answerSwell = remember { Animatable(1f) }
    val declineSwell = remember { Animatable(1f) }
    val answerFade = remember { Animatable(1f) }
    val declineFade = remember { Animatable(1f) }
    var chosen by remember { mutableStateOf<Boolean?>(null) }
    var answerAt by remember { mutableStateOf(Offset.Zero) }
    var declineAt by remember { mutableStateOf(Offset.Zero) }

    val ringing = rememberInfiniteTransition(label = "ringing")
    val breath by ringing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val ripple by ringing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ripple"
    )

    fun choose(answer: Boolean) {
        if (chosen != null) return
        chosen = answer
        haptics.done()
        onWave(GlassWave(if (answer) AnswerGreen else HangUpRed, if (answer) answerAt else declineAt))
        scope.launch {
            // The other one melts away while the chosen one swells.
            val other = if (answer) declineFade else answerFade
            launch { other.animateTo(0f, tween(260)) }
            val swell = if (answer) answerSwell else declineSwell
            swell.animateTo(1.16f, spring(dampingRatio = 0.45f, stiffness = 650f))
            if (answer) onAnswer() else onDecline()
            swell.animateTo(1f, spring(dampingRatio = 0.6f))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GlassCallButton(
            icon = DialerIcons.CallEnd,
            label = "Decline",
            color = HangUpRed,
            size = 88.dp,
            swell = declineSwell.value,
            fade = declineFade.value,
            glow = 0.3f,
            onCenter = { declineAt = it },
            onClick = { choose(false) }
        )
        GlassCallButton(
            icon = DialerIcons.Call,
            label = "Answer",
            color = AnswerGreen,
            size = 88.dp,
            swell = answerSwell.value * (1f + 0.04f * breath),
            fade = answerFade.value,
            glow = 0.35f + 0.25f * breath,
            ripple = ripple.takeIf { chosen == null },
            onCenter = { answerAt = it },
            onClick = { choose(true) }
        )
    }
}

/**
 * A wave of tinted glass at [progress] (0 to 1) from its origin to past the
 * farthest corner: a light wash of the colour, deeper towards the front, and
 * the front itself lit like the rim of a pane. [alpha] fades it as a whole.
 */
fun DrawScope.drawGlassWave(wave: GlassWave, progress: Float, alpha: Float) {
    if (progress <= 0f || alpha <= 0f) return
    val o = wave.origin
    val far = maxOf(
        hypot(o.x, o.y), hypot(size.width - o.x, o.y),
        hypot(o.x, size.height - o.y), hypot(size.width - o.x, size.height - o.y)
    )
    val radius = far * progress
    if (radius < 1f) return
    drawCircle(
        Brush.radialGradient(
            0f to wave.color.copy(alpha = 0.10f * alpha),
            0.85f to wave.color.copy(alpha = 0.26f * alpha),
            1f to wave.color.copy(alpha = 0.34f * alpha),
            center = o,
            radius = radius
        ),
        radius = radius,
        center = o
    )
    // The front: a soft band of the colour, then a bright hairline on it.
    val front = 1f - progress * 0.5f
    drawCircle(wave.color.copy(alpha = 0.22f * alpha * front), radius, o, style = Stroke(width = 22.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.7f * alpha * front), radius, o, style = Stroke(width = 1.5.dp.toPx()))
}

/**
 * A call button in deep glass: a pane tinted in [color] with a strong lens,
 * a soft halo of the same colour around it whose strength is [glow], [swell]
 * for the scale and [fade] for melting away. With [ripple] set (0 to 1,
 * running), rings of glass leave it one after the other. It sinks a little
 * under the finger, and reports its centre in the window to [onCenter].
 */
@Composable
fun GlassCallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    swell: Float = 1f,
    fade: Float = 1f,
    glow: Float = 0.3f,
    ripple: Float? = null,
    onCenter: ((Offset) -> Unit)? = null,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    val tinted = rememberTinted(glass, color.copy(alpha = 0.66f))
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "sink")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.graphicsLayer {
            alpha = fade
            val melt = 0.6f + 0.4f * fade
            scaleX = melt
            scaleY = melt
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = swell * sink
                    scaleY = swell * sink
                }
                .then(if (onCenter != null) Modifier.onGloballyPositioned { onCenter(it.boundsInRoot().center) } else Modifier)
                // Halo and rings are drawn around the button, unclipped.
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
                    if (ripple != null) {
                        for (k in 0 until 3) {
                            val t = (ripple + k / 3f) % 1f
                            val ring = r * (1f + 0.85f * t)
                            val fadeOut = (1f - t) * (1f - t)
                            drawCircle(color.copy(alpha = 0.45f * fadeOut), ring, style = Stroke(width = (3f - 2f * t).dp.toPx()))
                            drawCircle(Color.White.copy(alpha = 0.5f * fadeOut), ring - 1.5.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                }
        ) {
            val base = Modifier.size(size).clip(CircleShape)
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (tinted != null) base.glassZone(CircleShape, tinted, lens = 1.6f) else base.background(color))
                    .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            ) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.4f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** The same glass as [look], tinted with [tint] instead of the zone's own fill. */
@Composable
internal fun rememberTinted(look: GlassLook?, tint: Color?): GlassLook? = remember(look, tint) {
    if (look == null || tint == null) {
        null
    } else {
        GlassLook(look.dark, look.ground, look.halos, zoneTint = tint, floatTint = tint, accentTint = look.accentTint)
    }
}
