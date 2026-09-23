package com.qinglong.panel

import android.app.Application
import com.qinglong.panel.di.AppContainer

class QinglongApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
