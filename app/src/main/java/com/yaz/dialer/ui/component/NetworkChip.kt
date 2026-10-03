package com.yaz.dialer.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yaz.dialer.core.network.CellProtection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.network.Protection
import com.yaz.dialer.core.network.Protocol

/** Each level of protection its colour: green protected, neutral standard, amber old, red not protected. */
@Composable
fun protectionColor(protection: Protection): Color = when (protection) {
    Protection.PROTECTED -> Color(0xFF1E8E3E)
    Protection.STANDARD -> MaterialTheme.colorScheme.primary
    Protection.OLD -> Color(0xFFE37400)
    Protection.UNPROTECTED -> MaterialTheme.colorScheme.error
    Protection.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The network a call goes over, as a small pill of glass: "4G", "5G",
 * "Wi-Fi", with HD when the voice is in high definition. On an old or
 * unprotected network it says so in its colour, so it is seen before
 * anything sensitive is said.
 */
@Composable
fun NetworkChip(protocol: Protocol, protection: Protection, modifier: Modifier = Modifier, hd: Boolean = false) {
    val color = protectionColor(protection)
    val haptics = rememberHaptics()
    var explain by remember { mutableStateOf(false) }
    val onClick = {
        haptics.tick()
        explain = true
    }
    if (explain) NetworkDialog(protocol, protection, onDismiss = { explain = false })
    val words = buildList {
        add(if (protocol == Protocol.WIFI) "Wi-Fi calling" else protocol.label)
        if (hd) add("HD")
        if (protection == Protection.OLD || protection == Protection.UNPROTECTED) add(protection.label)
    }.joinToString(" · ")
    ZoneSurface(shape = CircleShape, onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            AnimatedContent(targetState = words, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "network") { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (protection == Protection.UNPROTECTED || protection == Protection.OLD) color else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** What the network means for a call, and the way to keep the phone off 2G. */
@Composable
private fun NetworkDialog(protocol: Protocol, protection: Protection, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Box(Modifier.size(14.dp).background(protectionColor(protection), CircleShape)) },
        title = { Text("${if (protocol == Protocol.WIFI) "Wi-Fi calling" else protocol.label}: ${protection.label}") },
        text = { Text(CellProtection.meaning(protection)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                for (action in listOf("android.settings.CELLULAR_NETWORK_SECURITY", Settings.ACTION_NETWORK_OPERATOR_SETTINGS)) {
                    if (runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
                }
            }) { Text("Turn off 2G") }
        }
    )
}
