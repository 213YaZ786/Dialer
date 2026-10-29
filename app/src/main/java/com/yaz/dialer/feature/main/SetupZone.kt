package com.yaz.dialer.feature.main

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yaz.dialer.ui.component.BoldButton
import com.yaz.dialer.ui.component.ZoneSurface

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
    ),
    FULL_SCREEN(
        "Show calls on the lock screen",
        "Incoming calls fill the screen when the phone is locked.",
        "Open settings"
    )
}

/**
 * The step still missing, or null when Dialer can take calls, read again
 * each time the app comes back, since each can be changed in Android at
 * any time; and [run] to start a step.
 */
internal class Setup(
    val step: SetupStep?,
    val run: (SetupStep) -> Unit,
    /** Another app (or none) is the caller ID and spam app: Telecom does not ask Dialer to screen calls. */
    val screeningMissing: Boolean,
    /** Android no longer shows the question for the screening role. */
    val screeningBlocked: Boolean,
    /** The step the card on Recents shows: [step] without the optional one set aside by "Not now". */
    val cardStep: SetupStep? = step,
    /** "Not now" on an optional step: remembered for good, the card moves on at once. */
    val skip: (SetupStep) -> Unit = {}
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
    // isRoleHeld, https://github.com/aj3423/SpamBlocker/blob/main/app/src/main/java/spam/blocker/util/Permission.kt).
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
    val step = remember(checks) { nextStep(context, skipOptional = false) }
    val cardStep = remember(checks) { nextStep(context, skipOptional = true) }
    val screeningMissing = remember(checks) { screeningMissing(context) }
    return Setup(step, screeningMissing = screeningMissing, screeningBlocked = screeningBlocked, cardStep = cardStep, skip = { skipped ->
        skip(context, skipped)
        checks++
    }, run = { wanted ->
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
            SetupStep.FULL_SCREEN -> context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            )
        }
    })
}

/**
 * The step still missing, shown on the main screen until Dialer can take
 * calls: when the first launch page was skipped, or something was turned
 * off in Android since.
 */
@Composable
fun SetupZone(modifier: Modifier = Modifier) {
    val setup = rememberSetup()
    val step = setup.cardStep ?: return

    // A zone on the page: Material 3's extra large corner (ShapeDefaults,
    // https://developer.android.com/reference/kotlin/androidx/compose/material3/ShapeDefaults).
    ZoneSurface(
        shape = ShapeDefaults.ExtraLarge,
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                step.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            setup.warning(step)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The optional step: SMS's SetupZone, "Not now" before the action.
                if (step == SetupStep.SCREENING) {
                    BoldButton(filled = false, onClick = { setup.skip(step) }) { Text("Not now") }
                }
                BoldButton(filled = true, onClick = { setup.run(step) }) { Text(setup.action(step)) }
            }
        }
    }
}

/**
 * The step set aside for good by "Not now" (only an optional one), taken from SMS's SetupZone.skip, which keeps its
 * bubbles step (/home/yghouar/Claude Code/APK/YAZ/SMS/app/src/main/java/com/yaz/sms/feature/main/SetupZone.kt),
 * stored as SharedPreferences.Editor.putBoolean
 * (https://developer.android.com/reference/android/content/SharedPreferences.Editor#putBoolean(java.lang.String,%20boolean)).
 */
private fun skip(context: Context, step: SetupStep) {
    if (step == SetupStep.SCREENING) {
        context.getSharedPreferences(SETUP, Context.MODE_PRIVATE).edit().putBoolean(SCREENING_SKIPPED, true).apply()
    }
}

private fun nextStep(context: Context, skipOptional: Boolean): SetupStep? {
    val roles = context.getSystemService(RoleManager::class.java)
    if (roles.isRoleAvailable(RoleManager.ROLE_DIALER) && !roles.isRoleHeld(RoleManager.ROLE_DIALER)) return SetupStep.ROLE
    val skipped = skipOptional && context.getSharedPreferences(SETUP, Context.MODE_PRIVATE).getBoolean(SCREENING_SKIPPED, false)
    if (!skipped && screeningMissing(context)) return SetupStep.SCREENING
    val notifications = context.getSystemService(NotificationManager::class.java)
    if (!notifications.areNotificationsEnabled()) return SetupStep.NOTIFICATIONS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !notifications.canUseFullScreenIntent()) {
        return SetupStep.FULL_SCREEN
    }
    return null
}

/** The caller ID and spam role exists on this phone and Dialer does not hold it (SpamBlocker's Permission.CallScreening.check). */
private fun screeningMissing(context: Context): Boolean {
    val roles = context.getSystemService(RoleManager::class.java)
    return roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

private const val NO_QUESTION_MS = 250L
private const val SETUP = "setup"
private const val SCREENING_SKIPPED = "screening_skipped"

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
