package com.dialer.app.feature.settings

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.dialer.app.data.settings.AnnounceMode
import com.dialer.app.core.system.AdvancedProtection
import com.dialer.app.core.network.CellProtection
import com.dialer.app.core.network.CellWatch
import com.dialer.app.core.network.Protocol
import com.dialer.app.ui.component.protectionColor
import org.koin.compose.koinInject
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialer.app.BuildConfig
import com.dialer.app.core.update.UpdateMode
import com.dialer.app.core.update.Updates
import com.dialer.app.data.settings.ThemeMode
import com.dialer.app.navigation.LocalReadableInset
import com.dialer.app.ui.component.BannerAction
import com.dialer.app.ui.component.ScreenBanner
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.icon.DialerIcons
import com.dialer.app.ui.theme.TEXT_SCALES
import com.dialer.app.ui.theme.textScaleLabel
import org.koin.androidx.compose.koinViewModel

private enum class OpenDialog { NONE, THEME, TEXT_SIZE, UPDATES, ANNOUNCE }

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }

    // Scrolls at full width, rows pushed in by the readable inset, so the
    // margins of a tablet scroll like the rest. See ReadableScroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalReadableInset.current)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        ScreenBanner(
            title = "Settings",
            leading = { BannerAction(icon = DialerIcons.ArrowBack, label = "Back", onClick = onBack) }
        )

        Section("Calls") {
            SwitchRow(
                title = "Silence unknown callers",
                summary = "Numbers not in your contacts ring without a sound. The call still shows and you can answer.",
                checked = settings.silenceUnknown,
                onChange = viewModel::setSilenceUnknown
            )
            // Android's Advanced Protection decides these two when it is on.
            val strict = remember { AdvancedProtection.isOn(context) }
            val byAndroid = "On while Android's Advanced Protection is on."
            SwitchRow(
                title = "Block faked numbers",
                summary = if (strict) byAndroid else "Calls your carrier finds are not really from the number shown are turned away. They still show in Recents.",
                checked = settings.blockSpoofed || strict,
                enabled = !strict,
                onChange = viewModel::setBlockSpoofed
            )
            SwitchRow(
                title = "Block hidden numbers",
                summary = if (strict) byAndroid else "Calls that hide their number are turned away. They still show in Recents.",
                checked = settings.blockHidden || strict,
                enabled = !strict,
                onChange = viewModel::setBlockHidden
            )
            SwitchRow(
                title = "Flip to silence",
                summary = "Turn the phone face down to stop the ringing.",
                checked = settings.flipToSilence,
                onChange = viewModel::setFlipToSilence
            )
            SettingRow(
                title = "Announce the caller",
                summary = announceLabel(settings.announce),
                onClick = { dialog = OpenDialog.ANNOUNCE }
            )
            SwitchRow(
                title = "Vibrate when answered",
                summary = "One buzz when the person you call picks up.",
                checked = settings.vibrateOnAnswer,
                onChange = viewModel::setVibrateOnAnswer
            )
            SettingRow(
                title = "Blocked numbers",
                summary = "The list Android keeps for every app",
                onClick = { open(context, context.getSystemService(TelecomManager::class.java).createManageBlockedNumbersIntent()) }
            )
        }

        Section("Network security") {
            val cell: CellWatch = koinInject()
            val sims by cell.sims.collectAsState()
            if (sims.isEmpty()) {
                SettingRow(title = "No SIM", summary = "Insert a SIM to see how its network protects your calls.", onClick = null)
            }
            sims.forEach { sim ->
                val protection = sim.protection
                SettingRow(
                    title = "${sim.name}: ${protection.label}",
                    summary = buildString {
                        append("Calls on ${sim.voice.label}")
                        if (sim.data != Protocol.NONE && sim.data != sim.voice) append(", data on ${sim.data.label}")
                        append(". ")
                        append(CellProtection.meaning(protection))
                    },
                    onClick = null,
                    trailing = { Box(Modifier.size(12.dp).background(protectionColor(protection), CircleShape)) }
                )
            }
            SettingRow(
                title = "Turn off 2G",
                summary = "In the SIM's settings, \"Allow 2G\". Keeps fake antennas from forcing your phone onto it.",
                onClick = { openFirst(context, "android.settings.CELLULAR_NETWORK_SECURITY", android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS, android.provider.Settings.ACTION_WIRELESS_SETTINGS) }
            )
        }

        Section("Phone") {
            SettingRow(
                title = "Call forwarding, call waiting, caller ID",
                summary = "Your carrier's settings",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) }
            )
            SettingRow(
                title = "SIMs and calling accounts",
                summary = "Which SIM calls, Wi-Fi calling",
                onClick = { open(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
            )
            SettingRow(
                title = "Voicemail",
                summary = "Number and notifications",
                onClick = { open(context, Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)) }
            )
            SettingRow(
                title = "Ringtone and vibration",
                summary = "Android's sound settings",
                onClick = { open(context, Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
            )
            SettingRow(
                title = "Accessibility",
                summary = "Hearing aids, real-time text",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)) }
            )
        }

        Section("Appearance") {
            SettingRow(
                title = "Theme",
                summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            )
            SwitchRow(
                title = "Pure black",
                summary = "Deeper blacks in dark mode. Easier on the battery with OLED screens.",
                checked = settings.pureBlack,
                onChange = viewModel::setPureBlack
            )
            SwitchRow(
                title = "Hide in recent apps",
                summary = "Dialer's picture in the recent apps stays blank, so your calls and contacts are not seen there.",
                checked = settings.hideInRecents,
                onChange = viewModel::setHideInRecents
            )
            SwitchRow(
                title = "Glass effects",
                summary = "Zones and floating buttons in liquid glass, over a soft light in your wallpaper's colours.",
                checked = settings.glass,
                onChange = viewModel::setGlass
            )
            SettingRow(
                title = "Text size",
                summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                onClick = { dialog = OpenDialog.TEXT_SIZE }
            )
        }

        Section("About") {
            SettingRow(
                title = "Updates",
                summary = updatesLabel(settings.updates),
                onClick = { dialog = OpenDialog.UPDATES }
            )
            SettingRow(
                title = "Dialer ${BuildConfig.VERSION_NAME}",
                summary = "A phone app with no account, no tracking and no ads.",
                onClick = null
            )
            SettingRow(
                title = "Source code",
                summary = "github.com/213YaZ786/Dialer",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/Dialer") }
            )
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    when (dialog) {
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = "Text size",
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onSelect = viewModel::setTextScale,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = settings.updates,
            onSelect = { mode ->
                viewModel.setUpdates(mode)
                // Installing needs Android's leave, asked when chosen.
                if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
            },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.ANNOUNCE -> ChoiceDialog(
            title = "Announce the caller",
            options = AnnounceMode.entries.map { it to announceLabel(it) },
            selected = settings.announce,
            onSelect = viewModel::setAnnounce,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.NONE -> Unit
    }
}

/** A titled group of rows on one rounded zone. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    // Centred and at title size: a heading names what the zone below holds.
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp)
    )
    ZoneSurface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    onClick: (() -> Unit)?,
    quiet: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val haptics = rememberHaptics()
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        supportingContent = summary?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // Every row answers with a tick; a switch row answers with the
        // switch's own feel instead, so it is quiet here.
        modifier = if (onClick != null) {
            Modifier.clickable {
                if (!quiet) haptics.tick()
                onClick()
            }
        } else {
            Modifier
        }
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val change = { on: Boolean ->
        haptics.toggle(on)
        onChange(on)
    }
    SettingRow(
        title = title,
        summary = summary,
        quiet = true,
        onClick = if (enabled) ({ change(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = change, enabled = enabled) }
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun announceLabel(mode: AnnounceMode): String = when (mode) {
    AnnounceMode.OFF -> "Off"
    AnnounceMode.HEADPHONES -> "With headphones only"
    AnnounceMode.ALWAYS -> "Always"
}

/** The first of these Android screens the phone has: newer ones first, the general one last. */
private fun openFirst(context: Context, vararg actions: String) {
    for (action in actions) {
        if (runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/** An Android settings screen; some phones leave one out, then nothing happens. */
private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install automatically"
}
