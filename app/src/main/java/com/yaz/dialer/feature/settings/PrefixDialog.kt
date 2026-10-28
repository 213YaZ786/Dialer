package com.yaz.dialer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.call.PrefixRules
import com.yaz.dialer.core.dial.SalesCalls
import com.yaz.dialer.data.settings.BlockedPrefix
import com.yaz.dialer.data.settings.PrefixAction
import com.yaz.dialer.feature.dialpad.DialFormat
import com.yaz.dialer.ui.component.BoldButton
import com.yaz.dialer.ui.component.GlassRadio
import com.yaz.dialer.ui.component.QuietButton
import com.yaz.dialer.ui.component.ZoneAlertDialog
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.rememberHaptics

/**
 * The start of a number to block or silence: a small field the user shortens or types, what it covers
 * in international form under it, and the choice Block (rejected before it rings, as the sales calls)
 * or Silence (rings without sound). The field takes the keyboard at once with Compose's FocusRequester
 * recipe (https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior),
 * as SearchPill does; the choice rows are the app's ChoiceDialog rows.
 */
@Composable
fun PrefixDialog(
    initial: String,
    region: String?,
    onDismiss: () -> Unit,
    onSave: (BlockedPrefix) -> Unit
) {
    val haptics = rememberHaptics()
    var typed by remember { mutableStateOf(initial) }
    var action by remember { mutableStateOf(PrefixAction.BLOCK) }
    val prefix = remember(typed, region) { PrefixRules.normalize(typed, region) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block numbers starting like this") },
        text = {
            Column {
                ZoneSurface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = typed,
                        onValueChange = { v ->
                            typed = v.filterIndexed { i, c -> c.isDigit() || c == ' ' || (c == '+' && i == 0) }.take(24)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).focusRequester(focus),
                        decorationBox = { field ->
                            if (typed.isEmpty()) Text("Start of the number", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    )
                }
                Text(
                    when {
                        prefix != null -> "Covers " + DialFormat.asYouType(prefix, region.orEmpty().ifEmpty { "ZZ" }) + "…"
                        typed.isBlank() -> "Shorten it to keep the numbers you want to stop."
                        else -> "Needs at least ${PrefixRules.MIN_DIGITS} digits after the country code."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (prefix == null && typed.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                ActionRow("Block", "Rejected before it rings.", action == PrefixAction.BLOCK) { action = PrefixAction.BLOCK }
                ActionRow("Silence", "Rings without sound.", action == PrefixAction.SILENCE) { action = PrefixAction.SILENCE }
                Text(
                    "Numbers saved in your contacts always ring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
        },
        confirmButton = {
            BoldButton(
                filled = true,
                enabled = prefix != null,
                onClick = {
                    if (prefix != null) {
                        haptics.firm()
                        onSave(BlockedPrefix(prefix, typed.trim().replace(Regex("\\s+"), " "), action))
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActionRow(title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        GlassRadio(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The country a typed national prefix is read in: the network's, else the SIM's, else the language's. */
@Composable
fun rememberPrefixRegion(): String? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { SalesCalls.country(context)?.uppercase() }
}
