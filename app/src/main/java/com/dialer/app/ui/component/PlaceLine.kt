package com.dialer.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dialer.app.core.dial.Numbers
import com.dialer.app.core.dial.Place
import com.dialer.app.core.dial.Places
import com.dialer.app.ui.icon.AppIcons
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The place of [number], worked out off the main thread; null while unknown. */
@Composable
fun rememberPlace(number: String): Place? {
    val context = LocalContext.current
    return produceState<Place?>(null, number) {
        value = withContext(Dispatchers.Default) { Places.of(number, Numbers.countryIso(context)) }
    }.value
}

/** What to say of a place, or nothing when it would only be noise. */
data class PlaceWords(val name: String?, val time: String?, val night: Boolean)

/**
 * A number from abroad says where and, when its clock differs from the
 * phone's, the time there; a landline from home says its city unless the
 * person is a contact; a mobile from home says nothing.
 */
@Composable
fun placeWords(place: Place?, known: Boolean): PlaceWords? {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // The time there moves on, to the minute.
    LaunchedEffect(place?.zone) {
        while (place?.zone != null) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    place ?: return null
    val there = place.zone?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), it) }
    val differs = there != null && there.offset != ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(now))
    val name = place.name?.takeIf { place.abroad || !known }
    if (name == null && !differs) return null
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val time = if (differs) "${there!!.format(DateTimeFormatter.ofPattern(pattern))} there" else null
    return PlaceWords(name, time, differs && Places.isNight(there!!.hour))
}

/** The place line under a number: "Madrid, Spain · 23:40 there", the time in amber with a moon at night. */
@Composable
fun PlaceLine(number: String, known: Boolean, modifier: Modifier = Modifier, small: Boolean = false) {
    val words = placeWords(rememberPlace(number), known)
    var last by remember { mutableStateOf(words) }
    words?.let { last = it }
    AnimatedVisibility(
        visible = words != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val w = last ?: return@AnimatedVisibility
        val style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Row(verticalAlignment = Alignment.CenterVertically) {
            w.name?.let {
                Text(it, style = style, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (w.name != null && w.time != null) Text(" · ", style = style, color = muted)
            w.time?.let {
                if (w.night) {
                    Icon(AppIcons.Night, contentDescription = "Night there", tint = NightAmber, modifier = Modifier.size(if (small) 12.dp else 14.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(it, style = style, color = if (w.night) NightAmber else muted, maxLines = 1)
            }
        }
    }
}

/** The colour of a late hour: amber, readable on light and dark glass. */
val NightAmber = Color(0xFFE39400)
