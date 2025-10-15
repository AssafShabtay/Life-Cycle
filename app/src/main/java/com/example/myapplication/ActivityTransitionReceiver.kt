package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ActivityTransition"
        const val ACTION_ACTIVITY_TRANSITION = "com.example.myapplication.ACTIVITY_TRANSITION"
        const val ACTION_ACTIVITY_CONFIDENCE = "com.example.myapplication.ACTIVITY_CONFIDENCE"
        const val ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_TRANSITION_TYPE = "transition_type"

        private const val PREFS_NAME = "activity_transition_prefs"
        private const val KEY_LAST_ACTIVITY = "last_activity"

        fun persistLastActivity(context: Context, activityType: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_ACTIVITY, activityType)
                .apply()
        }

        fun loadLastActivity(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_ACTIVITY, DetectedActivity.STILL)
        }

        fun getActivityDisplayName(activityType: Int): String {
            return when (activityType) {
                DetectedActivity.IN_VEHICLE -> "Driving"
                DetectedActivity.ON_BICYCLE -> "Cycling"
                DetectedActivity.ON_FOOT -> "On Foot"
                DetectedActivity.RUNNING -> "Running"
                DetectedActivity.STILL -> "Still"
                DetectedActivity.WALKING -> "Walking"
                else -> "Still"
            }
        }
    }

    private object ActivityStateAggregator {
        private const val CONFIDENCE_THRESHOLD = 65
        private const val HIGH_CONFIDENCE_THRESHOLD = 85
        private const val WINDOW_SIZE = 5
        private const val MIN_SWITCH_INTERVAL_MS = 15_000L

        private val window = ArrayDeque<Int>()
        var lastEmitted: Int = DetectedActivity.UNKNOWN
            private set
        var lastChangeRealtime: Long = 0L
            private set
        private var seeded = false

        fun seed(activityType: Int, now: Long) {
            if (seeded) return
            seeded = true
            lastEmitted = activityType
            lastChangeRealtime = now - MIN_SWITCH_INTERVAL_MS
            window.clear()
        }

        fun canAcceptEnter(activityType: Int, now: Long): Boolean {
            if (!seeded) return true
            val elapsed = now - lastChangeRealtime
            if (activityType == lastEmitted && elapsed < MIN_SWITCH_INTERVAL_MS / 2) {
                return false
            }
            if (activityType != lastEmitted && elapsed < MIN_SWITCH_INTERVAL_MS) {
                return false
            }
            return true
        }

        fun evaluateConfidence(activityType: Int, confidence: Int, now: Long): Int? {
            if (confidence < CONFIDENCE_THRESHOLD) return null

            window.addLast(activityType)
            if (window.size > WINDOW_SIZE) {
                window.removeFirst()
            }

            val majority = window.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: activityType
            val matured = window.size >= WINDOW_SIZE || confidence >= HIGH_CONFIDENCE_THRESHOLD
            if (!matured) return null

            if (!seeded) return majority

            if (majority == lastEmitted) return null

            val elapsed = now - lastChangeRealtime
            if (elapsed < MIN_SWITCH_INTERVAL_MS && confidence < HIGH_CONFIDENCE_THRESHOLD) {
                return null
            }

            return majority
        }

        fun markTransition(activityType: Int, now: Long) {
            seeded = true
            lastEmitted = activityType
            lastChangeRealtime = now
            window.clear()
        }

        fun resetWindow() {
            window.clear()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive action=$action extras=${intent.extras?.keySet()?.joinToString()}")

        val now = SystemClock.elapsedRealtime()
        ActivityStateAggregator.seed(loadLastActivity(context), now)

        when {
            ActivityTransitionResult.hasResult(intent) -> {
                val result = ActivityTransitionResult.extractResult(intent)
                if (result != null) {
                    handleTransitionResult(context, result)
                }
            }
            action == ACTION_ACTIVITY_CONFIDENCE && ActivityRecognitionResult.hasResult(intent) -> {
                ActivityRecognitionResult.extractResult(intent)?.let {
                    handleConfidenceResult(context, it)
                }
            }
            else -> {
                Log.d(TAG, "Ignoring intent with action=$action")
            }
        }
    }

    private fun handleTransitionResult(context: Context, result: ActivityTransitionResult) {
        for (event in result.transitionEvents) {
            val normalizedType = normalizeActivityType(event.activityType)
            val entering = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            val now = SystemClock.elapsedRealtime()

            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(System.currentTimeMillis()))
            Log.d(
                TAG,
                "Transition ${getActivityDisplayName(normalizedType)} ${if (entering) "ENTER" else "EXIT"} @ $timestamp"
            )

            if (entering) {
                if (!ActivityStateAggregator.canAcceptEnter(normalizedType, now)) {
                    Log.d(TAG, "Skipping rapid duplicate enter for ${getActivityDisplayName(normalizedType)}")
                    continue
                }
                ActivityStateAggregator.markTransition(normalizedType, now)
                persistLastActivity(context, normalizedType)
            } else {
                ActivityStateAggregator.resetWindow()
            }

            notifyLocationService(context, normalizedType, event.transitionType)
        }
    }

    private fun handleConfidenceResult(context: Context, result: ActivityRecognitionResult) {
        val topActivity = result.probableActivities
            .filter { it.type != DetectedActivity.UNKNOWN && it.type != DetectedActivity.TILTING }
            .maxByOrNull { it.confidence }
            ?: return

        val normalizedType = normalizeActivityType(topActivity.type)
        val now = SystemClock.elapsedRealtime()

        val candidate = ActivityStateAggregator.evaluateConfidence(normalizedType, topActivity.confidence, now)
            ?: return

        val previous = ActivityStateAggregator.lastEmitted
        ActivityStateAggregator.markTransition(candidate, now)
        persistLastActivity(context, candidate)

        if (previous != DetectedActivity.UNKNOWN && previous != candidate) {
            notifyLocationService(context, previous, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
        }
        notifyLocationService(context, candidate, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
    }

    private fun notifyLocationService(context: Context, activityType: Int, transitionType: Int) {
        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = ACTION_ACTIVITY_UPDATE
            putExtra(EXTRA_ACTIVITY_TYPE, activityType)
            putExtra(EXTRA_TRANSITION_TYPE, transitionType)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify LocationService: ${e.message}")
        }
    }

    private fun normalizeActivityType(activityType: Int): Int {
        return if (activityType == DetectedActivity.UNKNOWN) {
            DetectedActivity.STILL
        } else {
            activityType
        }
    }
}
