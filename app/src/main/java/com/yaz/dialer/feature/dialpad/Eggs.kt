package com.yaz.dialer.feature.dialpad

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaz.dialer.ui.component.DialogPane
import com.yaz.dialer.ui.component.QuietButton
import com.yaz.dialer.ui.component.rememberHaptics
import com.yaz.dialer.ui.icon.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The dialpad's hidden delights, each found by what is typed. */
enum class DialEgg { BELL, SNAKE, FILM }

/** 1876, the year of the first call; 76253, SNAKE on the letters; 555-01xx, the numbers films use. */
fun eggFor(number: String): DialEgg? {
    if (number == "1876") return DialEgg.BELL
    if (number == "76253") return DialEgg.SNAKE
    val digits = number.filter(Char::isDigit)
    if (number.all { it.isDigit() || it in "+-() " } && FILM.matches(digits)) return DialEgg.FILM
    return null
}

private val FILM = Regex("^(1?\\d{3})?55501\\d\\d$")

/** Over the suggestions: Bell's dial or the clapperboard, for a few seconds, then gone. */
@Composable
fun EggStage(egg: DialEgg, onDone: () -> Unit, modifier: Modifier = Modifier) {
    when (egg) {
        DialEgg.BELL -> BellDial(onDone, modifier)
        DialEgg.FILM -> Clapper(onDone, modifier)
        DialEgg.SNAKE -> Snake(onDone)
    }
}

/**
 * 1876: a rotary dial turns as a finger would turn it, comes back, and Bell's
 * first words over the line write themselves under it.
 */
