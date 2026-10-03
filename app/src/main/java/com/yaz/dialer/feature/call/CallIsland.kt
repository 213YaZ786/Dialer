package com.yaz.dialer.feature.call

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import com.yaz.dialer.ui.glass.glassFloating
import com.yaz.dialer.ui.glass.LocalGlassBackdrop
import com.yaz.dialer.ui.glass.LocalGlass
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaz.dialer.core.call.AudioRoute
import com.yaz.dialer.core.call.CallInfo
import com.yaz.dialer.core.call.CallPhase
import com.yaz.dialer.core.call.CallStore
import com.yaz.dialer.core.call.CallsState
import com.yaz.dialer.core.network.CellProtection
import com.yaz.dialer.core.network.NetworkAlert
import com.yaz.dialer.core.network.Protection
import com.yaz.dialer.ui.component.ContactAvatar
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay

private enum class Shape { RING, SMALL, OPEN }

/** The island's corners: one radius for every shape, the one Android's blur behind it takes too. */
val IslandCorner = 30.dp

/**
 * The call island: the call in progress, kept at the top of the screen
 * while its screen is not in front (in the app, and over the other apps
 * when the user allowed it). A pane of glass in the wallpaper's tones
 * that comes out of the camera: ringing, it holds who calls with Decline
 * and Answer; taken, it folds into the time and the voice's waves; a tap
 * goes back to the call screen, a long press opens mute, speaker and hang
 * up; it closes back into the camera when the call ends ([leaving]). Each
 * change of shape pops; each button swells and lets a wave of its colour
 * run through the glass, as on the call screen.
 *
 * [room] is the width it may take; [blurred] says Android blurs what lies
 * behind its window, so the glass can be clear. Inside the app, it is the
 * app's own glass ([overlay] false).
 */
/**
 * Which island this is: inside the app, or one of the two windows over
 * other apps. Over other apps no window ever changes width (Android moves
 * a window and draws its content a frame apart, and blurs the whole
 * window): the wide one holds the ringing call and the opened controls,
 * the pill holds the call going on, and they hand the island over at the
 * pill's exact place, the glass frosted for that instant.
 */
