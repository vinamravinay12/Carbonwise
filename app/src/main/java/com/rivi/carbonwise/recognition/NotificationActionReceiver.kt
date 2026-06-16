package com.rivi.carbonwise.recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.rivi.carbonwise.MainActivity
import com.rivi.carbonwise.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the notification quick-actions. "Yes, car" (or "Log it" for active travel)
 * logs the trip in a single tap *when GPS distance is known*; otherwise it opens the app
 * so the user can enter the distance.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (tripId < 0) return

        val appContext = context.applicationContext
        val repository = ServiceLocator.repository(appContext)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val trip = repository.getDetection(tripId)
                val distance = trip?.distanceKm
                val type = when (action) {
                    ACTION_CONFIRM_CAR -> "car_petrol"
                    else -> trip?.suggestedType ?: trip?.kind?.defaultType
                }
                if (trip != null && distance != null && distance > 0 && type != null) {
                    repository.confirmDetection(tripId, type, distance)
                    NotificationManagerCompat.from(appContext).cancel(notifId)
                } else {
                    // No measured distance → open the app for the user to fill it in.
                    openApp(appContext, tripId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun openApp(context: Context, tripId: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DETECTION, tripId)
        }
        context.startActivity(intent)
    }

    companion object {
        const val ACTION_CONFIRM_CAR = "com.rivi.carbonwise.ACTION_CONFIRM_CAR"
        const val ACTION_CONFIRM_ACTIVE = "com.rivi.carbonwise.ACTION_CONFIRM_ACTIVE"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
