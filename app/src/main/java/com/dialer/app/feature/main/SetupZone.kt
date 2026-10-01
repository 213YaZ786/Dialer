package com.dialer.app.feature.main

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dialer.app.ui.component.BoldButton
import com.dialer.app.ui.component.ZoneSurface

/** What Dialer needs before it can take calls, in the order it is asked. */
internal enum class SetupStep(val title: String, val message: String, val action: String) {
    ROLE(
        "Make Dialer your phone app",
        "Calls, the numbers you dial and the phone links of other apps then come to Dialer.",
        "Choose Dialer"
    ),
    NOTIFICATIONS(
        "Allow call notifications",
        "An incoming call shows as a notification, and so does the call in progress.",
        "Allow"
    ),
    FULL_SCREEN(
        "Show calls on the lock screen",
        "So an incoming call fills the screen even when the phone is locked.",
        "Open settings"
    )
}

/**
 * The step still missing, or null when Dialer can take calls, read again
 * each time the app comes back, since each can be changed in Android at
 * any time; and [run] to start a step.
 */
internal class Setup(val step: SetupStep?, val run: (SetupStep) -> Unit)

@Composable
internal fun rememberSetup(): Setup {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { checks++ }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused now or before: Android will not ask again, its own page is
        // the only way left, and the user decides there.
        if (!granted) openNotificationSettings(context)
        checks++
    }
    val step = remember(checks) { nextStep(context) }
    return Setup(step) { wanted ->
        when (wanted) {
            SetupStep.ROLE -> role.launch(
                context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
            )
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
    }
}

/**
 * The step still missing, shown on the main screen until Dialer can take
 * calls: when the first launch page was skipped, or something was turned
 * off in Android since.
 */
@Composable
fun SetupZone(modifier: Modifier = Modifier) {
    val setup = rememberSetup()
    val step = setup.step ?: return

    ZoneSurface(
        shape = RoundedCornerShape(24.dp),
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
            Row(Modifier.padding(top = 4.dp)) {
                BoldButton(filled = true, onClick = { setup.run(step) }) { Text(step.action) }
            }
        }
    }
}

private fun nextStep(context: Context): SetupStep? {
    val roles = context.getSystemService(RoleManager::class.java)
    if (roles.isRoleAvailable(RoleManager.ROLE_DIALER) && !roles.isRoleHeld(RoleManager.ROLE_DIALER)) return SetupStep.ROLE
    val notifications = context.getSystemService(NotificationManager::class.java)
    if (!notifications.areNotificationsEnabled()) return SetupStep.NOTIFICATIONS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !notifications.canUseFullScreenIntent()) {
        return SetupStep.FULL_SCREEN
    }
    return null
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}
