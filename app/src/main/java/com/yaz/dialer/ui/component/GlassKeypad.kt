package com.yaz.dialer.ui.component

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.hypot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.dialer.ui.glass.LocalGlass
import com.yaz.dialer.ui.glass.glassZone
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.ui.theme.zone
import kotlinx.coroutines.withTimeoutOrNull

private val KeyDigitStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp)

/** The letters under each key, as on every phone. */
private val KeyLetters = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+"
)

/**
 * The twelve keys of a phone, each its own pane of glass with its letters.
 *
 * [onPress] when a key goes down (a tone starts), [onRelease] when it comes
 * up. Held past the long press delay, [onLongPress] is offered the key: it
 * returns true when it used it (0 gives +), and the tone stops there.
 * [voicemail] marks the 1 key with the voicemail sign, as on the dialpad
 * (not during a call, where 1 is only a tone).
 */
@Composable
fun GlassKeypad(
    onPress: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 72.dp,
    onLongPress: (Char) -> Boolean = { false },
    voicemail: Boolean = false,
    lit: Char? = null
) {
    var index = 0
    val side = keypadKeySide(keySize)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key -> GlassKey(key, side, onPress, onRelease, onLongPress, voicemail && key == '1', order = index++, lit = lit == key) }
            }
        }
    }
}

/**
 * The side of every key, which grows with the font: the content (digit, letters, voicemail sign)
 * must sit inside the circle, so the diameter is the diagonal of the largest content measured with
 * TextMeasurer (https://developer.android.com/develop/ui/compose/text/measure-text-layout) plus a
 * margin, never under [keySize]; every key takes the same one so the grid stays aligned, and the
 * dialpad's call row takes it too. AOSP Dialer's DialpadKeyButton keeps digit and letters inside
 * the key as well
 * (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/dialpadview/DialpadKeyButton.java).
 */
@Composable
fun keypadKeySide(keySize: Dp = 72.dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lettersStyle = MaterialTheme.typography.labelSmall
    return remember(keySize, lettersStyle, density) {
        val digit = measurer.measure("0", KeyDigitStyle, softWrap = false, maxLines = 1).size
        val letters = KeyLetters.values.map { measurer.measure(it, lettersStyle, softWrap = false, maxLines = 1).size }
        val width = maxOf(digit.width, letters.maxOf { it.width })
        val height = digit.height + maxOf(letters.maxOf { it.height }, with(density) { 16.dp.roundToPx() })
        val diagonal = with(density) { hypot(width.toDouble(), height.toDouble()).toFloat().toDp() }
        maxOf(keySize, diagonal + 8.dp)
    }
}

@Composable
private fun GlassKey(
    key: Char,
    size: Dp,
    onPress: (Char) -> Unit,
    onRelease: () -> Unit,
    onLongPress: (Char) -> Boolean,
    voicemail: Boolean,
    order: Int,
    lit: Boolean = false
) {
    // Lit by a tune played on the keys: it swells and glows in the accent.
    val glow by androidx.compose.animation.core.animateFloatAsState(if (lit) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "lit")
    val accent = MaterialTheme.colorScheme.primary
    // The keys come in one after the other, from the top left, like a
    // wave of drops settling.
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * 22L)
        rise.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    val haptics = rememberHaptics()
    val glass = LocalGlass.current
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val base = Modifier.size(size).clip(CircleShape)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer {
            val p = rise.value
            alpha = p.coerceIn(0f, 1f)
            val s = (0.6f + 0.4f * p) * (1f + 0.1f * glow)
            scaleX = s
            scaleY = s
        }.then(if (glass != null) base.glassZone(CircleShape, glass, lens = 1f) else base.background(MaterialTheme.colorScheme.zone))
            .semantics {
                role = Role.Button
                contentDescription = key.toString()
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.tick()
                    onPress(key)
                    val up = withTimeoutOrNull(longPress) { waitForUpOrCancellation() }
                    if (up == null && onLongPress(key)) {
                        haptics.firm()
                        onRelease()
                        waitForUpOrCancellation()
                    } else {
                        if (up == null) waitForUpOrCancellation()
                        onRelease()
                    }
                }
            }
    ) {
        if (glow > 0.01f) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawCircle(accent.copy(alpha = 0.45f * glow)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.toString(), style = KeyDigitStyle)
            KeyLetters[key]?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (voicemail) {
                Icon(
                    AppIcons.Voicemail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
