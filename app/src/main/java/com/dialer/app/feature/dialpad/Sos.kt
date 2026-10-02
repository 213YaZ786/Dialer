package com.dialer.app.feature.dialpad

import android.content.Context
import android.telephony.TelephonyManager
import android.telephony.emergency.EmergencyNumber
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dialer.app.feature.call.HangUpRed
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long SOS is held before it calls: long enough that a pocket never does it. */
private const val HOLD_SECONDS = 3

/**
 * The emergency call, next to Voicemail. A tap only says how it works;
 * held, it fills with red second by second, a buzz each second, and
 * calls when full. Letting go before cancels.
 */
@Composable
fun SosButton(onCall: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val number = remember { emergencyNumber(context) }
    val call by rememberUpdatedState(onCall)
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(2000)
            hint = false
        }
    }
    val left = ceil(HOLD_SECONDS * (1f - fill.value)).toInt().coerceAtLeast(1)
    val label = when {
        holding -> "$number · $left"
        hint -> "Hold to call $number"
        else -> "SOS"
    }
    val lift by animateFloatAsState(if (holding) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "sos")
    val glass = LocalGlass.current
    val shape = CircleShape

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .animateContentSize()
            .clip(shape)
            .then(if (glass != null) Modifier.glassZone(shape, glass, lens = 1f) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
            // Held, the whole pill turns red and fills with a deeper red.
            .drawBehind {
                if (holding) drawRect(HangUpRed.copy(alpha = 0.7f))
                drawRect(HangUpRed, size = size.copy(width = size.width * fill.value))
            }
            .semantics {
                role = Role.Button
                contentDescription = "Emergency call. Hold to call $number"
            }
            .pointerInput(number) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    haptics.firm()
                    val countdown = scope.launch {
                        for (second in 1..HOLD_SECONDS) {
                            fill.animateTo(second / HOLD_SECONDS.toFloat(), tween(1000, easing = LinearEasing))
                            if (second < HOLD_SECONDS) haptics.firm()
                        }
                        haptics.done()
                        holding = false
                        call(number)
                        fill.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    if (countdown.isActive) {
                        countdown.cancel()
                        holding = false
                        if (fill.value < 0.1f) hint = true
                        scope.launch { fill.animateTo(0f, tween(250)) }
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (holding) Color.White else MaterialTheme.colorScheme.error
        )
    }
}

/**
 * The general emergency number the network and the SIM give, 112 among
 * them when it is; else 112, which GSM networks answer everywhere.
 */
private fun emergencyNumber(context: Context): String = runCatching {
    val general = context.getSystemService(TelephonyManager::class.java).emergencyNumberList.values.flatten()
        .filter { EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED in it.emergencyServiceCategories }
        .map { it.number }
    general.firstOrNull { it == "112" } ?: general.firstOrNull()
}.getOrNull() ?: "112"
