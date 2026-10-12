package com.august.intervaltimer.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.august.intervaltimer.timer.TimerCue

/**
 * Turns timer cues into distinct vibration patterns, so you can tell
 * "work is starting" from "rest is starting" without looking at the watch.
 * The 1–5 level scales both strength and pattern length, because a buzz
 * that's easy to feel at a desk disappears mid-burpee.
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
            TimerCue.COUNTDOWN_TICK ->
                VibrationEffect.createOneShot(ms(55), amplitude)
            // Two sharp buzzes: go time.
            TimerCue.WORK_START -> VibrationEffect.createWaveform(
                longArrayOf(0, ms(150), ms(90), ms(150)),
                intArrayOf(0, amplitude, 0, amplitude),
                -1,
            )
            // One long, slightly softer buzz: ease off.
            TimerCue.REST_START ->
                VibrationEffect.createOneShot(ms(320), (amplitude * 3 / 4).coerceAtLeast(70))
            // Three buzzes, last one long: all done.
            TimerCue.FINISH -> VibrationEffect.createWaveform(
                longArrayOf(0, ms(150), ms(90), ms(150), ms(90), ms(380)),
                intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                -1,
            )
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
