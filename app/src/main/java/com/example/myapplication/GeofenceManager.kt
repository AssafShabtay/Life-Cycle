package com.example.myapplication


import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

object GeofenceManager {
    private const val TAG = "GeofenceManager"
    private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 1001

    fun addGeofence(context: Context, slot: LocationSlot) {
        if (!hasLocationPermission(context)) {
            Log.e(TAG, "No location permission")
            return
        }

        val geofencingClient = LocationServices.getGeofencingClient(context)

        // Create the geofence
        val geofence = Geofence.Builder()
            .setRequestId(slot.id.toString())
            .setCircularRegion(
                slot.latitude,
                slot.longitude,
                slot.radius
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                getTransitionTypes(slot)
            )
            .setLoiteringDelay(30000) // 30 seconds
            .build()

        // Create geofencing request
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        // Add the geofence
        try {
            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent(context))
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added: ${slot.name}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence: ${e.message}")
                    handleGeofenceError(e)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception adding geofence: ${e.message}")
        }
    }

    fun removeGeofence(context: Context, geofenceId: String) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                Log.d(TAG, "Geofence removed: $geofenceId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofence: ${e.message}")
            }
    }

    fun removeAllGeofences(context: Context) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        geofencingClient.removeGeofences(getGeofencePendingIntent(context))
            .addOnSuccessListener {
                Log.d(TAG, "All geofences removed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove all geofences: ${e.message}")
            }
    }

    private fun getTransitionTypes(slot: LocationSlot): Int {
        var transitions = Geofence.GEOFENCE_TRANSITION_DWELL

        if (slot.notifyOnEnter) {
            transitions = transitions or Geofence.GEOFENCE_TRANSITION_ENTER
        }

        if (slot.notifyOnExit) {
            transitions = transitions or Geofence.GEOFENCE_TRANSITION_EXIT
        }

        return transitions
    }

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                GEOFENCE_PENDING_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                GEOFENCE_PENDING_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleGeofenceError(exception: Exception) {
        val errorMessage = GeofenceStatusCodes.getStatusCodeString(
            (exception.message?.toIntOrNull() ?: 0)
        )
        Log.e(TAG, "Geofence error: $errorMessage")
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "GeofenceReceiver"
        const val CHANNEL_ID = "GeofenceChannel"
        const val ACTION_GEOFENCE_EVENT = "com.example.myapplication.GEOFENCE_EVENT"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Geofence broadcast received")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            Log.e(TAG, "Geofencing error: ${GeofenceStatusCodes.getStatusCodeString(errorCode)}")
            return
        }

        // Get the geofence transition type
        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        Log.d(TAG, "Geofence transition: $geofenceTransition, Triggered: ${triggeringGeofences.size} geofences")

        // Process each triggered geofence
        scope.launch {
            val database = ActivityDatabase.getDatabase(context)
            val geofenceDao = database.geofenceDao()

            for (geofence in triggeringGeofences) {
                val slotId = geofence.requestId.toLongOrNull() ?: continue
                val slot = geofenceDao.getLocationSlotById(slotId) ?: continue

                when (geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        handleGeofenceEnter(context, slot, geofencingEvent)
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        handleGeofenceExit(context, slot, geofencingEvent)
                    }
                    Geofence.GEOFENCE_TRANSITION_DWELL -> {
                        handleGeofenceDwell(context, slot, geofencingEvent)
                    }
                }
            }
        }
    }

    private suspend fun handleGeofenceEnter(
        context: Context,
        slot: LocationSlot,
        event: GeofencingEvent
    ) {
        Log.d(TAG, "Entered geofence: ${slot.name}")

        val database = ActivityDatabase.getDatabase(context)
        val geofenceDao = database.geofenceDao()

        // Record the event
        val geofenceEvent = GeofenceEvent(
            slotId = slot.id,
            eventType = "ENTER",
            timestamp = Date(),
            latitude = event.triggeringLocation?.latitude ?: slot.latitude,
            longitude = event.triggeringLocation?.longitude ?: slot.longitude
        )
        geofenceDao.insertGeofenceEvent(geofenceEvent)

        // Start a new visit
        val visit = LocationVisit(
            slotId = slot.id,
            entryTime = Date(),
            entryLatitude = event.triggeringLocation?.latitude ?: slot.latitude,
            entryLongitude = event.triggeringLocation?.longitude ?: slot.longitude
        )
        geofenceDao.insertLocationVisit(visit)

        // Send notification if enabled
        if (slot.notifyOnEnter) {
            showNotification(
                context,
                "Arrived at ${slot.name}",
                "You've entered ${slot.name}",
                slot
            )
        }

        // Send broadcast to update UI
        sendGeofenceUpdateBroadcast(context, slot, "ENTER")
    }

    private suspend fun handleGeofenceExit(
        context: Context,
        slot: LocationSlot,
        event: GeofencingEvent
    ) {
        Log.d(TAG, "Exited geofence: ${slot.name}")

        val database = ActivityDatabase.getDatabase(context)
        val geofenceDao = database.geofenceDao()

        // Record the event
        val geofenceEvent = GeofenceEvent(
            slotId = slot.id,
            eventType = "EXIT",
            timestamp = Date(),
            latitude = event.triggeringLocation?.latitude ?: slot.latitude,
            longitude = event.triggeringLocation?.longitude ?: slot.longitude
        )
        geofenceDao.insertGeofenceEvent(geofenceEvent)

        // Complete the current visit
        val currentVisit = geofenceDao.getCurrentVisitForSlot(slot.id)
        currentVisit?.let { visit ->
            val exitTime = Date()
            val duration = exitTime.time - visit.entryTime.time
            val updatedVisit = visit.copy(
                exitTime = exitTime,
                duration = duration,
                exitLatitude = event.triggeringLocation?.latitude ?: slot.latitude,
                exitLongitude = event.triggeringLocation?.longitude ?: slot.longitude
            )
            geofenceDao.updateLocationVisit(updatedVisit)

            // Send notification if enabled
            if (slot.notifyOnExit) {
                val durationText = formatDuration(duration)
                showNotification(
                    context,
                    "Left ${slot.name}",
                    "You were there for $durationText",
                    slot
                )
            }
        }

        // Send broadcast to update UI
        sendGeofenceUpdateBroadcast(context, slot, "EXIT")
    }

    private suspend fun handleGeofenceDwell(
        context: Context,
        slot: LocationSlot,
        event: GeofencingEvent
    ) {
        Log.d(TAG, "Dwelling in geofence: ${slot.name}")

        // Optionally handle dwelling (staying in location for extended time)
        // This could be used for more detailed tracking or special notifications
    }

    private fun showNotification(
        context: Context,
        title: String,
        content: String,
        slot: LocationSlot
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setColor(slot.color.toInt())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(slot.id.toInt(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
        }
    }

    private fun sendGeofenceUpdateBroadcast(
        context: Context,
        slot: LocationSlot,
        eventType: String
    ) {
        val intent = Intent(ACTION_GEOFENCE_EVENT).apply {
            putExtra("slot_id", slot.id)
            putExtra("slot_name", slot.name)
            putExtra("event_type", eventType)
        }
        context.sendBroadcast(intent)
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "less than a minute"
        }
    }
}