package com.yaz.dialer.feature.recents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.yaz.dialer.ui.component.ZoneSurface

/** A card is at least this wide: its face column, a bubble of words and the call button (SMS's rule). */
internal val CARD_MIN_WIDTH = 280.dp

/** Every card is this tall at least, so the cards of a grid row stand even. */
internal val CARD_HEIGHT = 104.dp

/** The face of a person card, and the column it stands in with the name under it. */
internal val CARD_FACE_COLUMN = 80.dp

/**
 * The bubble's corners: a small radius on the face's side, as SMS's card bubble (CARD_BUBBLE in
 * https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/conversations/ConversationsScreen.kt,
 * the thread's Clusters.SMALL_RADIUS = 4 dp).
 */
private val CALL_BUBBLE = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)

/**
 * The group's last call as a bubble of glass beside the face: the kind's glyph (KindBadge: missed red, incoming
 * green, outgoing in the accent, blocked grey), its word with "×3" when the group holds several calls, the
 * duration when it was answered, the time inside the bubble at its end. A glyph and words for each kind of event,
 * as Signal's conversation list item (getThreadDisplayBody in
 * https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/conversationlist/ConversationListItem.java);
 * the time in the bubble and the bubble itself from SMS's LastMessageBubble (ConversationsScreen.kt). A blocked
 * call's reason follows "Blocked" in the secondary colour, never red. Not clickable: the card is the tap target.
 */
@Composable
internal fun LastCallBubble(group: CallGroup, why: String?, allowed: Boolean, time: String, modifier: Modifier = Modifier) {
    val call = group.first
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val blocked = call.kind == CallKind.BLOCKED
    val words = buildAnnotatedString {
        // A blocked call reads "Blocked · sales call" all in the secondary colour.
        withStyle(SpanStyle(color = if (blocked) secondary else kindTint(call.kind))) {
            append(kindWord(call.kind))
            if (blocked && why != null) append(" · $why")
            if (allowed) append(" (allowed)")
        }
        if (group.count > 1) withStyle(SpanStyle(color = secondary)) { append(" ×${group.count}") }
    }
    val answered = (call.kind == CallKind.INCOMING || call.kind == CallKind.OUTGOING) && call.duration > 0
    val duration = if (answered) durationLabel(call.duration) else null
    // No bubble inside the card (the user, 2026-10-28, variant A): the words sit straight in the card's capsule,
    // as Google's and Apple's recents rows; only the card is a zone, the tap target.
    androidx.compose.foundation.layout.Box(modifier) {
        // The time rides the last line of the words when it fits there, else goes under them: SMS's WithFooter
        // on its card bubble (LastMessageBubble), the glyph's room (8 + 22 + 6 dp) counted as the content's start.
        WithFooter(
            footer = {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, bottom = 1.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            words = words.text + duration.orEmpty()
        ) { onLayout ->
            // AOSP's second line: a small call type icon, then the words (new_call_log_entry.xml call_type_icon).
            val measure = measureLastLine(onLayout, extraStart = 22.dp)
            val unmeasured: (androidx.compose.ui.text.TextLayoutResult) -> Unit = remember { {} }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                KindBadge(call.kind, size = 18.dp, unseen = group.unseen)
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        words,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                        onTextLayout = if (duration == null) measure else unmeasured
                    )
                    if (duration != null) {
                        Text(duration, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, onTextLayout = measure)
                    }
                }
            }
        }
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
