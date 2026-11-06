/*
 * The welcome guide as tiles, after FlorisBoard's SetupScreen: every step is read from the system (what is held,
 * allowed) and shown done or not, the tiles are Modules' SetupTiles. Translated from Language's guide.
 *
 * Copyright (C) 2021-2025 The FlorisBoard Contributors, Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0), translated from
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt
 */
// not ported: SetupScreen (WelcomeScreen and WelcomeContent below are its port, as Language's)
// not ported: SetupLinks (the footer links of FlorisBoard, Dialer's Settings holds the source link)
// not ported: Flow (awaitTransition waits for a restored backup, Dialer has none)
// not ported: rememberMicrophone (rememberPermissions below is its port for several permissions)
package com.yaz.dialer.feature.main

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yaz.dialer.ui.component.BoldButton
import com.yaz.dialer.ui.component.LoadingMark
import com.yaz.dialer.ui.component.SetupTile
import com.yaz.dialer.ui.component.SetupTileDialog
import com.yaz.dialer.ui.component.SetupTileGrid
import com.yaz.dialer.ui.component.leftText
import com.yaz.dialer.ui.component.requiredDone
import com.yaz.dialer.ui.component.requiredLeft
import com.yaz.dialer.ui.icon.AppIcons

/**
 * The few choices worth making, as tiles: each is read from the system again each time the screen comes back
 * (SetupScreen reads isFlorisBoardEnabled, isFlorisBoardSelected and hasNotificationPermission, and sets the current
 * step from them), and turns done by itself. Steps: the phone app (required), the spam filter, the notifications,
 * the contacts, the call history. Everything else is set to its best value and never asked.
 * Ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 */
@Composable
fun rememberSetupTiles(): List<SetupTile> {
    val context = LocalContext.current
    val setup = rememberSetup()
    val contacts = rememberPermissions(arrayOf(Manifest.permission.READ_CONTACTS), "contacts")
    val callLog = rememberPermissions(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG), "callLog")
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    // The phone role is held, or this phone has none to give (RoleManager isRoleAvailable and isRoleHeld).
    val isDialer = remember(checks) {
        val roles = context.getSystemService(RoleManager::class.java)
        !roles.isRoleAvailable(RoleManager.ROLE_DIALER) || roles.isRoleHeld(RoleManager.ROLE_DIALER)
    }
    val hasNotificationPermission = remember(checks) {
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    return listOf(
        SetupTile(
            id = "phoneApp", name = "Phone app", title = "Phone app", icon = AppIcons.Call,
            state = if (isDialer) "Dialer" else "Not chosen",
            summary = "Calls and phone links open in Dialer. Tap to choose Dialer as the phone app.",
            done = isDialer, required = true,
            onClick = { setup.run(SetupStep.ROLE) }
        ),
        SetupTile(
            id = "spam", name = "Spam filter", title = "Spam filter", icon = AppIcons.Block,
            state = when {
                setup.screeningBlocked -> "Open settings"
                !setup.screeningMissing -> "On"
                else -> "Off"
            },
            summary = "Spam and sales calls are turned away before they ring. Tap to choose Dialer as the caller ID and spam app." +
                (setup.warning(SetupStep.SCREENING)?.let { " $it" } ?: ""),
            done = !setup.screeningMissing, required = false, error = setup.screeningBlocked,
            onClick = { setup.run(SetupStep.SCREENING) }
        ),
        SetupTile(
            id = "notifications", name = "Notifications", title = "Notifications", icon = AppIcons.Notifications,
            state = if (hasNotificationPermission) "Allowed" else "Not allowed",
            summary = "Incoming calls and the call in progress. Tap to allow them.",
            done = hasNotificationPermission, required = false,
            onClick = { setup.run(SetupStep.NOTIFICATIONS) }
        ),
        SetupTile(
            id = "contacts", name = "Contacts", title = "Contacts", icon = AppIcons.Person,
            state = when {
                contacts.granted -> "Allowed"
                contacts.refusedForGood -> "Refused, open settings"
                else -> "Not allowed"
            },
            summary = "The names and photos of who calls, and the search by name. Tap to allow it" +
                if (contacts.refusedForGood) "; it was refused, so Android's settings page for Dialer opens." else ".",
            done = contacts.granted, required = false, error = contacts.refusedForGood,
            onClick = contacts.request
        ),
        SetupTile(
            id = "callLog", name = "Call history", title = "Call history", icon = AppIcons.Recents,
            state = when {
                callLog.granted -> "Allowed"
                callLog.refusedForGood -> "Refused, open settings"
                else -> "Not allowed"
            },
            summary = "Recents, missed calls and deleting calls. Tap to allow it" +
                if (callLog.refusedForGood) "; it was refused, so Android's settings page for Dialer opens." else ".",
            done = callLog.granted, required = false, error = callLog.refusedForGood,
            onClick = callLog.request
        )
    )
}

