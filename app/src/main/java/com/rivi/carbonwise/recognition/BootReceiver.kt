package com.rivi.carbonwise.recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rivi.carbonwise.ServiceLocator

/**
 * Activity-Recognition subscriptions don't survive a reboot. If the user had auto-tracking
 * on, this re-registers it (and restarts the persistent service) once the device boots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val manager = ServiceLocator.recognitionManager(context.applicationContext)
        if (manager.isEnabled()) manager.start()
    }
}