enum class IslandRole { IN_APP, WIDE, PILL }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallIsland(
    state: CallsState,
    call: CallInfo,
    photo: String?,
    actions: CallStore,
    onOpenScreen: () -> Unit,
    room: Dp,
    opened: Boolean,
    onOpened: (Boolean) -> Unit,
    role: IslandRole = IslandRole.IN_APP,
    blurred: Boolean = false,
    active: Boolean = true,
    handOff: Boolean = false,
    leaving: Boolean = false,
    onGone: () -> Unit = {},
    onGlass: (Float) -> Unit = {},
    onFolded: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val overlay = role != IslandRole.IN_APP
    val asked = when {
        call.phase == CallPhase.RINGING -> Shape.RING
        opened -> Shape.OPEN
        else -> Shape.SMALL
    }
    val shape = if (role == IslandRole.PILL) Shape.SMALL else asked
    // Opened, it folds back by itself after a few quiet seconds; each touch starts them again.
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(opened, touched, active) {
        if (opened && active && role != IslandRole.PILL) {
            delay(4500)
            onOpened(false)
        }
    }
    // Felt only for what the user did, and for what the other side does
    // while the call is out of sight: picked up, or hung up on them.
    var byMe by remember(call.id) { mutableStateOf(false) }
    var lastPhase by remember(call.id) { mutableStateOf(call.phase) }
    LaunchedEffect(call.phase) {
        if (role != IslandRole.PILL) {
            if (call.phase == CallPhase.ACTIVE && lastPhase == CallPhase.DIALING) haptics.done()
            if (call.phase == CallPhase.ENDED && lastPhase != CallPhase.ENDED && !byMe) haptics.firm()
        }
        lastPhase = call.phase
    }

    // Sizes follow the screen: as wide as it allows when it asks something,
    // a centred pill of two thirds while the call goes on.
    val wide = min(room * 0.94f, 460.dp)
    fun sizeOf(s: Shape) = when (s) {
        Shape.RING -> DpSize(wide, 84.dp)
        Shape.OPEN -> DpSize(wide, 140.dp)
        Shape.SMALL -> DpSize(min(room * 0.66f, 300.dp).coerceAtLeast(min(room, 240.dp)), 60.dp)
    }
    val target = sizeOf(shape)
    // The wave a button lets go: answering, it settles away into the call;
    // declining or hanging up, it stays until the island goes; a toggle's
    // ripple fades as it spreads.
    var wave by remember(call.id) { mutableStateOf<IslandWave?>(null) }
    val waveProgress = remember(call.id) { Animatable(0f) }
    val waveAlpha = remember(call.id) { Animatable(1f) }
    LaunchedEffect(wave) {
        val w = wave ?: return@LaunchedEffect
        waveProgress.snapTo(0f)
        waveAlpha.snapTo(1f)
        when (w.kind) {
            WaveKind.STAY -> waveProgress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
            WaveKind.SETTLE -> {
                waveProgress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                waveAlpha.animateTo(0f, tween(300))
            }
            WaveKind.RIPPLE -> {
                launch { waveAlpha.animateTo(0f, tween(520, easing = LinearOutSlowInEasing)) }
                waveProgress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }
    // It drops out of the top and pops: a little past its height, then
    // back with a bounce, the glass clearing in meanwhile. Only the height
    // overshoots, the one size a window follows without a jolt. Over other
    // apps the glass frosts while the island changes width ([frost]), as
    // Android's blur covers the whole window and not the island alone.
    val width = remember { Animatable(target.width.value) }
    val height = remember { Animatable(60f) }
    val pinch = remember { Animatable(0.92f) }
    val shown = remember { Animatable(0f) }
    val frost = remember { Animatable(0f) }
    val motion = spring<Float>(dampingRatio = 0.9f, stiffness = 700f)
    suspend fun pop(to: Float) {
        height.animateTo(to + 9f, tween(120, easing = LinearOutSlowInEasing))
        height.animateTo(to, spring(dampingRatio = 0.5f, stiffness = 520f))
    }
    LaunchedEffect(target, leaving, active) {
        when {
            leaving -> {
                if (!active) return@LaunchedEffect
                // Declined or hung up from here: the red wave first fills the glass.
                if (wave?.kind == WaveKind.STAY) withTimeoutOrNull(600) { snapshotFlow { waveProgress.value }.first { it >= 0.9f } }
                launch { height.animateTo(60f, motion) }
                launch { pinch.animateTo(0.94f, tween(170)) }
                shown.animateTo(0f, tween(170, easing = FastOutLinearInEasing))
                onGone()
            }
            // Resting out of sight, at the pill's size, ready to take the island over.
            !active -> {
                delay(48)
                shown.snapTo(0f)
                frost.snapTo(1f)
                width.snapTo(target.width.value)
                height.snapTo(target.height.value)
                wave = null
            }
            handOff -> {
                // Taken over at the same place and size: no fade, the glass clears or widens.
                shown.snapTo(1f)
                pinch.snapTo(1f)
                frost.snapTo(1f)
                if (role == IslandRole.PILL) {
                    height.snapTo(target.height.value)
                    delay(48)
                    frost.animateTo(0f, tween(200))
                } else {
                    launch { pop(target.height.value) }
                    width.animateTo(target.width.value, spring(dampingRatio = 0.86f, stiffness = 650f))
                    frost.animateTo(0f, tween(200))
                }
            }
            else -> {
                launch { shown.animateTo(1f, tween(170, easing = LinearOutSlowInEasing)) }
                launch { pinch.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f)) }
                launch { pop(target.height.value) }
                val narrowing = role == IslandRole.WIDE && width.value != target.width.value
                if (narrowing) frost.animateTo(1f, tween(90)) else if (role == IslandRole.PILL) frost.snapTo(0f)
                width.animateTo(target.width.value, spring(dampingRatio = 0.86f, stiffness = 650f))
                if (role == IslandRole.WIDE && shape == Shape.SMALL) {
                    // Folded to the pill's place: once the answer's wave has settled, the pill takes over.
                    withTimeoutOrNull(700) { snapshotFlow { wave == null || waveAlpha.value <= 0.05f }.first { it } }
                    onFolded()
                } else if (frost.value > 0f) frost.animateTo(0f, tween(200))
            }
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun let(color: Color, kind: WaveKind, at: Offset) {
        wave = IslandWave(GlassWave(color, at - origin), kind)
    }
    // The call went onto 2G: out of sight of the call screen, a wave of red
    // runs through the glass and the phone gives a refusal, felt at the ear.
    val protocol = callProtocol(call)
    val twoG = protocol?.let(CellProtection::protectionOf) == Protection.UNPROTECTED
    val alarm = MaterialTheme.colorScheme.error
    rememberNetworkChange(call, protocol) {
        if (it == NetworkAlert.Change.DOWN && active && !leaving) {
            haptics.reject()
            wave = IslandWave(GlassWave(alarm, Offset(width.value * density.density / 2f, height.value * density.density / 2f)), WaveKind.RIPPLE)
        }
    }
    // A tap on the island itself dips it, like a key pressed.
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Android's blur behind the window follows the glass: in and out, and off while frosted.
    LaunchedEffect(Unit) { snapshotFlow { shown.value * (1f - frost.value) }.collect(onGlass) }

    val corner = RoundedCornerShape(IslandCorner)
    val look = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val scheme = MaterialTheme.colorScheme
    // Over other apps, the window keeps its width: the widest shape's, or the pill's.
    Box(Modifier.width(if (overlay) (if (role == IslandRole.WIDE) wide else target.width) else width.value.dp).height(height.value.dp), contentAlignment = Alignment.TopCenter) {
    Box(
        Modifier
            .size(width.value.dp, height.value.dp)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer {
                alpha = shown.value
                // Never past 1: over other apps, the window would cut it.
                scaleX = pinch.value.coerceAtMost(1f) * press.value
                scaleY = press.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .clip(corner)
            .then(
                if (!overlay && look != null && backdrop != null) Modifier.glassFloating(backdrop, corner, look, tint = look.zoneTint, lens = 1.2f)
                else Modifier.islandGlass(scheme, clear = overlay && blurred, frost = { frost.value })
            )
            .islandRim(scheme)
            // A tap: back to the call. Held: its quick controls, without leaving the app in front.
            .combinedClickable(
                enabled = active,
                onClick = {
                    haptics.tick()
                    scope.launch {
                        press.animateTo(0.96f, tween(70))
                        press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                    }
                    onOpenScreen()
                },
                onLongClick = {
                    if (shape != Shape.RING) {
                        haptics.firm()
                        // The pill frosts first, so the wide island takes over unseen.
                        if (role == IslandRole.PILL) scope.launch {
                            frost.animateTo(1f, tween(80))
                            onOpened(true)
                        } else onOpened(!opened)
                    }
                },
                onClickLabel = "Back to the call",
                onLongClickLabel = "The call's controls"
            ),
        contentAlignment = Alignment.Center
    ) {
      // Its own window over other apps provides no text colour: given here, for dark mode too.
      androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
        // The glass changes size; what it holds fades across, each laid out at its own final size.
        AnimatedContent(
            shape,
            transitionSpec = {
                (fadeIn(tween(150, delayMillis = 70)) + scaleIn(tween(220, delayMillis = 40), initialScale = 0.96f)) togetherWith fadeOut(tween(70)) using
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
            },
            modifier = Modifier.wrapContentSize(unbounded = true),
            label = "island"
        ) { now ->
            Box(Modifier.requiredSize(sizeOf(now)), contentAlignment = Alignment.Center) {
                when (now) {
                    Shape.RING -> Ringing(call, photo, twoG, onDecline = { at ->
                        byMe = true
                        haptics.reject()
                        let(HangUpRed, WaveKind.STAY, at)
                        actions.decline(call.id)
                    }, onAnswer = { at ->
                        haptics.firm()
                        let(AnswerGreen, WaveKind.SETTLE, at)
                        actions.answer(call.id)
                    })
                    Shape.SMALL -> Small(call, photo, twoG)
                    Shape.OPEN -> Open(state, call, photo, actions, twoG, onWave = { color, kind, at ->
                        touched++
                        if (kind == WaveKind.STAY) byMe = true
                        let(color, kind, at)
                    })
                }
            }
        }
        wave?.let { w ->
            Canvas(Modifier.matchParentSize()) { drawGlassWave(w.glass, waveProgress.value, waveAlpha.value) }
        }
      }
    }
    }
}

/**
 * The glass's body: the wallpaper's surface tone touched by its accent,
 * clear when Android blurs what lies behind, nearly solid when it cannot
 * (battery saver, or blur off) and while it changes width ([frost]); a
 * soft light from above and a glow of the accent from below.
 */
private fun Modifier.islandGlass(scheme: androidx.compose.material3.ColorScheme, clear: Boolean, frost: () -> Float = { 0f }) = drawBehind {
    val dark = scheme.surface.luminance() < 0.5f
    val body = androidx.compose.ui.graphics.lerp(scheme.surfaceContainerHigh, scheme.primary, if (dark) 0.16f else 0.14f)
    val see = if (clear) (if (dark) 0.50f else 0.42f) else 0.95f
    drawRect(body.copy(alpha = see + (0.97f - see).coerceAtLeast(0f) * frost()))
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.06f else 0.16f), 0.45f to Color.Transparent))
    drawRect(
        Brush.radialGradient(
            listOf(scheme.primary.copy(alpha = if (dark) 0.24f else 0.16f), Color.Transparent),
            center = Offset(size.width / 2, size.height * 1.1f),
            radius = size.width * 0.55f
        )
    )
}

