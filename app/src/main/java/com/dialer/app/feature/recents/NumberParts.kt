package com.dialer.app.feature.recents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dialer.app.core.calllog.Rhythm
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.call.HangUpRed
import com.dialer.app.feature.call.rememberTinted
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.icon.AppIcons

/** The page's first action: a wide pane of green glass. */
@Composable
fun CallBar(label: String, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    val green = rememberTinted(LocalGlass.current, AnswerGreen.copy(alpha = 0.32f))
    CompositionLocalProvider(LocalGlass provides (green ?: LocalGlass.current)) {
        ZoneSurface(
            shape = RoundedCornerShape(32.dp),
            color = AnswerGreen.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    scaleX = sink
                    scaleY = sink
                    alpha = if (enabled) 1f else 0.45f
                }
                .clip(RoundedCornerShape(32.dp))
                .clickable(interactionSource = press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Call, contentDescription = null, tint = AnswerGreen, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One action under the call: a tile of glass of its own, icon over word. */
@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color? = null, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    ZoneSurface(
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .height(88.dp)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = label) {
                haptics.tick()
                onClick()
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * The calls with this person over twelve weeks, one bar a week: spoken
 * calls in the accent, the others in red on top. Under it how much was
 * said in all, and the hour they usually happen at.
 */
@Composable
fun RhythmCard(rhythm: Rhythm, hourLabel: (Int) -> String) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }
    ZoneSurface(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Stat("${rhythm.calls}", if (rhythm.calls == 1) "call" else "calls", Modifier.weight(1f))
                Stat(talkedLabel(rhythm.talkedSeconds), "spoken", Modifier.weight(1f))
                rhythm.usualHour?.let { Stat(hourLabel(it), "usual time", Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(16.dp))
            val most = (rhythm.weeks.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val n = rhythm.weeks.size
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                val radius = CornerRadius(w / 2f, w / 2f)
                rhythm.weeks.forEachIndexed { i, week ->
                    val x = i * (w + gap)
                    drawRoundRect(empty, Offset(x, 0f), Size(w, size.height), radius)
                    val talked = size.height * week.talked / most * grow.value
                    val missed = size.height * week.missed / most * grow.value
                    if (missed > 0f) drawRoundRect(HangUpRed.copy(alpha = 0.7f), Offset(x, size.height - talked - missed), Size(w, missed + talked), radius)
                    if (talked > 0f) drawRoundRect(accent, Offset(x, size.height - talked), Size(w, talked), radius)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("12 weeks ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 0 min, 12 min, 3 h 05. */
private fun talkedLabel(seconds: Long): String = when {
    seconds < 3600 -> "${(seconds + 59) / 60} min"
    else -> "%d h %02d".format(seconds / 3600, (seconds % 3600) / 60)
}
