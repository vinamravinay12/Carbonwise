package com.rivi.carbonwise.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rivi.carbonwise.MainActivity
import com.rivi.carbonwise.R
import com.rivi.carbonwise.data.DetectedTrip

/**
 * Posts the "we noticed a trip" prompt. For a vehicle it asks the car-prior question
 * ("Were you just in a car? Yes / Something else"); for active travel it offers a single
 * "Log it". Quick-actions log in one tap when GPS distance is known (see
 * [NotificationActionReceiver]); otherwise tapping opens the app to enter distance.
 */
object DetectionNotifier {

    private const val CHANNEL_ID = "detected_trips"
    private const val CHANNEL_NAME = "Detected trips"

    fun notifyTrip(context: Context, trip: DetectedTrip) {
        ensureChannel(context)
        val notifId = trip.id.toInt()
        val openIntent = openAppIntent(context, trip.id, notifId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText(detail(trip))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)

        if (trip.kind == DetectedKind.VEHICLE) {
            builder.setContentTitle("Were you just in a car?")
            builder.addAction(
                0, "Yes, car",
                actionIntent(context, NotificationActionReceiver.ACTION_CONFIRM_CAR, trip.id, notifId),
            )
            builder.addAction(0, "Something else", openIntent)
        } else {
            builder.setContentTitle("Looks like you were ${trip.kind.label.lowercase()}")
            builder.addAction(
                0, "Log it",
                actionIntent(context, NotificationActionReceiver.ACTION_CONFIRM_ACTIVE, trip.id, notifId),
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): the detection still shows in-app.
        }
    }

    private fun detail(trip: DetectedTrip): String {
        val dist = trip.distanceKm?.takeIf { it > 0 }
            ?.let { " · ~${com.rivi.carbonwise.domain.formatAmount(it)} km" } ?: ""
        return "About ${trip.durationMinutes} min$dist. Tap to log it and see the impact."
    }

    private fun openAppIntent(context: Context, tripId: Long, notifId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DETECTION, tripId)
        }
        return PendingIntent.getActivity(context, notifId, intent, immutableFlags())
    }

    private fun actionIntent(context: Context, action: String, tripId: Long, notifId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_TRIP_ID, tripId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(context, action.hashCode() + notifId, intent, immutableFlags())
    }

    private fun immutableFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Prompts to confirm an automatically detected trip" }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
