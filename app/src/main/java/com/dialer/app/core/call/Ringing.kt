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
