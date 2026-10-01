package com.dialer.app.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dialer.app.navigation.LocalReadableInset
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.LocalDockPadding
import com.dialer.app.ui.component.ScreenBanner
import com.dialer.app.ui.icon.DialerIcons

/**
 * What every tab of the main screen shares: the banner with the tab's name
 * and the way to Settings, then the tab's own content down to the dock.
 */
@Composable
fun TabFrame(
    title: String,
    onOpenSettings: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = LocalReadableInset.current)) {
        // The screen draws under the status bar, its banner starts below it.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        ScreenBanner(
            title = title,
            subtitle = subtitle,
            trailing = {
                BannerAction(icon = DialerIcons.Settings, label = "Settings", onClick = onOpenSettings)
            }
        )
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize(), content = content)
        }
        Spacer(Modifier.height(LocalDockPadding.current))
    }
}
