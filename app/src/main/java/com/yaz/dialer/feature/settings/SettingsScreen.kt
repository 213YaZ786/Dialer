package com.yaz.dialer.feature.settings

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaz.dialer.BuildConfig
import com.yaz.dialer.core.dial.SalesCalls
import com.yaz.dialer.core.network.CellProtection
import com.yaz.dialer.core.network.CellWatch
import com.yaz.dialer.core.network.Protocol
import com.yaz.dialer.core.system.AdvancedProtection
import com.yaz.dialer.core.update.UpdateMode
import com.yaz.dialer.core.update.Updates
import com.yaz.dialer.data.settings.AnnounceMode
import com.yaz.dialer.data.settings.PrefixAction
import com.yaz.dialer.data.settings.ThemeMode
import com.yaz.dialer.feature.main.Setup
import com.yaz.dialer.feature.main.SetupStep
import com.yaz.dialer.feature.main.rememberSetup
import com.yaz.dialer.navigation.LocalReadableInset
import com.yaz.dialer.navigation.WidthClass
import com.yaz.dialer.ui.component.BoldButton
import com.yaz.dialer.ui.component.FloatingAction
import com.yaz.dialer.ui.component.FloatingFrame
import com.yaz.dialer.ui.component.FloatingTop
import com.yaz.dialer.ui.component.ChoiceDialog
import com.yaz.dialer.ui.component.GlassSwitch
import com.yaz.dialer.ui.component.LargeTitle
import com.yaz.dialer.ui.component.QuietButton
import com.yaz.dialer.ui.component.ZoneAlertDialog
import com.yaz.dialer.ui.component.ZoneSurface
import com.yaz.dialer.ui.component.protectionColor
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons
import com.yaz.dialer.ui.theme.TEXT_SCALES
import com.yaz.dialer.ui.theme.textScaleLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class OpenDialog { NONE, THEME, TEXT_SIZE, UPDATES, ANNOUNCE, NOT_SALES, PREFIXES, ADD_PREFIX }

/**
 * The groups of settings: folded one under another on a phone, a list and the open one's page on a wide window.
 * Ported from SMS's SettingsSection (SettingsScreen.kt in /home/yghouar/Claude Code/APK/YAZ/SMS, feature/settings).
 */
private enum class SettingsSection(val title: String, val icon: ImageVector) {
    CALLS("Calls", AppIcons.Call),
    NETWORK("Network security", AppIcons.Lock),
    PHONE("Phone", AppIcons.Smartphone),
    APPEARANCE("Appearance", AppIcons.Palette),
    ABOUT("About", AppIcons.Info)
}

/** What a tile of Settings shows: an icon, a short name, and under it its state or value (Quick Settings' tile). */
private sealed interface SettingsTile {
    val id: String
    val name: String
    val title: String
    val icon: ImageVector
    val summary: String
}

/**
 * A setting that is only a switch, shown as a tile (SMS's SwitchTile). [name] is the short name on the tile,
 * [title] the full one (TalkBack, the dialog), [summary] the explanation the long press shows. A tile that
 * [enabled] is false depends on something else (Android's Advanced Protection): it shows, dimmed, and does
 * not toggle.
 */
private class SwitchTile(
    override val id: String,
    override val name: String,
    override val title: String,
    override val icon: ImageVector,
    override val summary: String,
    val checked: Boolean,
    val enabled: Boolean = true,
    val onChange: (Boolean) -> Unit
) : SettingsTile

/**
 * A setting that opens a choice, a page or a link, shown as a tile (SMS's ActionTile): [value] is its current
 * value, the second line (Quick Settings' secondary label, AOSP SystemUI QSTileViewImpl); [summary] the full
 * text a long press shows. Without [onClick] (a SIM's state, the version) a tap shows the summary too.
 * [iconTint] colours the icon (a SIM's protection level).
 */
