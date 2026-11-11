package com.august.intervaltimer.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.august.intervaltimer.data.AppSettings
import com.august.intervaltimer.data.WhenWatchJoins
import com.august.intervaltimer.haptics.HapticsPlayer
import com.august.intervaltimer.model.IntervalType
import com.august.intervaltimer.model.isGeneratedName
import com.august.intervaltimer.sync.LinkState
import com.august.intervaltimer.timer.SoundPlayer
import com.august.intervaltimer.timer.TimerCue
import com.august.intervaltimer.timer.TimerPhase
import com.august.intervaltimer.timer.TimerUiState

/**
 * The phone's answer to a timer cue: a beep, a buzz, and (if switched on) a
 * spoken line such as "Squats" or "Rest. Next: Lunges". Music from other apps
 * is turned down for a moment while a cue plays, then comes back.
 */
class CuePlayer(context: Context) {

    private val appContext = context.applicationContext
    private val haptics = HapticsPlayer(appContext)
    private val ducker = AudioDucker(appContext)
    private var sound: SoundPlayer? = null
    private var soundVolume = -1
    private var voice: VoicePlayer? = null

    fun play(cue: TimerCue, state: TimerUiState, settings: AppSettings, link: LinkState) {
        // While the watch is running the workout too, the setting decides what the phone adds.
        // On NO WATCH or LOST the watch is not cueing, so the phone does.
        val watchJoined = link == LinkState.LINKED
        val playSound = !watchJoined || settings.whenWatchJoins != WhenWatchJoins.QUIET
        val playVibration = !watchJoined || settings.whenWatchJoins == WhenWatchJoins.BOTH

        if (settings.vibrate && playVibration) haptics.play(cue, settings.vibrationLevel)
        if (!playSound) return

        if (settings.soundEnabled) {
            ducker.duckFor(BEEP_DUCK_MILLIS)
            val player = soundFor(settings.soundVolume)
            when (cue) {
                TimerCue.COUNTDOWN_TICK -> player.countdownTick()
                TimerCue.WORK_START -> player.workStart()
                TimerCue.REST_START -> player.restStart()
                TimerCue.FINISH -> player.finish()
            }
        }
        if (settings.voiceEnabled) {
            spokenLine(cue, state)?.let { line ->
                ducker.duckFor(VOICE_DUCK_MILLIS)
                voiceFor().speak(line, settings.soundVolume / 100f)
            }
        }
    }

    /** Loads the voice ahead of the first cue, because it takes a moment to start. */
    fun warmUp(settings: AppSettings) {
        if (settings.voiceEnabled) voiceFor()
    }

    /** Frees the sound and speech engines; they are made again when next needed. */
    fun release() {
        sound?.release()
        sound = null
        soundVolume = -1
        voice?.release()
        voice = null
        ducker.releaseNow()
    }

    private fun soundFor(volume: Int): SoundPlayer {
        sound?.takeIf { soundVolume == volume }?.let { return it }
        sound?.release()
        return SoundPlayer(volume).also {
            sound = it
            soundVolume = volume
        }
    }

    private fun voiceFor(): VoicePlayer =
        voice ?: VoicePlayer(appContext, onFinished = { ducker.duckFor(AFTER_VOICE_MILLIS) }).also { voice = it }

    private companion object {
        const val BEEP_DUCK_MILLIS = 1_200L
        // Only a ceiling: the voice says when it has finished.
        const val VOICE_DUCK_MILLIS = 8_000L
        const val AFTER_VOICE_MILLIS = 300L
    }
}

/**
 * What to say for a cue, or null to stay silent. Work says the exercise name;
 * rest also says what is coming, so you can get in position.
 */
internal fun spokenLine(cue: TimerCue, state: TimerUiState): String? = when (cue) {
    TimerCue.COUNTDOWN_TICK -> null
    TimerCue.FINISH -> if (state.endedEarly) null else "Workout complete"
    TimerCue.WORK_START, TimerCue.REST_START -> {
        val current = state.currentInterval?.takeIf { state.phase == TimerPhase.RUNNING }
        val next = state.intervals.getOrNull(state.currentIndex + 1)
        when {
            current == null -> if (cue == TimerCue.WORK_START) "Work" else "Rest"
            // "Next: Work 4 of 8" is just noise; only exercises with real names are announced.
            current.type == IntervalType.REST && next != null && !isGeneratedName(next.name) ->
                "${current.name}. Next: ${next.name}"
            else -> current.name
        }
    }
}

/** Speaks short lines with the phone's text-to-speech voice. */
private class VoicePlayer(context: Context, private val onFinished: () -> Unit) {

    private var ready = false
    // A line asked for before the voice had loaded; only the newest matters.
    private var waiting: Pair<String, Float>? = null

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            configure()
            waiting?.let { (line, volume) -> speak(line, volume) }
        }
        waiting = null
    }

    private fun configure() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onFinished()
            @Deprecated("Android's older error callback; still required")
            override fun onError(utteranceId: String?) = onFinished()
            override fun onError(utteranceId: String?, errorCode: Int) = onFinished()
        })
    }

    fun speak(line: String, volume: Float) {
        if (!ready) {
            waiting = line to volume
            return
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.1f, 1f))
        }
        // QUEUE_FLUSH: a new interval always wins over a line still being spoken.
        runCatching { tts.speak(line, TextToSpeech.QUEUE_FLUSH, params, "cue") }
    }

    fun release() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}

/**
 * Asks Android to lower other apps' audio (music, podcasts) for a moment, the
 * way navigation voices do. Asking again before the time is up extends it.
 */
private class AudioDucker(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var held = false

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

    private val giveBack = Runnable { releaseNow() }

    fun duckFor(millis: Long) {
        // The voice reports back on its own thread; everything here stays on the main one.
        if (Looper.myLooper() != handler.looper) {
            handler.post { duckFor(millis) }
            return
        }
        if (!held) {
            held = runCatching {
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }.getOrDefault(false)
        }
        handler.removeCallbacks(giveBack)
        handler.postDelayed(giveBack, millis)
    }

    fun releaseNow() {
        handler.removeCallbacks(giveBack)
        if (held) {
            runCatching { audioManager.abandonAudioFocusRequest(request) }
            held = false
        }
    }
}
