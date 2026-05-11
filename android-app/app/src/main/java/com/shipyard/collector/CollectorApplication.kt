package com.shipyard.collector

import android.app.Application
import com.shipyard.collector.data.AppContainer

class CollectorApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
