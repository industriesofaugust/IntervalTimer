package com.august.intervaltimer.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.august.intervaltimer.timer.TimerCue

/**
 * Turns timer cues into distinct vibration patterns, so you can tell
 * "work is starting" from "rest is starting" without looking at the phone.
 * The patterns match the watch's. The 1-5 level scales both strength and
 * pattern length, because a buzz that's easy to feel at a desk disappears
 * mid-burpee.
 */
class HapticsPlayer(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        val manager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun play(cue: TimerCue, level: Int) {
        val step = level.coerceIn(1, 5) - 1
        val amplitude = (80 + step * 44).coerceAtMost(255)
        val scale = 1.0 + step * 0.2

        fun ms(base: Long) = (base * scale).toLong()

        val effect = when (cue) {
            // Short tap for each of the 3-2-1 seconds.
            TimerCue.COUNTDOWN_TICK -> buzz(amplitude, 0, ms(55))
            // Two sharp buzzes: go time.
            TimerCue.WORK_START -> buzz(amplitude, 0, ms(150), ms(90), ms(150))
            // One long, slightly softer buzz: ease off.
            TimerCue.REST_START ->
                buzz((amplitude * 3 / 4).coerceAtLeast(70), 0, ms(320))
            // Three buzzes, last one long: all done.
            TimerCue.FINISH -> buzz(amplitude, 0, ms(150), ms(90), ms(150), ms(90), ms(380))
        }
        runCatching { vibrator.vibrate(effect) }
    }

    /**
     * [timings] alternate pause, buzz, pause, buzz... starting with a pause.
     * Phones whose motor can't vary its strength ignore the amplitude, so
     * there the level only changes how long the buzzes last.
     */
    private fun buzz(amplitude: Int, vararg timings: Long): VibrationEffect =
        if (vibrator.hasAmplitudeControl()) {
            val amplitudes = IntArray(timings.size) { if (it % 2 == 1) amplitude else 0 }
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
}
