package com.walkout.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the WiFi monitor service after the device reboots.
 * Android stops all services on reboot, so this is required for persistence.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MainActivity.KEY_ACTIVE, false)) {
            WifiMonitorService.start(context)
        }
    }
}
