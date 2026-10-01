package com.dialer.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.data.settings.ThemeMode
import com.dialer.app.navigation.DialerApp
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassGround
import com.dialer.app.ui.glass.rememberGlassLook
import com.dialer.app.ui.theme.DialerTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val store: SettingsStore = koinInject()
            val settings by store.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Status and navigation bar icons follow the app's theme, not only
            // the system's, so a forced light theme keeps dark icons.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                )
                onDispose { }
            }

            DialerTheme(darkTheme = dark, pureBlack = settings.pureBlack, textScale = settings.textScale) {
                // Glass over Material You: the look for this theme, or none when
                // the user turned it off, and the page's ground with its ambient
                // light under everything. The screens are see through.
                val look = rememberGlassLook(MaterialTheme.colorScheme, settings.glass)
                CompositionLocalProvider(LocalGlass provides look) {
                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                        DialerApp()
                    }
                }
            }
        }
    }
}
