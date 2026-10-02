package com.dialer.app.feature.recents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialer.app.core.call.Dialing
import com.dialer.app.core.calllog.CallBack
import com.dialer.app.core.calllog.CallKind
import com.dialer.app.core.dial.DialRequests
import com.dialer.app.core.dial.Numbers
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.ui.component.ContactAvatar
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import org.koin.compose.koinInject

/**
 * The people still waiting for a call back, side by side above the
 * calls: a tap calls them back, a long press opens their calls.
 */
@Composable
fun CallBackStrip(waiting: List<CallBack>, index: PhoneIndex, width: Dp, onOpen: (String) -> Unit) {
    Column(Modifier.widthIn(max = width).fillMaxWidth()) {
        Text(
            "To call back",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(waiting, key = { it.last.id }) { waiting ->
                Waiting(waiting, index, onOpen)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Waiting(waiting: CallBack, index: PhoneIndex, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dial: DialRequests = koinInject()
    val call = waiting.last
    val contact = index.find(call.key)
    val name = contact?.name ?: call.cachedName ?: Numbers.format(context, call.number)
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    val shape = RoundedCornerShape(24.dp)
    ZoneSurface(
        shape = shape,
        modifier = Modifier
            .width(124.dp)
            .graphicsLayer {
                scaleX = sink
                scaleY = sink
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClickLabel = "Call back",
                onLongClickLabel = "Calls",
                onClick = {
                    haptics.firm()
                    if (!Dialing.call(context, call.number)) dial.open(call.number)
                },
                onLongClick = {
                    haptics.tick()
                    onOpen(call.number)
                }
            )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
            Box {
                ContactAvatar(contact?.name ?: call.cachedName, contact?.photo, 56.dp)
                KindBadge(CallKind.MISSED, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp), unseen = call.isNew)
            }
            Spacer(Modifier.height(10.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(
                agoLabel(call.date) + if (waiting.missed > 1) " · ×${waiting.missed}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
    }
}

/** Now, 12 min ago, 3 h ago, Yesterday, Monday. */
fun agoLabel(millis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - millis) / 60_000
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "$minutes min ago"
        minutes < 12 * 60 -> "${minutes / 60} h ago"
        else -> dayLabel(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }
}
