package com.dialer.app.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dialer.app.feature.call.ReturnToCallPill
import com.dialer.app.feature.call.ongoingCall
import com.dialer.app.navigation.LocalReadableInset
import com.dialer.app.ui.component.FloatingAction
import com.dialer.app.ui.component.FloatingFrame
import com.dialer.app.ui.component.FloatingTop
import com.dialer.app.ui.component.LocalDockPadding
import com.dialer.app.ui.component.TitlePill
import com.dialer.app.ui.icon.DialerIcons

/**
 * What every tab of the main screen shares. The tab's content takes the
 * whole screen, top to bottom; the tab's name, the way to Settings, the
 * setup steps while any is left and the tab's own [controls] (search,
 * filters) float over it in glass, as the dock does at the bottom.
 */
@Composable
fun TabFrame(
    title: String,
    onOpenSettings: () -> Unit,
    controls: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val inset = LocalReadableInset.current
    FloatingFrame(
        bottom = LocalDockPadding.current + 16.dp,
        top = {
            // While a call goes on, the way back to it takes the name's place.
            val call = ongoingCall()
            FloatingTop(
                title = title,
                trailing = { FloatingAction(DialerIcons.Settings, "Settings", onOpenSettings) },
                center = {
                    AnimatedContent(
                        targetState = call != null,
                        transitionSpec = { (scaleIn(initialScale = 0.8f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.8f) + fadeOut()) },
                        label = "top"
                    ) { inCall ->
                        var last by remember { mutableStateOf(call) }
                        call?.let { last = it }
                        val shown = last
                        if (inCall && shown != null) ReturnToCallPill(shown) else TitlePill(title)
                    }
                }
            )
            // Until Dialer can take calls, the steps to get there come first.
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) { SetupZone() }
            controls?.invoke()
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(horizontal = inset)) { content(padding) }
    }
}
