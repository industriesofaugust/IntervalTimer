package com.august.intervaltimer.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.august.intervaltimer.TimerApp
import com.august.intervaltimer.data.HealthConnectWriter
import com.august.intervaltimer.data.HealthPrefsRepository
import com.august.intervaltimer.data.HealthStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Health card is busy with, shown instead of its buttons. */
data class HealthProgress(val text: String, val done: Int = 0, val total: Int = 0)

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    private val results = (app as TimerApp).resultsRepository
    private val prefs = HealthPrefsRepository(app)
    private val writer = HealthConnectWriter(app, prefs)

    val available = writer.isAvailable()
    val permissions = writer.permissions

    val status = prefs.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthStatus())

    var linked by mutableStateOf(false)
        private set

    /** Whether the resting heart rate may be read, for the heart-rate zones. */
    var canReadResting by mutableStateOf(false)
        private set

    var progress by mutableStateOf<HealthProgress?>(null)
        private set

    /** What the last action did, until the next one starts. */
    var message by mutableStateOf<String?>(null)
        private set

    /** The age field's text, kept apart from the saved number so typing is never fought. */
    var ageText by mutableStateOf("")
        private set

    init {
        refresh()
        viewModelScope.launch { ageText = prefs.age.first()?.toString() ?: "" }
    }

    /** Asks Health Connect what is allowed now; the user can change it outside the app. */
    fun refresh() {
        viewModelScope.launch {
            linked = writer.isLinked()
            canReadResting = writer.canReadRestingHeartRate()
        }
    }

    /** After the user answers the permission screen, save the workouts that are not in yet. */
    fun onPermissionsAnswered() {
        viewModelScope.launch {
            linked = writer.isLinked()
            canReadResting = writer.canReadRestingHeartRate()
            if (linked) saveMissing()
        }
    }

    fun retry() {
        viewModelScope.launch {
            prefs.clearError()
            saveMissing()
        }
    }

    private suspend fun saveMissing() {
        message = null
        progress = HealthProgress("Saving")
        val summary = writer.saveUnsaved(results.getAll())
        progress = null
        message = when {
            summary.failed > 0 -> "${summary.failed} could not be saved"
            summary.saved > 0 -> "Saved ${summary.saved} ${workouts(summary.saved)}"
            else -> "Everything is already saved"
        }
    }

    /** Removes the older copies this app saved, then saves every workout again. */
    fun cleanUp() {
        viewModelScope.launch {
            message = null
            progress = HealthProgress("Cleaning up")
            val summary = writer.cleanUp(results.getAll()) { done, total ->
                progress = HealthProgress("Saving again", done, total)
            }
            progress = null
            message = when {
                summary == null -> "Could not clean up"
                summary.failed > 0 -> "Cleaned up, but ${summary.failed} could not be saved again"
                else -> "Cleaned up. ${summary.saved} ${workouts(summary.saved)} saved again"
            }
        }
    }

    fun stopSharing() {
        viewModelScope.launch {
            message = null
            val stopped = writer.stopSharing()
            linked = writer.isLinked()
            canReadResting = writer.canReadRestingHeartRate()
            message = if (stopped) "Stopped. Workouts already saved stay in Health Connect"
            else "Could not stop sharing"
        }
    }

    fun setAge(text: String) {
        val digits = text.filter { it.isDigit() }.take(3)
        ageText = digits
        viewModelScope.launch { prefs.setAge(digits.toIntOrNull()) }
    }

    private fun workouts(count: Int) = if (count == 1) "workout" else "workouts"
}
