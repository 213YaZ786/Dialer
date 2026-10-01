package com.dialer.app.feature.recents

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dialer.app.core.calllog.CallKind
import com.dialer.app.feature.call.AnswerGreen
import com.dialer.app.ui.icon.DialerIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** How a day reads above its calls: Today, Yesterday, Monday, 12 September. */
fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
    else -> day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
}

/** The time of day, in 24 hours or with AM and PM as the phone is set. */
fun timeLabel(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

/** 45 s, 3 min 20 s, 1 h 05 min. */
fun durationLabel(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> if (seconds % 60 == 0L) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"
    else -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
}

fun kindLabel(kind: CallKind): String = when (kind) {
    CallKind.INCOMING -> "Incoming call"
    CallKind.OUTGOING -> "Outgoing call"
    CallKind.MISSED -> "Missed call"
    CallKind.REJECTED -> "Declined call"
    CallKind.BLOCKED -> "Blocked call"
    CallKind.VOICEMAIL -> "Voicemail"
}

fun kindIcon(kind: CallKind): ImageVector = when (kind) {
    CallKind.INCOMING -> DialerIcons.ArrowIn
    CallKind.OUTGOING -> DialerIcons.ArrowOut
    CallKind.MISSED -> DialerIcons.Missed
    CallKind.REJECTED -> DialerIcons.CallEnd
    CallKind.BLOCKED -> DialerIcons.Block
    CallKind.VOICEMAIL -> DialerIcons.Voicemail
}

/** The short word under a name: what the last call of the line was. */
fun kindWord(kind: CallKind): String = when (kind) {
    CallKind.INCOMING -> "Incoming"
    CallKind.OUTGOING -> "Outgoing"
    CallKind.MISSED -> "Missed"
    CallKind.REJECTED -> "Declined"
    CallKind.BLOCKED -> "Blocked"
    CallKind.VOICEMAIL -> "Voicemail"
}

/**
 * Each kind of call its own colour, read before the words: missed in red,
 * answered in green, placed in the accent, turned away in grey.
 */
@Composable
fun kindTint(kind: CallKind): Color = when (kind) {
    CallKind.MISSED -> MaterialTheme.colorScheme.error
    CallKind.INCOMING -> AnswerGreen
    CallKind.OUTGOING -> MaterialTheme.colorScheme.primary
    CallKind.VOICEMAIL -> MaterialTheme.colorScheme.tertiary
    CallKind.REJECTED, CallKind.BLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
}
