package com.yaz.dialer.feature.recents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yaz.dialer.data.calllog.CallHistory
import com.yaz.dialer.ui.component.FloatingPane
import com.yaz.dialer.ui.component.QuietButton
import com.yaz.dialer.ui.component.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calls deleted from Recents or a number's page wait a few seconds out of sight before the call log
 * is touched, so "Undo" only shows them again (Google Messages and Gmail do the same: the delete is
 * committed when the bar goes). Source: SMS's undo bar and its pending state
 * (https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/conversations/ConversationsScreen.kt,
 * `swiped` and the AnimatedVisibility "Moved to Trash" / "Undo"), with the Material Snackbar timing of
 * a few seconds (https://m3.material.io/components/snackbar/guidelines); the delete itself is
 * [CallHistory.delete] (ContentResolver.delete on CallLog.Calls by `_ID`,
 * https://developer.android.com/reference/android/provider/CallLog.Calls).
 */
@Stable
class UndoDelete(private val history: CallHistory, private val scope: CoroutineScope) {
    /** Ids of the calls out of the list: waiting to be deleted, or just deleted until the log is read again. */
    var hidden by mutableStateOf(emptySet<Long>())
        private set

    /** The calls the bar can bring back; null when the bar is down. */
    var waiting by mutableStateOf<List<Long>?>(null)
        private set

    /** What the bar says; kept while the bar slides away. */
    var label by mutableStateOf("")
        private set

    private var timer: Job? = null

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        commit()
        hidden = hidden + ids
        waiting = ids.toList()
        label = if (ids.size == 1) "Call deleted" else "${ids.size} calls deleted"
        timer = scope.launch {
            delay(UNDO_MS)
            commit()
        }
    }

    fun undo() {
        timer?.cancel()
        val ids = waiting ?: return
        waiting = null
        hidden = hidden - ids.toSet()
    }

    /** Deletes for good what waits (a second delete, the bar's time, the screen left). */
    fun commit() {
        timer?.cancel()
        val ids = waiting ?: return
        waiting = null
        history.delete(ids)
        // The log is read again by its observer; should the delete have been refused, the calls come back.
        scope.launch {
            delay(1500)
            hidden = hidden - ids.toSet()
        }
    }

    private companion object {
        const val UNDO_MS = 5000L
    }
}

@Composable
fun rememberUndoDelete(history: CallHistory): UndoDelete {
    val scope = rememberCoroutineScope()
    val undo = remember(history) { UndoDelete(history, scope) }
    DisposableEffect(undo) { onDispose { undo.commit() } }
    return undo
}

/**
 * The bar itself, a pane of glass with the text and "Undo", at the bottom of its box. Source: SMS's
 * ConversationsScreen "Moved to Trash" bar (URL above the class).
 */
@Composable
fun BoxScope.UndoBar(undo: UndoDelete, bottom: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    AnimatedVisibility(
        visible = undo.waiting != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.align(Alignment.BottomCenter).padding(bottom = bottom)
    ) {
        FloatingPane(shape = CircleShape) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                Text(undo.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                QuietButton(onClick = {
                    haptics.done()
                    undo.undo()
                }) { Text("Undo") }
            }
        }
    }
}
