package com.yaz.dialer.feature.recents

import android.text.TextDirectionHeuristics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A layout of two children, the content and the overlay (a call's time): the overlay goes on the content's last
 * line when it fits there, to the right of the content when the content is narrower than its room, else on a row
 * of its own under it. Copied from SMS (feature/thread/ContentAvoidingLayout.kt in 213YaZ786/SMS, AGPL-3.0),
 * itself ported from Element X, AGPL-3.0 (https://github.com/element-hq/element-x-android,
 * libraries/ui-common/src/main/kotlin/io/element/android/libraries/ui/common/layout/ContentAvoidingLayout.kt).
 */
@Composable
internal fun ContentAvoidingLayout(
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    overlayOffset: DpOffset = DpOffset.Zero,
    content: @Composable ContentAvoidingLayoutScope.() -> Unit
) {
    val scope = remember { ContentAvoidingLayoutScopeInstance() }
    Layout(
        modifier = modifier,
        content = {
            scope.content()
            overlay()
        }
    ) { measurables, constraints ->
        val overlayPlaceable = measurables.last().measure(Constraints(minWidth = 0, maxWidth = constraints.maxWidth))
        val contentPlaceable = measurables.first().measure(Constraints(minWidth = 0, maxWidth = constraints.maxWidth))

        var layoutWidth = contentPlaceable.width
        var layoutHeight = contentPlaceable.height

        val data = scope.data.value
        val freeSpace = max(contentPlaceable.width - data.nonOverlappingContentWidth, 0)

        when {
            // Content and overlay together are wider than the room: the overlay takes a row of its own.
            !data.canOverlay() || data.nonOverlappingContentWidth + overlayPlaceable.width > constraints.maxWidth -> {
                layoutHeight += overlayPlaceable.height + overlayOffset.y.roundToPx()
            }
            // The content is narrower than the room: the overlay may go to its right.
            contentPlaceable.width < constraints.maxWidth -> {
                if (freeSpace < overlayPlaceable.width + spacing.roundToPx()) {
                    val calculatedWidth = max(data.nonOverlappingContentWidth + overlayPlaceable.width + spacing.roundToPx(), contentPlaceable.width)
                    layoutWidth = min(calculatedWidth, constraints.maxWidth)
                }
            }
            else -> Unit
        }

        layoutWidth = max(layoutWidth, constraints.minWidth)
        layoutHeight = max(layoutHeight, constraints.minHeight)

        layout(layoutWidth, layoutHeight) {
            contentPlaceable.placeRelative(0, 0)
            overlayPlaceable.placeRelative(layoutWidth - overlayPlaceable.width, layoutHeight - overlayPlaceable.height + overlayOffset.y.roundToPx())
        }
    }
}

/** What the content tells the layout, in pixels: its full size, and the size of the part the overlay cannot cover. */
internal data class ContentAvoidingLayoutData(
    val contentWidth: Int = 0,
    val contentHeight: Int = 0,
    val nonOverlappingContentWidth: Int = contentWidth,
    val nonOverlappingContentHeight: Int = contentHeight
) {
    fun canOverlay(): Boolean = contentWidth != Int.MAX_VALUE

    companion object {
        val NotOverlapping = ContentAvoidingLayoutData(contentWidth = Int.MAX_VALUE, contentHeight = Int.MAX_VALUE)
    }
}

internal interface ContentAvoidingLayoutScope {
    fun onContentLayoutChange(data: ContentAvoidingLayoutData)
}

private class ContentAvoidingLayoutScopeInstance(
    val data: MutableState<ContentAvoidingLayoutData> = mutableStateOf(ContentAvoidingLayoutData())
) : ContentAvoidingLayoutScope {
    override fun onContentLayoutChange(data: ContentAvoidingLayoutData) {
        this.data.value = data
    }
}

/**
 * The text's last line, measured for [ContentAvoidingLayout]: the `onTextLayout` of a Text. From SMS's
 * measureLastLine (same file as above), after Element X's ContentAvoidingLayout.measureLastTextLine, with its
 * right-to-left fix and the rule that text running against the layout's direction is never overlaid (Signal's
 * V2FooterPositionDelegate, https://github.com/signalapp/Signal-Android). [extraStart] and [extraEnd] are the
 * padding the Text sits in, outside its own layout.
 */
@Composable
internal fun measureLastLine(
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp
): (TextLayoutResult) -> Unit {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val start = with(density) { extraStart.roundToPx() }
    val end = with(density) { extraEnd.roundToPx() }
    return { text: TextLayoutResult ->
        val last = text.lineCount - 1
        val paragraph = runCatching { text.getParagraphDirection(0) }.getOrNull()
        val against = when (paragraph) {
            ResolvedTextDirection.Rtl -> layoutDirection == LayoutDirection.Ltr
            ResolvedTextDirection.Ltr -> layoutDirection == LayoutDirection.Rtl
            else -> false
        }
        if (against) {
            onContentLayoutChange(ContentAvoidingLayoutData.NotOverlapping)
        } else {
            val lineWidth = if (layoutDirection == LayoutDirection.Rtl) {
                text.size.width - text.getLineLeft(last).roundToInt()
            } else {
                text.getLineRight(last).roundToInt()
            }
            onContentLayoutChange(
                ContentAvoidingLayoutData(
                    contentWidth = text.size.width + start + end,
                    contentHeight = text.size.height,
                    nonOverlappingContentWidth = lineWidth + start,
                    nonOverlappingContentHeight = text.getLineBottom(last).roundToInt()
                )
            )
        }
    }
}

/**
 * True when [text] holds letters of both directions: its time then goes under it, as Signal does (BidiUtil
 * hasMixedTextDirection, https://github.com/signalapp/Signal-Android, core/util-jvm/src/main/java/org/signal/core/util/BidiUtil.kt),
 * with SMS's fix (Arabic letters count as right to left; feature/thread/Footer.kt).
 */
internal fun hasMixedTextDirection(text: CharSequence?): Boolean {
    if (text == null) return false
    var isLtr: Boolean? = null
    var i = 0
    while (i < text.length) {
        val codePoint = Character.codePointAt(text, i)
        i += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        val letterIsLtr = when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> true
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> false
            else -> continue
        }
        if (isLtr != null && isLtr != letterIsLtr) return true
        isLtr = letterIsLtr
    }
    return false
}

