package com.kj2431.pielauncher.ui

import android.content.Context
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.prefs.Prefs

/** Applies the user's theme choice and keeps the system bars legible. */
object ThemeHelper {

    fun nightMode(ctx: Context): Int = when (Prefs.themeMode(ctx)) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** Call BEFORE setContentView. Applies the pure-black overlay for AMOLED. */
    fun applyActivityTheme(activity: AppCompatActivity) {
        if (Prefs.themeMode(activity) == "amoled") {
            activity.theme.applyStyle(R.style.ThemeOverlay_PieLauncher_Amoled, true)
        }
    }

    /** Status/nav-bar icons get dark icons on light surfaces and vice versa. */
    fun applyBarContrast(activity: AppCompatActivity) {
        val tv = TypedValue()
        val bg = if (activity.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) tv.data else 0
        val lightBg = ColorUtils.calculateLuminance(bg) > 0.5
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = lightBg
        controller.isAppearanceLightNavigationBars = lightBg
    }
}
