package com.dialer.app.feature.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialer.app.core.call.AudioRoute
import com.dialer.app.core.call.CallInfo
import com.dialer.app.core.call.CallPhase
import com.dialer.app.core.call.CallStore
import com.dialer.app.core.call.CallsState
import com.dialer.app.core.call.NumberCheck
import androidx.compose.foundation.layout.width
import com.dialer.app.core.network.CellProtection
import com.dialer.app.core.network.CellWatch
import com.dialer.app.core.network.Protocol
import com.dialer.app.ui.component.NetworkChip
import com.dialer.app.core.call.Participant
import com.dialer.app.core.dial.T9
import com.dialer.app.data.contacts.PhoneBook
import com.dialer.app.data.contacts.PhoneIndex
import com.dialer.app.ui.component.ContactAvatar
import org.koin.compose.koinInject
import androidx.compose.runtime.collectAsState
import com.dialer.app.ui.component.BoldButton
import com.dialer.app.ui.component.GlassKeypad
import com.dialer.app.ui.component.QuietButton
import com.dialer.app.ui.component.ZoneAlertDialog
import com.dialer.app.ui.component.ZoneSurface
import com.dialer.app.ui.component.rememberHaptics
import com.dialer.app.ui.glass.GlassLook
import com.dialer.app.ui.glass.LocalGlass
import com.dialer.app.ui.glass.glassZone
import com.dialer.app.ui.icon.AppIcons
import com.dialer.app.ui.theme.zone
import kotlinx.coroutines.delay


