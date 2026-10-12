package com.august.intervaltimer.timer

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays the audio cues for the timer. ToneGenerator needs no Context and no
 * audio files, which keeps things simple; real sound design can come later.
 */
class SoundPlayer(volume: Int = DEFAULT_VOLUME) {

    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volume.coerceIn(0, 100)) }
            .getOrNull()

    fun countdownTick() = play(ToneGenerator.TONE_PROP_BEEP, 150)

    fun workStart() = play(ToneGenerator.TONE_PROP_BEEP2, 400)

    fun restStart() = play(ToneGenerator.TONE_PROP_ACK, 400)

    fun finish() = play(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 800)

    fun release() {
        tone?.release()
    }

    private fun play(toneType: Int, durationMillis: Int) {
        runCatching { tone?.startTone(toneType, durationMillis) }
    }

    private companion object {
        const val DEFAULT_VOLUME = 85
    }
}
