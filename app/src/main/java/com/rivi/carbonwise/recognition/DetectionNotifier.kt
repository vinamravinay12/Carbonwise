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

/** Posts a gentle "we noticed a trip — tap to log it" notification when a segment ends. */
object DetectionNotifier {

    private const val CHANNEL_ID = "detected_trips"
    private const val CHANNEL_NAME = "Detected trips"

    fun notifyTrip(context: Context, trip: DetectedTrip) {
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DETECTION, trip.id)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getActivity(context, trip.id.toInt(), tapIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Looks like you were ${trip.kind.label.lowercase()}")
            .setContentText("About ${trip.durationMinutes} min. Tap to log it and see the impact.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(trip.id.toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): the detection is still in the app.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Prompts to confirm an automatically detected trip" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
