package com.yaz.dialer.feature.recents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.calllog.Rhythm
import com.yaz.dialer.feature.call.HangUpRed
import com.yaz.dialer.ui.component.ZoneSurface

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