/** The direction [text] reads in, from its first strong letter, else [fallback] (SMS's directionOf, feature/thread/Footer.kt). */
private fun directionOf(text: CharSequence, fallback: LayoutDirection): LayoutDirection {
    if (text.isEmpty()) return fallback
    val rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)
    val rtlFirst = TextDirectionHeuristics.FIRSTSTRONG_RTL.isRtl(text, 0, text.length)
    return if (rtl != rtlFirst) fallback else if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/**
 * A content with its footer on its last line when it fits, else under it (mixed directions: always under).
 * SMS's WithFooter, Aligned and Below only (feature/thread/Footer.kt), after Element X's WithTimestampLayout
 * (https://github.com/element-hq/element-x-android, features/messages/impl/.../timeline/components/TimelineItemEventRow.kt).
 */
@Composable
internal fun WithFooter(
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    words: CharSequence = "",
    content: @Composable (onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit) -> Unit
) {
    if (hasMixedTextDirection(words)) {
        Column(modifier) {
            content {}
            Box(Modifier.align(Alignment.End)) { footer() }
        }
    } else {
        val original = LocalLayoutDirection.current
        val direction = remember(words, original) { directionOf(words, original) }
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            ContentAvoidingLayout(
                modifier = modifier,
                content = { content(this::onContentLayoutChange) },
                overlay = { CompositionLocalProvider(LocalLayoutDirection provides original) { footer() } }
            )
        }
    }
}
