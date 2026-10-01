package com.dialer.app.core.network

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CellProtectionTest {

    @Test
    fun fiveGStandaloneIsTheOnlyProtectedCellular() {
        assertEquals(Protocol.NR_SA, CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_NR))
        assertEquals(Protection.PROTECTED, CellProtection.protectionOf(Protocol.NR_SA))
    }

    @Test
    fun fiveGOnFourGIsStandard() {
        val nsa = CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA)
        assertEquals(Protocol.NR_NSA, nsa)
        assertEquals(Protection.STANDARD, CellProtection.protectionOf(nsa))
        assertEquals(Protection.STANDARD, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_LTE)))
    }

    @Test
    fun oldNetworks() {
        assertEquals(Protection.UNPROTECTED, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_EDGE)))
        assertEquals(Protection.UNPROTECTED, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_GSM)))
        assertEquals(Protection.OLD, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_HSPAP)))
    }

    @Test
    fun wifiCallingAndNothing() {
        assertEquals(Protection.PROTECTED, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_IWLAN)))
        assertEquals(Protection.UNKNOWN, CellProtection.protectionOf(CellProtection.protocolOf(TelephonyManager.NETWORK_TYPE_UNKNOWN)))
    }
}
