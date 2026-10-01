package com.dialer.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.LocalGlassBackdrop
import com.dialer.app.ui.glass.glassFloating

/**
 * A pane of glass floating over the list, as the dock does: what scrolls
 * under it shows through, bent near its edges. Needs the list recorded as
 * LocalGlassBackdrop (TabFrame does it); without it, or with glass off, it
 * is an ordinary zone. [accent] washes it with the accent, for a chosen pill.
 */
@Composable
fun FloatingPane(
    shape: Shape,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    if (look == null || backdrop == null) {
        ZoneSurface(shape = shape, modifier = modifier, accent = accent, onClick = onClick, content = content)
        return
    }
    val color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides color) {
        Box(
            modifier
                .clip(shape)
                // Frosted like a zone, so the text it carries stays readable
                // over what scrolls under it; the lens still bends the edges.
                .glassFloating(backdrop, shape, look, tint = if (accent) look.accentTint else look.zoneTint, lens = 1.2f)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        ) { content() }
    }
}

/**
 * A screen's banner floating over its list: the name in the middle, one
 * round action on each side, the list passing under it in glass. Same
 * layout as the shared ScreenBanner, which is a zone of the page instead.
 */
@Composable
fun FloatingBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    FloatingPane(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
            Column(
                Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
        }
    }
}
