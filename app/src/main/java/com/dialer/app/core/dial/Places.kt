package com.dialer.app.core.dial

import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Where a number is from, as far as the number itself tells: a city for a
 * landline, a country for a number from abroad, and the time zone when
 * there is only one. [abroad] when it is not the home country's.
 */
data class Place(val name: String?, val zone: ZoneId?, val abroad: Boolean)

/**
 * Places from numbers, offline, with Google's libphonenumber and its
 * geocoder: their data ships in the app, nothing is looked up online.
 */
object Places {

    private val util by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val zones by lazy { PhoneNumberToTimeZonesMapper.getInstance() }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Result<Place?>>()

    /** The place of [number] seen from [home] (a country code like FR), named in [locale]. Slow the first time: off the main thread. */
    fun of(number: String, home: String, locale: Locale = Locale.getDefault()): Place? =
        cache.getOrPut("$home|${locale.toLanguageTag()}|$number") { runCatching { find(number, home, locale) } }.getOrNull()

    private fun find(number: String, home: String, locale: Locale): Place? {
        if (number.count(Char::isDigit) < 6) return null
        val parsed = util.parse(number, home.uppercase())
        if (!util.isValidNumber(parsed)) return null
        val region = util.getRegionCodeForNumber(parsed) ?: return null
        val abroad = !region.equals(home, ignoreCase = true)
        val country = Locale("", region).getDisplayCountry(locale).takeIf { it.isNotBlank() }
        // A city when the number has one (landlines); the geocoder gives the country otherwise.
        val area = geocoder.getDescriptionForValidNumber(parsed, locale).takeIf { it.isNotBlank() && it != country }
        val name = if (abroad) listOfNotNull(area, country).joinToString(", ").ifBlank { null } else area
        // One time zone, or several that agree now; a country spread over several says none.
        val now = Instant.now()
        val ids = zones.getTimeZonesForNumber(parsed)
            .filter { it != PhoneNumberToTimeZonesMapper.getUnknownTimeZone() }
            .mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
        val zone = ids.takeIf { list -> list.isNotEmpty() && list.map { it.rules.getOffset(now) }.distinct().size == 1 }?.first()
        return Place(name, zone, abroad)
    }

    /** Night there: from 10 pm to 7 am, a time to think twice before calling. */
    fun isNight(hour: Int) = hour >= 22 || hour < 7
}