/**
 * The rim, all around: light that fades in over a few pixels from the
 * edge, a little more on top, the accent below; never a line of white.
 */
private fun Modifier.islandRim(scheme: androidx.compose.material3.ColorScheme) = drawWithContent {
    drawContent()
    val dark = scheme.surface.luminance() < 0.5f
    val r = IslandCorner.toPx().coerceAtMost(size.height / 2)
    val light = if (dark) 0.10f else 0.20f
    // Three widening strokes, fainter outward in: the edge's light falls off softly.
    listOf(1f to 1f, 3f to 0.45f, 6f to 0.18f).forEach { (dp, k) ->
        val w = dp.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = light * k),
                0.4f to Color.White.copy(alpha = light * k * 0.25f),
                1f to scheme.primary.copy(alpha = (if (dark) 0.22f else 0.14f) * k)
            ),
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((r - w / 2).coerceAtLeast(0f)),
            style = Stroke(w)
        )
    }
}

@Composable
private fun Ringing(call: CallInfo, photo: String?, twoG: Boolean, onDecline: (Offset) -> Unit, onAnswer: (Offset) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp)
    ) {
        ContactAvatar(call.title, photo, 52.dp, look = com.yaz.dialer.ui.component.rememberLook(call.number).value)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (call.encrypted) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                // A call from abroad says from where, and the time there.
                val from = com.yaz.dialer.ui.component.placeWords(com.yaz.dialer.ui.component.rememberPlace(call.number), known = call.name != null)
                    ?.takeIf { it.name != null || it.time != null }
                // The time first: cut short, the place goes, never the hour.
                if (from?.night == true) {
                    Icon(AppIcons.Night, contentDescription = "Night there", tint = com.yaz.dialer.ui.component.NightAmber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    from?.let { listOfNotNull(it.time?.removeSuffix(" there"), it.name).joinToString(" · ") }
                        ?: if (call.encrypted) (if (call.video) "Encrypted video" else "Encrypted") else if (call.video) "Video" else "Mobile",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (from?.night == true) com.yaz.dialer.ui.component.NightAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (twoG) TwoG()
            }
            Text(call.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Round(AppIcons.CallEnd, "Decline", HangUpRed, modifier = Modifier.width(68.dp).height(54.dp), onClick = onDecline)
        Spacer(Modifier.width(10.dp))
        Round(if (call.video) AppIcons.Videocam else AppIcons.Call, "Answer", AnswerGreen, modifier = Modifier.width(68.dp).height(54.dp), onClick = onAnswer)
    }
}

@Composable
private fun Small(call: CallInfo, photo: String?, twoG: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 18.dp)
    ) {
        ContactAvatar(call.title, photo, 40.dp, look = com.yaz.dialer.ui.component.rememberLook(call.number).value)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(call.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (call.encrypted) AppIcons.Lock else AppIcons.Call, contentDescription = if (call.encrypted) "Encrypted" else null, tint = AnswerGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(clockOf(call), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = AnswerGreen)
                if (twoG) TwoG()
            }
        }
        Waves(live = call.phase == CallPhase.ACTIVE)
    }
}

