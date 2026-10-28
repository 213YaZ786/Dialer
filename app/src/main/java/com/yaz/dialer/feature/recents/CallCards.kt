package com.yaz.dialer.feature.recents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.calllog.CallGroup
import com.yaz.dialer.core.calllog.CallKind

/** A card is at least this wide: its face column, a bubble of words and the call button (SMS's rule). */
internal val CARD_MIN_WIDTH = 280.dp

/** Every card is this tall at least, so the cards of a grid row stand even. */
internal val CARD_HEIGHT = 104.dp

/** The face of a person card, and the column it stands in with the name under it. */
internal val CARD_FACE_COLUMN = 80.dp

/**
 * The card's second line: the kind's glyph (KindBadge: missed red, incoming green, outgoing in the accent,
 * blocked grey), then the words: the kind's word, "×3" when the group holds several calls, " · reason" for a
 * blocked call, " (allowed)", " · 3 min 20 s" when it was answered. The time is not here: it ends the name's line
 * (CallLine). AOSP Dialer's secondary line joins type and reason in one text under the name
 * (CallLogEntryText.java,
 * https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java),
 * with a small call type icon before it (new_call_log_entry.xml call_type_icon); a glyph and words for each kind
 * of event as Signal's conversation list item (getThreadDisplayBody in
 * https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/conversationlist/ConversationListItem.java).
 * Two lines at most, then an ellipsis; no fixed height, so the largest font only makes it taller. A blocked call's
 * reason is in the secondary colour, never red. Not clickable: the card is the tap target.
 */
@Composable
internal fun LastCallBubble(group: CallGroup, why: String?, allowed: Boolean, modifier: Modifier = Modifier) {
    val call = group.first
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val blocked = call.kind == CallKind.BLOCKED
    val answered = (call.kind == CallKind.INCOMING || call.kind == CallKind.OUTGOING) && call.duration > 0
    val words = buildAnnotatedString {
        withStyle(SpanStyle(color = if (blocked) secondary else kindTint(call.kind))) { append(kindWord(call.kind)) }
        withStyle(SpanStyle(color = secondary)) {
            if (group.count > 1) append(" ${countLabel(group.count)}")
            if (blocked && why != null) append(" · $why")
            if (allowed) append(" (allowed)")
            if (answered) append(" · ${durationLabel(call.duration)}")
        }
    }
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        KindBadge(call.kind, size = 18.dp, unseen = group.unseen)
        Spacer(Modifier.width(4.dp))
        Text(
            words,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A small mark on a face's bottom corner (the lock of an encrypted call): a round of the card's colour so the
 * mark reads over a photo, the icon in the accent. After SMS's FaceMark (ConversationsScreen.kt).
 */
@Composable
internal fun CallFaceMark(icon: ImageVector, description: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(18.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
    }
}

/**
 * The cards of a day in rows of equal cells, the last row that is not full centred: SMS's CardRow
 * (ConversationsScreen.kt), after its SettingsScreen TileGrid.
 */
@Composable
internal fun CardRow(cards: List<CallGroup>, cell: Dp, gap: Dp, card: @Composable (CallGroup, Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
        cards.forEach { group -> key(group.first.id) { card(group, Modifier.width(cell)) } }
    }
}