@Composable
fun CallScreen(
    state: CallsState,
    keypadOpen: Boolean,
    onKeypad: (Boolean) -> Unit,
    onAddCall: () -> Unit,
    actions: CallStore
) {
    val call = state.primary ?: return
    val compact = keypadOpen && call.phase != CallPhase.ENDED
    val haptics = rememberHaptics()

    // The contact's photo when the number is saved with one: on the
    // caller's drop of glass, and blurred behind the whole screen.
    val book: PhoneBook = koinInject()
    LaunchedEffect(Unit) { book.refresh() }
    val contacts by book.entries.collectAsState()
    val photo = remember(contacts, call.number, call.isConference) {
        if (call.isConference) null else PhoneIndex(contacts).find(T9.clean(call.number))?.photo
    }

    // Writing back instead of answering: the replies replace Answer and Decline.
    var replying by remember(call.id) { mutableStateOf(false) }
    val silenced by actions.silenced.collectAsState()

    // The other side picked up: felt in the hand as well as seen.
    var lastPhase by remember(call.id) { mutableStateOf(call.phase) }
    LaunchedEffect(call.id, call.phase) {
        if (call.phase == CallPhase.ACTIVE && lastPhase == CallPhase.DIALING) haptics.done()
        if (call.phase == CallPhase.ENDED && lastPhase != CallPhase.ENDED) haptics.firm()
        lastPhase = call.phase
    }

    // The wave of glass let go by Answer or Decline, spread over the whole
    // window. Answering lets it settle away into the call; declining keeps
    // it until the screen closes.
    var wave by remember(call.id) { mutableStateOf<GlassWave?>(null) }
    val waveProgress = remember(call.id) { Animatable(0f) }
    val waveAlpha = remember(call.id) { Animatable(1f) }
    LaunchedEffect(wave) {
        val w = wave ?: return@LaunchedEffect
        waveProgress.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        if (w.color == AnswerGreen) waveAlpha.animateTo(0f, tween(520))
    }
    // At least the screen's height, so the buttons sit at the bottom, and
    // scrolling when two calls and the keypad need more: Hang up is never
    // pushed out of reach.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    CallingCard(photo)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        StatusPill(call)
        // The network the call goes over, red on 2G before anything is said.
        if (call.phase != CallPhase.ENDED && !compact) {
            val cell: CellWatch = koinInject()
            val sims by cell.sims.collectAsState()
            val protocol = if (call.wifi) Protocol.WIFI else sims.firstOrNull { it.isDefaultVoice }?.forCalls ?: sims.firstOrNull()?.forCalls
            if (protocol != null && protocol != Protocol.NONE) {
                Spacer(Modifier.height(10.dp))
                NetworkChip(protocol, CellProtection.protectionOf(protocol), hd = call.hd)
            }
        }
        Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
        Caller(call, photo, compact = compact)
        if (call.participants.isNotEmpty() && !compact && call.phase != CallPhase.ENDED) {
            Spacer(Modifier.height(16.dp))
            Participants(call.participants, onSplit = actions::split, onHangUp = actions::hangUp)
        }
        state.secondary?.let { other ->
            Spacer(Modifier.height(16.dp))
            // Joining or switching only makes sense once both calls are taken.
            val taken = call.phase != CallPhase.RINGING
            OtherCall(
                other,
                onSwap = { actions.swap(call.id) }.takeIf { taken },
                onMerge = { actions.merge(call.id) }.takeIf { taken && call.canMerge }
            )
        }
        Spacer(Modifier.weight(1f))

        // A readable column on a tablet, the whole width on a phone.
        Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            AnimatedContent(
                targetState = when {
                    call.phase == CallPhase.ENDED -> Panel.ENDED
                    call.phase == CallPhase.RINGING && replying -> Panel.REPLY
                    call.phase == CallPhase.RINGING -> Panel.RINGING
                    call.phase == CallPhase.CHOOSE_SIM -> Panel.SIM
                    keypadOpen -> Panel.KEYPAD
                    else -> Panel.CONTROLS
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "call panel"
            ) { panel ->
                when (panel) {
                    Panel.RINGING -> IncomingChoice(
                        onAnswer = { actions.answer(call.id) },
                        onDecline = { actions.decline(call.id) },
                        onWave = { wave = it },
                        onMessage = { replying = true }.takeIf { call.canReplyByText },
                        onSilence = actions::silence,
                        silenced = silenced,
                        onEndAndAnswer = { actions.endAndAnswer(call.id) }.takeIf {
                            state.calls.any { it.id != call.id && it.phase != CallPhase.ENDED && it.phase != CallPhase.RINGING }
                        }
                    )
                    Panel.REPLY -> Replies(
                        replies = call.replies,
                        onSend = { text ->
                            haptics.done()
                            actions.reply(call.id, text)
                        },
                        onBack = { replying = false }
                    )
                    Panel.SIM -> SimChooser(call, actions)
                    Panel.KEYPAD -> InCallKeypad(
                        onTone = { key -> actions.tone(call.id, key) },
                        onHide = { onKeypad(false) },
                        onHangUp = { actions.hangUp(call.id) }
                    )
                    Panel.CONTROLS -> CallControls(
                        state, call, actions,
                        onKeypad = { onKeypad(true) },
                        onAddCall = onAddCall,
                        onHangUp = { at ->
                            wave = GlassWave(HangUpRed, at)
                            actions.hangUp(call.id)
                        }
                    )
                    Panel.ENDED -> Ended(call)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    wave?.let { w ->
        Canvas(Modifier.matchParentSize()) { drawGlassWave(w, waveProgress.value, waveAlpha.value) }
    }
    }
}

private enum class Panel { RINGING, REPLY, SIM, KEYPAD, CONTROLS, ENDED }

/** One small pane at the top: what is happening, or how long it has lasted. */
@Composable
private fun StatusPill(call: CallInfo) {
    val text = when (call.phase) {
        CallPhase.RINGING -> "Incoming call"
        CallPhase.DIALING -> "Calling"
        CallPhase.CHOOSE_SIM -> "Choose a SIM"
        CallPhase.HOLDING -> "On hold"
        CallPhase.ENDED -> "Call ended"
        CallPhase.ACTIVE -> elapsed(call.connectedAt)
    }
    // On hold, the pill breathes, so a call left waiting is not forgotten.
    val breathing = rememberInfiniteTransition(label = "hold")
    val breath by breathing.animateFloat(1f, 0.55f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "breath")
    ZoneSurface(
        shape = CircleShape,
        modifier = Modifier.graphicsLayer { alpha = if (call.phase == CallPhase.HOLDING) breath else 1f }
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateContentSize()) {
            if (call.phase == CallPhase.ACTIVE) {
                RollingText(text, MaterialTheme.typography.titleMedium)
            } else {
                AnimatedContent(targetState = text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Each character on its own wheel: a digit that changes rolls up into the next one. */
@Composable
private fun RollingText(text: String, style: androidx.compose.ui.text.TextStyle) {
    Row(Modifier.clipToBounds()) {
        text.forEachIndexed { index, char ->
            // Keyed from the right, so the seconds stay the seconds when a minute digit is added.
            androidx.compose.runtime.key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "digit"
                ) { c -> Text(c.toString(), style = style) }
            }
        }
    }
}

/** Minutes and seconds since [since], ticking once a second. */
@Composable
internal fun elapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    if (since <= 0) return "Connecting"
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The person on the line: their photo or initial on a drop of glass, their
 * name, their number. While the call goes out, rings leave the drop; when
 * the other side picks up the drop pops once; on hold it fades back.
 */
@Composable
private fun Caller(call: CallInfo, photo: String?, compact: Boolean) {
    if (compact) {
        // With the keypad open, the name alone: the keys need the room.
        Text(call.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val size = 120.dp
    val accent = MaterialTheme.colorScheme.primary
    val rings = rememberInfiniteTransition(label = "dialing")
    val ring by rings.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    val pop = remember(call.id) { Animatable(1f) }
    var wasDialing by remember(call.id) { mutableStateOf(call.phase == CallPhase.DIALING) }
    LaunchedEffect(call.phase) {
        if (call.phase == CallPhase.ACTIVE && wasDialing) {
            pop.animateTo(1.12f, spring(dampingRatio = 0.4f, stiffness = 900f))
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
        wasDialing = call.phase == CallPhase.DIALING
    }
    val dim by animateFloatAsState(if (call.phase == CallPhase.HOLDING) 0.45f else 1f, tween(500), label = "dim")
    val dialing = call.phase == CallPhase.DIALING

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = dim
            }
            .drawBehind {
                if (!dialing) return@drawBehind
                val r = this.size.minDimension / 2f
                for (k in 0 until 3) {
                    val t = (ring + k / 3f) % 1f
                    val fade = (1f - t) * (1f - t)
                    drawCircle(accent.copy(alpha = 0.35f * fade), r * (1f + 0.7f * t), style = Stroke(width = (3f - 2f * t).dp.toPx()))
                }
            }
    ) {
        if (call.isConference) {
            ZoneSurface(shape = CircleShape, modifier = Modifier.size(size)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Group, contentDescription = null, tint = accent, modifier = Modifier.size(56.dp))
                }
            }
        } else {
            ContactAvatar(call.name, photo, size)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        call.title,
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    if (call.name != null && !call.isConference) {
        Spacer(Modifier.height(6.dp))
        Text(call.number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // What the network says of the number: checked, or likely faked.
    when (call.numberCheck) {
        NumberCheck.VERIFIED -> {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = AnswerGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Number checked by your carrier", style = MaterialTheme.typography.labelLarge, color = AnswerGreen)
            }
        }
        NumberCheck.FAILED -> {
            Spacer(Modifier.height(6.dp))
            Text(
                "This number may be faked: don't give any code or bank detail",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        NumberCheck.NONE -> Unit
    }
}

/**
 * The contact's photo behind the whole call screen, blurred to light and
 * colour, fading into the page towards the controls so the glass reads.
 */
@Composable
private fun CallingCard(photo: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo) {
        value = photo?.let { uri ->
            withContext(Dispatchers.IO) {
                // The thumbnail is enough: it is blurred anyway.
                runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val bitmap = image ?: return
    val show = remember { Animatable(0f) }
    LaunchedEffect(bitmap) { show.animateTo(1f, tween(700)) }
    val ground = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = show.value }) {
        androidx.compose.foundation.Image(
            bitmap,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(36.dp).graphicsLayer { alpha = 0.55f }
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to ground.copy(alpha = 0.25f), 0.45f to ground.copy(alpha = 0.35f), 0.8f to ground.copy(alpha = 0.85f), 1f to ground)
            )
        )
    }
}

/**
 * The people of a conference, one line each: Private takes one out to talk
 * to them alone while the others wait, the red phone lets one go.
 */
@Composable
private fun Participants(people: List<Participant>, onSplit: (Int) -> Unit, onHangUp: (Int) -> Unit) {
    val haptics = rememberHaptics()
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            people.forEach { person ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(person.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (person.canSplit) {
                        QuietButton(onClick = {
                            haptics.tick()
                            onSplit(person.id)
                        }, modifier = Modifier.padding(start = 8.dp)) { Text("Private") }
                    }
                    if (person.canHangUp) {
                        ZoneSurface(
                            shape = CircleShape,
                            onClick = {
                                haptics.firm()
                                onHangUp(person.id)
                            },
                            modifier = Modifier.padding(start = 8.dp).size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(AppIcons.CallEnd, contentDescription = "Hang up on ${person.title}", tint = HangUpRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The other call, still going on or on hold, with the way to switch to it. */
@Composable
private fun OtherCall(call: CallInfo, onSwap: (() -> Unit)?, onMerge: (() -> Unit)?) {
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (call.phase == CallPhase.HOLDING) "On hold" else "In call",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(call.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onMerge != null) QuietButton(onClick = onMerge, modifier = Modifier.padding(start = 8.dp)) { Text("Merge") }
            if (onSwap != null) BoldButton(onClick = onSwap, modifier = Modifier.padding(start = 8.dp)) { Text("Switch") }
        }
    }
}

/** The SIMs Telecom offers for this call, when no default is set. */
@Composable
private fun SimChooser(call: CallInfo, actions: CallStore) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                call.sims.forEach { sim ->
                    Text(
                        sim.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { actions.chooseSim(call.id, sim) }
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        BigButton(AppIcons.CallEnd, "Cancel", HangUpRed) {
            actions.hangUp(call.id)
        }
    }
}

@Composable
private fun Ended(call: CallInfo) {
    Text(
        call.endedReason ?: "",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp)
    )
}

/** The keypad during a call: each key its own pane of glass, sending its tone while held. */
@Composable
private fun InCallKeypad(onTone: (Char?) -> Unit, onHide: () -> Unit, onHangUp: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            typed.ifEmpty { " " },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        GlassKeypad(
            onPress = { key ->
                typed += key
                onTone(key)
            },
            onRelease = { onTone(null) }
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            Spacer(Modifier.size(80.dp))
            BigButton(AppIcons.CallEnd, "Hang up", HangUpRed, onClick = onHangUp)
            CallControl(AppIcons.Dialpad, "Hide", on = true, onClick = onHide)
        }
    }
}

/** Hang up and cancel: the same deep glass as the incoming call's buttons. */
@Composable
private fun BigButton(icon: ImageVector, label: String, fill: Color, size: Dp = 80.dp, onClick: () -> Unit) {
    GlassCallButton(icon = icon, label = label, color = fill, size = size, glow = 0.35f, onClick = onClick)
}

/**
 * Declining with a message: the usual replies as panes of glass, and one's
 * own words. The caller gets it as a text; Telecom sends it.
 */
@Composable
private fun Replies(replies: List<String>, onSend: (String) -> Unit, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var own by rememberSaveable { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        replies.forEachIndexed { index, reply ->
            Appear(order = index) {
                ZoneSurface(
                    shape = RoundedCornerShape(22.dp),
                    onClick = { onSend(reply) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(reply, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
        }
        Appear(order = replies.size) {
            ZoneSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 6.dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = own,
                        onValueChange = { own = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                        decorationBox = { field ->
                            if (own.isEmpty()) Text("Write your own…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    )
                    TextButton(enabled = own.isNotBlank(), onClick = { onSend(own.trim()) }) { Text("Send") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        QuietButton(onClick = {
            haptics.tick()
            onBack()
        }) { Text("Back") }
    }
}
