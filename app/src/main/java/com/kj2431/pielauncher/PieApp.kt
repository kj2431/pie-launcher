package com.kj2431.pielauncher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kj2431.pielauncher.ui.ThemeHelper

class PieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemeHelper.nightMode(this))
    }
}
