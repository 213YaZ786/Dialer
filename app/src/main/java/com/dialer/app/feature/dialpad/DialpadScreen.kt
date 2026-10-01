package com.dialer.app.feature.dialpad

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.dial.KeyTones
import com.dialer.app.core.dial.T9
import com.dialer.app.core.network.CellProtection
import com.dialer.app.core.network.CellWatch
import com.dialer.app.core.network.Protocol
import com.dialer.app.ui.component.NetworkChip
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.People
import com.dialer.app.core.dial.PhoneEntry
import com.dialer.app.data.calllog.CallHistory
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.data.settings.SettingsStore
import com.dialer.app.core.dial.T9Match
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.feature.call.GlassCallButton
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.BoldButton
import com.dialer.app.ui.component.GlassKeypad
import com.dialer.app.ui.component.QuietButton
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import com.dialer.app.ui.icon.DialerIcons
import java.util.Locale
import org.koin.compose.koinInject

/**
 * Typing a number: the twelve glass keys, the number as it will be dialled,
 * and above them the contacts it matches (by the letters on the keys, or
 * inside their numbers), a tap on one calls it. On a wide window the
 * contacts stand to the left of the keys.
 */
@Composable
fun DialpadScreen(initial: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val book: PhoneBook = koinInject()
    var number by rememberSaveable(initial) { mutableStateOf(initial) }

    val tones = remember { KeyTones(context) }
    DisposableEffect(Unit) { onDispose { tones.release() } }

    var contactsAllowed by remember { mutableStateOf(book.canRead()) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
        if (granted) book.refresh()
    }
    LaunchedEffect(contactsAllowed) { if (contactsAllowed) book.refresh() }
    val entries by book.entries.collectAsState()
    // Numbers called or calling lately that are not saved: found too, as
    // "Recent call", the number from yesterday's delivery is two digits away.
    val history: CallHistory = koinInject()
    LaunchedEffect(Unit) { history.refresh() }
    val calls by history.entries.collectAsState()
    val recent = remember(calls, entries) {
        val index = PhoneIndex(entries)
        calls.asSequence().filter { !it.hidden && index.find(it.key) == null }
            .distinctBy { it.key.removePrefix("+").takeLast(9) }
            .take(100)
            .mapIndexed { i, c -> PhoneEntry(-(i + 1L), Numbers.format(context, c.number), c.number, T9.clean(c.number), null, false) }
            .toList()
    }
    val matches = remember(number, entries, recent) { T9.search(number, entries + recent) }

    // Held 2 to 9: the number given to that key, or the choice of one.
    val settings: SettingsStore = koinInject()
    var assigning by remember { mutableStateOf<Int?>(null) }

    // Waiting for the call permission, with the call it was asked for.
    var pendingCall by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val askCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val placed = pendingCall
        pendingCall = null
        if (granted && placed != null && placed()) onClose()
    }
    fun place(placed: () -> Boolean) {
        if (!Dialing.canCall(context)) {
            pendingCall = placed
            askCall.launch(Manifest.permission.CALL_PHONE)
            return
        }
        if (placed()) onClose() else haptics.reject()
    }
    fun call(target: String) {
        if (T9.clean(target).isEmpty()) {
            haptics.reject()
            return
        }
        if (Dialing.special(context, target)) {
            onClose()
            return
        }
        place { Dialing.call(context, target) }
    }
    fun voicemail() = place { Dialing.voicemail(context) }

    var wide by remember { mutableStateOf(false) }
    val suggestions: @Composable (Modifier) -> Unit = { modifier ->
        Suggestions(
            typed = number,
            wide = wide,
            matches = matches,
            contactsAllowed = contactsAllowed,
            onAllowContacts = { askContacts.launch(Manifest.permission.READ_CONTACTS) },
            onCall = { call(it) },
            modifier = modifier
        )
    }
    val keys: @Composable () -> Unit = {
        Keys(
            number = number,
            onKey = { key ->
                number += key
                tones.start(key)
                // As on every phone, a code works the moment it is typed.
                if (key == '#' || key == '*') {
                    if (Dialing.special(context, number)) number = ""
                }
            },
            onKeyUp = { tones.stop() },
            onHeld = { key ->
                // The key already went in when it went down; held, it
                // stands for something else in its place.
                when {
                    key == '0' -> number = number.dropLast(1) + "+"
                    key == '*' -> number = number.dropLast(1) + ","
                    key == '#' -> number = number.dropLast(1) + ";"
                    key == '1' && number == "1" -> {
                        number = ""
                        voicemail()
                    }
                    key in '2'..'9' && number == key.toString() -> {
                        number = ""
                        val digit = key - '0'
                        val target = settings.current.speedDial[digit]
                        if (target != null) place { Dialing.call(context, target) } else assigning = digit
                    }
                    else -> return@Keys false
                }
                true
            },
            onPaste = { pasted -> number = pasted },
            onVoicemail = { voicemail() },
            onDelete = { number = number.dropLast(1) },
            onClear = { number = "" },
            onCall = { call(number) }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        wide = maxWidth > 760.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BannerAction(icon = DialerIcons.ArrowBack, label = "Close", onClick = onClose)
                Spacer(Modifier.weight(1f))
                // The network the call would go over, before it is placed.
                val cell: CellWatch = koinInject()
                val sims by cell.sims.collectAsState()
                sims.firstOrNull { it.isDefaultVoice }?.forCalls?.takeIf { it != Protocol.NONE }?.let { protocol ->
                    NetworkChip(protocol, CellProtection.protectionOf(protocol))
                }
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    suggestions(Modifier.weight(1f).fillMaxSize())
                    Spacer(Modifier.width(32.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { keys() }
                }
            } else {
                suggestions(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { keys() }
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    assigning?.let { digit ->
        SpeedDialDialog(
            digit = digit,
            people = People.of(entries).sortedByDescending { it.starred },
            onPick = { picked ->
                settings.update { it.copy(speedDial = it.speedDial + (digit to picked)) }
                haptics.done()
                assigning = null
            },
            onDismiss = { assigning = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keys(
    number: String,
    onKey: (Char) -> Unit,
    onKeyUp: () -> Unit,
    onHeld: (Char) -> Boolean,
    onPaste: (String) -> Unit,
    onVoicemail: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val iso = remember {
        context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase()
            ?: Locale.getDefault().country
    }
    val shown = remember(number) { PhoneNumberUtils.formatNumber(number, iso) ?: number }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 440.dp)) {
        // The number; held, it takes a number copied elsewhere. Before
        // anything is typed, the voicemail button stands in its place.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(76.dp).combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {},
                onLongClickLabel = "Paste",
                onLongClick = { paste(context)?.let { haptics.firm(); onPaste(it) } }
            )
        ) {
            if (number.isEmpty()) {
                QuietButton(onClick = {
                    haptics.tick()
                    onVoicemail()
                }) {
                    Icon(DialerIcons.Voicemail, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Voicemail")
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown,
                    fontSize = when {
                        shown.length < 13 -> 38.sp
                        shown.length < 17 -> 30.sp
                        else -> 24.sp
                    },
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                // Emergency numbers say so, in red, before they are dialled.
                val emergency = remember(number) { isEmergency(context, number) }
                androidx.compose.animation.AnimatedVisibility(visible = emergency) {
                    Text(
                        "Emergency call",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassKeypad(
            onPress = onKey,
            onRelease = onKeyUp,
            onLongPress = onHeld,
            voicemail = true
        )
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(Modifier.size(72.dp))
            GlassCallButton(icon = DialerIcons.Call, label = "Call", color = AnswerGreen, size = 72.dp, glow = 0.35f, onClick = onCall)
            // Backspace: a tap takes the last digit, held it clears the number.
            val glass = LocalGlass.current
            val base = Modifier.size(72.dp).clip(CircleShape)
            if (number.isEmpty()) {
                Spacer(Modifier.size(72.dp))
                return@Row
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (glass != null) base.glassZone(CircleShape, glass, lens = 1f) else base)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Delete",
                        onLongClickLabel = "Clear",
                        onClick = {
                            haptics.tick()
                            onDelete()
                        },
                        onLongClick = {
                            haptics.firm()
                            onClear()
                        }
                    )
            ) {
                Icon(DialerIcons.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A phone number from the clipboard, kept to what can be dialled; null when there is none. */
private fun paste(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
    return text.filter { it.isDigit() || it in "+*#,;" }.takeIf { it.any(Char::isDigit) }
}

/** The contacts the typed digits match, each a pane of glass that calls on a tap. */
@Composable
private fun Suggestions(
    typed: String,
    wide: Boolean,
    matches: List<T9Match>,
    contactsAllowed: Boolean,
    onAllowContacts: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!contactsAllowed) {
        Box(modifier, contentAlignment = Alignment.BottomCenter) {
            ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "Find your contacts as you type",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Dialer reads them on this phone only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BoldButton(onClick = onAllowContacts) { Text("Allow contacts") }
                }
            }
        }
        return
    }
    if (typed.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Type a number, or the letters of a name",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    // On a phone from the bottom up, so the best match sits right above the
    // number; on a wide window, beside the keys, top down as one reads.
    LazyColumn(
        modifier = modifier,
        reverseLayout = !wide,
        verticalArrangement = Arrangement.spacedBy(10.dp, if (wide) Alignment.Top else Alignment.Bottom),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(matches, key = { "${it.entry.contactId}/${it.entry.digits}" }) { match ->
            MatchRow(match, typed = typed, onCall = { onCall(match.entry.number) })
        }
    }
}

@Composable
private fun MatchRow(match: T9Match, typed: String, onCall: () -> Unit) {
    val haptics = rememberHaptics()
    ZoneSurface(
        shape = RoundedCornerShape(22.dp),
        onClick = {
            haptics.tick()
            onCall()
        },
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ZoneSurface(shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        match.entry.name.firstOrNull { it.isLetter() }?.uppercase() ?: "#",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    when {
                        match.entry.contactId < 0 -> highlightNumber(match.entry.name, typed, accent)
                        match.inName -> highlightName(match.entry.name, typed, accent)
                        else -> AnnotatedString(match.entry.name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        match.entry.contactId < 0 -> AnnotatedString("Recent call")
                        match.inName -> AnnotatedString(match.entry.number)
                        else -> highlightNumber(match.entry.number, typed, accent)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(DialerIcons.Call, contentDescription = "Call", tint = AnswerGreen)
        }
    }
}

/** The name with the letters the typed keys stand for, at the start of the word they matched, in [accent]. */
private fun highlightName(name: String, typed: String, accent: Color): AnnotatedString {
    val query = T9.clean(typed).removePrefix("+")
    return buildAnnotatedString {
        append(name)
        var start = 0
        for (word in name.split(Regex("(?<=[\\s\\-_.])"))) {
            val bare = word.trimEnd(' ', '-', '_', '.')
            if (T9.digitsOf(bare).startsWith(query)) {
                // Letters with no key (an apostrophe) count for nothing, so
                // walk the word until the typed digits are covered.
                var covered = 0
                var end = start
                while (end < start + bare.length && covered < query.length) {
                    if (T9.digitsOf(name[end].toString()).isNotEmpty()) covered++
                    end++
                }
                addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), start, end)
                break
            }
            start += word.length
        }
    }
}

/** The number with the digits that matched what was typed, in [accent], spaces and signs skipped over. */
private fun highlightNumber(number: String, typed: String, accent: Color): AnnotatedString {
    val query = T9.clean(typed).removePrefix("+")
    return buildAnnotatedString {
        append(number)
        if (query.isEmpty()) return@buildAnnotatedString
        val positions = number.indices.filter { number[it].isDigit() }
        val digits = positions.joinToString("") { number[it].toString() }
        val at = digits.indexOf(query)
        if (at >= 0) {
            addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), positions[at], positions[at + query.length - 1] + 1)
        }
    }
}

private val KnownEmergency = setOf("112", "911", "999", "000", "15", "17", "18", "114", "115", "119", "110", "100", "101", "102", "108", "190", "193")

/** The phone's own list when it gives one, else the usual numbers. */
private fun isEmergency(context: Context, number: String): Boolean {
    if (number.length < 2 || number.length > 4) return false
    return runCatching { context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number) }.getOrNull()
        ?: (number in KnownEmergency)
}
