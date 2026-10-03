package com.dialer.app.core.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.dialer.app.data.settings.AnnounceMode
import java.util.Locale

/**
 * Turning the phone face down while it rings stops the ringing, as on
 * Pixels. Only a turn counts: a phone already lying face down when the
 * call comes has to be lifted first.
 */
class FlipToSilence(context: Context, private val onFlip: () -> Unit) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private var listening = false
    private var wasUp = false
    private var downSince = 0L

    fun start() {
        if (listening) return
        val gravity = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        wasUp = false
        downSince = 0L
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values[2]
        when {
            z > UP -> {
                wasUp = true
                downSince = 0L
            }
            z < DOWN && wasUp -> {
                val now = SystemClock.elapsedRealtime()
                if (downSince == 0L) downSince = now
                // Held there a moment, so a phone waved about does not count.
                if (now - downSince > HOLD_MS) {
                    stop()
                    onFlip()
                }
            }
            else -> downSince = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val UP = 3f
        const val DOWN = -8.5f
        const val HOLD_MS = 350L
    }
}

/**
 * Reads out who is calling while the phone rings: the contact's name, or
 * the number in groups of two. With headphones only, or always, as chosen.
 * Android's own text to speech does it, on the phone.
 */
class CallerAnnouncer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun announce(mode: AnnounceMode, name: String?, number: String) {
        if (mode == AnnounceMode.OFF) return
        if (mode == AnnounceMode.HEADPHONES && !headphones()) return
        val words = name ?: number.filter(Char::isDigit).chunked(2).joinToString(" ").ifBlank { return }
        val engine = tts
        if (engine == null) {
            pending = words
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.language = Locale.getDefault()
                    pending?.let(::speak)
                }
                pending = null
            }
        } else if (ready) {
            speak(words)
        }
    }

    private fun speak(words: String) {
        // Twice, a moment apart, as the ringtone goes on between.
        tts?.speak(words, TextToSpeech.QUEUE_FLUSH, Bundle(), "caller")
        tts?.playSilentUtterance(1800, TextToSpeech.QUEUE_ADD, "pause")
        tts?.speak(words, TextToSpeech.QUEUE_ADD, Bundle(), "caller-again")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun headphones(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET
            )
        }
    }
}

/**
 * Some people's calls ring through Do not disturb and silent, as chosen in
 * the Contacts app ("bypass" in their contact): Telecom stays quiet then,
 * so the phone rings here on the alarm's sound, with their ringtone, until
 * the call is answered, declined or ends. When Telecom rings anyway (the
 * phone not muted, or Do not disturb letting their calls through), nothing
 * more: never two rings.
 */
class BypassRinger(private val context: Context) {

    private var ringtone: android.media.Ringtone? = null
    private var vibrating = false
    private var forCall: Int? = null

    fun ring(callId: Int, number: String) {
        if (forCall == callId) return
        stop()
        forCall = callId
        Thread {
            val look = com.dialer.app.core.dial.ContactLook.ofNumber(context, number)
            if (look?.bypass != true || !quietFor(number)) return@Thread
            val uri = contactRingtone(number) ?: android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)
            android.os.Handler(context.mainLooper).post {
                if (forCall != callId) return@post
                ringtone = uri?.let { android.media.RingtoneManager.getRingtone(context, it) }?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    isLooping = true
                    runCatching { play() }
                }
                runCatching {
                    val effect = android.os.VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
                    context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                        .vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM))
                    vibrating = true
                }
            }
        }.start()
    }

    fun stop() {
        forCall = null
        runCatching { ringtone?.stop() }
        ringtone = null
        if (vibrating) runCatching { context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator.cancel() }
        vibrating = false
    }

    /** Telecom will not ring this call aloud: silent or vibrate, or Do not disturb not letting it through. */
    private fun quietFor(number: String): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        return when (nm.currentInterruptionFilter) {
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL, android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> false
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> !callsAllowed(nm, number)
            else -> true
        }
    }

    /** Whether Do not disturb's own rules already let this caller's calls ring. */
    private fun callsAllowed(nm: android.app.NotificationManager, number: String): Boolean = runCatching {
        val policy = nm.notificationPolicy
        if (policy.priorityCategories and android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CALLS == 0) return false
        when (policy.priorityCallSenders) {
            android.app.NotificationManager.Policy.PRIORITY_SENDERS_ANY -> true
            android.app.NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS -> com.dialer.app.core.dial.ContactLookup.nameOf(context, number) != null
            android.app.NotificationManager.Policy.PRIORITY_SENDERS_STARRED -> starred(number)
            else -> false
        }
    }.getOrDefault(false)

    private fun starred(number: String): Boolean = runCatching {
        context.contentResolver.query(
            android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number)),
            arrayOf(android.provider.ContactsContract.PhoneLookup.STARRED), null, null, null
        )?.use { c -> c.moveToFirst() && c.getInt(0) == 1 }
    }.getOrNull() == true

    /** The ringtone chosen for this person in their contact, if any. */
    private fun contactRingtone(number: String): android.net.Uri? = runCatching {
        context.contentResolver.query(
            android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number)),
            arrayOf(android.provider.ContactsContract.PhoneLookup.CUSTOM_RINGTONE), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.let(android.net.Uri::parse) else null }
    }.getOrNull()
}
