// File: app/src/main/java/com/hermex/android/HermexApplication.kt
package com.hermex.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HermexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize app components
    }
}