@Composable
private fun Open(state: CallsState, call: CallInfo, photo: String?, actions: CallStore, twoG: Boolean, onWave: (Color, WaveKind, Offset) -> Unit) {
    val haptics = rememberHaptics()
    val speaker = state.route?.kind == AudioRoute.Kind.SPEAKER
    val accent = MaterialTheme.colorScheme.primary
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(call.title, photo, 36.dp, look = com.yaz.dialer.ui.component.rememberLook(call.number).value)
            Text(
                call.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Waves(live = call.phase == CallPhase.ACTIVE)
            Spacer(Modifier.width(10.dp))
            Text(clockOf(call), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AnswerGreen)
            if (twoG) TwoG()
        }
        // Three capsules sharing the whole width: no empty glass between them.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val wide = Modifier.weight(1f).height(56.dp)
            Round(if (state.muted) AppIcons.MicOff else AppIcons.Mic, if (state.muted) "Unmute" else "Mute", null, on = state.muted, modifier = wide) { at ->
                haptics.toggle(!state.muted)
                onWave(accent, WaveKind.RIPPLE, at)
                actions.mute(!state.muted)
            }
            Round(AppIcons.Speaker, if (speaker) "Earpiece" else "Speaker", null, on = speaker, modifier = wide) { at ->
                haptics.toggle(!speaker)
                onWave(accent, WaveKind.RIPPLE, at)
                val target = if (speaker) AudioRoute.Kind.EARPIECE else AudioRoute.Kind.SPEAKER
                state.routes.firstOrNull { it.kind == target }?.let(actions::route)
            }
            Round(AppIcons.CallEnd, "Hang up", HangUpRed, modifier = wide) { at ->
                haptics.reject()
                onWave(HangUpRed, WaveKind.STAY, at)
                actions.hangUp(call.id)
            }
        }
    }
}

