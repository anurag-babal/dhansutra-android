package com.example.dhansutra

import android.app.Application
import com.example.dhansutra.di.AppContainer

class DhanSutraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
