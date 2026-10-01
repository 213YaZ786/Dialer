package com.dialer.app.feature.recents

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialer.app.core.calllog.CallKind

/**
 * What a call was, as a small round badge in its colour: set on the
 * caller's face in Recents, or alone beside a line of history. A missed
 * call not seen yet sends out a soft ring, until it is seen.
 */
@Composable
fun KindBadge(kind: CallKind, modifier: Modifier = Modifier, size: Dp = 22.dp, unseen: Boolean = false) {
    val tint = kindTint(kind)
    val ground = MaterialTheme.colorScheme.surface
    val pulse = if (unseen) {
        val waves = rememberInfiniteTransition(label = "unseen")
        val t by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
        t
    } else {
        null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .drawBehind {
                pulse?.let { t ->
                    val r = this.size.minDimension / 2f
                    drawCircle(tint.copy(alpha = 0.5f * (1f - t)), r * (1f + 0.9f * t), style = Stroke(width = 2.dp.toPx()))
                }
            }
            .background(tint.copy(alpha = 0.18f).compositeOver(ground), CircleShape)
            .border(1.5.dp, ground, CircleShape)
    ) {
        Icon(kindIcon(kind), contentDescription = kindLabel(kind), tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}
