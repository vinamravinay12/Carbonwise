package com.rivi.carbonwise.recognition

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rivi.carbonwise.R
import com.rivi.carbonwise.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that measures a trip's distance and movement signature from GPS while
 * it's happening. Started on a detected ENTER and stopped on EXIT. Writes metrics to the
 * open segment as it goes, so distance survives even if the service is later killed.
 *
 * Note: starting this from the background (when Activity Recognition fires) is restricted on
 * Android 12+; [start] swallows that failure, and the app falls back to the duration
 * estimate / manual entry so logging still works.
 */
class TripLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private var segmentId: Long = -1L

    private var lastLocation: Location? = null
    private var lastFixTime: Long = 0L
    private var startTime: Long = 0L
    private var totalMeters = 0.0
    private var maxSpeedKmh = 0.0
    private var stopCount = 0
    private var gpsGaps = 0
    private var wasMoving = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onFix(it) }
            persist()
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        segmentId = intent?.getLongExtra(EXTRA_SEGMENT_ID, -1L) ?: -1L

        // Without location permission a location-typed FGS throws; bail before that happens.
        if (!hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Entering the foreground can still be refused if we're started from an ineligible
        // background state (Android 12+). Catch it and stop quietly — the app falls back to
        // the duration estimate / manual distance, so logging never breaks.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w("CarbonWise", "Could not enter foreground for trip tracking: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        startTime = System.currentTimeMillis()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()
        try {
            client.requestLocationUpdates(request, callback, mainLooper)
        } catch (e: SecurityException) {
            Log.w("CarbonWise", "Location permission missing: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    private fun onFix(location: Location) {
        val now = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        lastLocation?.let { prev ->
            totalMeters += prev.distanceTo(location)
            if (lastFixTime > 0 && now - lastFixTime > GAP_MS) gpsGaps++
        }
        val speedKmh = (if (location.hasSpeed()) location.speed else 0f) * 3.6
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        // Count a "stop" each time movement settles to near-zero after having moved.
        if (speedKmh < STOP_KMH && wasMoving) {
            stopCount++
            wasMoving = false
        } else if (speedKmh > MOVE_KMH) {
            wasMoving = true
        }
        lastLocation = location
        lastFixTime = now
    }

    private fun persist() {
        if (segmentId < 0) return
        val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(1.0)
        val avgKmh = (totalMeters / elapsedSec) * 3.6
        val id = segmentId
        val meters = totalMeters
        val max = maxSpeedKmh
        val stops = stopCount
        val gaps = gpsGaps
        scope.launch {
            ServiceLocator.repository(applicationContext)
                .updateTripMetrics(id, meters, avgKmh, max, stops, gaps)
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Trip tracking", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Measuring distance for a detected trip" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tracking your trip")
            .setContentText("Measuring distance to log its footprint")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val EXTRA_SEGMENT_ID = "segment_id"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val GAP_MS = 45_000L
        private const val STOP_KMH = 3.0
        private const val MOVE_KMH = 11.0

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        /**
         * Best-effort start. Skips entirely without location permission, and background-start
         * restrictions on API 31+ are swallowed — in both cases the app falls back to the
         * duration estimate / manual distance.
         */
        fun start(context: Context, segmentId: Long) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, TripLocationService::class.java)
                .putExtra(EXTRA_SEGMENT_ID, segmentId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w("CarbonWise", "Could not start trip tracking service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripLocationService::class.java))
        }
    }
}
