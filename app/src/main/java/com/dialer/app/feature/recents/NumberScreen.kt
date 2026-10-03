package com.dialer.app.feature.recents

import com.dialer.app.ui.component.FloatingAction
import com.dialer.app.ui.component.FloatingFrame
import com.dialer.app.ui.component.FloatingTop
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
import androidx.compose.material3.TextButton
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
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.calllog.Rhythms
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.T9
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.HeroGlow
import com.dialer.app.ui.component.QuietButton
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.icon.AppIcons
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
    val entries by history.entries.collectAsState()
    val contacts by book.entries.collectAsState()
    val key = remember(number) { T9.clean(number) }
    val hidden = key.isEmpty()
    val calls = remember(entries, key) {
        entries.filter { if (hidden) it.hidden else !it.hidden && CallGrouping.sameDigits(it.key, key) }
    }
    val contact = remember(contacts, key) { if (hidden) null else PhoneIndex(contacts).find(key) }
    val shown = if (hidden) "Private number" else Numbers.format(context, number)

    var blocked by remember(number) { mutableStateOf(!hidden && NumberActions.isBlocked(context, number)) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(null, leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // The person's light behind the top of the page, under the glass.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // The person's colour, chosen in the Contacts app, when there is no photo.
            val color by androidx.compose.runtime.produceState<Int?>(null, contact?.contactId) {
                value = contact?.contactId?.let { id -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.dialer.app.core.dial.ContactLook.of(context, id)?.color } }
            }
            HeroGlow(contact?.photo, height = padding.calculateTopPadding() + 340.dp, color = color?.let { androidx.compose.ui.graphics.Color(it) })
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                ContactAvatar(contact?.name, contact?.photo, 128.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    contact?.name ?: shown,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                if (contact != null) {
                    Text(shown, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                // Where the number is from and the time there, from the number itself.
                if (!hidden) com.dialer.app.ui.component.PlaceLine(number, known = contact != null, modifier = Modifier.padding(top = 4.dp))
                if (blocked) {
                    Spacer(Modifier.height(8.dp))
                    ZoneSurface(shape = CircleShape) {
                        Text("Blocked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
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
                            if (contact != null) {
                                ActionTile(AppIcons.Person, "Contact", Modifier.weight(1f)) { NumberActions.openContact(context, contact.contactId) }
                            } else {
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    KindBadge(call.kind, size = 32.dp)
                                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(kindLabel(call.kind), style = MaterialTheme.typography.bodyLarge)
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
    if (confirmDelete) {
        ZoneAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete these calls?") },
            text = { Text("The ${calls.size} calls with ${contact?.name ?: shown} leave the call history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    history.delete(calls.map { it.id })
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
