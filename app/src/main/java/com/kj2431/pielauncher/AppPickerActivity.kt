package com.kj2431.pielauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import com.kj2431.pielauncher.ui.EdgeToEdge
import com.kj2431.pielauncher.model.AppInfo

/**
 * Multi-select list of launchable apps. Returns the selected package names
 * (in tap order) as an ArrayList extra; the caller appends them as slices.
 */
class AppPickerActivity : AppCompatActivity() {

    private val apps = mutableListOf<AppInfo>()
    private val selected = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_app_picker)
        val tb = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        tb.setNavigationOnClickListener { finish() }
        title = getString(R.string.add_apps)
        EdgeToEdge.setup(this, findViewById(R.id.root))

        loadApps()
        val adapter = AppAdapter()
        findViewById<ListView>(R.id.appList).apply {
            this.adapter = adapter
            setOnItemClickListener { _, _, pos, _ ->
                val pkg = apps[pos].packageName
                if (selected.contains(pkg)) selected.remove(pkg) else selected.add(pkg)
                adapter.notifyDataSetChanged()
            }
        }
        findViewById<Button>(R.id.save).apply {
            text = getString(R.string.add_selected)
            setOnClickListener {
                setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(selected)))
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) return@forEach
            apps += AppInfo(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
        }
        apps.sortBy { it.label.lowercase() }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.row_app, parent, false)
            val app = apps[p]
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            v.findViewById<TextView>(R.id.label).text = app.label
            val idx = selected.indexOf(app.packageName)
            v.findViewById<CheckBox>(R.id.check).isChecked = idx >= 0
            v.findViewById<TextView>(R.id.order).text = if (idx >= 0) (idx + 1).toString() else ""
            return v
        }
    }

    companion object { const val EXTRA_PACKAGES = "packages" }
}
