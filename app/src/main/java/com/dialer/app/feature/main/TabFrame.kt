package com.dialer.app.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dialer.app.navigation.LocalReadableInset
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.FloatingBanner
import com.dialer.app.ui.component.LocalDockPadding
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.LocalGlassBackdrop
import com.dialer.app.ui.glass.glassSource
import com.dialer.app.ui.glass.rememberGlassBackdrop
import com.dialer.app.ui.icon.DialerIcons

/**
 * What every tab of the main screen shares. The tab's content takes the
 * whole screen, top to bottom; the banner with the tab's name and the way
 * to Settings, the setup steps while any is left, and the tab's own
 * [controls] (search, filters) float over it in glass, as the dock does
 * at the bottom, so the list passes under both and the glass bends it.
 *
 * [content] gets the padding that keeps its first and last lines clear of
 * the floating parts when scrolled to either end.
 */
@Composable
fun TabFrame(
    title: String,
    onOpenSettings: () -> Unit,
    subtitle: String? = null,
    controls: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val look = LocalGlass.current
    val backdrop = rememberGlassBackdrop()
    var header by remember { mutableStateOf(0.dp) }
    val inset = LocalReadableInset.current
    val bottom = LocalDockPadding.current + 16.dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (look != null) Modifier.glassSource(backdrop, look) else Modifier)
                .padding(horizontal = inset)
        ) {
            content(PaddingValues(top = header, bottom = bottom))
        }
        // The status bar stays readable: what scrolls up under it fades
        // into the page's ground instead of running under the clock.
        val ground = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(ground.copy(alpha = 0.92f), ground.copy(alpha = 0.6f))))
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop.takeIf { look != null }) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = inset)
                    .onSizeChanged { header = with(density) { it.height.toDp() } }
            ) {
                // The screen draws under the status bar, the banner starts below it.
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                FloatingBanner(
                    title = title,
                    subtitle = subtitle,
                    trailing = { BannerAction(icon = DialerIcons.Settings, label = "Settings", onClick = onOpenSettings) }
                )
                // Until Dialer can take calls, the steps to get there come first.
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) { SetupZone() }
                controls?.invoke()
            }
        }
    }
}