private class ActionTile(
    override val id: String,
    override val name: String,
    override val title: String,
    override val icon: ImageVector,
    val value: String,
    override val summary: String,
    val iconTint: Color? = null,
    val onClick: (() -> Unit)? = null
) : SettingsTile

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val prefixes by viewModel.prefixes.collectAsState()
    val setup = rememberSetup()
    val region = rememberPrefixRegion()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    // The tile whose long press is open (its id), "" when none.
    var tileId by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(SettingsSection.CALLS) }
    // On a phone every section starts folded; the one open stays open across a turn of the screen.
    var openSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    // The page's scroll, held here so a drag on the header drives it (FloatingFrame's `scroll`): the folded
    // list on a phone, the chosen section's page on a wide window (it starts at the top again for each section).
    val compactScroll = rememberScrollState()
    val detailScroll = rememberSaveable(section, saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }

    val cell: CellWatch = koinInject()
    val sims by cell.sims.collectAsState()
    // Android's Advanced Protection decides two of the switches when it is on.
    val strict = remember { AdvancedProtection.isOn(context) }
    val byAndroid = "On while Android's Advanced Protection is on."
    // Only where the country keeps ranges for sales calls.
    val country = remember { SalesCalls.country(context) }
    val salesHere = country in SalesCalls.countries

    val tiles: Map<SettingsSection, List<SwitchTile>> = mapOf(
        SettingsSection.CALLS to listOfNotNull(
            SwitchTile(
                id = "silence", name = "Silence unknown", title = "Silence unknown callers", icon = AppIcons.VolumeOff,
                summary = "Numbers not in your contacts ring without sound.",
                checked = settings.silenceUnknown, onChange = viewModel::setSilenceUnknown
            ),
            SwitchTile(
                id = "spoofed", name = "Faked numbers", title = "Block faked numbers", icon = AppIcons.Report,
                summary = if (strict) byAndroid else "Calls your carrier finds faked are rejected.",
                checked = settings.blockSpoofed || strict, enabled = !strict, onChange = viewModel::setBlockSpoofed
            ),
            SwitchTile(
                id = "hidden", name = "Hidden numbers", title = "Block hidden numbers", icon = AppIcons.VisibilityOff,
                summary = if (strict) byAndroid else "Calls without a number are rejected.",
                checked = settings.blockHidden || strict, enabled = !strict, onChange = viewModel::setBlockHidden
            ),
            if (salesHere) SwitchTile(
                id = "sales", name = "Sales calls", title = "Block sales calls", icon = AppIcons.NotificationsOff,
                summary = "Numbers reserved for cold calls are rejected, unless saved in your contacts.",
                checked = settings.blockSalesCalls, onChange = viewModel::setBlockSalesCalls
            ) else null,
            SwitchTile(
                id = "flip", name = "Flip to silence", title = "Flip to silence", icon = AppIcons.Smartphone,
                summary = "Turn the phone face down to stop the ringing.",
                checked = settings.flipToSilence, onChange = viewModel::setFlipToSilence
            ),
            SwitchTile(
                id = "vibrate", name = "Vibrate on answer", title = "Vibrate when answered", icon = AppIcons.Vibration,
                summary = "One buzz when the other person answers.",
                checked = settings.vibrateOnAnswer, onChange = viewModel::setVibrateOnAnswer
            )
        ),
        SettingsSection.APPEARANCE to listOf(
            SwitchTile(
                id = "black", name = "Pure black", title = "Pure black", icon = AppIcons.Night,
                summary = "Black background in dark mode.",
                checked = settings.pureBlack, onChange = viewModel::setPureBlack
            ),
            SwitchTile(
                id = "recents", name = "Hide in recents", title = "Hide in recent apps", icon = AppIcons.VisibilityOff,
                summary = "Dialer's preview stays blank in recent apps.",
                checked = settings.hideInRecents, onChange = viewModel::setHideInRecents
            ),
            SwitchTile(
                id = "glass", name = "Glass effects", title = "Glass effects", icon = AppIcons.Sparkles,
                summary = "Buttons and panes in liquid glass.",
                checked = settings.glass, onChange = viewModel::setGlass
            )
        )
    )

    // What opens a choice, a page or a link: tiles of the same grid, the value as the second line.
    val actions: Map<SettingsSection, List<ActionTile>> = mapOf(
        SettingsSection.CALLS to listOfNotNull(
            // Numbers said to be no sales call (from Recents): kept here, never in Android's blocked list.
            if (settings.salesAllowed.isNotEmpty()) {
                val count = settings.salesAllowed.size
                ActionTile(
                    id = "notSales", name = "Not sales calls", title = "Not sales calls", icon = AppIcons.CheckCircle,
                    value = "$count number" + (if (count > 1) "s" else ""),
                    summary = "$count number" + (if (count > 1) "s" else "") + " always ring.",
                    onClick = { dialog = OpenDialog.NOT_SALES }
                )
            } else null,
            // Starts of numbers the user blocks or silences (added from a number's page or here).
            ActionTile(
                id = "prefixes", name = "Blocked prefixes", title = "Blocked prefixes", icon = AppIcons.Dialpad,
                value = if (prefixes.isEmpty()) "None" else "${prefixes.size} prefix" + (if (prefixes.size > 1) "es" else ""),
                summary = "The start of numbers you block or silence",
                onClick = { dialog = OpenDialog.PREFIXES }
            ),
            ActionTile(
                id = "announce", name = "Announce caller", title = "Announce the caller", icon = AppIcons.Speaker,
                value = announceLabel(settings.announce), summary = announceLabel(settings.announce),
                onClick = { dialog = OpenDialog.ANNOUNCE }
            ),
            ActionTile(
                id = "blocked", name = "Blocked numbers", title = "Blocked numbers", icon = AppIcons.Block,
                value = "Android's list", summary = "The list Android keeps for every app",
                onClick = { open(context, context.getSystemService(TelecomManager::class.java).createManageBlockedNumbersIntent()) }
            )
        ),
        SettingsSection.NETWORK to run {
            val simTiles = if (sims.isEmpty()) {
                listOf(
                    ActionTile(
                        id = "nosim", name = "SIM", title = "No SIM", icon = AppIcons.Smartphone,
                        value = "No SIM", summary = "Insert a SIM to see how well its network protects calls."
                    )
                )
            } else sims.mapIndexed { index, sim ->
                val protection = sim.protection
                ActionTile(
                    id = "sim$index", name = sim.name, title = "${sim.name}: ${protection.label}", icon = AppIcons.Smartphone,
                    value = protection.label,
                    summary = buildString {
                        append("Calls on ${sim.voice.label}")
                        if (sim.data != Protocol.NONE && sim.data != sim.voice) append(", data on ${sim.data.label}")
                        append(". ")
                        append(CellProtection.meaning(protection))
                    },
                    iconTint = protectionColor(protection)
                )
            }
            simTiles + ActionTile(
                id = "2g", name = "Turn off 2G", title = "Turn off 2G", icon = AppIcons.Warning,
                value = "Android's settings", summary = "Keeps fake antennas from pushing your phone onto 2G.",
                onClick = { openFirst(context, "android.settings.CELLULAR_NETWORK_SECURITY", android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS, android.provider.Settings.ACTION_WIRELESS_SETTINGS) }
            )
        },
        SettingsSection.PHONE to listOf(
            ActionTile(
                id = "forwarding", name = "Call settings", title = "Call forwarding, call waiting, caller ID", icon = AppIcons.Call,
                value = "Your carrier's", summary = "Call forwarding, call waiting, caller ID: your carrier's settings",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) }
            ),
            ActionTile(
                id = "accounts", name = "SIMs and accounts", title = "SIMs and calling accounts", icon = AppIcons.Smartphone,
                value = "Which SIM calls", summary = "Which SIM calls, Wi-Fi calling",
                onClick = { open(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
            ),
            ActionTile(
                id = "voicemail", name = "Voicemail", title = "Voicemail", icon = AppIcons.Voicemail,
                value = "Number, alerts", summary = "Number and notifications",
                onClick = { open(context, Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)) }
            ),
            ActionTile(
                id = "ringtone", name = "Ringtone", title = "Ringtone and vibration", icon = AppIcons.Vibration,
                value = "Android's sound settings", summary = "Ringtone and vibration: Android's sound settings",
                onClick = { open(context, Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
            ),
            ActionTile(
                id = "accessibility", name = "Accessibility", title = "Accessibility", icon = AppIcons.Headset,
                value = "Hearing aids, text", summary = "Hearing aids, real-time text",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)) }
            )
        ),
        SettingsSection.APPEARANCE to listOf(
            ActionTile(
                id = "theme", name = "Theme", title = "Theme", icon = AppIcons.Brightness,
                value = themeLabel(settings.themeMode), summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            ),
            ActionTile(
                id = "textSize", name = "Text size", title = "Text size", icon = AppIcons.FormatSize,
                value = textScaleLabel(settings.textScale),
                summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                onClick = { dialog = OpenDialog.TEXT_SIZE }
            )
        ),
        SettingsSection.ABOUT to listOf(
            ActionTile(
                id = "guide", name = "Guide", title = "Guide", icon = AppIcons.Play,
                value = "Show again", summary = "See the welcome pages and the dialpad button's show again",
                onClick = {
                    viewModel.replayGuide()
                    onBack()
                }
            ),
            ActionTile(
                id = "updates", name = "Updates", title = "Updates", icon = AppIcons.Update,
                value = updatesLabel(settings.updates), summary = updatesLabel(settings.updates),
                onClick = { dialog = OpenDialog.UPDATES }
            ),
            ActionTile(
                id = "version", name = "Dialer", title = "Dialer ${BuildConfig.VERSION_NAME}", icon = AppIcons.Info,
                value = BuildConfig.VERSION_NAME, summary = "A phone app with no account, no tracking and no ads."
            ),
            ActionTile(
                id = "source", name = "Source code", title = "Source code", icon = AppIcons.Code,
                value = "github.com/213YaZ786/Dialer", summary = "github.com/213YaZ786/Dialer",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/Dialer") }
            )
        )
    )

    // A section's body: its tiles, the switches first, then what opens something. Another app being the caller
    // ID and spam app means Telecom never asks Dialer to screen: the warning stays on top of Calls.
    val body: @Composable (SettingsSection) -> Unit = { which ->
        if (which == SettingsSection.CALLS && setup.screeningMissing) ScreeningWarning(setup)
        val list: List<SettingsTile> = tiles[which].orEmpty() + actions[which].orEmpty()
        if (list.isNotEmpty()) TileGrid(list) { tileId = it.id }
    }
    // One section's page: its name, then its body.
    val page: @Composable ColumnScope.(SettingsSection) -> Unit = { which ->
        SectionTitle(which.title)
        body(which)
    }

    // The window decides: a list and a page side by side from Medium up, as a phone held upright
    // keeps folded sections. The panes take the whole width, so the readable inset is dropped.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = WidthClass.of(maxWidth) != WidthClass.COMPACT
        CompositionLocalProvider(LocalReadableInset provides if (wide) 0.dp else LocalReadableInset.current) {
            FloatingFrame(
                bottom = 0.dp,
                scroll = if (wide) detailScroll else compactScroll,
                top = { FloatingTop("Settings", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) },
                largeTitle = LargeTitle(
                    "Settings",
                    status = when {
                        setup.screeningMissing -> "Spam calls can ring"
                        salesHere && settings.blockSalesCalls -> "Sales calls blocked"
                        // The same state the tiles read: a switch turned on or off changes this at once.
                        settings.silenceUnknown || settings.blockSpoofed || strict || settings.blockHidden ||
                            prefixes.isNotEmpty() -> "Spam filter on"
                        else -> "Spam filter off"
                    }
                )
            ) { padding ->
                if (wide) {
                    ListDetail(
                        padding = padding,
                        section = section,
                        onSection = { section = it },
                        detailScroll = detailScroll,
                        detail = { page(section) }
                    )
                } else {
                    // Scrolls at full width, rows pushed in by the readable inset, so the
                    // margins of a tablet scroll like the rest. See ReadableScroll.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(compactScroll)
                            .padding(horizontal = LocalReadableInset.current)
                    ) {
                        HeaderSpacer(padding)
                        Spacer(Modifier.height(24.dp))
                        FoldedSections(open = openSection, onOpen = { openSection = it }) { which -> body(which) }
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }

    (tiles.values.flatten() + actions.values.flatten()).firstOrNull { it.id == tileId }?.let { tile ->
        when (tile) {
            is SwitchTile -> TileDialog(tile, onDismiss = { tileId = "" })
            is ActionTile -> InfoDialog(tile, onDismiss = { tileId = "" })
        }
    }

    when (dialog) {
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onPick = viewModel::setTheme,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = "Text size",
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onPick = viewModel::setTextScale,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = settings.updates,
            onPick = { mode ->
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
            onPick = viewModel::setAnnounce,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.NOT_SALES -> ZoneAlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Not sales calls") },
            text = {
                Column {
                    settings.salesAllowed.sorted().forEach { number ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(com.yaz.dialer.core.dial.Numbers.format(context, number), modifier = Modifier.weight(1f))
                            QuietButton(onClick = {
                                viewModel.removeSalesAllowed(number)
                                if (settings.salesAllowed.size <= 1) dialog = OpenDialog.NONE
                            }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = { QuietButton(onClick = { dialog = OpenDialog.NONE }) { Text("Done") } }
        )
        OpenDialog.PREFIXES -> ZoneAlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Blocked prefixes") },
            text = {
                Column {
                    if (prefixes.isEmpty()) {
                        Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    prefixes.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.label)
                                Text(
                                    if (rule.action == PrefixAction.BLOCK) "Block" else "Silence",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            QuietButton(onClick = { viewModel.removePrefix(rule.prefix) }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = { QuietButton(onClick = { dialog = OpenDialog.NONE }) { Text("Done") } },
            dismissButton = { QuietButton(onClick = { dialog = OpenDialog.ADD_PREFIX }) { Text("Add") } }
        )
        OpenDialog.ADD_PREFIX -> PrefixDialog(
            initial = "",
            region = region,
            onDismiss = { dialog = OpenDialog.PREFIXES },
            onSave = {
                viewModel.addPrefix(it)
                dialog = OpenDialog.PREFIXES
            }
        )
        OpenDialog.NONE -> Unit
    }
}

/**
 * The warning on top of Calls while another app is the caller ID and spam app: what is wrong, and the button
 * that fixes it. Plain text on the page (it does nothing), only the button is a tap target.
 */
@Composable
private fun ScreeningWarning(setup: Setup) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 16.dp)
    ) {
        Text("Spam calls can ring", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Text("Dialer is not the caller ID & spam app", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        BoldButton(filled = true, onClick = { setup.run(SetupStep.SCREENING) }) { Text(setup.action(SetupStep.SCREENING)) }
    }
}

/**
 * The room the floating header takes. It shrinks as the large title collapses, so it is read when the
 * spacer is laid out: reading it in composition would redo the whole page on every frame of the scroll.
 * (SMS's HeaderSpacer.)
 */
@Composable
private fun HeaderSpacer(padding: PaddingValues) {
    Spacer(Modifier.layout { _, constraints -> layout(constraints.minWidth, padding.calculateTopPadding().roundToPx()) {} })
}

/**
 * List-detail on a wide window: the sections as a list on the left, the chosen one's page on the right,
 * each pane scrolling on its own. SMS's ListDetail, itself after the pattern of Android's large-screen
 * canonical layout "list-detail" as the Reply sample draws it (ReplyListContent: a TwoPane of the list and the
 * opened item),
 * https://github.com/android/compose-samples/blob/main/Reply/app/src/main/java/com/example/reply/ui/ReplyListContent.kt
 * The list takes a third.
 */
@Composable
private fun ListDetail(
    padding: PaddingValues,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    detailScroll: androidx.compose.foundation.ScrollState,
    detail: @Composable ColumnScope.() -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 8.dp)
        ) {
            HeaderSpacer(padding)
            Spacer(Modifier.height(24.dp))
            SettingsSection.entries.forEach { SectionListRow(it, selected = it == section) { onSection(it) } }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        // The page starts at the top again for each section.
        key(section) {
            Column(
                Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .verticalScroll(detailScroll)
            ) {
                HeaderSpacer(padding)
                detail()
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * A section in the list of a wide window: its icon and name, the chosen one washed with the accent.
 * On a phone the same row heads a folded section: [expanded] says whether its tiles are open under it,
 * and TalkBack reads it as a button that is expanded or collapsed (a state description, as Compose's
 * accessibility guide gives a toggling control:
 * https://developer.android.com/develop/ui/compose/accessibility/semantics). From SMS's SectionListRow.
 */
@Composable
private fun SectionListRow(section: SettingsSection, selected: Boolean, expanded: Boolean? = null, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    ZoneSurface(
        shape = RoundedCornerShape(20.dp),
        accent = selected,
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                if (expanded == null) this.selected = selected
                else {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
            }
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(section.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/**
 * A phone's Settings (SMS's FoldedSections): the sections as the wide window's list shows them, all folded; a
 * tap unfolds that section's tiles right under its row (the others fold, so one is open at a time; a second
 * tap folds it). The tiles slide open with AnimatedVisibility's expandVertically + fadeIn, Compose's recipe for
 * content that appears under a header
 * (https://developer.android.com/develop/ui/compose/animation/composables-modifiers).
 */
@Composable
private fun FoldedSections(
    open: SettingsSection?,
    onOpen: (SettingsSection?) -> Unit,
    tilesOf: @Composable (SettingsSection) -> Unit
) {
    Column {
        SettingsSection.entries.forEach { which ->
            val expanded = which == open
            Box(Modifier.padding(horizontal = 16.dp)) {
                SectionListRow(which, selected = expanded, expanded = expanded) { onOpen(if (expanded) null else which) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                // Room around the grid so the tiles' soft shadows are not cut while it slides.
                Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) { tilesOf(which) }
            }
        }
    }
}

/** A section's name, centred, on the page: it does nothing, so it has no zone. (SMS's SectionTitle.) */
@Composable
private fun SectionTitle(title: String) {
    // Centred and at title size: a heading names what lies below.
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp)
    )
}

/**
 * The tiles of a section, in equal cells, 2 columns on a phone upright, 3 then 4 as the
 * room grows (a cell is at least 160 dp). The cells of Android's Quick Settings: AOSP SystemUI's TileLayout
 * (equal cells, the column count from the window's size class),
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/TileLayout.java
 * (SMS's TileGrid.) A last row that is not full is centred, so no side is left empty.
 */
@Composable
private fun TileGrid(tiles: List<SettingsTile>, onExplain: (SettingsTile) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val columns = (maxWidth / 160.dp).toInt().coerceIn(2, 4)
        // The cell is a column's share of the width, whatever the row holds.
        val cell = (maxWidth - 12.dp * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                // The tiles of a row are as tall as the tallest (a value on two lines).
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    row.forEach { tile ->
                        val tileModifier = Modifier.width(cell).fillMaxHeight()
                        when (tile) {
                            is SwitchTile -> SwitchTileView(tile, tileModifier) { onExplain(tile) }
                            is ActionTile -> ActionTileView(tile, tileModifier) { onExplain(tile) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One switch as a tile of glass, as a Quick Settings tile (SMS's SwitchTileView): the icon over the name, the
 * state under it, a tap toggles, a long press opens the explanation. Android's QSTileViewImpl (icon, label,
 * secondary label; the long press for details; TalkBack reads a Switch, checked or not, with its state),
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/tileimpl/QSTileViewImpl.kt
 */
@Composable
private fun SwitchTileView(tile: SwitchTile, modifier: Modifier = Modifier, onExplain: () -> Unit) {
    val haptics = rememberHaptics()
    val on = tile.checked
    val state = if (on) "On" else "Off"
    TileFace(
        icon = tile.icon,
        name = tile.name,
        state = state,
        on = on,
        modifier = modifier,
        dimmed = !tile.enabled,
        onClick = {
            if (tile.enabled) {
                haptics.toggle(!on)
                tile.onChange(!on)
            }
        },
        onLongClick = {
            haptics.firm()
            onExplain()
        },
        semantics = {
            contentDescription = tile.title
            role = Role.Switch
            toggleableState = ToggleableState(on)
            stateDescription = if (tile.enabled) state else "$state, unavailable"
        }
    )
}

/**
 * One setting that opens something, as a tile of the same look (SMS's ActionTileView): the icon, the name, its
 * current value as the second line (QSTileViewImpl's secondary label), never "On" and never in the accent: only
 * a switch that is on is. A tap does what the row's tap did, a long press shows the full text. TalkBack gets a
 * button with the full name and the value as its state.
 */
@Composable
private fun ActionTileView(tile: ActionTile, modifier: Modifier = Modifier, onExplain: () -> Unit) {
    val haptics = rememberHaptics()
    TileFace(
        icon = tile.icon,
        iconTint = tile.iconTint,
        name = tile.name,
        state = tile.value,
        on = false,
        modifier = modifier,
        dimmed = false,
        onClick = {
            haptics.tick()
            val open = tile.onClick
            if (open != null) open() else onExplain()
        },
        onLongClick = {
            haptics.firm()
            onExplain()
        },
        semantics = {
            contentDescription = tile.title
            role = Role.Button
            stateDescription = tile.value
        }
    )
}

/**
 * The look of a tile, a Quick Settings tile (SMS's TileFace): the icon over the name, the state or value under
 * it on one or two lines. Android's QSTileViewImpl (icon, label, secondary label; the long press for details),
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/tileimpl/QSTileViewImpl.kt
 * Active tiles ([on]) are washed with the accent (ZoneSurface accent). The tap target is the transparent
 * inner area, as Modules' glass note asks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileFace(
    icon: ImageVector,
    name: String,
    state: String,
    on: Boolean,
    modifier: Modifier,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    semantics: SemanticsPropertyReceiver.() -> Unit,
    iconTint: Color? = null
) {
    // SystemUI's own sizes (res/values/dimens.xml: qs_tile_height 80dp, qs_corner_radius 28dp): a tile is
    // as tall as its content needs, not square, so a section fits on one screen; our icon stands over the
    // name instead of beside it, hence 96 dp at least.
    val shape = RoundedCornerShape(28.dp)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    ZoneSurface(shape = shape, accent = on, modifier = modifier.heightIn(min = 96.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .combinedClickable(
                    onLongClickLabel = "Explain",
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .semantics(mergeDescendants = true, properties = semantics)
                .graphicsLayer { alpha = if (dimmed) 0.38f else 1f }
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint ?: if (on) LocalContentColor.current else quiet
            )
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                state,
                style = MaterialTheme.typography.labelSmall,
                color = if (on) LocalContentColor.current else quiet,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).clearAndSetSemantics {}
            )
        }
    }
}

/** An action tile's long press (or a tap with nothing to open): the setting's full name and its full text. (SMS's InfoDialog.) */
@Composable
private fun InfoDialog(tile: ActionTile, onDismiss: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tile.title) },
        text = { Text(tile.summary, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { BoldButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** A tile's long press: the setting's full name and explanation, and its switch. (SMS's TileDialog.) */
@Composable
private fun TileDialog(tile: SwitchTile, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val change = { on: Boolean ->
        haptics.toggle(on)
        tile.onChange(on)
    }
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tile.title) },
        text = {
            Column {
                Text(tile.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = tile.enabled) { change(!tile.checked) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (tile.checked) "On" else "Off")
                    GlassSwitch(checked = tile.checked, onCheckedChange = change, enabled = tile.enabled)
                }
            }
        },
        confirmButton = { BoldButton(onClick = onDismiss) { Text("Done") } }
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
