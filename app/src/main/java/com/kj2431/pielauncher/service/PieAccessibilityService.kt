package com.kj2431.pielauncher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional, user-enabled service. We don't read screen content — we only need a
 * bound AccessibilityService so the action runner can call performGlobalAction()
 * for things like Back, Recents, Notifications, Power menu and Split screen.
 */
class PieAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: PieAccessibilityService? = null
            private set
        val isAvailable: Boolean get() = instance != null
    }
}
