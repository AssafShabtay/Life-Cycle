package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ActivityTransition"
        const val ACTION_ACTIVITY_UPDATE = "com.example.myapplication.ACTIVITY_UPDATE"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 ActivityTransitionReceiver.onReceive() called")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras?.keySet()?.joinToString()}")

        if (ActivityTransitionResult.hasResult(intent)) {
            Log.d(TAG, "✅ ActivityTransitionResult found in intent")
            val result = ActivityTransitionResult.extractResult(intent)
            result?.let {
                Log.d(TAG, "Processing ${it.transitionEvents.size} transition events")
                handleActivityTransitions(context, it)
            } ?: run {
                Log.w(TAG, "❌ ActivityTransitionResult was null")
            }
        } else {
            Log.w(TAG, "❌ No ActivityTransitionResult found in intent")
        }
    }

    private fun handleActivityTransitions(context: Context, result: ActivityTransitionResult) {
        for (event in result.transitionEvents) {
            val normalizedActivityType = normalizeActivityType(event.activityType)
            val activityName = getActivityName(normalizedActivityType)
            val transitionType = getTransitionType(event.transitionType)
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(event.elapsedRealTimeNanos / 1_000_000))

            Log.d(TAG, "🏃 Activity: $activityName, Transition: $transitionType, Time: $timestamp")

            when (normalizedActivityType) {
                DetectedActivity.IN_VEHICLE -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleDrivingStarted(context)
                    } else {
                        handleDrivingStopped(context)
                    }
                }
                DetectedActivity.RUNNING -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleRunningStarted(context)
                    } else {
                        handleRunningStopped(context)
                    }
                }
                DetectedActivity.WALKING -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleWalkingStarted(context)
                    } else {
                        handleWalkingStopped(context)
                    }
                }
                DetectedActivity.ON_BICYCLE -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleCyclingStarted(context)
                    } else {
                        handleCyclingStopped(context)
                    }
                }
                DetectedActivity.STILL -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleStillStarted(context)
                    } else {
                        handleStillStopped(context)
                    }
                }
                DetectedActivity.ON_FOOT -> {
                    if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        handleOnFootStarted(context)
                    } else {
                        handleOnFootStopped(context)
                    }
                }
            }

            // Notify LocationService about activity change
            notifyLocationService(context, event, normalizedActivityType)
        }
    }

    private fun notifyLocationService(context: Context, event: ActivityTransitionEvent, activityType: Int) {
        Log.d(TAG, "📨 Notifying LocationService of activity change")
        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = ACTION_ACTIVITY_UPDATE
            putExtra(EXTRA_ACTIVITY_TYPE, activityType)
            putExtra(EXTRA_TRANSITION_TYPE, event.transitionType)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "✅ Service intent sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start service: ${e.message}")
        }
    }

    // DRIVING
    private fun handleDrivingStarted(context: Context) {
        Log.d(TAG, "🚗 User started driving")
        // Add your custom logic for when driving starts
        // Example: Increase location update frequency, start route tracking
        // You can also send notifications, update database, etc.
    }

    private fun handleDrivingStopped(context: Context) {
        Log.d(TAG, "🚗 User stopped driving")
        // Add your custom logic for when driving stops
        // Example: Save trip data, calculate distance traveled
    }

    // RUNNING
    private fun handleRunningStarted(context: Context) {
        Log.d(TAG, "🏃 User started running")
        // Add your custom logic for when running starts
        // Example: Start fitness tracking, increase location accuracy
    }

    private fun handleRunningStopped(context: Context) {
        Log.d(TAG, "🏃 User stopped running")
        // Add your custom logic for when running stops
        // Example: Calculate calories burned, save workout data
    }

    // WALKING
    private fun handleWalkingStarted(context: Context) {
        Log.d(TAG, "🚶 User started walking")
        // Add your custom logic for when walking starts
        // Example: Start step counting, moderate location updates
    }

    private fun handleWalkingStopped(context: Context) {
        Log.d(TAG, "🚶 User stopped+ walking")
        // Add your custom logic for when walking stops
        // Example: Save walking session data
    }

    // CYCLING
    private fun handleCyclingStarted(context: Context) {
        Log.d(TAG, "🚴 User started cycling")
        // Add your custom logic for when cycling starts
        // Example: Start cycling workout tracking
    }

    private fun handleCyclingStopped(context: Context) {
        Log.d(TAG, "🚴 User stopped cycling")
        // Add your custom logic for when cycling stops
        // Example: Calculate cycling statistics
    }

    // STILL
    private fun handleStillStarted(context: Context) {
        Log.d(TAG, "🧍 User became still")
        // Add your custom logic for when user becomes still
        // Example: Reduce location update frequency to save battery
    }

    private fun handleStillStopped(context: Context) {
        Log.d(TAG, "🧍 User is no longer still")
        // Add your custom logic for when user starts moving
        // Example: Resume normal location tracking
    }

    // ON_FOOT
    private fun handleOnFootStarted(context: Context) {
        Log.d(TAG, "👣 User is on foot")
        // Add your custom logic for general on-foot activity
    }

    private fun handleOnFootStopped(context: Context) {
        Log.d(TAG, "👣 User is no longer on foot")
        // Add your custom logic when on-foot activity ends
    }

    private fun normalizeActivityType(activityType: Int): Int {
        return if (activityType == DetectedActivity.UNKNOWN) {
            DetectedActivity.STILL
        } else {
            activityType
        }
    }

    private fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.UNKNOWN -> "STILL"
            else -> "UNKNOWN"
        }
    }

    private fun getTransitionType(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN"
        }
    }
}