package com.dialer.app.ui.component

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import com.dialer.app.ui.icon.DialerIcons
import com.dialer.app.ui.theme.zone
import kotlinx.coroutines.withTimeoutOrNull

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
    voicemail: Boolean = false
) {
    var index = 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key -> GlassKey(key, keySize, onPress, onRelease, onLongPress, voicemail && key == '1', order = index++) }
            }
        }
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
    order: Int
) {
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
            val s = 0.6f + 0.4f * p
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.toString(), fontSize = 30.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp)
            KeyLetters[key]?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (voicemail) {
                Icon(
                    DialerIcons.Voicemail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
