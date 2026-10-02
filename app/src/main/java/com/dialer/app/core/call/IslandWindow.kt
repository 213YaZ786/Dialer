package com.dialer.app.core.call

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.dialer.app.R
import com.dialer.app.core.dial.T9
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.call.CallIsland
import com.dialer.app.feature.call.IslandCorner
import com.dialer.app.feature.call.IslandRole
import com.dialer.app.feature.call.openCallScreen
import com.dialer.app.ui.theme.DialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The call island over the other apps, at the top of the screen. Shown
 * while a call goes on and its screen is not in front, when the user let
 * the phone app draw over other apps (Android's own setting); never on
 * the lock screen, where the call screen itself shows.
 *
 * Two windows that never change width, each blurred behind by Android:
 * the wide one (a ringing call, the opened controls) and the pill (the
 * call going on). Only one shows at a time; they hand the island over at
 * the pill's place (see [IslandRole]).
 */
class IslandWindow(
    private val context: Context,
    private val store: CallStore,
    private val book: PhoneBook,
    private val settings: SettingsStore
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val dialogs = mutableMapOf<IslandRole, ComponentDialog>()
    private var watch: Job? = null
    private var lockWait: Job? = null

    /** Android blurs what lies behind the island (off in battery saver, or when blur is turned off). */
    private val blurred = MutableStateFlow(windows.isCrossWindowBlurEnabled)
    private val blurListener = java.util.function.Consumer<Boolean> { on -> blurred.value = on }

    /** True while the island closes back into the camera, before its windows go. */
    private val leaving = MutableStateFlow(false)

    /** The controls opened by a long press, whichever window shows them. */
    private val opened = MutableStateFlow(false)

    /** The window holding the island now, and whether it took it over from the other one. */
    private val stage = MutableStateFlow(IslandRole.WIDE)
    private val handOff = MutableStateFlow(false)
    private var callId: Int? = null

    /** Screen off or unlocked: the island is looked at again (never over the lock screen). */
    private val screen = MutableStateFlow(0)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            screen.value++
        }
    }

    fun start(scope: CoroutineScope) {
        if (watch != null) return
        windows.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
        context.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED
        )
        watch = scope.launch {
            launch { stage.collect { touchable() } }
            combine(store.state, store.screenShown, screen, opened) { state, shown, _, open -> Triple(state, shown, open) }.collect { (state, shown, open) ->
                val call = state.primary?.takeIf { it.phase != CallPhase.ENDED }
                if (call != null && call.id != callId) {
                    callId = call.id
                    opened.value = false
                }
                val wanted = call != null && !shown && allowed() && unlocked()
                // Locked during a call: Android does not always say when it is
                // unlocked again, so it is looked at twice a second until then.
                if (call != null && !unlocked()) {
                    if (lockWait?.isActive != true) lockWait = launch {
                        while (!unlocked()) delay(500)
                        screen.value++
                    }
                } else {
                    lockWait?.cancel()
                }
                val small = call != null && call.phase != CallPhase.RINGING && !open
                when {
                    wanted -> {
                        show(small)
                        // A ringing call or the controls need the wide window: it takes over at once.
                        if (!small && stage.value == IslandRole.PILL) {
                            handOff.value = true
                            stage.value = IslandRole.WIDE
                        }
                    }
                    // Back to the call screen, or locked: gone at once.
                    shown || !unlocked() -> hide()
                    else -> leaving.value = true
                }
                store.islandShown(wanted && call?.phase == CallPhase.RINGING)
            }
        }
    }

    fun stop() {
        watch?.cancel()
        watch = null
        runCatching { windows.removeCrossWindowBlurEnabledListener(blurListener) }
        runCatching { context.unregisterReceiver(screenReceiver) }
        hide()
        store.islandShown(false)
    }

    private fun allowed() = Settings.canDrawOverlays(context)

    /** The island will hold a ringing call now: Android's banner need not show it too. */
    fun covers(): Boolean = allowed() && unlocked()

    private fun unlocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true &&
            context.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun px(dp: Float) = (dp * context.resources.displayMetrics.density).toInt()

    private fun blurRadius() = px(26f)

    private fun show(small: Boolean) {
        leaving.value = false
        if (dialogs.isNotEmpty()) return
        stage.value = if (small) IslandRole.PILL else IslandRole.WIDE
        handOff.value = false
        runCatching {
            // The pill first, the wide one above it.
            dialogs[IslandRole.PILL] = make(IslandRole.PILL)
            dialogs[IslandRole.WIDE] = make(IslandRole.WIDE)
            touchable()
        }.onFailure { hide() }
    }

    /**
     * One of the island's windows: a dialog's window, not a bare view, since
     * only a window can ask Android to blur what lies behind it, clipped to
     * its corners.
     */
    private fun make(role: IslandRole): ComponentDialog {
        val island = ComponentDialog(context, R.style.Theme_Dialer_Island)
        val content = ComposeView(island.context).apply {
            setContent {
                val prefs by settings.settings.collectAsState()
                DialerTheme(pureBlack = prefs.pureBlack, textScale = prefs.textScale) {
                    val state by store.state.collectAsState()
                    val contacts by book.entries.collectAsState()
                    val glass by blurred.collectAsState()
                    val going by leaving.collectAsState()
                    val now by stage.collectAsState()
                    val taken by handOff.collectAsState()
                    val open by opened.collectAsState()
                    // The call stays in sight while the island closes after it ended.
                    var last by remember { mutableStateOf(state.primary) }
                    state.primary?.let { last = it }
                    val call = last ?: return@DialerTheme
                    val photo = remember(contacts, call.number) { PhoneIndex(contacts).find(T9.clean(call.number))?.photo }
                    CallIsland(
                        state, call, photo, store,
                        onOpenScreen = { openCallScreen(context) },
                        room = LocalConfiguration.current.screenWidthDp.dp,
                        opened = open,
                        onOpened = { opened.value = it },
                        role = role,
                        blurred = glass,
                        active = now == role,
                        handOff = taken,
                        leaving = going,
                        onGone = { if (leaving.value) hide() },
                        onGlass = { clear -> glass(role, clear) },
                        onFolded = {
                            if (stage.value == IslandRole.WIDE) {
                                handOff.value = true
                                stage.value = IslandRole.PILL
                            }
                        }
                    )
                }
            }
        }
        island.setContentView(Unbounded(island.context).apply { addView(content) })
        island.setCancelable(false)
        val window = island.window ?: error("no window")
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // Clear, with the island's corners: the blur behind takes them from it.
        window.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = px(IslandCorner.value).toFloat()
        })
        window.setBackgroundBlurRadius(0)
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Just under the status bar: Android's own icons stay in sight.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            val statusBar = runCatching {
                windows.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            }.getOrDefault(0)
            y = statusBar + px(6f)
            title = if (role == IslandRole.PILL) "Call island pill" else "Call island"
        }
        island.show()
        return island
    }

    /** Only the window holding the island takes touches; the other lets them through. */
    private fun touchable() {
        dialogs.forEach { (role, dialog) ->
            val window = dialog.window ?: return@forEach
            if (role == stage.value) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            else window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    /** The blur behind follows the glass as it clears in or out; at 0 Android drops it. */
    private fun glass(role: IslandRole, clear: Float) {
        val window = dialogs[role]?.window ?: return
        window.setBackgroundBlurRadius(if (blurred.value && clear > 0.01f) (blurRadius() * clear).toInt().coerceAtLeast(1) else 0)
    }

    private fun hide() {
        leaving.value = false
        opened.value = false
        dialogs.values.forEach { runCatching { it.dismiss() } }
        dialogs.clear()
    }

    /**
     * Lets the island take the width it asks: Android first offers a
     * dialog's window a narrow width and keeps it unless told it is too
     * small, which a Compose view never says by itself.
     */
    private class Unbounded(context: Context) : FrameLayout(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val child = getChildAt(0) ?: return super.onMeasure(widthSpec, heightSpec)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            child.measure(free, free)
            setMeasuredDimension(
                View.resolveSizeAndState(child.measuredWidth, widthSpec, 0),
                View.resolveSizeAndState(child.measuredHeight, heightSpec, 0)
            )
        }
    }
}
