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
import com.yaz.dialer.ui.component.SettingsActionTile
import com.yaz.dialer.ui.component.SettingsFoldedSections
import com.yaz.dialer.ui.component.SettingsHeaderSpacer
import com.yaz.dialer.ui.component.SettingsListDetail
import com.yaz.dialer.ui.component.SettingsSection
import com.yaz.dialer.ui.component.SettingsSectionTitle
import com.yaz.dialer.ui.component.SettingsSwitchTile
import com.yaz.dialer.ui.component.SettingsTile
import com.yaz.dialer.ui.component.SettingsTileDialogs
import com.yaz.dialer.ui.component.SettingsTileGrid
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

/** The groups of settings: folded one under another on a phone, a list and the open one's page on a wide window. */
private val CALLS = SettingsSection("calls", "Calls", AppIcons.Call)
private val NETWORK = SettingsSection("network", "Network security", AppIcons.Lock)
private val PHONE = SettingsSection("phone", "Phone", AppIcons.Smartphone)
private val APPEARANCE = SettingsSection("appearance", "Appearance", AppIcons.Palette)
private val ABOUT = SettingsSection("about", "About", AppIcons.Info)
private val SECTIONS = listOf(CALLS, NETWORK, PHONE, APPEARANCE, ABOUT)

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
    var sectionId by rememberSaveable { mutableStateOf(CALLS.id) }
    val section = SECTIONS.first { it.id == sectionId }
    // On a phone every section starts folded; the one open stays open across a turn of the screen.
    var openId by rememberSaveable { mutableStateOf("") }
    val openSection = SECTIONS.firstOrNull { it.id == openId }
    // The page's scroll, held here so a drag on the header drives it (FloatingFrame's `scroll`): the folded
    // list on a phone, the chosen section's page on a wide window (it starts at the top again for each section).
    val compactScroll = rememberScrollState()
    val detailScroll = rememberSaveable(sectionId, saver = androidx.compose.foundation.ScrollState.Saver) {
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

    val tiles: Map<SettingsSection, List<SettingsSwitchTile>> = mapOf(
        CALLS to listOfNotNull(
            SettingsSwitchTile(
                id = "silence", name = "Silence unknown", title = "Silence unknown callers", icon = AppIcons.VolumeOff,
                summary = "Numbers not in your contacts ring without sound.",
                checked = settings.silenceUnknown, onChange = viewModel::setSilenceUnknown
            ),
            SettingsSwitchTile(
                id = "spoofed", name = "Faked numbers", title = "Block faked numbers", icon = AppIcons.Report,
                summary = if (strict) byAndroid else "Calls your carrier finds faked are rejected.",
                checked = settings.blockSpoofed || strict, enabled = !strict, onChange = viewModel::setBlockSpoofed
            ),
            SettingsSwitchTile(
                id = "hidden", name = "Hidden numbers", title = "Block hidden numbers", icon = AppIcons.VisibilityOff,
                summary = if (strict) byAndroid else "Calls without a number are rejected.",
                checked = settings.blockHidden || strict, enabled = !strict, onChange = viewModel::setBlockHidden
            ),
            if (salesHere) SettingsSwitchTile(
                id = "sales", name = "Sales calls", title = "Block sales calls", icon = AppIcons.NotificationsOff,
                summary = "Numbers reserved for cold calls are rejected, unless saved in your contacts.",
                checked = settings.blockSalesCalls, onChange = viewModel::setBlockSalesCalls
            ) else null,
            SettingsSwitchTile(
                id = "flip", name = "Flip to silence", title = "Flip to silence", icon = AppIcons.Smartphone,
                summary = "Turn the phone face down to stop the ringing.",
                checked = settings.flipToSilence, onChange = viewModel::setFlipToSilence
            ),
            SettingsSwitchTile(
                id = "vibrate", name = "Vibrate on answer", title = "Vibrate when answered", icon = AppIcons.Vibration,
                summary = "One buzz when the other person answers.",
                checked = settings.vibrateOnAnswer, onChange = viewModel::setVibrateOnAnswer
            )
        ),
        APPEARANCE to listOf(
            SettingsSwitchTile(
                id = "black", name = "Pure black", title = "Pure black", icon = AppIcons.Night,
                summary = "Black background in dark mode.",
                checked = settings.pureBlack, onChange = viewModel::setPureBlack
            ),
            SettingsSwitchTile(
                id = "recents", name = "Hide in recents", title = "Hide in recent apps", icon = AppIcons.VisibilityOff,
                summary = "Dialer's preview stays blank in recent apps.",
                checked = settings.hideInRecents, onChange = viewModel::setHideInRecents
            ),
            SettingsSwitchTile(
                id = "glass", name = "Glass effects", title = "Glass effects", icon = AppIcons.Sparkles,
                summary = "Buttons and panes in liquid glass.",
                checked = settings.glass, onChange = viewModel::setGlass
            )
        )
    )

    // What opens a choice, a page or a link: tiles of the same grid, the value as the second line.
    val actions: Map<SettingsSection, List<SettingsActionTile>> = mapOf(
        CALLS to listOfNotNull(
            // Numbers said to be no sales call (from Recents): kept here, never in Android's blocked list.
            if (settings.salesAllowed.isNotEmpty()) {
                val count = settings.salesAllowed.size
                SettingsActionTile(
                    id = "notSales", name = "Not sales calls", title = "Not sales calls", icon = AppIcons.CheckCircle,
                    value = "$count number" + (if (count > 1) "s" else ""),
                    summary = "$count number" + (if (count > 1) "s" else "") + " always ring.",
                    onClick = { dialog = OpenDialog.NOT_SALES }
                )
            } else null,
            // Starts of numbers the user blocks or silences (added from a number's page or here).
            SettingsActionTile(
                id = "prefixes", name = "Blocked prefixes", title = "Blocked prefixes", icon = AppIcons.Dialpad,
                value = if (prefixes.isEmpty()) "None" else "${prefixes.size} prefix" + (if (prefixes.size > 1) "es" else ""),
                summary = "The start of numbers you block or silence",
                onClick = { dialog = OpenDialog.PREFIXES }
            ),
            SettingsActionTile(
                id = "announce", name = "Announce caller", title = "Announce the caller", icon = AppIcons.Speaker,
                value = announceLabel(settings.announce), summary = announceLabel(settings.announce),
                onClick = { dialog = OpenDialog.ANNOUNCE }
            ),
            SettingsActionTile(
                id = "blocked", name = "Blocked numbers", title = "Blocked numbers", icon = AppIcons.Block,
                value = "Android's list", summary = "The list Android keeps for every app",
                onClick = { open(context, context.getSystemService(TelecomManager::class.java).createManageBlockedNumbersIntent()) }
            )
        ),
        NETWORK to run {
            val simTiles = if (sims.isEmpty()) {
                listOf(
                    SettingsActionTile(
                        id = "nosim", name = "SIM", title = "No SIM", icon = AppIcons.Smartphone,
                        value = "No SIM", summary = "Insert a SIM to see how well its network protects calls."
                    )
                )
            } else sims.mapIndexed { index, sim ->
                val protection = sim.protection
                SettingsActionTile(
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
            simTiles + SettingsActionTile(
                id = "2g", name = "Turn off 2G", title = "Turn off 2G", icon = AppIcons.Warning,
                value = "Android's settings", summary = "Keeps fake antennas from pushing your phone onto 2G.",
                onClick = { openFirst(context, "android.settings.CELLULAR_NETWORK_SECURITY", android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS, android.provider.Settings.ACTION_WIRELESS_SETTINGS) }
            )
        },
        PHONE to listOf(
            SettingsActionTile(
                id = "forwarding", name = "Call settings", title = "Call forwarding, call waiting, caller ID", icon = AppIcons.Call,
                value = "Your carrier's", summary = "Call forwarding, call waiting, caller ID: your carrier's settings",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)) }
            ),
            SettingsActionTile(
                id = "accounts", name = "SIMs and accounts", title = "SIMs and calling accounts", icon = AppIcons.Smartphone,
                value = "Which SIM calls", summary = "Which SIM calls, Wi-Fi calling",
                onClick = { open(context, Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) }
            ),
            SettingsActionTile(
                id = "voicemail", name = "Voicemail", title = "Voicemail", icon = AppIcons.Voicemail,
                value = "Number, alerts", summary = "Number and notifications",
                onClick = { open(context, Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)) }
            ),
            SettingsActionTile(
                id = "ringtone", name = "Ringtone", title = "Ringtone and vibration", icon = AppIcons.Vibration,
                value = "Android's sound settings", summary = "Ringtone and vibration: Android's sound settings",
                onClick = { open(context, Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
            ),
            SettingsActionTile(
                id = "accessibility", name = "Accessibility", title = "Accessibility", icon = AppIcons.Headset,
                value = "Hearing aids, text", summary = "Hearing aids, real-time text",
                onClick = { open(context, Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)) }
            )
        ),
        APPEARANCE to listOf(
            SettingsActionTile(
                id = "theme", name = "Theme", title = "Theme", icon = AppIcons.Brightness,
                value = themeLabel(settings.themeMode), summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            ),
            SettingsActionTile(
                id = "textSize", name = "Text size", title = "Text size", icon = AppIcons.FormatSize,
                value = textScaleLabel(settings.textScale),
                summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                onClick = { dialog = OpenDialog.TEXT_SIZE }
            )
        ),
        ABOUT to listOf(
            SettingsActionTile(
                id = "guide", name = "Guide", title = "Guide", icon = AppIcons.Play,
                value = "Show again", summary = "See the welcome pages and the dialpad button's show again",
                onClick = {
                    viewModel.replayGuide()
                    onBack()
                }
            ),
            SettingsActionTile(
                id = "updates", name = "Updates", title = "Updates", icon = AppIcons.Update,
                value = updatesLabel(settings.updates), summary = updatesLabel(settings.updates),
                onClick = { dialog = OpenDialog.UPDATES }
            ),
            SettingsActionTile(
                id = "version", name = "Dialer", title = "Dialer ${BuildConfig.VERSION_NAME}", icon = AppIcons.Info,
                value = BuildConfig.VERSION_NAME, summary = "A phone app with no account, no tracking and no ads."
            ),
            SettingsActionTile(
                id = "source", name = "Source code", title = "Source code", icon = AppIcons.Code,
                value = "github.com/213YaZ786/Dialer", summary = "github.com/213YaZ786/Dialer",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/Dialer") }
            )
        )
    )

    // A section's body: its tiles, the switches first, then what opens something. Another app being the caller
    // ID and spam app means Telecom never asks Dialer to screen: the warning stays on top of Calls.
    val body: @Composable (SettingsSection) -> Unit = { which ->
        if (which == CALLS && setup.screeningMissing) ScreeningWarning(setup)
        val list: List<SettingsTile> = tiles[which].orEmpty() + actions[which].orEmpty()
        if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
    }
    // One section's page: its name, then its body.
    val page: @Composable ColumnScope.(SettingsSection) -> Unit = { which ->
        SettingsSectionTitle(which.title)
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
                    SettingsListDetail(
                        padding = padding,
                        sections = SECTIONS,
                        section = section,
                        onSection = { sectionId = it.id },
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
                        SettingsHeaderSpacer(padding)
                        Spacer(Modifier.height(24.dp))
                        SettingsFoldedSections(SECTIONS, open = openSection, onOpen = { openId = it?.id.orEmpty() }) { which -> body(which) }
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }

    (tiles.values.flatten() + actions.values.flatten()).firstOrNull { it.id == tileId }?.let { tile ->
        SettingsTileDialogs(tile, onDismiss = { tileId = "" })
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