@Composable
private fun BellDial(onDone: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val turn = remember { Animatable(0f) }
    var letters by remember { mutableIntStateOf(0) }
    val ink = MaterialTheme.colorScheme.primary
    val rim = MaterialTheme.colorScheme.onSurfaceVariant
    LaunchedEffect(Unit) {
        haptics.firm()
        // Pulled round to the stop, then it runs back with its clicks, twice.
        repeat(2) {
            turn.animateTo(250f, tween(650, easing = FastOutSlowInEasing))
            turn.animateTo(0f, tween(900, easing = LinearEasing))
        }
        for (i in 1..WATSON.length) {
            letters = i
            delay(38)
        }
        delay(2600)
        onDone()
    }
    LaunchedEffect(Unit) {
        var last = 0
        while (isActive) {
            val step = (turn.value / 25f).toInt()
            if (step != last) { haptics.tick(); last = step }
            delay(16)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(150.dp)) {
            val r = size.minDimension / 2f
            drawCircle(rim.copy(alpha = 0.25f), r)
            drawCircle(rim, r, style = Stroke(2.dp.toPx()))
            rotate(turn.value) {
                // Ten holes round the wheel, the gap at the finger stop.
                for (k in 0 until 10) {
                    val a = Math.toRadians(-60.0 - k * 27.0)
                    val c = Offset(center.x + (r * 0.7f) * kotlin.math.cos(a).toFloat(), center.y - (r * 0.7f) * kotlin.math.sin(a).toFloat())
                    drawCircle(ink.copy(alpha = 0.85f), r * 0.13f, c, style = Stroke(2.5.dp.toPx()))
                }
            }
            drawCircle(ink.copy(alpha = 0.18f), r * 0.32f)
            // The finger stop.
            val stop = Math.toRadians(-40.0)
            drawLine(rim, Offset(center.x + r * 0.78f * kotlin.math.cos(stop).toFloat(), center.y - r * 0.78f * kotlin.math.sin(stop).toFloat()), Offset(center.x + r * 1.02f * kotlin.math.cos(stop).toFloat(), center.y - r * 1.02f * kotlin.math.sin(stop).toFloat()), strokeWidth = 4.dp.toPx())
        }
        Spacer(Modifier.height(16.dp))
        Text(
            WATSON.take(letters),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).height(52.dp)
        )
        Text("Alexander Graham Bell, 10 March 1876", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val WATSON = "“Mr. Watson, come here, I want to see you.”"

/** 555-01xx: a clapperboard falls on the number and claps shut. */
@Composable
private fun Clapper(onDone: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val fall = remember { Animatable(0f) }
    val stick = remember { Animatable(-28f) }
    LaunchedEffect(Unit) {
        fall.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
        delay(250)
        stick.animateTo(0f, tween(110, easing = FastOutSlowInEasing))
        haptics.done()
        stick.animateTo(-4f, spring(dampingRatio = 0.3f, stiffness = 900f))
        stick.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 600f))
        delay(2200)
        onDone()
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(
            Modifier.size(170.dp, 130.dp).graphicsLayer {
                translationY = (1f - fall.value) * -160.dp.toPx()
                rotationZ = (1f - fall.value) * -12f
                alpha = fall.value.coerceIn(0f, 1f)
            }
        ) {
            val w = size.width
            val board = Size(w, size.height * 0.72f)
            val top = size.height - board.height
            drawRoundRect(ink, Offset(0f, top), board, CornerRadius(10.dp.toPx()))
            // The slate's lines: what films write in chalk.
            for (k in 1..2) {
                val y = top + board.height * k / 3f
                drawLine(paper.copy(alpha = 0.7f), Offset(12.dp.toPx(), y), Offset(w - 12.dp.toPx(), y), strokeWidth = 1.5.dp.toPx())
            }
            // The clap stick, hinged at the left, its black and white stripes.
            val h = size.height * 0.2f
            rotate(stick.value, Offset(0f, top)) {
                drawRoundRect(ink, Offset(0f, top - h - 2.dp.toPx()), Size(w, h), CornerRadius(4.dp.toPx()))
                val stripe = w / 7f
                for (k in 0 until 7 step 2) {
                    val x = k * stripe + stripe * 0.3f
                    drawLine(paper, Offset(x, top - 2.dp.toPx()), Offset(x + stripe * 0.7f, top - h - 2.dp.toPx()), strokeWidth = stripe * 0.45f)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Movie number", style = MaterialTheme.typography.titleMedium)
        Text("Kept for films: no one answers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
    }
}

/**
 * 76253: the Nokia 3310's Snake, on a pane of glass: 2 4 6 8 or a swipe
 * steer it, it eats the dots and grows, its own tail ends the game.
 */
@Composable
private fun Snake(onDone: () -> Unit) {
    val haptics = rememberHaptics()
    val cols = 16
    val rows = 20
    var body by remember { mutableStateOf(listOf(Cell(8, 10), Cell(7, 10), Cell(6, 10))) }
    var heading by remember { mutableStateOf(Cell(1, 0)) }
    var asked by remember { mutableStateOf(Cell(1, 0)) }
    var food by remember { mutableStateOf(Cell(12, 6)) }
    var over by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    fun steer(to: Cell) {
        if (to.x == -heading.x && to.y == -heading.y) return
        asked = to
        haptics.tick()
    }
    LaunchedEffect(round) {
        while (isActive && !over) {
            delay((170 - body.size * 3L).coerceAtLeast(80L))
            heading = asked
            val head = body.first()
            val next = Cell((head.x + heading.x + cols) % cols, (head.y + heading.y + rows) % rows)
            if (next in body.dropLast(1)) {
                over = true
                haptics.reject()
                break
            }
            body = if (next == food) {
                haptics.done()
                val grown = listOf(next) + body
                food = generateSequence { Cell((0 until cols).random(), (0 until rows).random()) }.first { it !in grown }
                grown
            } else listOf(next) + body.dropLast(1)
        }
    }
    val ink = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogPane {
            Column(Modifier.padding(20.dp).widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (over) "Game over · ${body.size - 3}" else "Snake · ${body.size - 3}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(cols / rows.toFloat()).pointerInput(Unit) {
                        var sum = Offset.Zero
                        detectDragGestures(onDragStart = { sum = Offset.Zero }) { change, drag ->
                            change.consume()
                            sum += drag
                            if (sum.getDistance() > 40f) {
                                steer(if (abs(sum.x) > abs(sum.y)) Cell(if (sum.x > 0) 1 else -1, 0) else Cell(0, if (sum.y > 0) 1 else -1))
                                sum = Offset.Zero
                            }
                        }
                    }
                ) {
                    val cell = size.width / cols
                    drawRoundRect(grid.copy(alpha = 0.25f), size = size, cornerRadius = CornerRadius(16.dp.toPx()))
                    val pulse = 0.75f + 0.25f * sin(System.nanoTime() / 2e8 * PI).toFloat()
                    drawCircle(dot, cell * 0.38f * pulse, Offset((food.x + 0.5f) * cell, (food.y + 0.5f) * cell))
                    body.forEachIndexed { i, c ->
                        val fade = 1f - i.toFloat() / (body.size + 4)
                        drawRoundRect(ink.copy(alpha = fade), Offset(c.x * cell + 1.5f, c.y * cell + 1.5f), Size(cell - 3f, cell - 3f), CornerRadius(cell * 0.3f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                // The 3310's keys: 2 up, 4 left, 6 right, 8 down.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SnakeKey("2", AppIcons.ExpandLess) { steer(Cell(0, -1)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        SnakeKey("4", null, turn = 90f) { steer(Cell(-1, 0)) }
                        SnakeKey("6", null, turn = -90f) { steer(Cell(1, 0)) }
                    }
                    SnakeKey("8", AppIcons.ExpandMore) { steer(Cell(0, 1)) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (over) QuietButton(onClick = {
                        body = listOf(Cell(8, 10), Cell(7, 10), Cell(6, 10)); heading = Cell(1, 0); asked = Cell(1, 0); over = false; round++
                    }) { Text("Play again") }
                    QuietButton(onClick = onDone) { Text("Close") }
                }
            }
        }
    }
}

private data class Cell(val x: Int, val y: Int)

@Composable
private fun SnakeKey(digit: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, turn: Float = 0f, onPress: () -> Unit) {
    com.yaz.dialer.ui.component.ZoneSurface(shape = CircleShape, onClick = onPress, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = digit, tint = MaterialTheme.colorScheme.primary)
            else Icon(AppIcons.ExpandMore, contentDescription = digit, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
    }
}

/**
 * Held #, nothing typed: the phrase of Tárrega's Gran Vals (1902, public
 * domain) that Nokia's tune comes from, played as soft notes, each lighting
 * its key, low notes on 1, high ones towards 0.
 */
object GranVals {
    // Note, length in eighths: E5 D5 F#4 G#4 | C#5 B4 D4 E4 | B4 A4 C#4 E4 | A4.
    private val phrase = listOf(
        76 to 1, 74 to 1, 66 to 2, 68 to 2, 73 to 1, 71 to 1, 62 to 2, 64 to 2, 71 to 1, 69 to 1, 61 to 2, 64 to 2, 69 to 4
    )
    private val keys = listOf(61, 62, 64, 66, 68, 69, 71, 73, 74, 76).zip("1234567890".toList()).toMap()

    /** Plays the phrase, telling [onKey] which key sounds (null at the end). */
    suspend fun play(onKey: (Char?) -> Unit) {
        val rate = 44_100
        val eighth = 0.15
        val samples = phrase.sumOf { (it.second * eighth * rate).toInt() }
        val pcm = ShortArray(samples)
        var at = 0
        for ((note, length) in phrase) {
            val n = (length * eighth * rate).toInt()
            val f = 440.0 * Math.pow(2.0, (note - 69) / 12.0)
            for (i in 0 until n) {
                val t = i / rate.toDouble()
                // A plucked string: quick attack, gentle decay, a little of the octave.
                val env = minOf(1.0, t / 0.008) * Math.exp(-t * 4.0)
                val s = sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)
                pcm[at + i] = (s * env * 0.35 * Short.MAX_VALUE / 1.3).toInt().toShort()
            }
            at += n
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples * 2)
            .build()
        try {
            track.write(pcm, 0, samples)
            track.play()
            for ((note, length) in phrase) {
                onKey(keys[note])
                delay((length * eighth * 1000).toLong())
            }
        } finally {
            onKey(null)
            runCatching { track.stop() }
            track.release()
        }
    }
}
