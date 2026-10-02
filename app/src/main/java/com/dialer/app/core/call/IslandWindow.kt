package com.dialer.app.core.call

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dialer.app.core.dial.T9
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.call.CallIsland
import com.dialer.app.feature.call.openCallScreen
import com.dialer.app.ui.theme.DialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private var view: ComposeView? = null
    private var owner: Owner? = null
    private var watch: Job? = null

    fun start(scope: CoroutineScope) {
        if (watch != null) return
        watch = scope.launch {
            combine(store.state, store.screenShown) { state, shown -> state to shown }.collect { (state, shown) ->
                val call = state.primary?.takeIf { it.phase != CallPhase.ENDED }
                val wanted = call != null && !shown && allowed() && unlocked()
                if (wanted) show() else hide()
                store.islandShown(wanted && call?.phase == CallPhase.RINGING)
            }
        }
    }

    fun stop() {
        watch?.cancel()
        watch = null
        hide()
        store.islandShown(false)
    }

    private fun allowed() = Settings.canDrawOverlays(context)

    /** The island will hold a ringing call now: Android's banner need not show it too. */
    fun covers(): Boolean = allowed() && unlocked()

    private fun unlocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true &&
            context.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun show() {
        if (view != null) return
        val lifecycle = Owner().also { owner = it }
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                val prefs by settings.settings.collectAsState()
                DialerTheme(pureBlack = prefs.pureBlack, textScale = prefs.textScale) {
                    val state by store.state.collectAsState()
                    val contacts by book.entries.collectAsState()
                    val call = state.primary ?: return@DialerTheme
                    val photo = remember(contacts, call.number) { PhoneIndex(contacts).find(T9.clean(call.number))?.photo }
                    CallIsland(state, call, photo, store, onOpenScreen = { openCallScreen(context) })
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Just under the status bar: Android's own call chip and icons stay in sight.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            val statusBar = runCatching {
                windows.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            }.getOrDefault(0)
            y = statusBar + (6 * context.resources.displayMetrics.density).toInt()
            title = "Call island"
        }
        runCatching {
            windows.addView(compose, params)
            lifecycle.resume()
            view = compose
        }.onFailure { lifecycle.destroy() }
    }

    private fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
        owner?.destroy()
        owner = null
    }

    /** What a Compose view outside an activity needs: a lifecycle and a place for saved state. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
