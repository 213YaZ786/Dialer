package com.dialer.app.feature.recents

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.calllog.CallGroup
import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.calllog.CallKind
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.People
import com.dialer.app.ui.component.SearchPill
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.main.TabFrame
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.EmptyZone
import com.dialer.app.ui.component.FloatingPane
import com.dialer.app.ui.component.LoadingMark
import com.dialer.app.ui.component.NewDot
import com.dialer.app.ui.component.PillItem
import com.dialer.app.ui.component.PillMenu
import com.dialer.app.ui.component.PillMotion
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.component.rememberPillMenu
import com.dialer.app.ui.icon.AppIcons
import com.dialer.app.core.calllog.CallBacks
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * The calls, newest first, day by day. A missed call not yet seen carries a
 * dot; the green phone calls back at once, a tap on the line opens the
 * number with all its calls, a long press offers the rest.
 */
@Composable
fun RecentsScreen(visible: Boolean, onOpenSettings: () -> Unit, onOpenNumber: (String) -> Unit) {
    val history: CallHistory = koinInject()
    val book: PhoneBook = koinInject()
    var allowed by remember { mutableStateOf(history.canRead()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        allowed = result[Manifest.permission.READ_CALL_LOG] == true
    }
    LaunchedEffect(allowed) {
        if (allowed) {
            history.refresh()
            if (book.canRead()) book.refresh()
        }
    }
    val entries by history.entries.collectAsState()
    val loaded by history.loaded.collectAsState()
    val contacts by book.entries.collectAsState()
    val index = remember(contacts) { PhoneIndex(contacts) }
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val sections = remember(entries, missedOnly, query, index) {
        val q = People.plain(query.trim())
        val digits = query.filter(Char::isDigit)
        CallGrouping.sections(
            entries.filter { call ->
                (!missedOnly || call.kind == CallKind.MISSED) && (
                    q.isEmpty() ||
                        (digits.length >= 2 && call.key.contains(digits)) ||
                        People.plain(index.find(call.key)?.name ?: call.cachedName.orEmpty()).let { name ->
                            name.startsWith(q) || name.split(' ', '-').any { it.startsWith(q) }
                        }
                    )
            }
        )
    }

    // Who still waits for a call back, above all calls when nothing filters them.
    val waiting = remember(entries, missedOnly, query) {
        if (missedOnly || query.isNotBlank()) emptyList() else CallBacks.pending(entries, System.currentTimeMillis())
    }

    // Seen once the tab has been in front a moment: the dots then shrink
    // away and the system's missed call notification goes.
    LaunchedEffect(visible, entries) {
        if (visible) {
            delay(SEEN_AFTER_MS)
            history.markMissedSeen()
        }
    }

    TabFrame(
        title = "Recents",
        onOpenSettings = onOpenSettings,
        controls = {
            if (allowed && loaded && entries.isNotEmpty()) {
                Filters(missedOnly, onChange = { missedOnly = it }, query = query, onQuery = { query = it })
            }
        }
    ) { padding ->
        when {
            !allowed -> EmptyZone(
                title = "Your calls show here",
                message = "Dialer reads the call history on this phone only.",
                icon = AppIcons.Recents,
                actionLabel = "Allow call history",
                onAction = { ask.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)) },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            !loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingMark(size = 72.dp) }
            entries.isEmpty() -> EmptyZone(
                title = "No calls yet",
                message = "Your calls show here.",
                icon = AppIcons.Recents,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> {
                if (loaded && sections.isEmpty()) {
                    EmptyZone(
                        title = if (query.isNotBlank()) "No calls found" else "No missed calls",
                        message = if (query.isNotBlank()) "No name or number matches \"$query\"." else "Every call was answered.",
                        icon = AppIcons.Missed,
                        modifier = Modifier.fillMaxSize().padding(padding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (waiting.isNotEmpty()) {
                            item(key = "callbacks") { CallBackStrip(waiting, index, LINE_WIDTH, onOpenNumber) }
                        }
                        sections.forEach { section ->
                            item(key = "day/${section.day}") {
                                Text(
                                    dayLabel(section.day),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.widthIn(max = LINE_WIDTH).fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 2.dp)
                                )
                            }
                            items(section.groups, key = { "call/${it.first.id}" }) { group ->
                                CallLine(group, index, onOpen = { onOpenNumber(group.first.number) }, onDelete = {
                                    history.delete(group.calls.map { it.id })
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** All calls, or only the missed ones: two pills under the banner. */
@Composable
private fun Filters(missedOnly: Boolean, onChange: (Boolean) -> Unit, query: String, onQuery: (String) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        SearchPill(query, onQuery, hint = "Search calls", modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false).fillMaxWidth(), floating = true)
        listOf(false to "All", true to "Missed").forEach { (value, label) ->
            FloatingPane(
                shape = CircleShape,
                accent = missedOnly == value,
                onClick = {
                    haptics.tick()
                    onChange(value)
                }
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLine(group: CallGroup, index: PhoneIndex, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val call = group.first
    val contact = if (call.hidden) null else index.find(call.key)
    val name = contact?.name ?: call.cachedName
    val shown = if (call.hidden) "Private number" else Numbers.format(context, call.number)
    val menu = rememberPillMenu()
    var confirmBlock by remember { mutableStateOf(false) }

    fun callBack() {
        haptics.firm()
        // Without the call permission the number waits in the dialpad.
        if (!Dialing.call(context, call.number)) dial.open(call.number)
    }

    Box(Modifier.widthIn(max = LINE_WIDTH).fillMaxWidth().then(menu.tracker)) {
        val shape = RoundedCornerShape(22.dp)
        ZoneSurface(
            shape = shape,
            modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(
                onClickLabel = "Details",
                onLongClickLabel = "More",
                onClick = {
                    haptics.tick()
                    onOpen()
                },
                onLongClick = menu::open
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                Box {
                    ContactAvatar(name, contact?.photo, 48.dp)
                    KindBadge(call.kind, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp), unseen = group.unseen)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        name ?: shown,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (call.kind == CallKind.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Over the encrypted line: a lock before the kind.
                        if (call.encrypted) {
                            Icon(AppIcons.Lock, contentDescription = "Encrypted", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        // The kind in its colour, then the rest quieter.
                        Text(
                            kindWord(call.kind) + if (group.count > 1) " ×${group.count}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = kindTint(call.kind),
                            maxLines = 1
                        )
                        val parts = listOfNotNull(call.location, timeLabel(context, call.date))
                        Text(
                            " · " + parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!call.hidden) {
                    ZoneSurface(shape = CircleShape, onClick = ::callBack, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Call, contentDescription = "Call", tint = AnswerGreen)
                        }
                    }
                }
            }
        }
        PillMenu(
            menu,
            buildList {
                if (!call.hidden) {
                    add(PillItem(AppIcons.Call, "Call", PillMotion.BOUNCE, AnswerGreen) { callBack() })
                    add(PillItem(AppIcons.Message, "Send a message", PillMotion.WIGGLE) { NumberActions.message(context, call.number) })
                    add(PillItem(AppIcons.Copy, "Copy number", PillMotion.BOUNCE) { NumberActions.copy(context, call.number) })
                    if (contact == null) {
                        add(PillItem(AppIcons.PersonAdd, "Add to contacts", PillMotion.BOUNCE) { NumberActions.addContact(context, call.number) })
                    }
                    if (NumberActions.canBlock(context)) {
                        add(PillItem(AppIcons.Block, "Block", PillMotion.WIGGLE) { confirmBlock = true })
                    }
                }
                add(PillItem(AppIcons.Delete, "Delete from history", PillMotion.DROP) { onDelete() })
            }
        )
    }

    if (confirmBlock) {
        BlockDialog(name ?: shown, onDismiss = { confirmBlock = false }) {
            confirmBlock = false
            NumberActions.block(context, call.number)
        }
    }
}

/** Blocking asks once: a blocked number no longer rings, and texts from it no longer arrive. */
@Composable
fun BlockDialog(who: String, onDismiss: () -> Unit, onBlock: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Block, contentDescription = null) },
        title = { Text("Block $who?") },
        text = { Text("Calls and texts from this number will no longer reach you.") },
        confirmButton = { TextButton(onClick = onBlock) { Text("Block") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val LINE_WIDTH = 640.dp
private const val SEEN_AFTER_MS = 2500L
