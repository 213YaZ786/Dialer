package com.dialer.app.feature.contacts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.calllog.CallGrouping
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.NumberActions
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.People
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.recents.dayLabel
import com.dialer.app.feature.recents.durationLabel
import com.dialer.app.feature.recents.kindIcon
import com.dialer.app.feature.recents.kindLabel
import com.dialer.app.feature.recents.kindTint
import com.dialer.app.feature.recents.timeLabel
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.RoundAction
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.icon.DialerIcons
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * One contact: their face and name, the star, every number with its own
 * call and message, and the calls with any of them. Editing opens
 * Android's contacts app, which keeps the contacts.
 */
@Composable
fun ContactScreen(contactId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val book: PhoneBook = koinInject()
    val history: CallHistory = koinInject()
    val dial: DialRequests = koinInject()
    LaunchedEffect(Unit) {
        book.refresh()
        history.refresh()
    }
    val entries by book.entries.collectAsState()
    val calls by history.entries.collectAsState()
    val person = remember(entries, contactId) { People.of(entries).firstOrNull { it.id == contactId } }
    val theirs = remember(calls, person) {
        person?.let { p -> calls.filter { c -> !c.hidden && p.numbers.any { CallGrouping.sameDigits(it.digits, c.key) } } }.orEmpty()
    }
    // The star pops when it is turned on.
    val pop = remember { Animatable(1f) }

    fun call(number: String) {
        haptics.firm()
        if (!Dialing.call(context, number)) {
            dial.open(number)
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            BannerAction(icon = DialerIcons.ArrowBack, label = "Back", onClick = onBack)
        }
        val p = person ?: return@Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            ContactAvatar(p.name, p.photo, 120.dp)
            Spacer(Modifier.height(16.dp))
            Text(p.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                RoundAction(DialerIcons.Call, "Call", AnswerGreen) { call(p.number) }
                RoundAction(DialerIcons.Message, "Message") { NumberActions.message(context, p.number) }
                Box(Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }) {
                    RoundAction(if (p.starred) DialerIcons.Star else DialerIcons.StarOutline, if (p.starred) "Favorite" else "Add to favorites", StarGold) {
                        val on = !p.starred
                        NumberActions.star(context, p.id, on)
                        if (on) {
                            haptics.done()
                            scope.launch {
                                pop.animateTo(1.25f, spring(dampingRatio = 0.35f, stiffness = 900f))
                                pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
                            }
                        }
                    }
                }
                RoundAction(DialerIcons.Edit, "Edit") { NumberActions.editContact(context, p.id) }
            }

            Spacer(Modifier.height(28.dp))
            Heading("Numbers")
            ZoneSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    p.numbers.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                            Text(Numbers.format(context, entry.number), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            ZoneSurface(shape = CircleShape, onClick = { NumberActions.message(context, entry.number) }, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(DialerIcons.Message, contentDescription = "Message", tint = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(Modifier.size(8.dp))
                            ZoneSurface(shape = CircleShape, onClick = { call(entry.number) }, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(DialerIcons.Call, contentDescription = "Call", tint = AnswerGreen) }
                            }
                        }
                    }
                }
            }

            if (theirs.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Heading("History")
                ZoneSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        theirs.take(30).forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Icon(kindIcon(c.kind), contentDescription = null, tint = kindTint(c.kind), modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(kindLabel(c.kind), style = MaterialTheme.typography.bodyLarge)
                                    if (c.duration > 0) Text(durationLabel(c.duration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val day = Instant.ofEpochMilli(c.date).atZone(ZoneId.systemDefault()).toLocalDate()
                                Text("${dayLabel(day)}, ${timeLabel(context, c.date)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)
    )
}
