package com.dialer.app.feature.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.People
import com.dialer.app.core.dial.Person
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.main.TabFrame
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.EmptyZone
import com.dialer.app.ui.component.PillItem
import com.dialer.app.ui.component.PillMenu
import com.dialer.app.ui.component.PillMotion
import com.dialer.app.ui.component.SearchPill
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.component.rememberPillMenu
import com.dialer.app.ui.icon.DialerIcons
import org.koin.compose.koinInject

/**
 * Everyone in the phone's contacts who has a number, A to Z under their
 * letter, with a search on top. The green phone calls, a tap opens the
 * contact, a long press offers the rest.
 */
@Composable
fun ContactsScreen(onOpenSettings: () -> Unit, onOpenContact: (Long) -> Unit) {
    val book: PhoneBook = koinInject()
    var allowed by remember { mutableStateOf(book.canRead()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(allowed) { if (allowed) book.refresh() }
    val entries by book.entries.collectAsState()
    val people = remember(entries) { People.of(entries) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(people, query) { People.search(people, query) }
    val context = LocalContext.current

    TabFrame(title = "Contacts", onOpenSettings = onOpenSettings) {
        if (!allowed) {
            EmptyZone(
                title = "Your contacts show here",
                message = "Dialer reads them on this phone only.",
                icon = DialerIcons.Person,
                actionLabel = "Allow contacts",
                onAction = { ask.launch(Manifest.permission.READ_CONTACTS) },
                modifier = Modifier.fillMaxSize()
            )
            return@TabFrame
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SearchPill(query, { query = it }, hint = "Search ${people.size} contacts", modifier = Modifier.widthIn(max = 560.dp).weight(1f, fill = false).fillMaxWidth())
            Spacer(Modifier.width(10.dp))
            ZoneSurface(shape = CircleShape, onClick = { NumberActions.newContact(context) }, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(DialerIcons.PersonAdd, contentDescription = "New contact", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        when {
            people.isEmpty() -> EmptyZone(
                title = "No contacts yet",
                message = "The contacts saved on this phone show here.",
                icon = DialerIcons.Person,
                actionLabel = "New contact",
                onAction = { NumberActions.newContact(context) },
                modifier = Modifier.fillMaxSize()
            )
            shown.isEmpty() -> EmptyZone(
                title = "No one found",
                message = "No name or number matches \"$query\".",
                icon = DialerIcons.Search,
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                shown.groupBy { initialOf(it.name) }.forEach { (letter, group) ->
                    item(key = "letter/$letter") {
                        Text(
                            letter,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 8.dp, top = 10.dp)
                        )
                    }
                    items(group, key = { "person/${it.id}" }) { person ->
                        PersonLine(person, onOpen = { onOpenContact(person.id) })
                    }
                }
            }
        }
    }
}

/** The letter a name files under; digits and signs under #. */
private fun initialOf(name: String): String =
    People.plain(name).firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonLine(person: Person, onOpen: () -> Unit, subtitle: String? = null, saved: Boolean = true) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val menu = rememberPillMenu()
    fun call() {
        haptics.firm()
        if (!Dialing.call(context, person.number)) dial.open(person.number)
    }
    Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().then(menu.tracker)) {
        val shape = RoundedCornerShape(22.dp)
        ZoneSurface(
            shape = shape,
            modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(
                onClickLabel = "Open",
                onLongClickLabel = "More",
                onClick = {
                    haptics.tick()
                    onOpen()
                },
                onLongClick = menu::open
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                ContactAvatar(person.name, person.photo, 44.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (person.starred) {
                            Spacer(Modifier.width(6.dp))
                            Icon(DialerIcons.Star, contentDescription = "Favorite", tint = StarGold, modifier = Modifier.size(16.dp))
                        }
                    }
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                ZoneSurface(shape = CircleShape, onClick = ::call, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(DialerIcons.Call, contentDescription = "Call", tint = AnswerGreen)
                    }
                }
            }
        }
        PillMenu(
            menu,
            listOfNotNull(
                PillItem(DialerIcons.Call, "Call", PillMotion.BOUNCE, AnswerGreen) { call() },
                PillItem(DialerIcons.Message, "Send a message", PillMotion.WIGGLE) { NumberActions.message(context, person.number) },
                if (!saved) PillItem(DialerIcons.PersonAdd, "Add to contacts", PillMotion.BOUNCE) { NumberActions.addContact(context, person.number) } else null,
                if (saved) PillItem(
                    if (person.starred) DialerIcons.StarOutline else DialerIcons.Star,
                    if (person.starred) "Remove from favorites" else "Add to favorites",
                    PillMotion.BOUNCE,
                    StarGold
                ) { NumberActions.star(context, person.id, !person.starred) } else null,
                if (saved) PillItem(DialerIcons.Edit, "Edit", PillMotion.WIGGLE) { NumberActions.editContact(context, person.id) } else null
            )
        )
    }
}

val StarGold = Color(0xFFFFB300)
