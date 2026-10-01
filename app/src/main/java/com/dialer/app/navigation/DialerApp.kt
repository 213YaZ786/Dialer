package com.dialer.app.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.fadeIn
import com.dialer.app.ui.icon.DialerIcons
import com.dialer.app.ui.glass.glassFloating
import com.dialer.app.feature.dialpad.DialpadScreen
import com.dialer.app.feature.call.ReturnToCall
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateDpAsState
import com.dialer.app.core.dial.DialRequests
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
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
import com.dialer.app.ui.glass.glassGround
import com.dialer.app.feature.main.WelcomeScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
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
import com.dialer.app.feature.contacts.ContactScreen
import com.dialer.app.feature.contacts.ContactsScreen
import com.dialer.app.feature.favorites.FavoritesScreen
import com.dialer.app.feature.recents.NumberScreen
import com.dialer.app.feature.recents.RecentsScreen
import com.dialer.app.core.calllog.CallKind
import com.dialer.app.data.calllog.CallHistory
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
            MainTabs(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNumber = { number -> navController.navigate(Routes.number(number)) },
                onOpenContact = { id -> navController.navigate(Routes.contact(id)) }
            )
        }
        composable(
            Routes.CONTACT,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            ContactScreen(
                contactId = entry.arguments?.getLong("id") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.NUMBER,
            arguments = listOf(navArgument("n") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            NumberScreen(
                number = entry.arguments?.getString("n").orEmpty(),
                onBack = { navController.popBackStack() }
            )
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
private fun MainTabs(onOpenSettings: () -> Unit, onOpenNumber: (String) -> Unit, onOpenContact: (Long) -> Unit) {
    val tabs = TopDestination.entries
    val store: SettingsStore = koinInject()
    // Read once: the app reopens on the tab it was left on. After that the
    // pager state is saved, and coming back from Settings keeps the tab.
    val initialPage = remember { store.current.lastTab.coerceIn(0, tabs.size - 1) }
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // The first launch page, until it is closed. Saveable, so turning the
    // device keeps it open.
    var showWelcome by rememberSaveable { mutableStateOf(!store.current.welcomeSeen) }
    fun closeWelcome() {
        showWelcome = false
        if (!store.current.welcomeSeen) store.update { it.copy(welcomeSeen = true) }
    }

    // The dialpad, over the tabs, with the number it opened on; null closed.
    // Dial intents (tel: links, Add call) open it through DialRequests.
    val dial: DialRequests = koinInject()
    val pendingDial by dial.pending.collectAsState()
    var dialpad by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDial) {
        pendingDial?.let {
            dialpad = it
            dial.consume()
        }
    }

    // A dot on Recents while a missed call waits to be seen.
    val history: CallHistory = koinInject()
    LaunchedEffect(Unit) { history.refresh() }
    val calls by history.entries.collectAsState()
    val unseenMissed = calls.any { it.isNew && it.kind == CallKind.MISSED }

    // The missed call notification and the phone's "call history" links
    // open Recents.
    val showRecents by dial.recents.collectAsState()
    LaunchedEffect(showRecents) {
        if (showRecents) {
            dialpad = null
            pager.scrollToPage(TopDestination.RECENTS.ordinal)
            dial.recentsShown()
        }
    }

    // Once when the app opens, never over the first launch page; debug
    // builds are a different app and skip it.
    val settings by store.settings.collectAsState()
    if (!showWelcome && !BuildConfig.DEBUG) UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)

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

        // Declared after the tab one, so back closes the page first.
        PredictiveBackHandler(enabled = showWelcome) { events ->
            try {
                events.collect { }
                closeWelcome()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }

        PredictiveBackHandler(enabled = dialpad != null) { events ->
            try {
                events.collect { }
                dialpad = null
            } catch (cancelled: CancellationException) {
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
                        TopDestination.FAVORITES -> FavoritesScreen(onOpenSettings = onOpenSettings, onOpenContact = onOpenContact, onOpenNumber = onOpenNumber)
                        TopDestination.RECENTS -> RecentsScreen(
                            visible = pager.settledPage == page && dialpad == null && !showWelcome,
                            onOpenSettings = onOpenSettings,
                            onOpenNumber = onOpenNumber
                        )
                        TopDestination.CONTACTS -> ContactsScreen(onOpenSettings = onOpenSettings, onOpenContact = onOpenContact)
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
        val items = tabs.map { DockItem(it.icon, it.label, dot = it == TopDestination.RECENTS && unseenMissed) }
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
                    DialpadButton(
                        onClick = { dialpad = "" },
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 24.dp)
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
                    // Level with the dock, at the thumb's side.
                    DialpadButton(
                        onClick = { dialpad = "" },
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 16.dp)
                    )
                }
            }
        }

        // Above the tabs and the dock, opaque, with the page's ground and its
        // ambient light. A Surface also stops touches reaching the tabs.
        if (showWelcome) {
            Surface(
                Modifier.fillMaxSize().glassGround(LocalGlass.current, MaterialTheme.colorScheme.background),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Readable { WelcomeScreen(onStart = ::closeWelcome) }
            }
        }

        // The dialpad slides up over everything, keeping its number while it
        // slides back down.
        var shownNumber by remember { mutableStateOf("") }
        dialpad?.let { shownNumber = it }
        AnimatedVisibility(
            visible = dialpad != null,
            enter = slideInVertically(tween(NAV_MS)) { it / 4 } + fadeIn(tween(NAV_MS)),
            exit = slideOutVertically(tween(NAV_MS)) { it / 4 } + fadeOut(tween(NAV_MS))
        ) {
            Surface(
                Modifier.fillMaxSize().glassGround(LocalGlass.current, MaterialTheme.colorScheme.background),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                DialpadScreen(initial = shownNumber, onClose = { dialpad = null })
            }
        }

        // Over everything while a call goes on, below the page's banner or
        // level with the dialpad's Close: back to the call in one tap.
        val callTop by animateDpAsState(if (dialpad != null) 12.dp else 84.dp, tween(NAV_MS), label = "callTop")
        ReturnToCall(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = callTop))
    }
}

/**
 * The way to the dialpad: a round pane of glass washed with the accent,
 * bending the tabs under it like the dock does.
 */
@Composable
private fun DialpadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val shape = CircleShape
    val base = modifier.size(64.dp).clip(shape)
    Box(
        contentAlignment = Alignment.Center,
        modifier = when {
            look != null && backdrop != null -> base.glassFloating(backdrop, shape, look, tint = look.accentTint, lens = 1.4f)
            else -> base.background(MaterialTheme.colorScheme.primaryContainer)
        }.clickable(role = Role.Button, onClickLabel = "Dialpad") {
            haptics.firm()
            onClick()
        }
    ) {
        Icon(DialerIcons.Dialpad, contentDescription = "Dialpad", tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Long enough to be read as motion, short enough not to be waited on. */
private const val NAV_MS = 260
