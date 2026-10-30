package com.yaz.dialer.feature.recents

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ShapeDefaults
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmCard(rhythm: Rhythm, hourLabel: (Int) -> String) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 120f)) }
    // A card on the page: Material 3's extra large corner (ShapeDefaults,
    // https://developer.android.com/reference/kotlin/androidx/compose/material3/ShapeDefaults).
    ZoneSurface(shape = ShapeDefaults.ExtraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            // The stats side by side while the widest, measured on one line in this font and language
            // (TextMeasurer, https://developer.android.com/develop/ui/compose/text/measure-text-layout),
            // fits three times; else one under the other, full width: FlowRow's maxItemsInEachRow with
            // weight (https://developer.android.com/develop/ui/compose/layouts/flow), as PersonActions does.
            val stats = buildList {
                add("${rhythm.calls}" to if (rhythm.calls == 1) "call" else "calls")
                add(talkedLabel(rhythm.talkedSeconds) to "spoken")
                rhythm.usualHour?.let { add(hourLabel(it) to "usual time") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val valueStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                val labelStyle = MaterialTheme.typography.labelMedium
                val widest = remember(stats, valueStyle, labelStyle, density) {
                    stats.maxOf { (value, label) ->
                        maxOf(
                            measurer.measure(value, valueStyle, softWrap = false, maxLines = 1).size.width,
                            measurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size.width
                        )
                    }.let { with(density) { it.toDp() } }
                }
                val gap = 12.dp
                val perRow = if (widest * stats.size + gap * (stats.size - 1) <= maxWidth) stats.size else 1
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.Bottom,
                    maxItemsInEachRow = perRow
                ) {
                    stats.forEach { (value, label) -> Stat(value, label, Modifier.weight(1f)) }
                }
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
