package com.rivi.carbonwise.recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.rivi.carbonwise.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Activity-Recognition transitions. On ENTER it opens a segment; on EXIT it
 * closes the matching segment into a PENDING detection and notifies the user. Event times
 * arrive as elapsed-realtime nanos, so we convert them to wall-clock millis here.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROCESS) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        val appContext = context.applicationContext
        val repository = ServiceLocator.repository(appContext)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (event in result.transitionEvents) {
                    val kind = DetectedKind.fromDetectedActivity(event.activityType) ?: continue
                    val whenMillis = toWallClockMillis(event.elapsedRealTimeNanos)
                    when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                            val segmentId = repository.recordActivityEnter(kind, whenMillis)
                            // Measure distance/speed via GPS for the duration of the trip.
                            TripLocationService.start(appContext, segmentId)
                        }

                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                            TripLocationService.stop(appContext)
                            val trip = repository.recordActivityExit(kind, whenMillis)
                            if (trip != null) DetectionNotifier.notifyTrip(appContext, trip)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CarbonWise", "Failed to process transition: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun toWallClockMillis(eventElapsedNanos: Long): Long {
        val sinceEventNanos = SystemClock.elapsedRealtimeNanos() - eventElapsedNanos
        return System.currentTimeMillis() - sinceEventNanos / 1_000_000L
    }

    companion object {
        const val ACTION_PROCESS = "com.rivi.carbonwise.ACTION_ACTIVITY_TRANSITION"
    }
}
