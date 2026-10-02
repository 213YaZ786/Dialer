package com.dialer.app.core.network

import com.dialer.app.core.network.NetworkAlert.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAlertTest {

    @Test
    fun goingOnto2gDuringACallIsSaid() {
        assertEquals(Change.DOWN, NetworkAlert.change(Protection.STANDARD, Protection.UNPROTECTED))
        assertEquals(Change.DOWN, NetworkAlert.change(Protection.PROTECTED, Protection.UNPROTECTED))
    }

    @Test
    fun aCallThatStartsOn2gIsSaidToo() {
        assertEquals(Change.DOWN, NetworkAlert.change(null, Protection.UNPROTECTED))
    }

    @Test
    fun stayingOn2gIsSaidOnce() {
        assertNull(NetworkAlert.change(Protection.UNPROTECTED, Protection.UNPROTECTED))
    }

    @Test
    fun comingBackFrom2gIsSaid() {
        assertEquals(Change.BACK, NetworkAlert.change(Protection.UNPROTECTED, Protection.STANDARD))
        assertEquals(Change.BACK, NetworkAlert.change(Protection.UNPROTECTED, Protection.OLD))
    }

    @Test
    fun losingTheNetworkIsNotComingBack() {
        assertNull(NetworkAlert.change(Protection.UNPROTECTED, Protection.UNKNOWN))
    }

    @Test
    fun otherChangesAreLeftToTheChip() {
        assertNull(NetworkAlert.change(Protection.PROTECTED, Protection.STANDARD))
        assertNull(NetworkAlert.change(Protection.STANDARD, Protection.OLD))
        assertNull(NetworkAlert.change(null, Protection.STANDARD))
    }
}
