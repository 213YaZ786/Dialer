package com.yaz.dialer.feature.recents

import com.yaz.dialer.ui.component.FloatingAction
import com.yaz.dialer.ui.component.FloatingFrame
import com.yaz.dialer.ui.component.FloatingTop
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import com.yaz.dialer.ui.component.PillItem
import com.yaz.dialer.ui.component.PillMenu
import com.yaz.dialer.ui.component.PillMotion
import com.yaz.dialer.ui.component.rememberPillMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.yaz.dialer.ui.component.BoldButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.yaz.dialer.core.call.Dialing
import com.yaz.dialer.core.call.PrefixRules
import com.yaz.dialer.core.call.ScreeningRules
import com.yaz.dialer.data.settings.PrefixStore
import com.yaz.dialer.feature.settings.PrefixDialog
import com.yaz.dialer.feature.settings.rememberPrefixRegion
import com.yaz.dialer.core.calllog.CallGrouping
import com.yaz.dialer.core.calllog.Rhythms
import com.yaz.dialer.core.dial.DialRequests
import com.yaz.dialer.core.dial.NumberActions
import com.yaz.dialer.core.dial.Numbers
import com.yaz.dialer.core.dial.T9
import com.yaz.dialer.data.calllog.CallHistory
import com.yaz.dialer.data.contacts.PhoneBook
import com.yaz.dialer.data.contacts.PhoneIndex
import com.yaz.dialer.ui.component.ContactAvatar
import com.yaz.dialer.ui.component.HeroGlow
import com.yaz.dialer.ui.component.QuietButton
import com.yaz.dialer.ui.component.ZoneAlertDialog
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons
import java.time.Instant
import java.time.ZoneId
import org.koin.compose.koinInject

/**
 * One number: who it is, what can be done with it, and every call with it,
 * newest first. Reached from a line of Recents.
 */
