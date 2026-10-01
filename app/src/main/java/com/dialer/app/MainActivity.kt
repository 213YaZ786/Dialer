package com.dialer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dialer.app.navigation.DialerApp
import com.dialer.app.ui.theme.DialerSurface

/**
 * The main screen. It also answers the dial intents (a tel: link, the
 * phone button of another app): Android requires it of a phone app, and
 * the number lands in the dialpad once the dialpad exists.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DialerSurface { DialerApp() }
        }
    }
}
