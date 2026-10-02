package com.dialer.app.feature.favorites

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.People
import com.dialer.app.core.dial.Person
import com.dialer.app.core.dial.T9
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.contacts.NumberChooser
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dialer.app.feature.contacts.StarGold
import com.dialer.app.feature.main.TabFrame
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.EmptyZone
import com.dialer.app.ui.component.PillItem
import com.dialer.app.ui.component.PillMenu
import com.dialer.app.ui.component.PillMotion
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.component.rememberPillMenu
import com.dialer.app.ui.icon.DialerIcons
import org.koin.compose.koinInject

/**
 * The people called in one tap: the favourites as large drops of glass,
 * then the people called most these last weeks, saved or not.
 */
@Composable
fun FavoritesScreen(onOpenSettings: () -> Unit, onOpenNumber: (String) -> Unit) {
    val book: PhoneBook = koinInject()
    val history: CallHistory = koinInject()
    val settings: SettingsStore = koinInject()
    LaunchedEffect(Unit) {
        book.refresh()
        history.refresh()
    }
    val entries by book.entries.collectAsState()
    val calls by history.entries.collectAsState()
    val prefs by settings.settings.collectAsState()
    val people = remember(entries) { People.of(entries) }
    val favorites = remember(people) { people.filter { it.starred } }
    val frequents = remember(calls, people) { People.frequents(calls, people, System.currentTimeMillis()) }
    val context = LocalContext.current

    TabFrame(title = "Favorites", onOpenSettings = onOpenSettings) { padding ->
        if (favorites.isEmpty() && frequents.isEmpty()) {
            EmptyZone(
                title = "No favorites yet",
                message = "Star a contact to call them in one tap.",
                icon = DialerIcons.Star,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@TabFrame
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.widthIn(max = 672.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(favorites, key = { "fav/${it.id}" }) { person ->
                val digit = prefs.speedDial.entries.firstOrNull { (_, n) -> person.numbers.any { T9.clean(n) == it.digits } }?.key
                FavoriteTile(person, digit, onOpen = { onOpenNumber(person.number) })
            }
            if (frequents.isNotEmpty()) {
                item(key = "frequent-heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Frequently called",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
                // A row of faces: one tap calls, as the favourites above.
                item(key = "frequents", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(frequents, key = { it.number }) { frequent ->
                            FrequentFace(
                                name = frequent.person?.name ?: Numbers.format(context, frequent.number),
                                photo = frequent.person?.photo,
                                calls = frequent.calls,
                                number = frequent.number,
                                onOpen = { onOpenNumber(frequent.number) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** Someone called often: their face in a ring of glass, a tap calls, a long press opens their calls. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrequentFace(name: String, photo: String?, calls: Int, number: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClickLabel = "Call",
                onLongClickLabel = "Calls",
                onClick = {
                    haptics.firm()
                    if (!Dialing.call(context, number)) dial.open(number)
                },
                onLongClick = {
                    haptics.tick()
                    onOpen()
                }
            )
    ) {
        ZoneSurface(shape = CircleShape, modifier = Modifier.size(76.dp)) {
            Box(contentAlignment = Alignment.Center) { ContactAvatar(name, photo, 64.dp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(name.substringBefore(' '), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$calls calls", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A favourite: their face large on a pane of glass. A tap calls them, a long press offers the rest. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(person: Person, speedDigit: Int?, onOpen: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val menu = rememberPillMenu()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "sink")
    var choosing by remember { mutableStateOf(false) }
    fun call() {
        if (person.numbers.size > 1) {
            haptics.tick()
            choosing = true
            return
        }
        haptics.firm()
        if (!Dialing.call(context, person.number)) dial.open(person.number)
    }
    if (choosing) {
        NumberChooser(person, onDismiss = { choosing = false }) { number ->
            choosing = false
            haptics.firm()
            if (!Dialing.call(context, number)) dial.open(number)
        }
    }
    Box(Modifier.then(menu.tracker)) {
        val shape = RoundedCornerShape(28.dp)
        ZoneSurface(
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = sink
                    scaleY = sink
                }
                .clip(shape)
                .combinedClickable(
                    interactionSource = press,
                    indication = null,
                    onClickLabel = "Call",
                    onLongClickLabel = "More",
                    onClick = ::call,
                    onLongClick = menu::open
                )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 22.dp, horizontal = 10.dp)) {
                Box {
                    ContactAvatar(person.name, person.photo, 88.dp)
                    if (speedDigit != null) {
                        ZoneSurface(shape = CircleShape, accent = true, modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(speedDigit.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        PillMenu(
            menu,
            listOf(
                PillItem(DialerIcons.Call, "Call", PillMotion.BOUNCE, AnswerGreen) { call() },
                PillItem(DialerIcons.Message, "Send a message", PillMotion.WIGGLE) { NumberActions.message(context, person.number) },
                PillItem(DialerIcons.Recents, "Calls", PillMotion.BOUNCE) { onOpen() },
                PillItem(DialerIcons.Person, "Open in Contacts", PillMotion.BOUNCE) { NumberActions.openContact(context, person.id) },
                PillItem(DialerIcons.StarOutline, "Remove from favorites", PillMotion.DROP, StarGold) { NumberActions.star(context, person.id, false) }
            )
        )
    }
}
