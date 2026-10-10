package com.yaz.dialer.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yaz.dialer.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dialer's loading mark, the launcher icon in motion: the disc breathes,
 * rings of its colour leave it as a phone rings, and the handset rocks a
 * little on each ring. The disc is the icon's: a grey body multiplied by
 * the theme's accent, its white rim over it, the handset on top.
 *
 * [progress] from 0 to 1 sends the rings out as far as a gesture has gone.
 * While [running] it rings on its own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) {
    val body = ImageBitmap.imageResource(R.drawable.dial_mark_body)
    val shine = ImageBitmap.imageResource(R.drawable.dial_mark_shine)
    val handset = ImageBitmap.imageResource(R.drawable.dial_mark_handset)
    // The launcher icon's own tone (ic_launcher_disc), so the mark is the icon by day and by night.
    val accent = androidx.compose.ui.res.colorResource(android.R.color.system_accent1_500)
    val tint = remember(accent) { ColorFilter.tint(accent, BlendMode.Modulate) }

    val transition = rememberInfiniteTransition(label = "ringing")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val t = if (running) clock else progress.coerceIn(0f, 1f) * 0.5f

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f * DISC
        // Two rings leaving the disc, half a loop apart.
        for (k in 0 until 2) {
            val p = (t * 2f + k / 2f) % 1f
            val fade = (1f - p) * (1f - p)
            drawCircle(accent.copy(alpha = 0.45f * fade), r * (1f + 0.42f * p), style = Stroke(width = r * 0.07f * (1f - 0.5f * p)))
        }
        // The disc breathes with the rings, the handset rocks on each one.
        val breath = 1f + 0.035f * sin(2f * PI.toFloat() * t * 2f)
        val rock = 4f * sin(2f * PI.toFloat() * t * 4f) * (if (running) 1f else progress)
        scale(breath) {
            layer(body, r, tint)
            layer(shine, r, null)
            rotate(rock) { layer(handset, r, null) }
        }
    }
}

private fun DrawScope.layer(image: ImageBitmap, r: Float, filter: ColorFilter?) {
    val side = (r * 2f).toInt()
    drawImage(
        image,
        dstOffset = IntOffset((center.x - r).toInt(), (center.y - r).toInt()),
        dstSize = IntSize(side, side),
        colorFilter = filter,
        filterQuality = FilterQuality.High
    )
}

/** The disc's share of the mark, leaving room for the rings around it. */
private const val DISC = 0.70f
private const val LOOP_MILLIS = 2400
