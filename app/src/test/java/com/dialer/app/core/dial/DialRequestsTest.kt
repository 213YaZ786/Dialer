package com.dialer.app.core.dial

import org.junit.Assert.assertEquals
import org.junit.Test

class DialRequestsTest {

    @Test
    fun aLinkKeepsOnlyWhatCanBeDialled() {
        assertEquals("+33612345678", DialRequests.fromLink("+33 6 12-34-56-78"))
        assertEquals("123", DialRequests.fromLink("1<script>2‮3"))
    }

    @Test
    fun aCodeFromALinkIsKeptForTheUserToSeeNotRun() {
        assertEquals("*#*#4636#*#*", DialRequests.fromLink("*#*#4636#*#*"))
    }

    @Test
    fun aLinkIsCutToASaneLength() {
        assertEquals(64, DialRequests.fromLink("1".repeat(500)).length)
    }

    @Test
    fun noLinkGivesAnEmptyDialpad() {
        assertEquals("", DialRequests.fromLink(null))
    }
}
