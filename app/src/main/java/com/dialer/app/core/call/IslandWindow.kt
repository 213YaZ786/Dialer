package com.dialer.app.core.call

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.WindowManager
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
import com.dialer.app.feature.call.openCallScreen
import com.dialer.app.ui.theme.DialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The call island over the other apps, in a small window of its own at
 * the top of the screen, around the camera. Shown while a call goes on
 * and its screen is not in front, when the user let the phone app draw
 * over other apps (Android's own setting); never on the lock screen,
 * where the call screen itself shows.
 */
class IslandWindow(
    private val context: Context,
    private val store: CallStore,
    private val book: PhoneBook,
    private val settings: SettingsStore
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var dialog: ComponentDialog? = null
    private var watch: Job? = null

    /** Android blurs what lies behind the island (off in battery saver, or when blur is turned off). */
    private val blurred = MutableStateFlow(windows.isCrossWindowBlurEnabled)
    private val blurListener = java.util.function.Consumer<Boolean> { on ->
        blurred.value = on
        dialog?.window?.setBackgroundBlurRadius(if (on) blurRadius() else 0)
    }

    /** True while the island closes back into the camera, before its window goes. */
    private val leaving = MutableStateFlow(false)

    fun start(scope: CoroutineScope) {
        if (watch != null) return
        windows.addCrossWindowBlurEnabledListener(context.mainExecutor, blurListener)
        watch = scope.launch {
            combine(store.state, store.screenShown) { state, shown -> state to shown }.collect { (state, shown) ->
                val call = state.primary?.takeIf { it.phase != CallPhase.ENDED }
                val wanted = call != null && !shown && allowed() && unlocked()
                when {
                    wanted -> show()
                    // Back to the call screen: gone at once, the screen takes its place.
                    shown -> hide()
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

    private fun show() {
        leaving.value = false
        if (dialog != null) return
        // A dialog's window, not a bare view: only a window can ask Android
        // to blur what lies behind it, clipped to the island's corners.
        val island = ComponentDialog(context, R.style.Theme_Dialer_Island)
        val content = ComposeView(island.context).apply {
            setContent {
                val prefs by settings.settings.collectAsState()
                DialerTheme(pureBlack = prefs.pureBlack, textScale = prefs.textScale) {
                    val state by store.state.collectAsState()
                    val contacts by book.entries.collectAsState()
                    val glass by blurred.collectAsState()
                    val going by leaving.collectAsState()
                    // The call stays in sight while the island closes after it ended.
                    var last by remember { mutableStateOf(state.primary) }
                    state.primary?.let { last = it }
                    val call = last ?: return@DialerTheme
                    val photo = remember(contacts, call.number) { PhoneIndex(contacts).find(T9.clean(call.number))?.photo }
                    CallIsland(
                        state, call, photo, store,
                        onOpenScreen = { openCallScreen(context) },
                        room = LocalConfiguration.current.screenWidthDp.dp,
                        overlay = true,
                        blurred = glass,
                        leaving = going,
                        onGone = { if (leaving.value) hide() },
                        onGlass = { clear -> glass(clear) }
                    )
                }
            }
        }
        island.setContentView(Unbounded(island.context).apply { addView(content) })
        island.setCancelable(false)
        val window = island.window ?: return
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
        window.setBackgroundBlurRadius(if (blurred.value) 1 else 0)
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
            title = "Call island"
        }
        runCatching {
            island.show()
            dialog = island
        }
    }

    /** The blur behind follows the glass as it clears in or out; never quite 0, which would drop it. */
    private fun glass(clear: Float) {
        if (!blurred.value) return
        dialog?.window?.setBackgroundBlurRadius((blurRadius() * clear).toInt().coerceAtLeast(1))
    }

    private fun hide() {
        leaving.value = false
        dialog?.let { runCatching { it.dismiss() } }
        dialog = null
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
