package com.august.intervaltimer

import android.app.Application
import com.august.intervaltimer.settings.WatchStores
import com.august.intervaltimer.workout.WorkoutSession

class IntervalTimerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any activity or service, so everything finds settings ready.
        WatchStores.init(this)
        // Also ends any workout sensors left running from before the app was closed.
        WorkoutSession.init(this)
    }
}
