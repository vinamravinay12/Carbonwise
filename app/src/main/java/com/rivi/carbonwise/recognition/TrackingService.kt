package com.rivi.carbonwise.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rivi.carbonwise.MainActivity
import com.rivi.carbonwise.R

/**
 * Lightweight persistent foreground service whose only job is to keep CarbonWise alive and
 * in a healthy state while auto-tracking is on, so Activity-Recognition transitions are
 * reliably delivered (instead of being lost when the OS/OEM kills the backgrounded app).
 *
 * It does no sensing itself — GPS runs only during a trip (see [TripLocationService]).
 * Declared as a `health` foreground service, which Activity-Recognition permission satisfies.
 */
class TrackingService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w("CarbonWise", "Could not start tracking service: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Auto-tracking", NotificationManager.IMPORTANCE_MIN,
            ).apply { description = "Keeps CarbonWise watching for trips" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            },
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CarbonWise auto-tracking is on")
            .setContentText("Watching for trips to log automatically")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(tap)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracking_active"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w("CarbonWise", "Could not start tracking service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
