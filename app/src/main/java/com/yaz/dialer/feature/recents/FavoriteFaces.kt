package com.yaz.dialer.feature.recents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.call.Dialing
import com.yaz.dialer.core.dial.DialRequests
import com.yaz.dialer.core.dial.NumberActions
import com.yaz.dialer.core.dial.Person
import com.yaz.dialer.feature.call.AnswerGreen
import com.yaz.dialer.ui.component.ContactAvatar
import com.yaz.dialer.ui.component.PillItem
import com.yaz.dialer.ui.component.PillMenu
import com.yaz.dialer.ui.component.PillMotion
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.component.rememberPillMenu
import com.yaz.dialer.ui.icon.AppIcons
import org.koin.compose.koinInject

/** At most nine favourites on top, as iOS Messages keeps nine pinned faces (SMS's PINNED_FACES_MAX). */
internal const val FAVORITE_FACES_MAX = 9

/**
 * The starred contacts as round faces wrapping over the top of the list, the name under each on one line: SMS's
 * PinnedFaces (feature/conversations/HomeParts.kt, a FlowRow with maxItemsInEachRow from Jetpack Compose's
 * foundation-layout FlowLayout.kt, three to a row on a phone and more as the window widens, never scrolled
 * sideways). [missed] holds, per contact id, the missed calls not yet seen, counted in red on the face (SMS's
 * unread count badge). A tap opens the number's page, a long press offers the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriteFaces(faces: List<Person>, missed: Map<Long, Int>, modifier: Modifier, onOpen: (Person) -> Unit) {
    BoxWithConstraints(modifier) {
        val gap = 8.dp
        val columns = ((maxWidth + gap) / (104.dp + gap)).toInt().coerceIn(3, FAVORITE_FACES_MAX)
        val cell = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
            maxItemsInEachRow = columns,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
        ) {
            faces.take(FAVORITE_FACES_MAX).forEach { person ->
                key(person.id) { FavoriteFace(person, missed[person.id] ?: 0, Modifier.width(cell)) { onOpen(person) } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteFace(person: Person, missed: Int, modifier: Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val menu = rememberPillMenu()
    var confirmBlock by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    fun call() {
        haptics.firm()
        if (!Dialing.call(context, person.number)) dial.open(person.number)
    }
    Box(modifier.then(menu.tracker)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = sink
                    scaleY = sink
                }
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    interactionSource = press,
                    indication = null,
                    onClickLabel = "Calls",
                    onLongClickLabel = "More",
                    onClick = {
                        haptics.tick()
                        onOpen()
                    },
                    onLongClick = menu::open
                )
        ) {
            Box {
                ContactAvatar(person.name, person.photo, 64.dp, look = person.look)
                if (missed > 0) {
                    // The unread badge of SMS's pinned faces, in the error colour; TalkBack reads "2 missed".
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .clearAndSetSemantics { contentDescription = "$missed missed" }
                    ) {
                        Text(
                            if (missed > 9) "9+" else missed.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
            Text(
                person.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // The rows' menu (RecentsScreen's CallLine), for a saved contact: no add, no history to delete.
        PillMenu(
            menu,
            buildList {
                add(PillItem(AppIcons.Call, "Call", PillMotion.BOUNCE, AnswerGreen) { call() })
                add(PillItem(AppIcons.Message, "Send a message", PillMotion.WIGGLE) { NumberActions.message(context, person.number) })
                add(PillItem(AppIcons.Copy, "Copy number", PillMotion.BOUNCE) { NumberActions.copy(context, person.number) })
                if (NumberActions.canBlock(context)) {
                    add(PillItem(AppIcons.Block, "Block", PillMotion.WIGGLE, danger = true) { confirmBlock = true })
                }
            }
        )
    }
    if (confirmBlock) {
        BlockDialog(person.name, onDismiss = { confirmBlock = false }) {
            confirmBlock = false
            NumberActions.block(context, person.number)
        }
    }
}
