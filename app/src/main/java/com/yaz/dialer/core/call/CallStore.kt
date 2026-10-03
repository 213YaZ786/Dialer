package com.yaz.dialer.core.call

import android.content.Context
import com.yaz.dialer.data.settings.SettingsStore
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.Connection
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a call stands, in the words the call screen needs. */
enum class CallPhase { CHOOSE_SIM, DIALING, RINGING, ACTIVE, HOLDING, ENDED }

/** A SIM, or any account a call can go out on, as Telecom offers it. */
data class SimChoice(val handle: PhoneAccountHandle, val label: String)

data class CallInfo(
    /** Stable for the life of the call, for keys and notification actions. */
    val id: Int,
    val phase: CallPhase,
    /** The contact's name when the number is saved, or the network's caller name. */
    val name: String?,
    /** The number as people write it, or what stands for it when it is hidden. */
    val number: String,
    /** When the call was connected, 0 before. Drives the timer. */
    val connectedAt: Long,
    val isConference: Boolean,
    val canHold: Boolean,
    val canMute: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    /** Why it ended, in the network's own words when it gave any. */
    val endedReason: String?,
    val sims: List<SimChoice>,
    /** The people of a conference, each its own call inside it. */
    val participants: List<Participant> = emptyList(),
    /** A ringing call that can be declined with a text, and the replies offered. */
    val canReplyByText: Boolean = false,
    val replies: List<String> = emptyList(),
    /** Placed from this phone, not received: it vibrates once when answered. */
    val outgoing: Boolean = false,
    /** Over Wi-Fi calling rather than the mobile network. */
    val wifi: Boolean = false,
    /** High definition voice (VoLTE, VoNR, Wi-Fi calling). */
    val hd: Boolean = false,
    /** What the network says of the caller's number (STIR/SHAKEN): checked, failed, or not known. */
    val numberCheck: NumberCheck = NumberCheck.NONE,
    /** Over the messaging app's end-to-end encrypted line (a self-managed call), not the phone network. */
    val encrypted: Boolean = false,
    /** A video call: the other side's picture and one's own. */
    val video: Boolean = false,
    /** The SIM the call goes over, or -1 when it is not known (or not a SIM's call). */
    val subId: Int = -1
) {
    /** What the screen shows big: the name, else the number. */
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

/** One person in a conference: they can be talked to alone, or let go. */
data class Participant(val id: Int, val title: String, val canSplit: Boolean, val canHangUp: Boolean)

/** The network's check of an incoming number, as French and US carriers do it now. */
enum class NumberCheck { NONE, VERIFIED, FAILED }

/** Where the sound of the call goes. */
data class AudioRoute(val kind: Kind, val label: String) {
    enum class Kind { EARPIECE, SPEAKER, BLUETOOTH, WIRED, OTHER }
}

data class CallsState(
    val calls: List<CallInfo> = emptyList(),
    val muted: Boolean = false,
    val route: AudioRoute? = null,
    val routes: List<AudioRoute> = emptyList()
) {
    /** The call the screen is about: ringing first, then the live one, then the rest. */
    val primary: CallInfo?
        get() = calls.firstOrNull { it.phase == CallPhase.RINGING }
            ?: calls.firstOrNull { it.phase == CallPhase.CHOOSE_SIM }
            ?: calls.firstOrNull { it.phase == CallPhase.ACTIVE || it.phase == CallPhase.DIALING }
            ?: calls.firstOrNull { it.phase == CallPhase.HOLDING }
            ?: calls.firstOrNull()

    /** Another call waiting on hold while the primary one goes on. */
    val secondary: CallInfo?
        get() = primary?.let { p -> calls.firstOrNull { it.id != p.id && it.phase != CallPhase.ENDED } }

    val anyRinging: Boolean get() = calls.any { it.phase == CallPhase.RINGING }
    val anyLive: Boolean get() = calls.any { it.phase != CallPhase.ENDED }
}

/**
 * What the system hands the app about its calls, kept in one place.
 *
 * Telecom gives the calls to the InCallService, which can live without any
 * screen. The call screen and the notifications only read this state and
 * ask for actions here; the service does what needs its own powers (audio
 * routes, mute) through [Controls], set while it is bound.
 */
class CallStore(private val context: Context, private val scope: CoroutineScope, private val settings: SettingsStore) {

    /** What only the bound InCallService can do. */
    interface Controls {
        fun applyMute(muted: Boolean)
        fun applyRoute(route: AudioRoute)
    }

    /**
     * The call screen is in front of the user. A ringing call then needs no
     * banner over it: the screen already offers Answer and Decline.
     */
    private val _screenShown = MutableStateFlow(false)
    val screenShown: StateFlow<Boolean> = _screenShown.asStateFlow()
    fun screenShown(shown: Boolean) { _screenShown.value = shown }

    /** The island over the other apps holds a ringing call: no banner on top of it. */
    private val _islandShown = MutableStateFlow(false)
    val islandShown: StateFlow<Boolean> = _islandShown.asStateFlow()
    fun islandShown(shown: Boolean) { _islandShown.value = shown }

    private val _state = MutableStateFlow(CallsState())
    val state: StateFlow<CallsState> = _state.asStateFlow()

    private val ids = AtomicInteger(0)
    private val answered = HashSet<Int>()
    private val calls = LinkedHashMap<Int, Call>()
    private val ended = HashMap<Int, CallInfo>()
    private val callbacks = HashMap<Int, Call.Callback>()
    var controls: Controls? = null

    fun add(call: Call) {
        val id = ids.incrementAndGet()
        calls[id] = call
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
            override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
            override fun onParentChanged(call: Call, parent: Call?) = publish()
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = publish()
            override fun onCannedTextResponsesLoaded(call: Call, cannedTextResponses: List<String>) = publish()
        }
        callbacks[id] = callback
        call.registerCallback(callback)
        publish()
    }

    fun remove(call: Call) {
        val id = idOf(call) ?: return
        callbacks.remove(id)?.let(call::unregisterCallback)
        // Kept a moment as ended, so the screen can say so instead of
        // vanishing under the user's thumb.
        ended[id] = info(id, call).copy(phase = CallPhase.ENDED)
        calls.remove(id)
        publish()
        scope.launch {
            delay(ENDED_LINGER_MS)
            ended.remove(id)
            publish()
        }
    }

    fun setAudio(muted: Boolean, route: AudioRoute?, routes: List<AudioRoute>) {
        _state.update { it.copy(muted = muted, route = route, routes = routes) }
    }

    /** Answered as it was offered: a video call stays a video call. */
    fun answer(id: Int) = calls[id]?.let { it.answer(it.details.videoState) }

    /**
     * A second call rings during one: the first is hung up, and the new one
     * answered once it has gone, so Telecom does not hold it on the way.
     */
    fun endAndAnswer(id: Int) {
        val others = calls.filter { (other, call) -> other != id && call.parent == null && call.details.state != Call.STATE_RINGING }
        others.values.forEach { it.disconnect() }
        scope.launch {
            val until = System.currentTimeMillis() + 2_000
            while (others.keys.any { it in calls } && System.currentTimeMillis() < until) delay(50)
            calls[id]?.answer(0)
        }
    }
    fun decline(id: Int) = calls[id]?.reject(false, null)

    /** Declines and sends [text] to the caller; Telecom sends it, no SMS access needed here. */
    fun reply(id: Int, text: String) = calls[id]?.reject(true, text)

    /** Stops the ringing without answering: the call goes on until the caller gives up or voicemail takes it. */
    fun silence() {
        runCatching { context.getSystemService(TelecomManager::class.java).silenceRinger() }
        _silenced.value = true
    }

    private val _silenced = MutableStateFlow(false)
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()
    fun hangUp(id: Int) = calls[id]?.disconnect()
    fun hold(id: Int, on: Boolean) = calls[id]?.let { if (on) it.hold() else it.unhold() }

    /** A video call's channel to the line that carries it, for its pictures and camera. */
    fun videoCall(id: Int): android.telecom.InCallService.VideoCall? = calls[id]?.videoCall

    /** The camera of a video call: on or off, front or back. */
    data class Camera(val on: Boolean = true, val front: Boolean = true)
    private val _camera = MutableStateFlow(Camera())
    val camera: StateFlow<Camera> = _camera.asStateFlow()

    fun camera(id: Int, choice: Camera) {
        _camera.value = choice
        val cameraId = if (!choice.on) null else cameraIdFacing(choice.front)
        runCatching { videoCall(id)?.setCamera(cameraId) }
    }

    private fun cameraIdFacing(front: Boolean): String? = runCatching {
        val cm = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val want = if (front) android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT else android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == want }
            ?: cm.cameraIdList.firstOrNull()
    }.getOrNull()
    fun mute(on: Boolean) = controls?.applyMute(on)
    fun route(route: AudioRoute) = controls?.applyRoute(route)
    fun chooseSim(id: Int, sim: SimChoice) = calls[id]?.phoneAccountSelected(sim.handle, false)

    /** Keypad tones while a key is held, for voice menus. */
    fun tone(id: Int, key: Char?) = calls[id]?.let { if (key == null) it.stopDtmfTone() else it.playDtmfTone(key) }

    /** Takes one person out of the conference to talk to them alone; the others wait on hold. */
    fun split(id: Int) = calls[id]?.splitFromConference()

    /** Joins the live call with the other one into a conference. */
    fun merge(id: Int) {
        val call = calls[id] ?: return
        val other = call.conferenceableCalls.firstOrNull()
        if (other != null) call.conference(other) else call.mergeConference()
    }

    /** Puts the live call on hold and takes the held one back. */
    fun swap(id: Int) {
        val call = calls[id] ?: return
        if (call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
            call.swapConference()
            return
        }
        // Holding the live call lets Telecom bring the other one back.
        val held = calls.entries.firstOrNull { it.key != id && it.value.details.state == Call.STATE_HOLDING }
        if (held != null) held.value.unhold() else call.hold()
    }

    private fun buzz() {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            } else {
                vibrator.vibrate(effect)
            }
        }
    }

    private fun idOf(call: Call): Int? = calls.entries.firstOrNull { it.value == call }?.key

    private fun publish() {
        // A call inside a conference is shown as the conference, not alone.
        val live = calls.filter { it.value.parent == null }.map { (id, call) -> info(id, call) }
        _state.update { it.copy(calls = live + ended.values.sortedBy(CallInfo::id)) }
        if (live.none { it.phase == CallPhase.RINGING }) _silenced.value = false
        // The phone is often at the ear or in a pocket while it rings out:
        // one firm buzz says the other side picked up.
        live.filter { it.outgoing && it.phase == CallPhase.ACTIVE && answered.add(it.id) }
            .forEach { _ -> if (settings.current.vibrateOnAnswer) buzz() }
    }

    private fun info(id: Int, call: Call): CallInfo {
        val details = call.details
        val phase = when (details.state) {
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallPhase.CHOOSE_SIM
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallPhase.RINGING
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_HOLDING -> CallPhase.HOLDING
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallPhase.ENDED
            else -> CallPhase.DIALING
        }
        val name = details.contactDisplayName?.toString()?.takeIf { it.isNotBlank() }
            ?: details.callerDisplayName?.takeIf {
                it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED
            }
        return CallInfo(
            id = id,
            phase = phase,
            name = if (call.children.isNotEmpty()) "Conference call" else name,
            number = numberOf(details),
            connectedAt = details.connectTimeMillis,
            isConference = call.children.isNotEmpty(),
            canHold = details.can(Call.Details.CAPABILITY_HOLD) || details.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMute = details.can(Call.Details.CAPABILITY_MUTE),
            canMerge = call.conferenceableCalls.isNotEmpty() || details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwap = details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE) || calls.size > 1,
            endedReason = details.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() },
            sims = if (phase == CallPhase.CHOOSE_SIM) simsOffered(details) else emptyList(),
            canReplyByText = phase == CallPhase.RINGING && details.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT),
            replies = call.cannedTextResponses.orEmpty().ifEmpty { DEFAULT_REPLIES },
            outgoing = details.callDirection == Call.Details.DIRECTION_OUTGOING,
            wifi = details.hasProperty(Call.Details.PROPERTY_WIFI),
            hd = details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO),
            encrypted = EncryptedCalls.isEncrypted(call),
            video = android.telecom.VideoProfile.isVideo(details.videoState) && call.videoCall != null,
            subId = details.accountHandle?.let { handle ->
                runCatching { context.getSystemService(android.telephony.TelephonyManager::class.java).getSubscriptionId(handle) }.getOrNull()
            } ?: -1,
            numberCheck = if (details.callDirection != Call.Details.DIRECTION_INCOMING) NumberCheck.NONE else when (details.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> NumberCheck.VERIFIED
                Connection.VERIFICATION_STATUS_FAILED -> NumberCheck.FAILED
                else -> NumberCheck.NONE
            },
            participants = call.children.mapNotNull { child ->
                val childId = idOf(child) ?: return@mapNotNull null
                val d = child.details
                Participant(
                    id = childId,
                    title = d.contactDisplayName?.toString()?.takeIf { it.isNotBlank() } ?: numberOf(d),
                    canSplit = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
                    canHangUp = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)
                )
            }
        )
    }

    private fun numberOf(details: Call.Details): String {
        when (details.handlePresentation) {
            TelecomManager.PRESENTATION_RESTRICTED -> return "Private number"
            TelecomManager.PRESENTATION_PAYPHONE -> return "Payphone"
            TelecomManager.PRESENTATION_UNKNOWN -> return "Unknown number"
        }
        val raw = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
            ?: details.handle?.let(Uri::toString)
            ?: return "Unknown number"
        return PhoneNumberUtils.formatNumber(raw, countryIso()) ?: raw
    }

    private fun simsOffered(details: Call.Details): List<SimChoice> {
        val handles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            details.extras?.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, PhoneAccountHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            details.extras?.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS)
        }.orEmpty()
        val telecom = context.getSystemService(TelecomManager::class.java)
        return handles.mapIndexed { index, handle ->
            val label = runCatching { telecom.getPhoneAccount(handle)?.label?.toString() }.getOrNull()
            SimChoice(handle, label?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}")
        }
    }

    /** The country of the network, else of the phone's language, to format numbers the local way. */
    private fun countryIso(): String =
        context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase()
            ?: Locale.getDefault().country

    private companion object {
        const val ENDED_LINGER_MS = 1800L
        val DEFAULT_REPLIES = listOf(
            "Can't talk now. What's up?",
            "I'll call you right back.",
            "I'll call you later.",
            "Can't talk now. Call me later?"
        )
    }
}
