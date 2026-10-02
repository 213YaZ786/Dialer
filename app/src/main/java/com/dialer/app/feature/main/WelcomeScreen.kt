package com.dialer.app.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dialer.app.core.update.Updates
import com.dialer.app.ui.component.BoldButton
import com.dialer.app.ui.component.LoadingMark
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.icon.DialerIcons

/**
 * The one page shown at the first launch: what Dialer needs to be the phone
 * app, done from here, and why it uses the internet at all, which is only
 * to update itself. Each zone turns into a tick once done.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val setup = rememberSetup()
    // Installing from the app is allowed in Android's own page, so it is
    // read again whenever the user comes back from there.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val canInstall = remember(checks) { Updates.canInstall(context) }

    // Android's own question comes up by itself on arriving here, once;
    // the zone keeps its button for whoever dismissed it.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && setup.step == SetupStep.ROLE) {
            asked = true
            setup.run(SetupStep.ROLE)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(40.dp))
        // The app's own mark, large and ringing, as on the other apps' first page.
        LoadingMark(size = 160.dp)
        Spacer(Modifier.height(20.dp))
        Text("Welcome to Dialer", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "A phone app with no account, no tracking and no ads.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        val step = setup.step
        WelcomeZone(
            icon = DialerIcons.Call,
            title = step?.title ?: "Dialer is your phone app",
            message = step?.message ?: "Calls and phone links open in Dialer.",
            done = step == null,
            action = step?.action,
            onAction = { step?.let(setup.run) }
        )
        Spacer(Modifier.height(16.dp))
        WelcomeZone(
            icon = DialerIcons.Update,
            title = "Automatic updates",
            message = "Dialer installs its new versions. Its only connection is this check on GitHub.",
            done = canInstall,
            action = "Allow updates".takeIf { !canInstall },
            onAction = { Updates.allowInstalls(context) }
        )

        Spacer(Modifier.height(32.dp))
        BoldButton(filled = true, onClick = onStart) { Text("Start") }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** One thing to know or to do, with its button until it is done, then a tick. */
@Composable
private fun WelcomeZone(
    icon: ImageVector,
    title: String,
    message: String,
    done: Boolean,
    action: String?,
    onAction: () -> Unit
) {
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) DialerIcons.CheckCircle else icon,
                    contentDescription = if (done) "Done" else null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!done && action != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    BoldButton(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}
