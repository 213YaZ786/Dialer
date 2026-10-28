package com.yaz.dialer.feature.recents

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.yaz.dialer.ui.component.BoldButton
import com.yaz.dialer.ui.component.QuietButton
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDirection
import com.yaz.dialer.core.call.Dialing
import com.yaz.dialer.core.calllog.CallGroup
import com.yaz.dialer.core.calllog.CallGrouping
import com.yaz.dialer.core.calllog.CallKind
import com.yaz.dialer.core.dial.DialRequests
import com.yaz.dialer.core.dial.NumberActions
import com.yaz.dialer.core.dial.Numbers
import com.yaz.dialer.core.dial.People
import com.yaz.dialer.ui.component.SearchPill
import com.yaz.dialer.data.calllog.CallHistory
import com.yaz.dialer.data.contacts.PhoneBook
import com.yaz.dialer.data.contacts.PhoneIndex
import com.yaz.dialer.feature.call.AnswerGreen
import com.yaz.dialer.feature.main.TabFrame
import com.yaz.dialer.ui.component.ContactAvatar
import com.yaz.dialer.ui.component.EmptyZone
import com.yaz.dialer.ui.component.FloatingPane
import com.yaz.dialer.ui.component.LoadingMark
import com.yaz.dialer.ui.component.NewDot
import com.yaz.dialer.ui.component.PillItem
import com.yaz.dialer.ui.component.PillMenu
import com.yaz.dialer.ui.component.PillMotion
import com.yaz.dialer.ui.component.ZoneAlertDialog
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.component.rememberPillMenu
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.navigation.LocalReadableInset
import com.yaz.dialer.ui.component.SideDockClearance
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
    val everyCall by history.entries.collectAsState()
    // A deleted call waits a few seconds for "Undo" before the log is touched: it is out of the list meanwhile.
    val undo = rememberUndoDelete(history)
    val entries = remember(everyCall, undo.hidden) { if (undo.hidden.isEmpty()) everyCall else everyCall.filter { it.id !in undo.hidden } }
    val loaded by history.loaded.collectAsState()
    val contacts by book.entries.collectAsState()
    val index = remember(contacts, com.yaz.dialer.core.dial.PrivateNames.version.intValue) { PhoneIndex(contacts) }
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

    val settingsStore: com.yaz.dialer.data.settings.SettingsStore = koinInject()
    val settings by settingsStore.settings.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // The starred contacts, up to nine, as faces on top (the way feature/favorites reads them: People.of, starred).
    // A missed call not yet seen (Android's NEW flag, cleared once the tab has been in front a moment) counts on
    // its person's face.
    val faces = remember(contacts) { People.of(contacts).filter { it.starred }.take(FAVORITE_FACES_MAX) }
    val faceMissed = remember(faces, entries) {
        faces.associate { person ->
            person.id to entries.count { c ->
                c.kind == CallKind.MISSED && c.isNew && !c.hidden && person.numbers.any { CallGrouping.sameDigits(it.digits, c.key) }
            }
        }
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
      Box(Modifier.fillMaxSize()) {
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
                    // As many columns as fit, each card at least 280 dp, as SMS's conversation list
                    // (ConversationsScreen.kt, after Android's "Feed" canonical layout, GridCells.Adaptive(minSize):
                    // https://developer.android.com/guide/topics/large-screens/large-screen-canonical-layouts):
                    // one column on a phone upright.
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val gap = 8.dp
                        // The side dock (DialerApp draws it over the tabs' left margin when the readable column
                        // leaves room, ui/component/FloatingDock.kt SideDockClearance = 96 dp) is cleared on both
                        // sides so the grid stays centred under the search row; with less room DialerApp already
                        // pads the tab by that clearance. Insets as Material 3 navigation rail guidance puts it:
                        // content beside the rail, never under it
                        // (https://m3.material.io/components/navigation-rail/guidelines).
                        val rail = if (LocalReadableInset.current >= SideDockClearance) SideDockClearance else 0.dp
                        val inner = maxWidth - 32.dp - rail * 2
                        val columns = ((inner + gap) / (CARD_MIN_WIDTH + gap)).toInt().coerceIn(1, 4)
                        val cell = (inner - gap * (columns - 1)) / columns
                        val wide = if (columns == 1) Modifier.widthIn(max = LINE_WIDTH).fillMaxWidth() else Modifier.fillMaxWidth()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp + rail, end = 16.dp + rail, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Hidden while searching and in the Missed filter.
                            if (faces.isNotEmpty() && !missedOnly && query.isBlank()) {
                                item(key = "favorites") {
                                    FavoriteFaces(faces, faceMissed, wide, onOpen = { onOpenNumber(it.number) })
                                }
                            }
                            sections.forEach { section ->
                                item(key = "day/${section.day}") {
                                    Text(
                                        dayLabel(section.day),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = wide.padding(start = 8.dp, top = 12.dp, bottom = 2.dp)
                                    )
                                }
                                val line: @Composable (CallGroup, Modifier) -> Unit = { group, m ->
                                    CallLine(
                                        group, index, salesAllowed = settings.salesAllowed,
                                        swipeRight = settings.swipeRight, swipeLeft = settings.swipeLeft,
                                        // Rows follow a swipe only while this tab is settled in front: not while the pager moves.
                                        swipes = visible,
                                        onOpen = { onOpenNumber(group.first.number) },
                                        onDelete = { undo.delete(group.calls.map { it.id }) },
                                        onNotSales = { number, allow -> settingsStore.update { it.copy(salesAllowed = com.yaz.dialer.core.call.ScreeningRules.withAllowed(it.salesAllowed, number, allow)) } },
                                        modifier = m,
                                        even = columns > 1
                                    )
                                }
                                if (columns == 1) {
                                    items(section.groups, key = { "call/${it.first.id}" }) { group -> line(group, wide) }
                                } else {
                                    items(section.groups.chunked(columns), key = { row -> "call/${row.first().first.id}" }) { row ->
                                        CardRow(row, cell, gap, line)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        UndoBar(undo, bottom = padding.calculateBottomPadding() + 8.dp)
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
private fun CallLine(
    group: CallGroup, index: PhoneIndex, salesAllowed: Set<String>,
    swipeRight: com.yaz.dialer.data.settings.SwipeChoice, swipeLeft: com.yaz.dialer.data.settings.SwipeChoice, swipes: Boolean,
    onOpen: () -> Unit, onDelete: () -> Unit, onNotSales: (String, Boolean) -> Unit,
    /** The card's outer size: a column's width, or a grid cell's. */
    modifier: Modifier = Modifier.widthIn(max = LINE_WIDTH).fillMaxWidth(),
    /** In a grid of several columns every card has the same least height; in one column each is as tall as it needs. */
    even: Boolean = false
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val call = group.first
    // For a blocked call, why: "Blocked · sales call". A sales call can be allowed from here.
    val prefixes by koinInject<com.yaz.dialer.data.settings.PrefixStore>().rules.collectAsState()
    val why = remember(call.id, call.blockReason, prefixes) { blockedWhy(context, call, prefixes) }
    val isSales = why == "sales call"
    val allowedNow = isSales && com.yaz.dialer.core.call.ScreeningRules.isAllowed(salesAllowed, call.number)
    val contact = if (call.hidden) null else index.find(call.key)
    // An empty name (a log line or a contact saved with ""), as blank, falls back to the number.
    val name = (contact?.name ?: call.cachedName)?.takeIf { it.isNotBlank() }
    // AOSP's primary text: "Private" for a hidden number, the formatted number, else "Unknown"
    // (CallLogEntryText.java, https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
    val shown = if (call.hidden) "Private number" else Numbers.format(context, call.number).ifBlank { "Unknown" }
    val menu = rememberPillMenu()
    var confirmBlock by remember { mutableStateOf(false) }

    fun callBack() {
        haptics.firm()
        // Without the call permission the number waits in the dialpad.
        if (!Dialing.call(context, call.number)) dial.open(call.number)
    }

    // What each side does is chosen in Settings. Source: SMS's swipe choice mapped to a row's sides
    // (https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/conversations/ConversationsScreen.kt,
    // `left = SwipeAction.of(settings.swipeLeft ...)`), the choices of Samsung One UI Phone (iOS keeps Delete,
    // Google none). A hidden number has no call, message or block; "Nothing" leaves the side still.
    val canBlock = remember { NumberActions.canBlock(context) }
    fun swipeOf(choice: com.yaz.dialer.data.settings.SwipeChoice): com.yaz.dialer.ui.component.SwipeAction? = when {
        !swipes -> null
        choice == com.yaz.dialer.data.settings.SwipeChoice.DELETE ->
            com.yaz.dialer.ui.component.SwipeAction(AppIcons.Delete, "Delete", danger = true) { onDelete() }
        call.hidden -> null
        choice == com.yaz.dialer.data.settings.SwipeChoice.CALL ->
            com.yaz.dialer.ui.component.SwipeAction(AppIcons.Call, "Call") { callBack() }
        choice == com.yaz.dialer.data.settings.SwipeChoice.MESSAGE ->
            com.yaz.dialer.ui.component.SwipeAction(AppIcons.Message, "Message", removes = false) { NumberActions.message(context, call.number) }
        choice == com.yaz.dialer.data.settings.SwipeChoice.BLOCK && canBlock ->
            com.yaz.dialer.ui.component.SwipeAction(AppIcons.Block, "Block", danger = true, removes = false) { confirmBlock = true }
        else -> null
    }
    com.yaz.dialer.ui.component.SwipeRow(
        // A sales call turned away: swiped right it is allowed (Not a sales call); the line stays, marked.
        start = if (swipes && isSales && !allowedNow && swipeRight != com.yaz.dialer.data.settings.SwipeChoice.NONE) {
            com.yaz.dialer.ui.component.SwipeAction(AppIcons.CheckCircle, "Not a sales call", removes = false) { onNotSales(call.number, true) }
        } else {
            swipeOf(swipeRight)
        },
        end = swipeOf(swipeLeft),
        modifier = modifier
    ) {
    Box(Modifier.fillMaxWidth().then(menu.tracker)) {
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
            // The person card, as SMS's conversation card (ConversationsScreen.kt ConversationLine): a fixed column
            // for the face with its name under it on one line, the last call as a bubble beside it, the call button
            // at the end. Every card has the same least height so a grid row stays even.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = if (even) CARD_HEIGHT else 0.dp).padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            ) {
                // AOSP Dialer's call log entry (new_call_log_entry.xml,
                // https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllog/ui/res/layout/new_call_log_entry.xml):
                // the photo, then the name on the first line and the call's type and time on the second, the call
                // button at the end, so the words take the card's whole width (the user, 2026-10-28: room wasted
                // beside a short "Missed", a long reason cramped under the face).
                Box(Modifier.padding(start = 6.dp)) {
                    ContactAvatar(name, contact?.photo, 48.dp, look = contact?.look)
                    if (call.encrypted) CallFaceMark(AppIcons.Lock, "End-to-end encrypted", Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    // First line: the name, then the time at its end (iOS Phone's recents put the time on the name's
                    // line). The name takes what the time leaves and is cut with an ellipsis; the time is never cut
                    // and never wraps. Baselines aligned as the two sizes differ.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            name ?: shown,
                            // The name starts at the face's side whatever its script: an Arabic name in a
                            // left-to-right app would otherwise end up against the time (Compose's TextDirection,
                            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/style/TextDirection.kt).
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr
                            ),
                            fontWeight = if (group.unseen) FontWeight.Bold else FontWeight.Normal,
                            color = if (call.kind == CallKind.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).alignByBaseline()
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            timeLabel(context, call.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                    LastCallBubble(group, why, allowedNow, Modifier.fillMaxWidth())
                }
                if (!call.hidden) {
                    Spacer(Modifier.width(6.dp))
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
                if (isSales) {
                    // A number in a sales range that is not one: it rings from now on (saved in Settings, not in Android's blocked list).
                    add(
                        PillItem(AppIcons.Call, if (allowedNow) "Sales call again" else "Not a sales call", PillMotion.BOUNCE) {
                            haptics.firm()
                            onNotSales(call.number, !allowedNow)
                        }
                    )
                }
                if (!call.hidden) {
                    add(PillItem(AppIcons.Call, "Call", PillMotion.BOUNCE, AnswerGreen) { callBack() })
                    add(PillItem(AppIcons.Message, "Send a message", PillMotion.WIGGLE) { NumberActions.message(context, call.number) })
                    add(PillItem(AppIcons.Copy, "Copy number", PillMotion.BOUNCE) { NumberActions.copy(context, call.number) })
                    if (contact == null) {
                        add(PillItem(AppIcons.PersonAdd, "Add to contacts", PillMotion.BOUNCE) { NumberActions.addContact(context, call.number) })
                    }
                    if (NumberActions.canBlock(context)) {
                        add(PillItem(AppIcons.Block, "Block", PillMotion.WIGGLE, danger = true) { confirmBlock = true })
                    }
                }
                add(PillItem(AppIcons.Delete, "Delete from history", PillMotion.DROP, danger = true) { onDelete() })
            }
        )
    }
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
        confirmButton = { BoldButton(filled = true, onClick = onBlock) { Text("Block") } },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val LINE_WIDTH = 640.dp
private const val SEEN_AFTER_MS = 2500L
