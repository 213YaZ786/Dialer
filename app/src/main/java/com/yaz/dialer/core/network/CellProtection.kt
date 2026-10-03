package com.yaz.dialer.core.network

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/** The generation of mobile network a SIM is on, as far as protection goes. */
enum class Protocol(val label: String) {
    NR_SA("5G"),
    NR_NSA("5G NSA"),
    LTE("4G"),
    G3("3G"),
    G2("2G"),
    WIFI("Wi-Fi"),
    NONE("No network")
}

/**
 * How well a network protects calls and the SIM's identity.
 *
 * 5G standalone hides the SIM's identity from fake antennas (IMSI
 * catchers) and is the only one that does. 4G, and 5G built on 4G, encrypt
 * well but give the identity away to a fake antenna: the usual case, not
 * an alarm. 3G is old. 2G encryption can be broken with cheap equipment
 * and a fake antenna can switch it off: calls on it can be listened to.
 * Wi-Fi calling goes through an encrypted tunnel to the carrier.
 * None of them hides a call from the carrier itself (and lawful
 * interception through it): that takes an end-to-end encrypted call.
 */
enum class Protection(val label: String) {
    PROTECTED("Protected"),
    STANDARD("Standard"),
    OLD("Old network"),
    UNPROTECTED("Not protected"),
    UNKNOWN("Unknown")
}

object CellProtection {

    /** A network type of TelephonyManager, the voice one or the data one. */
    fun protocolOf(networkType: Int, override: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE): Protocol = when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> Protocol.NR_SA
        TelephonyManager.NETWORK_TYPE_LTE -> when (override) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> Protocol.NR_NSA
            else -> Protocol.LTE
        }
        TelephonyManager.NETWORK_TYPE_IWLAN -> Protocol.WIFI
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GSM,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> Protocol.G2
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> Protocol.G3
        else -> Protocol.NONE
    }

    fun protectionOf(protocol: Protocol): Protection = when (protocol) {
        Protocol.NR_SA, Protocol.WIFI -> Protection.PROTECTED
        Protocol.NR_NSA, Protocol.LTE -> Protection.STANDARD
        Protocol.G3 -> Protection.OLD
        Protocol.G2 -> Protection.UNPROTECTED
        Protocol.NONE -> Protection.UNKNOWN
    }

    /** What a person needs to know about [protection], in one sentence. */
    fun meaning(protection: Protection): String = when (protection) {
        // Over the air only: the carrier, and those it lets listen, still hear phone calls.
        Protection.PROTECTED -> "Encrypted between your phone and the network, your SIM's identity hidden. Your carrier can still hear the call: only encrypted calls with SMS users are private end to end."
        Protection.STANDARD -> "Encrypted between your phone and the antenna only. Your carrier can still hear the call: only encrypted calls with SMS users are private end to end."
        Protection.OLD -> "An old network with weaker protection."
        Protection.UNPROTECTED -> "A call on 2G can be listened to. Turn off 2G to avoid it."
        Protection.UNKNOWN -> "No mobile network right now."
    }
}
