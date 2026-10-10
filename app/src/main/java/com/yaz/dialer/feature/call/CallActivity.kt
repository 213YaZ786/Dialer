package com.yaz.dialer.feature.call

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBars
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yaz.dialer.MainActivity
import com.yaz.dialer.core.call.AudioRoute
import com.yaz.dialer.core.call.CallPhase
import com.yaz.dialer.core.call.CallStore
import com.yaz.dialer.ui.theme.DialerSurface
import org.koin.android.ext.android.inject

/**
 * The call screen. Shown over the lock screen and allowed to turn the
 * screen on, as an incoming call must be. It holds no call itself: it
 * shows [CallStore] and closes once the last call is over.
 */
class CallActivity : ComponentActivity() {

    private val calls: CallStore by inject()
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // No other app's floating window over the call: nothing can lay a
        // false button over Answer or Hang up.
        window.setHideOverlayWindows(true)
        answerFrom(intent)

        proximity = getSystemService(PowerManager::class.java)
            .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dialer:proximity")

        setContent {
            val state by calls.state.collectAsState()
            var keypad by rememberSaveable { mutableStateOf(false) }

            // Nothing left to show: the last call ended and was said so.
            LaunchedEffect(state.calls.isEmpty()) {
                if (state.calls.isEmpty()) finish()
            }

            // The screen goes dark against the ear while talking on the
            // phone's own earpiece, never while the keypad is in use or the
            // sound comes from elsewhere.
            val primary = state.primary
            val nearEar = primary != null &&
                (primary.phase == CallPhase.ACTIVE || primary.phase == CallPhase.DIALING) &&
                (state.route == null || state.route?.kind == AudioRoute.Kind.EARPIECE) &&
                !keypad
            LaunchedEffect(nearEar) { holdProximity(nearEar) }

            // Back during a call: the screen folds into the island's place at the top, then closes;
            // the island takes it from there (it grew out of the island on the way in).
            val fold = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val folding = primary != null && (primary.phase == CallPhase.ACTIVE || primary.phase == CallPhase.DIALING || primary.phase == CallPhase.HOLDING)
            androidx.activity.compose.BackHandler(folding && fold.value == 0f) {
                scope.launch {
                    fold.animateTo(1f, androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    finish()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                    else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                }
            }
            DialerSurface {
                androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxSize()) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val statusBars = androidx.compose.foundation.layout.WindowInsets.statusBars
                    val top = statusBars.getTop(density) + with(density) { 12.dp.toPx() }
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    // The island's size while a call goes on: two thirds of the width up to 300 dp, 60 dp tall.
                    val pillW = minOf(w * 0.66f, with(density) { 300.dp.toPx() })
                    val pillH = with(density) { 60.dp.toPx() }
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().graphicsLayer {
                            val t = fold.value
                            if (t > 0f) {
                                // One scale for both sides (nothing is squashed), down to the island's width;
                                // the top and the bottom close in on the island's line meanwhile.
                                val middle = top + pillH / 2f
                                val sc = 1f + (pillW / w - 1f) * t
                                val tall = h + (pillH / sc - h) * t
                                val from = (middle - tall / 2f) * t
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, (middle / h).coerceIn(0f, 1f))
                                scaleX = sc
                                scaleY = sc
                                shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                                    val r = (tall / 2f) * t
                                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, from, size.width, from + tall, androidx.compose.ui.geometry.CornerRadius(r, r)))
                                }
                                clip = true
                                alpha = 1f - t * t * 0.7f
                            }
                        }
                    ) {
                        CallScreen(
                            state = state,
                            keypadOpen = keypad,
                            onKeypad = { keypad = it },
                            onAddCall = {
                                startActivity(
                                    Intent(this@CallActivity, MainActivity::class.java)
                                        .setAction(Intent.ACTION_DIAL)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            actions = calls
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        calls.screenShown(true)
    }

    override fun onStop() {
        calls.screenShown(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        answerFrom(intent)
    }

    override fun onDestroy() {
        holdProximity(false)
        super.onDestroy()
    }

    /** Answer pressed in the incoming call notification. */
    private fun answerFrom(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_ANSWER, -1) ?: -1
        if (id >= 0) {
            calls.answer(id)
            intent?.removeExtra(EXTRA_ANSWER)
        }
    }

    private fun holdProximity(on: Boolean) {
        val lock = proximity ?: return
        if (on && !lock.isHeld) lock.acquire(MAX_CALL_MS)
        if (!on && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    companion object {
        const val EXTRA_ANSWER = "answer"
        /** A safety bound on the wake lock, far beyond any real call. */
        private const val MAX_CALL_MS = 6 * 60 * 60 * 1000L
    }
}
