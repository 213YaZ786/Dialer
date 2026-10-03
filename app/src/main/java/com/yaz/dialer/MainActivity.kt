package com.yaz.dialer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.yaz.dialer.data.settings.SettingsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yaz.dialer.core.dial.DialRequests
import com.yaz.dialer.navigation.DialerApp
import com.yaz.dialer.ui.theme.DialerSurface
import org.koin.android.ext.android.inject

/**
 * The main screen. It also answers the dial intents (a tel: link, the
 * phone button of another app, Add call during a call): the dialpad opens
 * with the number in it.
 */
class MainActivity : ComponentActivity() {

    private val dial: DialRequests by inject()
    private val settings: SettingsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a real launch: a recreated activity already took it.
        if (savedInstanceState == null) receive(intent)
        setContent {
            DialerSurface { DialerApp() }
        }
        // The recent apps screen keeps a picture of the app: blank if the
        // user prefers, so calls and contacts are not seen there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                settings.settings.map { it.hideInRecents }.distinctUntilChanged().collect { hide ->
                    setRecentsScreenshotEnabled(!hide)
                }
            }
        }
    }

    /** singleTask: a dial intent while the app runs arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW if intent.type == CallLog.Calls.CONTENT_TYPE -> dial.showRecents()
            DialRequests.ACTION_SHOW_NUMBER ->
                dial.showNumber(DialRequests.fromLink(intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart))
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                // A link only fills the dialpad, never calls nor runs a code
                // by itself: the user reads it and presses Call. Kept to what
                // can be dialled, and to a sane length.
                dial.open(DialRequests.fromLink(intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart))
            }
        }
    }
}
