package com.kj2431.pielauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.prefs.Prefs

/** Restarts the overlay after reboot, but only if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.serviceEnabled(context)) {
            ContextCompat.startForegroundService(
                context, Intent(context, PieLauncherService::class.java)
            )
        }
    }
}