@Composable
fun NumberScreen(number: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val history: CallHistory = koinInject()
    val book: PhoneBook = koinInject()
    val dial: DialRequests = koinInject()
    LaunchedEffect(Unit) { history.refresh() }
    val everyCall by history.entries.collectAsState()
    // One call deleted waits a few seconds for "Undo" before the log is touched (UndoDelete).
    val undo = rememberUndoDelete(history)
    val entries = remember(everyCall, undo.hidden) { if (undo.hidden.isEmpty()) everyCall else everyCall.filter { it.id !in undo.hidden } }
    val contacts by book.entries.collectAsState()
    val key = remember(number) { T9.clean(number) }
    val hidden = key.isEmpty()
    val calls = remember(entries, key) {
        entries.filter { if (hidden) it.hidden else !it.hidden && CallGrouping.sameDigits(it.key, key) }
    }
    val contact = remember(contacts, com.yaz.dialer.core.dial.PrivateNames.version.intValue, key) { if (hidden) null else PhoneIndex(contacts).find(key) }
    val shown = if (hidden) "Private number" else Numbers.format(context, number)

    var blocked by remember(number) { mutableStateOf(!hidden && NumberActions.isBlocked(context, number)) }
    var confirmBlock by remember { mutableStateOf(false) }
    // The user's blocked prefixes (Settings lists them): this page can add one, starting from this number.
    val prefixStore: PrefixStore = koinInject()
    val prefixes by prefixStore.rules.collectAsState()
    val prefixRegion = rememberPrefixRegion()
    var askPrefix by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pageScroll = rememberScrollState()
    FloatingFrame(
        bottom = 0.dp,
        scroll = pageScroll,
        top = { FloatingTop(null, leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) },
        overlay = { UndoBar(undo, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp) }
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll),horizontalAlignment = Alignment.CenterHorizontally) {
            // The person's light behind the top of the page, under the glass.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // The person's colour, chosen in the Contacts app, when there is no photo.
            val color by androidx.compose.runtime.produceState<Int?>(null, contact?.contactId) {
                value = contact?.contactId?.let { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.yaz.dialer.core.dial.ContactLook.of(context, id)?.color } }
            }
            HeroGlow(contact?.photo, height = padding.calculateTopPadding() + 180.dp, color = color?.let { androidx.compose.ui.graphics.Color(it) })
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                // The header as AOSP Dialer's call details: the face at the start, a block to its end with the
                // name, the number and the place, centred on the face. Ported from
                // https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calldetails/res/layout/contact_container.xml
                // (QuickContactBadge, then contact_name, phone_number, network toEndOf the photo) and its dimens.xml
                // (Apache-2.0); Row mirrors in right-to-left.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(contact?.name, contact?.photo, 80.dp, look = contact?.look)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            contact?.name ?: shown,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = if (contact == null && !hidden) androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis else androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (contact != null) {
                            Text(
                                shown,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis
                            )
                        }
                        // Where the number is from and the time there, from the number itself.
                        if (!hidden) com.yaz.dialer.ui.component.PlaceLine(number, known = contact != null, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (blocked) {
                    Spacer(Modifier.height(8.dp))
                    ZoneSurface(shape = CircleShape) {
                        Text("Blocked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }

                // A number the sales call block turned away: one touch says it is not one (and back).
                val settingsStore: com.yaz.dialer.data.settings.SettingsStore = koinInject()
                val settings by settingsStore.settings.collectAsState()
                val allowed = !hidden && ScreeningRules.isAllowed(settings.salesAllowed, number)
                if (!hidden && (allowed || calls.any { blockedWhy(context, it, prefixes) == "sales call" })) {
                    Spacer(Modifier.height(10.dp))
                    ZoneSurface(
                        shape = CircleShape,
                        accent = allowed,
                        onClick = {
                            haptics.firm()
                            settingsStore.update { it.copy(salesAllowed = ScreeningRules.withAllowed(it.salesAllowed, number, !allowed)) }
                        }
                    ) {
                        Text(
                            "Not a sales call",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { stateDescription = if (allowed) "On" else "Off" }
                        )
                    }
                }

                if (!hidden) {
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CallBar(label = "Call", enabled = true) {
                            haptics.firm()
                            if (!Dialing.call(context, number)) {
                                dial.open(number)
                                onBack()
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionTile(AppIcons.Message, "Message", Modifier.weight(1f)) { NumberActions.message(context, number) }
                            // A private person of the Contacts app: nothing to open or add in Android's contacts.
                            if (contact != null && contact.contactId > 0) {
                                ActionTile(AppIcons.Person, "Contact", Modifier.weight(1f)) { NumberActions.openContact(context, contact.contactId) }
                            } else if (contact == null) {
                                ActionTile(AppIcons.PersonAdd, "Add", Modifier.weight(1f)) { NumberActions.addContact(context, number) }
                            }
                            ActionTile(AppIcons.Copy, "Copy", Modifier.weight(1f)) { NumberActions.copy(context, number) }
                            if (NumberActions.canBlock(context)) {
                                ActionTile(AppIcons.Block, if (blocked) "Unblock" else "Block", Modifier.weight(1f), tint = MaterialTheme.colorScheme.error) {
                                    if (blocked) {
                                        NumberActions.unblock(context, number)
                                        blocked = NumberActions.isBlocked(context, number)
                                    } else {
                                        confirmBlock = true
                                    }
                                }
                            }
                        }
                        // A whole range of spam numbers (not an official sales range): the start of this number.
                        QuietButton(onClick = { askPrefix = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(AppIcons.Block, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Block numbers starting like this")
                        }
                    }
                }

                if (calls.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    val rhythm = remember(calls) { Rhythms.of(calls, System.currentTimeMillis()) }
                    Box(Modifier.fillMaxWidth()) {
                        RhythmCard(rhythm) { hour ->
                            timeLabel(context, java.time.LocalDate.now().atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        }
                    }
                }

                if (calls.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)
                    )
                    ZoneSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            calls.forEachIndexed { i, call ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                HistoryRow(call, blockedWhy(context, call, prefixes)) { undo.delete(listOf(call.id)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    QuietButton(onClick = { confirmDelete = true }) {
                        Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete this history")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (confirmBlock) {
        BlockDialog(contact?.name ?: shown, onDismiss = { confirmBlock = false }) {
            confirmBlock = false
            NumberActions.block(context, number)
            blocked = NumberActions.isBlocked(context, number)
        }
    }
    if (askPrefix) {
        // The number as the user reads it (national at home), its first digits to be shortened.
        val digits = remember(number) { T9.clean(Numbers.format(context, number)) }
        PrefixDialog(
            initial = digits.take(if (digits.startsWith("+")) 7 else 6),
            region = prefixRegion,
            onDismiss = { askPrefix = false },
            onSave = {
                askPrefix = false
                prefixStore.update { rules -> PrefixRules.with(rules, it) }
            }
        )
    }
    if (confirmDelete) {
        ZoneAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete these calls?") },
            text = { Text("The ${calls.size} calls with ${contact?.name ?: shown} leave the call history.") },
            confirmButton = {
                BoldButton(filled = true, onClick = {
                    confirmDelete = false
                    history.delete(calls.map { it.id })
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { QuietButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/**
 * One call of a number's history. Swiped left or held, it can be deleted (the bar then offers Undo).
 * Source: Recents' CallLine in RecentsScreen.kt (SwipeRow end action, PillMenu with its tracker); the long
 * press through detectTapGestures and a long-click semantic, as Compose's tap-and-press docs show
 * (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press).
 */
@Composable
private fun HistoryRow(call: com.yaz.dialer.core.calllog.CallEntry, why: String?, onDelete: () -> Unit) {
    val context = LocalContext.current
    val menu = rememberPillMenu()
    com.yaz.dialer.ui.component.SwipeRow(
        start = null,
        end = com.yaz.dialer.ui.component.SwipeAction(AppIcons.Delete, "Delete", danger = true) { onDelete() },
        modifier = Modifier.fillMaxWidth().clipToBounds()
    ) {
        Box(Modifier.fillMaxWidth().then(menu.tracker)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .semantics { onLongClick("More") { menu.open(); true } }
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { menu.open() }) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                KindBadge(call.kind, size = 32.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        kindLabel(call.kind) + (why?.let { ", $it" }.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (call.duration > 0) {
                        Text(durationLabel(call.duration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val day = Instant.ofEpochMilli(call.date).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "${dayLabel(day)}, ${timeLabel(context, call.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PillMenu(menu, listOf(PillItem(AppIcons.Delete, "Delete from history", PillMotion.DROP, danger = true) { onDelete() }))
        }
    }
}
