package com.yaz.dialer.feature.call

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yaz.dialer.core.dial.ContactLook
import com.yaz.dialer.core.dial.PhoneEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * A person's poster behind the whole call screen, as set in the Contacts
 * app: their large photo full screen, zoomed in with the chosen point at
 * the centre, darker towards the controls; without a photo, their
 * monogram large on a light of their colour. It settles in on arrival.
 */
@Composable
internal fun PosterStage(entry: PhoneEntry) {
    val context = LocalContext.current
    val look = entry.look
    val photo by produceState<ImageBitmap?>(null, entry.contactId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, entry.contactId.toString())
                val bytes = ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)?.use { it.readBytes() } ?: return@runCatching null
                // Read at about the screen's size: a full photo can be much larger.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val settle = remember { Animatable(0f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val accent = MaterialTheme.colorScheme.primary
    val tone = look?.color?.let { Color(it) } ?: accent
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = settle.value
            val s = 1.06f - 0.06f * settle.value
            scaleX = s
            scaleY = s
        }
    ) {
        val image = photo
        if (image != null) {
            Canvas(Modifier.fillMaxSize()) {
                val w = image.width.toFloat()
                val h = image.height.toFloat()
                val zoom = look?.zoom ?: 1f
                val scale = max(size.width / w, size.height / h) * zoom
                // The chosen point of the photo at the centre, the photo always covering the screen.
                val fx = (look?.x ?: 0.5f) * w * scale
                val fy = (look?.y ?: 0.4f) * h * scale
                val left = (size.width / 2f - fx).coerceIn(size.width - w * scale, 0f)
                val top = (size.height / 2f - fy).coerceIn(size.height - h * scale, 0f)
                drawImage(
                    image,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize((w * scale).toInt(), (h * scale).toInt())
                )
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.22f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f)))
            }
        } else {
            // No photo: their monogram, large, on a light of their colour.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = maxWidth
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.Black)
                    // Deep enough for white words whatever the colour, a pale one included.
                    val core = androidx.compose.ui.graphics.lerp(tone, Color.Black, 0.35f)
                    val edge = androidx.compose.ui.graphics.lerp(tone, Color.Black, 0.75f)
                    drawRect(Brush.radialGradient(listOf(core, edge, Color.Black), center = Offset(size.width / 2f, size.height * 0.42f), radius = size.maxDimension * 0.7f))
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
                }
                val mark = look?.emoji ?: look?.letters ?: initials(entry.name)
                val density = LocalDensity.current
                Text(
                    mark,
                    color = Color.White.copy(alpha = 0.92f),
                    style = monogramStyle(look?.font).copy(fontSize = with(density) { (side * if (look?.emoji != null) 0.42f else 0.36f).toSp() }),
                    textAlign = TextAlign.Center,
                    // In the free middle of the screen, under the name, above the buttons.
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.33f).graphicsLayer { alpha = 0.85f }
                )
            }
        }
    }
}

/** The call screen over a poster: the dark colours of the wallpaper, and glass made for them. */
@Composable
internal fun PosterTheme(on: Boolean, content: @Composable () -> Unit) {
    if (!on) return content()
    val context = LocalContext.current
    val scheme = remember { androidx.compose.material3.dynamicDarkColorScheme(context) }
    val glassOn = com.yaz.dialer.ui.glass.LocalGlass.current != null
    val look = com.yaz.dialer.ui.glass.rememberGlassLook(scheme, glassOn)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.yaz.dialer.ui.glass.LocalGlass provides look,
            androidx.compose.material3.LocalContentColor provides scheme.onBackground
        ) { content() }
    }
}

/** The name on a poster, in the style chosen in the Contacts app. */
internal fun posterNameStyle(base: TextStyle, style: String): TextStyle = when (style) {
    "bold" -> base.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.02).em)
    "light" -> base.copy(fontWeight = FontWeight.ExtraLight)
    "serif" -> base.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
    "round" -> base.copy(fontWeight = FontWeight.SemiBold)
    else -> base.copy(fontWeight = FontWeight.Medium)
}

/** The name as the poster shows it: capitals for the bold style. */
internal fun posterName(name: String, style: String) = if (style == "bold") name.uppercase() else name

private fun monogramStyle(font: String?): TextStyle = when (font) {
    "serif" -> TextStyle(fontFamily = FontFamily.Serif)
    "mono" -> TextStyle(fontFamily = FontFamily.Monospace)
    "rounded" -> TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
    "bold" -> TextStyle(fontWeight = FontWeight.Black)
    else -> TextStyle(fontWeight = FontWeight.Normal)
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
