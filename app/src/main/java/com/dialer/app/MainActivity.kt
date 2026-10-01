package com.dialer.app

import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.navigation.DialerApp
import com.dialer.app.ui.theme.DialerSurface
import org.koin.android.ext.android.inject

/**
 * The main screen. It also answers the dial intents (a tel: link, the
 * phone button of another app, Add call during a call): the dialpad opens
 * with the number in it.
 */
class MainActivity : ComponentActivity() {

    private val dial: DialRequests by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a real launch: a recreated activity already took it.
        if (savedInstanceState == null) receive(intent)
        setContent {
            DialerSurface { DialerApp() }
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
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
                dial.open(number)
            }
        }
    }
}
