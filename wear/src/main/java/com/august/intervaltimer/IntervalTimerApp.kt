package com.august.intervaltimer

import android.app.Application
import com.august.intervaltimer.settings.WatchStores

class IntervalTimerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any activity or service, so everything finds settings ready.
        WatchStores.init(this)
    }
}
