package com.rivi.carbonwise.recognition

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest

/**
 * Starts/stops Activity-Recognition transition updates and remembers whether the user
 * has auto-tracking switched on. Transition results are delivered to
 * [ActivityTransitionReceiver] via a broadcast PendingIntent.
 */
class ActivityRecognitionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("carbonwise_tracking", Context.MODE_PRIVATE)

    /** The runtime permission only exists on API 29+; below that it's granted at install. */
    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** Begin receiving transition updates. No-op (returns false) without permission. */
    fun start(): Boolean {
        if (!hasPermission()) return false
        val request = ActivityTransitionRequest(buildTransitions())
        return try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent())
                .addOnSuccessListener { setEnabled(true) }
                .addOnFailureListener { e -> Log.w(TAG, "Failed to start tracking: ${e.message}") }
            setEnabled(true)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission for tracking: ${e.message}")
            false
        }
    }

    fun stop() {
        setEnabled(false)
        try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent())
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to stop tracking: ${e.message}")
        }
    }

    private fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    private fun buildTransitions(): List<ActivityTransition> =
        DetectedKind.tracked.flatMap { activityType ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
        }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
            .setAction(ActivityTransitionReceiver.ACTION_PROCESS)
        // The Activity-Recognition API writes results into the intent, so it must be mutable.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private companion object {
        const val TAG = "CarbonWise"
        const val KEY_ENABLED = "auto_tracking_enabled"
    }
}
