package com.yaz.dialer.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import com.yaz.dialer.ui.glass.glassZone
import kotlinx.coroutines.delay
import androidx.compose.animation.fadeIn
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.ui.glass.glassFloating
import com.yaz.dialer.feature.dialpad.DialpadScreen
import com.yaz.dialer.feature.call.ReturnToCall
import androidx.compose.foundation.layout.statusBarsPadding
import com.yaz.dialer.core.dial.DialRequests
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
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
import com.yaz.dialer.ui.glass.glassGround
import com.yaz.dialer.feature.main.WelcomeScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yaz.dialer.BuildConfig
import com.yaz.dialer.data.settings.SettingsStore
import com.yaz.dialer.feature.contacts.ContactsScreen
import com.yaz.dialer.feature.favorites.FavoritesScreen
import com.yaz.dialer.feature.recents.NumberScreen
import com.yaz.dialer.feature.recents.RecentsScreen
import com.yaz.dialer.core.calllog.CallKind
import com.yaz.dialer.data.calllog.CallHistory
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.yaz.dialer.feature.settings.SettingsScreen
import com.yaz.dialer.ui.component.DockClearance
import com.yaz.dialer.ui.component.DockItem
import com.yaz.dialer.ui.component.FloatingDock
import com.yaz.dialer.ui.component.LocalDockPadding
import com.yaz.dialer.ui.component.SideDockClearance
import com.yaz.dialer.ui.component.UpdatePrompt
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.glass.LocalGlass
import com.yaz.dialer.ui.glass.LocalGlassBackdrop
import com.yaz.dialer.ui.glass.glassSource
import com.yaz.dialer.ui.glass.rememberGlassBackdrop
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
                onOpenNumber = { number -> navController.navigate(Routes.number(number)) }
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
private fun MainTabs(onOpenSettings: () -> Unit, onOpenNumber: (String) -> Unit) {
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
    // Opened for another app: closed (back, Close, or a call placed), the dialpad gives the screen back to it.
    var forOutside by rememberSaveable { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    fun closeDialpad() {
        dialpad = null
        if (forOutside) {
            forOutside = false
            activity?.moveTaskToBack(true)
        }
    }
    LaunchedEffect(pendingDial) {
        pendingDial?.let {
            dialpad = it
            forOutside = dial.fromOutside
            dial.consume()
        }
    }

    // Another app asked for a number's page.
    val numberPage by dial.numberPage.collectAsState()
    LaunchedEffect(numberPage) {
        numberPage?.let {
            dialpad = null; forOutside = false
            dial.numberShown()
            onOpenNumber(it)
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
            dialpad = null; forOutside = false
            pager.scrollToPage(TopDestination.RECENTS.ordinal)
            dial.recentsShown()
        }
    }

    // Once when the app opens, never over the first launch page; debug
    // builds are a different app and skip it.
    val settings by store.settings.collectAsState()
    // Settings > Guide asks for the welcome pages again, then the show.
    LaunchedEffect(settings.welcomeSeen) { if (!settings.welcomeSeen) showWelcome = true }
    if (!showWelcome && !BuildConfig.DEBUG) UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)

    LaunchedEffect(pager.settledPage) {
        val page = pager.settledPage
        if (store.current.lastTab != page) store.update { it.copy(lastTab = page) }
    }

    val haptics = rememberHaptics()

    // Once, after the first launch page: the dialpad button shows that it
    // moves. A tap anywhere ends it.
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(showWelcome) {
        if (!showWelcome && !store.current.dialpadHintSeen) {
            delay(700)
            hint = true
        }
    }
    fun closeHint() {
        hint = false
        store.update { it.copy(dialpadHintSeen = true) }
    }

    // Once, after a first call: the call island over the other apps, which
    // only Android's own switch can allow.
    val context = androidx.compose.ui.platform.LocalContext.current
    var offerIsland by remember { mutableStateOf(false) }
    LaunchedEffect(calls.isNotEmpty(), showWelcome, hint) {
        offerIsland = calls.isNotEmpty() && !showWelcome && !hint && !store.current.islandOffered &&
            !android.provider.Settings.canDrawOverlays(context)
    }
    if (offerIsland) com.yaz.dialer.ui.component.ZoneAlertDialog(
        onDismissRequest = {
            offerIsland = false
            store.update { it.copy(islandOffered = true) }
        },
        title = { androidx.compose.material3.Text("Keep your calls in sight") },
        text = { androidx.compose.material3.Text("During a call, a small island at the top of the screen shows its time and controls in every app. Android asks you to allow it once.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                offerIsland = false
                store.update { it.copy(islandOffered = true) }
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName))
                    )
                }
            }) { androidx.compose.material3.Text("Allow") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                offerIsland = false
                store.update { it.copy(islandOffered = true) }
            }) { androidx.compose.material3.Text("Not now") }
        }
    )

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
                closeDialpad()
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
                        TopDestination.FAVORITES -> FavoritesScreen(onOpenSettings = onOpenSettings, onOpenNumber = onOpenNumber)
                        TopDestination.RECENTS -> RecentsScreen(
                            visible = pager.settledPage == page && dialpad == null && !showWelcome,
                            onOpenSettings = onOpenSettings,
                            onOpenNumber = onOpenNumber
                        )
                        TopDestination.CONTACTS -> ContactsScreen(onOpenSettings = onOpenSettings, onOpenNumber = onOpenNumber)
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
                    HintDim(hint)
                    MovableDialpadButton(onClick = { dialpad = "" }, above = 24.dp, demo = hint)
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
                    HintDim(hint)
                    // At the thumb's side, just above the dock, until moved.
                    MovableDialpadButton(onClick = { dialpad = "" }, above = DockClearance + 4.dp, demo = hint)
                }
            }
        }

        if (hint) {
            // Up top, out of the button's way: what the show means.
            HintCard(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp, start = 24.dp, end = 24.dp))
            // Over everything while it plays: any tap ends it.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        haptics.tick()
                        closeHint()
                    }
                }
            )
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
                DialpadScreen(initial = shownNumber, onClose = { closeDialpad() })
            }
        }

        // On the dialpad, level with its Close, while a call goes on: back to
        // the call in one tap. On the tabs it takes the name's place.
        if (dialpad != null) ReturnToCall(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
    }
}

