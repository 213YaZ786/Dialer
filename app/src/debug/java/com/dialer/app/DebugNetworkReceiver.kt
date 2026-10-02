package com.dialer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dialer.app.core.network.CellWatch
import com.dialer.app.core.network.Protocol

/**
 * Debug builds only: puts the SIMs on another network for a test, since
 * the emulator's radio never leaves 5G.
 * `adb shell am broadcast -n com.dialer.app.debug/com.dialer.app.DebugNetworkReceiver --es net 2G`
 * (2G, 3G, 4G, 5G, or nothing to let the real network back).
 */
class DebugNetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CellWatch.forced.value = when (intent.getStringExtra("net")) {
            "2G" -> Protocol.G2
            "3G" -> Protocol.G3
            "4G" -> Protocol.LTE
            "5G" -> Protocol.NR_SA
            else -> null
        }
    }
}
