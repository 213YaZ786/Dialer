package com.yaz.dialer.core.dial

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    private fun place(number: String, home: String = "FR") = Places.of(number, home, Locale.ENGLISH)

    @Test
    fun aLandlineFromAbroadSaysItsCityCountryAndZone() {
        val p = place("+34 91 123 45 67")!!
        assertTrue(p.abroad)
        assertEquals("Madrid, Spain", p.name)
        assertEquals(ZoneId.of("Europe/Madrid"), p.zone)
    }

    @Test
    fun aLandlineFromHomeSaysItsCity() {
        val p = place("(650) 253-0000", home = "US")!!
        assertFalse(p.abroad)
        assertEquals("Mountain View, CA", p.name)
    }

    @Test
    fun aCountryWithoutCitiesSaysNothingAtHome() {
        assertNull(place("01 42 68 53 00")!!.name)
    }

    @Test
    fun aMobileFromHomeSaysNoPlace() {
        val p = place("06 12 34 56 78")!!
        assertFalse(p.abroad)
        assertNull(p.name)
    }

    @Test
    fun aNumberFromAnotherContinentHasItsClock() {
        val p = place("+1 650-253-0000")!!
        assertTrue(p.abroad)
        assertEquals(ZoneId.of("America/Los_Angeles"), p.zone)
        assertTrue(p.name!!.endsWith("United States"))
    }

    @Test
    fun shortOrHiddenNumbersHaveNoPlace() {
        assertNull(place("112"))
        assertNull(place("Private"))
        assertNull(place(""))
    }

    @Test
    fun nightIsFromTenToSeven() {
        assertTrue(Places.isNight(22))
        assertTrue(Places.isNight(3))
        assertFalse(Places.isNight(7))
        assertFalse(Places.isNight(21))
    }
}