/**
 * A button of the island: filled with its colour, or glass that takes the
 * accent when on; round, or a capsule when [modifier] gives it a width.
 */
@Composable
private fun Round(
    icon: ImageVector,
    label: String,
    color: Color?,
    size: Dp = 54.dp,
    on: Boolean = false,
    modifier: Modifier = Modifier.size(size),
    onClick: (at: Offset) -> Unit
) {
    val fill by androidx.compose.animation.animateColorAsState(
        color ?: if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        tween(220),
        label = "fill"
    )
    val tint = when {
        color != null -> Color.White
        on -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Tapped, it swells and springs back, letting its wave go from its centre.
    val swell = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .graphicsLayer {
                scaleX = swell.value
                scaleY = swell.value
            }
            .clip(CircleShape)
            .background(fill)
            .clickable(onClickLabel = label) {
                scope.launch {
                    swell.animateTo(1.07f, tween(90, easing = LinearOutSlowInEasing))
                    swell.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 700f))
                }
                onClick(center)
            },
        contentAlignment = Alignment.Center
    ) {
        // The icon turns into its other self when the button switches.
        androidx.compose.animation.Crossfade(icon, animationSpec = tween(180), label = "icon") {
            Icon(it, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

/** The call is on 2G: said in red next to the time, a warning sign before it. */
@Composable
private fun TwoG() {
    val red = MaterialTheme.colorScheme.error
    Spacer(Modifier.width(6.dp))
    Icon(AppIcons.Warning, contentDescription = "On 2G, can be listened to", tint = red, modifier = Modifier.size(13.dp))
    Spacer(Modifier.width(2.dp))
    Text("2G", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = red)
}

/** How a button's wave behaves once it has spread. */
private enum class WaveKind { SETTLE, STAY, RIPPLE }

private class IslandWave(val glass: GlassWave, val kind: WaveKind)

/**
 * The voice's waves: a few rounded bars, each lit from the call's green
 * at its foot to a light yellow at its top, rising and falling on its own
 * beat so the whole sways like speech (Android gives no app a phone
 * call's sound, so they move with the call, not with the words); low and
 * still while the call waits.
 */
@Composable
private fun Waves(live: Boolean) {
    val t by rememberInfiniteTransition(label = "waves").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val calm by animateFloatAsState(if (live) 1f else 0f, tween(400), label = "calm")
    Canvas(Modifier.width(52.dp).height(22.dp)) {
        val bar = 3.dp.toPx()
        val gap = (size.width - Beats.size * bar) / (Beats.size - 1)
        val low = 4.dp.toPx()
        val brush = Brush.verticalGradient(listOf(Color(0xFFD7F56B), AnswerGreen), startY = 0f, endY = size.height)
        Beats.forEachIndexed { i, (delayS, periodS) ->
            // Eased up and down, like the first preview's bars.
            val phase = ((t + delayS) / periodS) % 1f
            val ease = 0.5f - 0.5f * cos(phase * 2f * PI.toFloat())
            val h = low + (size.height - low) * ease * calm
            drawRoundRect(brush, Offset(i * (bar + gap), (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}

/** Each bar's offset and beat, in seconds. */
private val Beats = listOf(0f to 0.9f, 0.12f to 0.96f, 0.3f to 0.88f, 0.05f to 1.02f, 0.22f to 0.92f, 0.4f to 0.98f, 0.16f to 0.9f, 0.34f to 1f)

/** 0:44, 12:03; "Calling" before it is taken, "On hold" while held. */
@Composable
private fun clockOf(call: CallInfo): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.phase) {
        while (call.phase == CallPhase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return when (call.phase) {
        CallPhase.ACTIVE -> if (call.connectedAt > 0) ((now - call.connectedAt) / 1000).coerceAtLeast(0).let { "%d:%02d".format(it / 60, it % 60) } else "0:00"
        CallPhase.HOLDING -> "On hold"
        CallPhase.ENDED -> "Ended"
        else -> "Calling"
    }
}

/** Back to the call's screen. */
fun openCallScreen(context: Context) {
    context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