/**
 * The way to the dialpad: a round pane of glass washed with the accent,
 * bending the tabs under it like the dock does. A tap opens the dialpad;
 * held, it lifts (a little larger, a firm tick) and follows the finger
 * anywhere on the screen, and stays where it is let go, kept for next time.
 * Until moved it sits at the thumb's side, [above] the bottom edge.
 */
@Composable
private fun MovableDialpadButton(onClick: () -> Unit, above: Dp, demo: Boolean = false) {
    val haptics = rememberHaptics()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val shape = CircleShape
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
    ) {
        val side = with(density) { ButtonSize.toPx() }
        val roomX = (constraints.maxWidth - side).coerceAtLeast(0f)
        val roomY = (constraints.maxHeight - side).coerceAtLeast(0f)
        val usual = with(density) { Offset(roomX - 8.dp.toPx(), roomY - (above - 12.dp).coerceAtLeast(0.dp).toPx()) }
        val saved = if (settings.dialpadX >= 0f) Offset(settings.dialpadX * roomX, settings.dialpadY * roomY) else null
        var dragging by remember { mutableStateOf<Offset?>(null) }
        val show = if (demo) rememberDemo() else null
        val rest = dragging ?: saved ?: usual
        // In the show, it travels from where it rests and comes back.
        val at = show?.let { d ->
            with(density) {
                Offset(
                    (rest.x + d.dx.dp.toPx() * (if (rest.x > roomX / 2) -1f else 1f)).coerceIn(0f, roomX),
                    (rest.y + d.dy.dp.toPx() * (if (rest.y > roomY / 2) -1f else 1f)).coerceIn(0f, roomY)
                )
            }
        } ?: rest
        val current by rememberUpdatedState(at)
        val held by animateFloatAsState(if (dragging != null) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
        val lift = show?.lift ?: held

        val base = Modifier
            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
            .size(ButtonSize)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .clip(shape)
        Box(
            contentAlignment = Alignment.Center,
            modifier = when {
                look != null && backdrop != null -> base.glassFloating(backdrop, shape, look, tint = look.accentTint, lens = 1.4f)
                else -> base.background(MaterialTheme.colorScheme.primaryContainer)
            }
                .semantics {
                    role = Role.Button
                    contentDescription = "Dialpad"
                }
                .pointerInput(roomX, roomY) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = withTimeoutOrNull(longPress) { waitForUpOrCancellation() }
                        if (up != null) {
                            haptics.firm()
                            onClick()
                            return@awaitEachGesture
                        }
                        // Held: picked up, it follows the finger.
                        haptics.firm()
                        var where = current
                        dragging = where
                        drag(down.id) { change ->
                            val d = change.positionChange()
                            change.consume()
                            where = Offset((where.x + d.x).coerceIn(0f, roomX), (where.y + d.y).coerceIn(0f, roomY))
                            dragging = where
                        }
                        haptics.tick()
                        val placed = where
                        store.update {
                            it.copy(
                                dialpadX = if (roomX > 0f) placed.x / roomX else 1f,
                                dialpadY = if (roomY > 0f) placed.y / roomY else 1f
                            )
                        }
                        dragging = null
                    }
                }
        ) {
            Icon(AppIcons.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        // The finger of the show, pressing the button and carrying it.
        show?.let { d ->
            Box(
                Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size(ButtonSize)
                    .graphicsLayer {
                        alpha = d.finger
                        val s = 0.55f + 0.25f * (1f - d.finger)
                        scaleX = s
                        scaleY = s
                    }
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

/** Where the show is: how far the button has gone (dp, towards the middle), its lift, the finger's presence. */
private class DemoState(val dx: Float, val dy: Float, val lift: Float, val finger: Float)

/**
 * The show of the dialpad button, five seconds over and over: the finger
 * comes, the button lifts, travels up and across, comes back, settles.
 */
@Composable
private fun rememberDemo(): DemoState {
    val loop = rememberInfiniteTransition(label = "demo")
    val spec = { frames: KeyframesSpec.KeyframesSpecConfig<Float>.() -> Unit ->
        infiniteRepeatable(keyframes { durationMillis = DEMO_MS; frames() })
    }
    val dx by loop.animateFloat(0f, 0f, spec {
        0f at 0; 0f at 1150 using FastOutSlowInEasing; 190f at 2400 using FastOutSlowInEasing
        120f at 3100 using FastOutSlowInEasing; 0f at 4050; 0f at DEMO_MS
    }, label = "dx")
    val dy by loop.animateFloat(0f, 0f, spec {
        0f at 0; 0f at 1150 using FastOutSlowInEasing; 220f at 2400 using FastOutSlowInEasing
        360f at 3100 using FastOutSlowInEasing; 0f at 4050; 0f at DEMO_MS
    }, label = "dy")
    val lift by loop.animateFloat(1f, 1f, spec {
        1f at 0; 1f at 730; 1.14f at 1150; 1.14f at 4050; 1f at 4470; 1f at DEMO_MS
    }, label = "lift")
    val finger by loop.animateFloat(0f, 0f, spec {
        0f at 0; 0f at 310; 1f at 730; 1f at 4050; 0f at 4470; 0f at DEMO_MS
    }, label = "finger")
    return DemoState(dx, dy, lift, finger)
}

private const val DEMO_MS = 5200

/** Dims the tabs and the dock under the show, not the button. */
@Composable
private fun HintDim(on: Boolean) {
    AnimatedVisibility(visible = on, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    }
}

/** What the show means, on a pane of glass: what moves, how, and how to close. */
@Composable
private fun HintCard(modifier: Modifier) {
    val look = LocalGlass.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = 380.dp)
            .clip(shape)
            .then(if (look != null) Modifier.glassZone(shape, look, lens = 1f) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text("The dialpad button moves", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hold it, then slide it wherever your thumb likes it. A tap opens the dialpad.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text("Tap to close. Settings › Guide shows it again.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private val ButtonSize = 64.dp

/** Long enough to be read as motion, short enough not to be waited on. */
private const val NAV_MS = 260
