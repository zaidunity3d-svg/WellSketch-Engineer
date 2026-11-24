package com.wellsketch.engineer

import android.app.Application

class WellSketchApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
