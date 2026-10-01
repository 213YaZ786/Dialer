package com.dialer.app.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dialer.app.BuildConfig
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.main.ContactsScreen
import com.dialer.app.feature.main.FavoritesScreen
import com.dialer.app.feature.main.RecentsScreen
import com.dialer.app.feature.settings.SettingsScreen
import com.dialer.app.ui.component.DockClearance
import com.dialer.app.ui.component.DockItem
import com.dialer.app.ui.component.FloatingDock
import com.dialer.app.ui.component.LocalDockPadding
import com.dialer.app.ui.component.SideDockClearance
import com.dialer.app.ui.component.UpdatePrompt
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.LocalGlassBackdrop
import com.dialer.app.ui.glass.glassSource
import com.dialer.app.ui.glass.rememberGlassBackdrop
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DialerApp() {
    val navController = rememberNavController()
    // The only owner of the window insets: screens below draw under the bars
    // and take them as padding themselves. Transparent, because the page's
    // ground with its ambient light is painted once under the whole app.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { _ ->
        DialerNavHost(navController)
    }
}

@Composable
private fun DialerNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        // Opening scales up from slightly small, going back scales down, the
        // same motion as the other apps.
        enterTransition = {
            scaleIn(initialScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeIn(animationSpec = tween(NAV_MS))
        },
        exitTransition = { fadeOut(animationSpec = tween(NAV_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_MS)) },
        popExitTransition = {
            scaleOut(targetScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeOut(animationSpec = tween(NAV_MS))
        },
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.MAIN) {
            MainTabs(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            ReadableScroll {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    // After the NavHost, so it takes the back gesture before the NavHost's
    // predictive pop can.
    PlainBack(navController)
}

/**
 * The three tabs side by side in one pager. On a phone a swipe moves between
 * them, with the floating dock at the bottom. From 600 dp wide the same dock
 * stands upright on the left edge and the tabs switch in place. Back from
 * Recents or Contacts returns to Favorites before leaving the app.
 */
@Composable
private fun MainTabs(onOpenSettings: () -> Unit) {
    val tabs = TopDestination.entries
    val store: SettingsStore = koinInject()
    // Read once: the app reopens on the tab it was left on. After that the
    // pager state is saved, and coming back from Settings keeps the tab.
    val initialPage = remember { store.current.lastTab.coerceIn(0, tabs.size - 1) }
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Once when the app opens; debug builds are a different app and skip it.
    val settings by store.settings.collectAsState()
    if (!BuildConfig.DEBUG) UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)

    LaunchedEffect(pager.settledPage) {
        val page = pager.settledPage
        if (store.current.lastTab != page) store.update { it.copy(lastTab = page) }
    }

    val haptics = rememberHaptics()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = WidthClass.of(maxWidth).usesSideDock

        fun go(index: Int) {
            // Firm: moving to another tab is taking the whole screen
            // somewhere else, not pressing a button on the one in sight.
            if (index != pager.currentPage) haptics.firm()
            scope.launch {
                // On the side, tabs switch in place, like a navigation rail.
                // At the bottom they slide, because there pages are also swiped.
                if (side) pager.scrollToPage(index) else pager.animateScrollToPage(index)
            }
        }

        // Predictive back: the tabs shrink under the finger while the gesture
        // is held, spring back if it is abandoned, and only then does the tab
        // change.
        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackHandler(enabled = pager.currentPage != 0) { events ->
            try {
                events.collect { event -> backProgress = event.progress }
                backProgress = 0f
                go(0)
            } catch (cancelled: CancellationException) {
                backProgress = 0f
                throw cancelled
            }
        }

        val pages: @Composable (Modifier) -> Unit = { modifier ->
            HorizontalPager(
                state = pager,
                // All three stay alive, so switching tabs never reloads or
                // loses the scroll position.
                beyondViewportPageCount = tabs.size - 1,
                // With the side dock a sideways swipe would only fight
                // horizontal gestures in the wide content.
                userScrollEnabled = !side,
                modifier = modifier.graphicsLayer {
                    val shrink = 1f - 0.08f * backProgress
                    scaleX = shrink
                    scaleY = shrink
                }
            ) { page ->
                ReadableScroll {
                    when (tabs[page]) {
                        TopDestination.FAVORITES -> FavoritesScreen(onOpenSettings = onOpenSettings)
                        TopDestination.RECENTS -> RecentsScreen(onOpenSettings = onOpenSettings)
                        TopDestination.CONTACTS -> ContactsScreen(onOpenSettings = onOpenSettings)
                    }
                }
            }
        }

        // The screens draw under the navigation bar, so what sits at the
        // bottom of them clears it on top of the dock.
        val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // In glass, the tabs are recorded as they are drawn, for the dock
        // floating over them to bend them.
        val look = LocalGlass.current
        val tabsBackdrop = rememberGlassBackdrop()
        val tabsSource = if (look != null) Modifier.glassSource(tabsBackdrop, look) else Modifier
        val dockBackdrop = tabsBackdrop.takeIf { look != null }
        val items = tabs.map { DockItem(it.icon, it.label) }
        val position = pager.currentPage + pager.currentPageOffsetFraction

        if (side) {
            // Where the margins around the readable column are wide enough,
            // the pill sits in the left one and the column stays centred.
            val clearsPill = maxWidth - ReadableWidth >= SideDockClearance * 2
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDockPadding provides navigationBar) {
                    pages(
                        Modifier
                            .fillMaxSize()
                            .then(tabsSource)
                            .padding(start = if (clearsPill) 0.dp else SideDockClearance)
                    )
                }
                CompositionLocalProvider(LocalGlassBackdrop provides dockBackdrop) {
                    FloatingDock(
                        items = items,
                        position = position,
                        onSelect = ::go,
                        vertical = true,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDockPadding provides DockClearance + navigationBar) {
                    pages(Modifier.fillMaxSize().then(tabsSource))
                }
                CompositionLocalProvider(LocalGlassBackdrop provides dockBackdrop) {
                    FloatingDock(
                        items = items,
                        position = position,
                        onSelect = ::go,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/** Long enough to be read as motion, short enough not to be waited on. */
private const val NAV_MS = 260
