package com.kj2431.pielauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.action.ActionId
import com.kj2431.pielauncher.action.ActionRunner
import com.kj2431.pielauncher.model.PieItem
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.root.RootHelper
import com.kj2431.pielauncher.view.PieConfig
import com.kj2431.pielauncher.view.PieEdgeView

/**
 * Foreground service that owns one overlay window per enabled edge. Each window
 * is a thin activation strip while idle and expands to full screen while its
 * donut pie is open, so the whole press-drag-release gesture stays in one window.
 */
class PieLauncherService : Service() {

    private lateinit var wm: WindowManager
    private val views = mutableListOf<PieEdgeView>()
    private val stripParams = HashMap<PieEdgeView, WindowManager.LayoutParams>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
        addEdges()
        if (Prefs.rootActionsEnabled(this)) RootHelper.prewarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeEdges(); addEdges()   // pick up Settings changes
        if (Prefs.rootActionsEnabled(this)) RootHelper.prewarm()
        return START_STICKY
    }

    // ---- Edge windows -------------------------------------------------------
    private fun addEdges() {
        val metrics: DisplayMetrics = resources.displayMetrics
        val density = metrics.density
        val thicknessPx = (Prefs.thicknessDp(this) * density).toInt()
        val lengthFrac = Prefs.lengthPercent(this) / 100f
        val centerFrac = Prefs.centerPercent(this) / 100f
        val pieColors = resolveColors()

        Prefs.edges(this).forEach { edge ->
            lateinit var view: PieEdgeView
            val cfg = PieConfig(
                showIcon = Prefs.showIcon(this),
                showText = Prefs.showText(this),
                responseMs = Prefs.responseMs(this),
                animMs = Prefs.animationMs(this),
                longPressMs = Prefs.longPressMs(this),
                hapticMs = Prefs.hapticMs(this),
                bgColor = pieColors.bg,
                primaryColor = pieColors.primary,
                longColor = pieColors.longc
            )
            view = PieEdgeView(
                this, edge,
                showHint = Prefs.showActivationBars(this),
                config = cfg,
                itemsProvider = { buildItems() },
                onExpand = { expand(view) },
                onCollapse = { collapse(view) }
            )

            val vertical = edge == Prefs.Edge.LEFT || edge == Prefs.Edge.RIGHT
            // Size = length% of the edge; centred at centerFrac of the edge.
            val edgeLen = if (vertical) metrics.heightPixels else metrics.widthPixels
            val sizePx = (edgeLen * lengthFrac).toInt().coerceAtLeast(thicknessPx)
            val offset = (edgeLen * centerFrac - sizePx / 2f).toInt().coerceIn(0, edgeLen - sizePx)

            val lp = WindowManager.LayoutParams(
                if (vertical) thicknessPx else sizePx,
                if (vertical) sizePx else thicknessPx,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or imeFlag(),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = edgeGravity(edge) or (if (vertical) Gravity.TOP else Gravity.START)
                if (vertical) y = offset else x = offset
            }

            runCatching { wm.addView(view, lp); views += view; stripParams[view] = lp }
        }
    }

    private data class PieColors(val bg: Int, val primary: Int, val longc: Int)

    /** Resolves the three slice colors; "system" slots fall back to system defaults. */
    private fun resolveColors(): PieColors {
        val d = systemDefaults()
        fun pick(pref: String, def: Int): Int =
            if (pref == "system") def
            else runCatching { android.graphics.Color.parseColor(pref) }.getOrDefault(def)
        return PieColors(
            bg = pick(Prefs.pieBgColor(this), d.bg),
            primary = pick(Prefs.pieColor(this), d.primary),
            longc = pick(Prefs.pieLongColor(this), d.longc)
        )
    }

    /**
     * Three defaults pulled from the device's dynamic palette (Android 12+).
     * Sorted by luminance: the darkest becomes the resting background, the
     * brightest the click highlight, the middle the long-press color.
     */
    private fun systemDefaults(): PieColors {
        if (Build.VERSION.SDK_INT >= 31) {
            val c = listOf(
                ContextCompat.getColor(this, android.R.color.system_accent1_700),
                ContextCompat.getColor(this, android.R.color.system_accent2_500),
                ContextCompat.getColor(this, android.R.color.system_accent3_300)
            ).sortedBy { androidx.core.graphics.ColorUtils.calculateLuminance(it) }
            return PieColors(bg = c[0], longc = c[1], primary = c[2])
        }
        return PieColors(bg = 0xFF262626.toInt(), primary = resolveThemePrimary(), longc = 0xFFEF9F27.toInt())
    }

    private fun resolveThemePrimary(): Int {
        val themed = androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_PieLauncher)
        val tv = android.util.TypedValue()
        return if (themed.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(themed, tv.resourceId) else tv.data
        } else 0xFF009688.toInt()
    }

    private fun removeEdges() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear(); stripParams.clear()
    }

    /** Grow the edge window to full screen so the donut + gesture have room. */
    private fun expand(view: PieEdgeView) {
        val full = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or imeFlag(),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
        runCatching { wm.updateViewLayout(view, full) }
    }

    /** Shrink back to the thin activation strip once the gesture ends. */
    private fun collapse(view: PieEdgeView) {
        stripParams[view]?.let { runCatching { wm.updateViewLayout(view, it) } }
    }


    // ---- Slice building -----------------------------------------------------
    private fun buildItems(): List<PieItem> {
        val items = Prefs.slices(this).mapNotNull { token -> sliceFromToken(token) }
        if (items.isNotEmpty()) return items
        return listOf(
            PieItem("Home", ContextCompat.getDrawable(this, ActionId.HOME.iconRes)) { ActionRunner.run(this, ActionId.HOME) },
            PieItem("Back", ContextCompat.getDrawable(this, ActionId.BACK.iconRes)) { ActionRunner.run(this, ActionId.BACK) },
            PieItem("Recents", ContextCompat.getDrawable(this, ActionId.RECENTS.iconRes)) { ActionRunner.run(this, ActionId.RECENTS) },
            PieItem("Notifs", ContextCompat.getDrawable(this, ActionId.NOTIFICATIONS.iconRes)) { ActionRunner.run(this, ActionId.NOTIFICATIONS) }
        )
    }

    /**
     * Token grammar: PRIMARY[##LONG] where each part is "app:<pkg>" or
     * "act:<ID>[|arg]". PRIMARY supplies the label + icon + short action; LONG
     * (optional) supplies the long-press action.
     */
    private fun sliceFromToken(token: String): PieItem? {
        val primary = resolve(token.substringBefore("##")) ?: return null
        val long = if (token.contains("##")) resolve(token.substringAfter("##")) else null
        return PieItem(primary.label, primary.icon, long?.label, long?.icon, long?.action, primary.action)
    }

    private data class Resolved(val label: String, val icon: Drawable?, val action: () -> Unit)

    private fun resolve(sub: String): Resolved? = when {
        sub.startsWith("app:") -> {
            val pkg = sub.removePrefix("app:")
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return null
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val icon = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
            Resolved(label, icon) { runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        sub.startsWith("act:") -> {
            val body = sub.removePrefix("act:")
            val id = runCatching { ActionId.valueOf(body.substringBefore("|")) }.getOrNull() ?: return null
            val arg = if (body.contains("|")) body.substringAfter("|") else null
            Resolved(id.label, ContextCompat.getDrawable(this, id.iconRes)) { ActionRunner.run(this, id, arg) }
        }
        else -> null
    }

    // ---- Helpers ------------------------------------------------------------
    /**
     * When "Pie behind keyboard" is on, FLAG_ALT_FOCUSABLE_IM inverts a
     * non-focusable window's relationship to the IME so it sits BEHIND the
     * keyboard instead of in front of it.
     */
    private fun imeFlag(): Int =
        if (Prefs.belowKeyboard(this)) WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM else 0

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun edgeGravity(edge: Prefs.Edge): Int = when (edge) {
        Prefs.Edge.LEFT -> Gravity.START
        Prefs.Edge.RIGHT -> Gravity.END
        Prefs.Edge.TOP -> Gravity.TOP
        Prefs.Edge.BOTTOM -> Gravity.BOTTOM
    }


    private fun startInForeground() {
        val channelId = "pie_launcher_running"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val n: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    override fun onDestroy() { removeEdges(); RootHelper.shutdown(); super.onDestroy() }
}
