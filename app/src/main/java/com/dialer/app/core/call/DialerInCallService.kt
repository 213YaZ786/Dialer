package com.dialer.app.core.call

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.dialer.app.feature.call.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bound by Telecom while there is at least one call, because Dialer is the
 * phone app. It hands the calls to [CallStore], keeps the call notification
 * up as a foreground service so the call outlives any screen, and owns
 * what only it can do: the audio route and the microphone.
 *
 * Audio routes: Android 14 introduced call endpoints, which name each
 * Bluetooth device; older versions only know the route kinds. Both are
 * handled, the newer one when present.
 */
class DialerInCallService : InCallService(), KoinComponent, CallStore.Controls {

    private val store: CallStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifier: CallNotifier
    private var watching: Job? = null
    private var foreground = false

    private var endpoints: List<CallEndpoint> = emptyList()

    override fun onCreate() {
        super.onCreate()
        notifier = CallNotifier(this)
        store.controls = this
        watching = scope.launch {
            combine(store.state, store.screenShown) { state, shown -> state to shown }
                .collect { (state, shown) -> show(state, shown) }
        }
    }

    override fun onDestroy() {
        if (store.controls === this) store.controls = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        store.add(call)
        // A call going out, or picked up elsewhere, gets the screen at once.
        // A ringing one goes through the notification's full screen intent,
        // which is how Android wants an incoming call to wake the phone.
        if (call.details.state != Call.STATE_RINGING) openScreen()
    }

    override fun onCallRemoved(call: Call) {
        store.remove(call)
    }

    private fun openScreen() {
        startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** One notification follows the calls: incoming, then ongoing, then gone. */
    private fun show(state: CallsState, screenShown: Boolean) {
        val notification = notifier.build(state, screenShown)
        if (notification == null) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            return
        }
        startForeground(CallNotifier.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        foreground = true
    }

    // Audio, Android 14 and later.

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        endpoints = availableEndpoints
        publishEndpoints(current = null)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) = publishEndpoints(callEndpoint)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) {
        val s = store.state.value
        store.setAudio(isMuted, s.route, s.routes)
    }

    private var currentEndpoint: CallEndpoint? = null

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun publishEndpoints(current: CallEndpoint?) {
        if (current != null) currentEndpoint = current
        val s = store.state.value
        store.setAudio(s.muted, currentEndpoint?.let(::routeOf), endpoints.map(::routeOf))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun routeOf(endpoint: CallEndpoint): AudioRoute {
        val kind = when (endpoint.endpointType) {
            CallEndpoint.TYPE_EARPIECE -> AudioRoute.Kind.EARPIECE
            CallEndpoint.TYPE_SPEAKER -> AudioRoute.Kind.SPEAKER
            CallEndpoint.TYPE_BLUETOOTH -> AudioRoute.Kind.BLUETOOTH
            CallEndpoint.TYPE_WIRED_HEADSET -> AudioRoute.Kind.WIRED
            else -> AudioRoute.Kind.OTHER
        }
        // A Bluetooth device keeps its own name, the others take a plain one.
        val label = when (kind) {
            AudioRoute.Kind.EARPIECE -> "Phone"
            AudioRoute.Kind.SPEAKER -> "Speaker"
            AudioRoute.Kind.WIRED -> "Wired headset"
            else -> endpoint.endpointName.toString().ifBlank { "Bluetooth" }
        }
        return AudioRoute(kind, label)
    }

    // Audio, Android 12 and 13.

    @Deprecated("Replaced by call endpoints on Android 14, still the only way before it")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val routes = buildList {
            if (audioState.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute(AudioRoute.Kind.EARPIECE, "Phone"))
            if (audioState.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute(AudioRoute.Kind.WIRED, "Wired headset"))
            if (audioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute(AudioRoute.Kind.SPEAKER, "Speaker"))
            if (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                add(AudioRoute(AudioRoute.Kind.BLUETOOTH, audioState.activeBluetoothDevice?.let { runCatching { it.name }.getOrNull() } ?: "Bluetooth"))
            }
        }
        val current = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRoute.Kind.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.Kind.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.Kind.WIRED
            else -> AudioRoute.Kind.EARPIECE
        }
        store.setAudio(audioState.isMuted, routes.firstOrNull { it.kind == current }, routes)
    }

    // Controls for the call screen.

    override fun applyMute(muted: Boolean) = setMuted(muted)

    override fun applyRoute(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val target = endpoints.firstOrNull { routeOf(it) == route } ?: return
            requestCallEndpointChange(target, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) = Unit
            })
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(
                when (route.kind) {
                    AudioRoute.Kind.SPEAKER -> CallAudioState.ROUTE_SPEAKER
                    AudioRoute.Kind.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
                    AudioRoute.Kind.WIRED -> CallAudioState.ROUTE_WIRED_HEADSET
                    else -> CallAudioState.ROUTE_EARPIECE
                }
            )
        }
    }
}
