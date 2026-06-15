package com.kj2431.pielauncher.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Future-proof edge-to-edge handling that does not rely on the Android 15
 * opt-out flag (removed at targetSdk 36). We draw under the system bars and pad
 * [root] by the system-bar insets so the header clears the status bar and
 * content clears the navigation bar.
 */
object EdgeToEdge {
    fun setup(activity: AppCompatActivity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        ThemeHelper.applyBarContrast(activity)
    }
}
