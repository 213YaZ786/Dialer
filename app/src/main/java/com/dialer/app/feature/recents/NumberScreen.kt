package com.dialer.app.feature.recents

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.T9
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.QuietButton
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.icon.DialerIcons
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
    val location = calls.firstNotNullOfOrNull { it.location }

    var blocked by remember(number) { mutableStateOf(!hidden && NumberActions.isBlocked(context, number)) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            BannerAction(icon = DialerIcons.ArrowBack, label = "Back", onClick = onBack)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            ContactAvatar(contact?.name, contact?.photo, 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                contact?.name ?: shown,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            val under = listOfNotNull(shown.takeIf { contact != null }, location).joinToString(" · ")
            if (under.isNotEmpty()) {
                Text(under, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (blocked) {
                Spacer(Modifier.height(8.dp))
                ZoneSurface(shape = CircleShape) {
                    Text("Blocked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }

            if (!hidden) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    Action(DialerIcons.Call, "Call", AnswerGreen) {
                        haptics.firm()
                        if (!Dialing.call(context, number)) {
                            dial.open(number)
                            onBack()
                        }
                    }
                    Action(DialerIcons.Message, "Message") { NumberActions.message(context, number) }
                    if (contact != null) {
                        Action(DialerIcons.Person, "Contact") { NumberActions.openContact(context, contact.contactId) }
                    } else {
                        Action(DialerIcons.PersonAdd, "Add") { NumberActions.addContact(context, number) }
                    }
                    Action(DialerIcons.Copy, "Copy") {
                        haptics.tick()
                        NumberActions.copy(context, number)
                    }
                    if (NumberActions.canBlock(context)) {
                        Action(DialerIcons.Block, if (blocked) "Unblock" else "Block") {
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
                                Icon(kindIcon(call.kind), contentDescription = null, tint = kindTint(call.kind), modifier = Modifier.size(20.dp))
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
                    Icon(DialerIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete this history")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

/** A round pane of glass with its icon, and its name under it. */
@Composable
private fun Action(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ZoneSurface(shape = CircleShape, onClick = onClick, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint ?: MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
