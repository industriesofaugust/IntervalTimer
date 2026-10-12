package com.august.intervaltimer.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.august.intervaltimer.model.Interval
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hosts a [TimerEngine] for a screen. Used by the phone, where the timer
 * only needs to live as long as the app is open.
 */
class TimerViewModel : ViewModel() {

    private val engine = TimerEngine(viewModelScope)

    val uiState: StateFlow<TimerUiState> = engine.uiState
    val cues: SharedFlow<TimerCue> = engine.cues

    fun start(intervals: List<Interval>) = engine.start(intervals)
    fun togglePause() = engine.togglePause()
    fun skip() = engine.skip()
    fun stop() = engine.stop()
    fun endEarly() = engine.endEarly()
}
