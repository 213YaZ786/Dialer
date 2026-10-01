package com.dialer.app.core.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** One SIM and the networks it is on: the one calls use, the one data uses. */
data class SimNetwork(val subId: Int, val name: String, val voice: Protocol, val data: Protocol, val isDefaultVoice: Boolean) {
    /** Calls are what this app is about: the voice network decides, data only when voice has none. */
    val forCalls: Protocol get() = if (voice != Protocol.NONE) voice else data
    val protection: Protection get() = CellProtection.protectionOf(forCalls)
}

/**
 * The SIMs' networks, followed live, but only while something shows them:
 * the dialpad, a call, the settings. Nothing runs in the background for it.
 */
class CellWatch(private val context: Context, scope: CoroutineScope) {

    val sims: StateFlow<List<SimNetwork>> = watch().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun allowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun watch() = callbackFlow {
        if (!allowed()) {
            awaitClose { }
            return@callbackFlow
        }
        val base = context.getSystemService(TelephonyManager::class.java)
        val subs = runCatching { context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList }.getOrNull().orEmpty()
        val defaultVoice = SubscriptionManager.getDefaultVoiceSubscriptionId()
        val state = LinkedHashMap<Int, SimNetwork>()
        subs.forEachIndexed { i, sub ->
            val name = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}"
            state[sub.subscriptionId] = SimNetwork(sub.subscriptionId, name, Protocol.NONE, Protocol.NONE, sub.subscriptionId == defaultVoice || subs.size == 1)
        }
        trySend(state.values.toList())

        val registered = subs.map { sub ->
            val tm = base.createForSubscriptionId(sub.subscriptionId)
            val id = sub.subscriptionId
            val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener, TelephonyCallback.ServiceStateListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    state[id] = state.getValue(id).copy(data = CellProtection.protocolOf(info.networkType, info.overrideNetworkType))
                    trySend(state.values.toList())
                }

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    val voice = runCatching { tm.voiceNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    val inService = serviceState.state == ServiceState.STATE_IN_SERVICE
                    state[id] = state.getValue(id).copy(voice = if (inService) CellProtection.protocolOf(voice) else Protocol.NONE)
                    trySend(state.values.toList())
                }
            }
            runCatching { tm.registerTelephonyCallback(context.mainExecutor, callback) }
            tm to callback
        }
        awaitClose { registered.forEach { (tm, callback) -> runCatching { tm.unregisterTelephonyCallback(callback) } } }
    }
}
