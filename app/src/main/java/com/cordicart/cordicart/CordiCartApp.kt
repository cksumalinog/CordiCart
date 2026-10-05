package com.cordicart.cordicart

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Runs once when the app starts, before any screen.
 * The layouts use light backgrounds, so dark mode is turned off to keep text readable.
 */
class CordiCartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
