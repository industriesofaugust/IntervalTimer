package com.august.intervaltimer

import android.app.Application
import androidx.room.Room
import com.august.intervaltimer.data.AppDatabase
import com.august.intervaltimer.data.AppSettingsRepository
import com.august.intervaltimer.data.ResultsRepository
import com.august.intervaltimer.data.TemplateSyncPublisher
import com.august.intervaltimer.data.TemplatesRepository
import com.august.intervaltimer.data.WatchDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application-wide singletons. `lazy` means the database is only opened the
 * first time something asks for it.
 */
class TimerApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "interval-timer.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    val templatesRepository: TemplatesRepository by lazy {
        TemplatesRepository(database.templateDao())
    }

    val resultsRepository: ResultsRepository by lazy {
        ResultsRepository(database.resultDao())
    }

    val settingsRepository: AppSettingsRepository by lazy {
        AppSettingsRepository(this)
    }

    // Lives as long as the app process; used for work no single screen owns.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val templatePublisher by lazy { TemplateSyncPublisher(this) }

    /** What the Devices card and the NO WATCH help show about the watch. */
    val watchDevice: WatchDevice by lazy {
        WatchDevice(this, applicationScope) {
            templatePublisher.publish(templatesRepository.observeTemplates().first())
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Whenever the templates change (and once at startup), push the
        // latest list to the watch. runCatching: sync must never crash the
        // app if Play Services is unavailable.
        applicationScope.launch {
            templatesRepository.observeTemplates().collect { templates ->
                runCatching { templatePublisher.publish(templates) }
            }
        }
    }
}
