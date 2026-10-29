package com.yaz.dialer.feature.main

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yaz.dialer.feature.call.ReturnToCallPill
import com.yaz.dialer.feature.call.ongoingCall
import com.yaz.dialer.navigation.LocalReadableInset
import com.yaz.dialer.ui.component.FloatingAction
import com.yaz.dialer.ui.component.FloatingFrame
import com.yaz.dialer.ui.component.FloatingTop
import com.yaz.dialer.ui.component.LocalDockPadding
import com.yaz.dialer.ui.icon.AppIcons

/**
 * Where a tab hands its content's scroll state up, to DialerApp, which gives the dock the same vertical drag as the
 * tab's header (state hoisted through a provided callback, as FloatingFrame's LocalFrameScroll hands its drag down).
 * Source: Material Components' HeaderBehavior, a drag on a bar scrolls the content
 * (https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/appbar/HeaderBehavior.java).
 */
val LocalTabScroll = staticCompositionLocalOf<((ScrollableState) -> Unit)?> { null }

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
    /** The content's own scroll state: a drag on the header scrolls it (FloatingFrame's `scroll`). */
    scroll: androidx.compose.foundation.gestures.ScrollableState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val inset = LocalReadableInset.current
    val handUp = LocalTabScroll.current
    LaunchedEffect(scroll) { if (scroll != null) handUp?.invoke(scroll) }
    FloatingFrame(
        scroll = scroll,
        bottom = LocalDockPadding.current + 16.dp,
        top = {
            // While a call goes on, the way back to it takes the name's place.
            val call = ongoingCall()
            FloatingTop(
                title = title,
                trailing = { FloatingAction(AppIcons.Settings, "Settings", onOpenSettings) },
                center = {
                    AnimatedContent(
                        targetState = call != null,
                        transitionSpec = { (scaleIn(initialScale = 0.8f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.8f) + fadeOut()) },
                        label = "top"
                    ) { inCall ->
                        var last by remember { mutableStateOf(call) }
                        call?.let { last = it }
                        val shown = last
                        // The name does nothing: plain centred text, the same as FloatingTop's own title
                        // (ui/component/FloatingPane.kt), no zone. Only the way back to the call is a tap target.
                        if (inCall && shown != null) ReturnToCallPill(shown) else Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                }
            )
            // Until Dialer can take calls, the steps to get there come first.
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) { SetupZone() }
            controls?.invoke()
        }
    ) { padding ->
        // The content takes the whole width, as SMS's TabFrame (feature/main/TabFrame.kt): Recents' grid of
        // cards fills a wide window; the screens that want a readable column apply LocalReadableInset themselves.
        Box(Modifier.fillMaxSize()) { content(padding) }
    }
}