/** The rights of a tile as a screen sees them, read again each time the screen comes back (after the Settings page). */
class PermissionsState(
    val granted: Boolean,
    /** Refused with Android's "don't ask again": only the app's page in Settings can allow it now. */
    val refusedForGood: Boolean,
    /** Asks for the rights, or opens the app's page in Settings when they were refused for good. */
    val request: () -> Unit
)

/**
 * The runtime permissions as Android's runtime permission workflow says: a refusal for good is asked before, not
 * granted, and no rationale to show (the first permission decides, they are asked together). "Asked before" is kept
 * in the setup preferences under "asked_" + [key], since a refusal before the first question looks the same.
 * Ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/common/Phone.kt#rememberMicrophone
 */
@Composable
fun rememberPermissions(permissions: Array<String>, key: String): PermissionsState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(SETUP, Context.MODE_PRIVATE) }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checks++ }
    val granted = remember(checks) {
        ContextCompat.checkSelfPermission(context, permissions[0]) == PackageManager.PERMISSION_GRANTED
    }
    val asked = remember(checks) { prefs.getBoolean("asked_$key", false) }
    val refusedForGood = !granted && asked && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[0])
    return PermissionsState(granted, refusedForGood) {
        if (refusedForGood) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } else {
            prefs.edit().putBoolean("asked_$key", true).apply()
            ask.launch(permissions)
        }
    }
}

/**
 * The guide: the logo, the title, how many steps are left, the tiles, then Start (Done when reopened from Settings),
 * which waits for the required ones. Laid out as SetupScreen: header, steps, footer with the one button.
 * Android's own question for the phone role comes up by itself on arriving here, once; the tile keeps it for whoever
 * dismissed it.
 * Ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 */
@Composable
fun WelcomeScreen(reopened: Boolean, onStart: () -> Unit) {
    val steps = rememberSetupTiles()
    // The tile whose long press is open (its id), "" when none.
    var explained by rememberSaveable { mutableStateOf("") }

    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val phoneApp = steps.first { it.id == "phoneApp" }
        if (!asked && !phoneApp.done) {
            asked = true
            phoneApp.onClick()
        }
    }

    WelcomeContent(steps, reopened, onStart) { explained = it.id }

    steps.firstOrNull { it.id == explained }?.let { tile ->
        SetupTileDialog(tile, onDismiss = { explained = "" })
    }
}

/**
 * What the guide draws from the tiles it is given: the layout of SetupScreen (header, steps, footer with the one
 * button), split from [WelcomeScreen] as HomeScreen is from HomeContent, so that a picture can be taken with fixed
 * tiles, without the system checks. Ported from
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeContent
 */
@Composable
internal fun WelcomeContent(tiles: List<SetupTile>, reopened: Boolean, onStart: () -> Unit, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(40.dp))
            LoadingMark(size = 120.dp, running = false)
            Spacer(Modifier.height(20.dp))
            // Every title in the accent (the user, 2026-10-31), as Modules' SettingsSectionTitle.
            Text("Welcome to Dialer", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(steps.leftText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            SetupTileGrid(steps, onExplain)

            Spacer(Modifier.height(32.dp))
            val ready = steps.requiredDone()
            BoldButton(filled = true, enabled = ready, onClick = onStart) { Text(if (reopened) "Done" else "Start") }
            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Still needed: " + steps.requiredLeft().joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
