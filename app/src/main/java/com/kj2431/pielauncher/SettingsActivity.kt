package com.kj2431.pielauncher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import com.kj2431.pielauncher.ui.EdgeToEdge

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_settings)
        val tb = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        tb.setNavigationOnClickListener { finish() }
        title = getString(R.string.settings)
        EdgeToEdge.setup(this, findViewById(R.id.root))
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
