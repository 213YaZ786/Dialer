package com.dialer.app.core.dial

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings

/**
 * The tone each key makes while typing a number, the way phones do, only
 * when "Dial pad tones" is on in Android's sound settings and the ringer is
 * not silent. During a call the network plays the tones, not this.
 */
class KeyTones(private val context: Context) {

    private var generator: ToneGenerator? = null

    private fun enabled(): Boolean {
        val setting = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        val ringer = context.getSystemService(AudioManager::class.java).ringerMode
        return setting && ringer == AudioManager.RINGER_MODE_NORMAL
    }

    fun start(key: Char) {
        if (!enabled()) return
        val tone = when (key) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (key - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        val g = generator ?: runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull()?.also { generator = it }
        g?.startTone(tone, MAX_TONE_MS)
    }

    fun stop() {
        generator?.stopTone()
    }

    fun release() {
        generator?.release()
        generator = null
    }

    private companion object {
        const val TONE_VOLUME = 80
        const val MAX_TONE_MS = 300
    }
}
