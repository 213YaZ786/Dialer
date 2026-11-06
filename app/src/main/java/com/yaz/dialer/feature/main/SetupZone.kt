package com.yaz.dialer.feature.main

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** What Dialer needs before it can take calls, in the order it is asked. */
internal enum class SetupStep(val title: String, val message: String, val action: String) {
    ROLE(
        "Make Dialer your phone app",
        "To make and receive calls with it.",
        "Choose Dialer"
    ),
    SCREENING(
        "Filter spam calls",
        "Spam and sales calls are turned away before they ring. Choose Dialer as the caller ID and spam app.",
        "Choose Dialer"
    ),
    NOTIFICATIONS(
        "Allow call notifications",
        "Incoming calls and the call in progress.",
        "Allow"
    )
}

/**
 * [run] to start a step, and what Android says of the spam role, read again
 * each time the app comes back, since each can be changed in Android at
 * any time.
 */
internal class Setup(
    val run: (SetupStep) -> Unit,
    /** Another app (or none) is the caller ID and spam app: Telecom does not ask Dialer to screen calls. */
    val screeningMissing: Boolean,
    /** Android no longer shows the question for the screening role. */
    val screeningBlocked: Boolean
) {
    /** The label of [step]'s button: Android's page once it no longer asks. */
    fun action(step: SetupStep): String =
        if (step == SetupStep.SCREENING && screeningBlocked) "Open settings" else step.action

    /** What stays true while the step is refused, or null. */
    fun warning(step: SetupStep): String? =
        if (step == SetupStep.SCREENING && screeningBlocked) "Android no longer asks. Choose Dialer as the caller ID and spam app in its settings." else null
}

@Composable
internal fun rememberSetup(): Setup {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { checks++ }
    // Asked as SpamBlocker asks (Permission.CallScreening: createRequestRoleIntent(ROLE_CALL_SCREENING), checked with
    // isRoleHeld, https://github.com/aj3423/SpamBlocker/blob/master/app/src/main/java/spam/blocker/util/Permission.kt).
    // An answer back in under 250 ms is Android refusing without asking, as SMS's SetupZone and AOSP Messaging's
    // PermissionCheckActivity read it: its default apps page is where the user then decides.
    var screeningBlocked by rememberSaveable { mutableStateOf(false) }
    var screeningAskedAt by remember { mutableLongStateOf(0L) }
    val screening = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val held = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        if (!held && SystemClock.elapsedRealtime() - screeningAskedAt < NO_QUESTION_MS) {
            screeningBlocked = true
            openDefaultApps(context)
        }
        checks++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused now or before: Android will not ask again, its own page is
        // the only way left, and the user decides there.
        if (!granted) openNotificationSettings(context)
        checks++
    }
    val screeningMissing = remember(checks) { screeningMissing(context) }
    return Setup(screeningMissing = screeningMissing, screeningBlocked = screeningBlocked, run = { wanted ->
        when (wanted) {
            SetupStep.ROLE -> role.launch(
                context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
            )
            SetupStep.SCREENING ->
                if (screeningBlocked) {
                    openDefaultApps(context)
                } else {
                    screeningAskedAt = SystemClock.elapsedRealtime()
                    screening.launch(
                        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    )
                }
            SetupStep.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
        }
    })
}

/** The caller ID and spam role exists on this phone and Dialer does not hold it (SpamBlocker's Permission.CallScreening.check). */
private fun screeningMissing(context: Context): Boolean {
    val roles = context.getSystemService(RoleManager::class.java)
    return roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private const val NO_QUESTION_MS = 250L

/** The preferences file that keeps what the guide remembers: "asked_" + a permission group's key. */
internal const val SETUP = "setup"

/**
 * Android's default apps page, where the caller ID and spam app is chosen when the role is no longer asked:
 * Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
 * (https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_DEFAULT_APPS_SETTINGS), started as
 * SMS's SetupZone.openDefaultApps starts it.
 */
private fun openDefaultApps(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}
