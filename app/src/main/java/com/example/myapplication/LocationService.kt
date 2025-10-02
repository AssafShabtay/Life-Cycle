package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.myapplication.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.Date
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: ActivityDatabase
    private lateinit var dao: ActivityDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null

    // Current activity tracking
    private var currentActivity: Int = DetectedActivity.UNKNOWN
    private var isInActivity: Boolean = false

    // Movement tracking variables
    private var movementStartLocation: Location? = null
    private var movementStartTime: Date? = null
    private var currentActivityId: Long? = null
    private var currentMovementActivity: MovementActivity? = null
    private val locationBuffer: MutableList<LocationTrack> = mutableListOf()
    private var lastKnownLocation: Location? = null

    // Still location tracking
    private var stillStartLocation: Location? = null
    private var stillStartTime: Date? = null
    private var currentStillLocationId: Long? = null

    // Movement detection
    private var movementCenterLocation: Location? = null
    private var maxDistanceFromCenter: Float = 0f
    private val MOVEMENT_RADIUS_THRESHOLD = 100f // 100 meters
    private var hasMovedBeyondThreshold = false

    // Periodic save interval (milliseconds)
    private val SAVE_INTERVAL = 30000L // Save every 30 seconds

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "LocationServiceChannel"
        const val TAG = "LocationService"
        const val ACTION_ACTIVITY_UPDATE_UI = "com.example.myapplication.ACTIVITY_UPDATE_UI"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
        // Activity types that involve movement
        val MOVEMENT_ACTIVITIES = setOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
            DetectedActivity.ON_BICYCLE
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        database = ActivityDatabase.getDatabase(this)
        dao = database.activityDao()
        createLocationCallback()
        createLocationRequest(DetectedActivity.UNKNOWN)
        startPeriodicSave()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActivityTransitionReceiver.ACTION_ACTIVITY_UPDATE) {
            val activityType = intent.getIntExtra(
                ActivityTransitionReceiver.EXTRA_ACTIVITY_TYPE,
                DetectedActivity.UNKNOWN
            )
            val transitionType = intent.getIntExtra(
                ActivityTransitionReceiver.EXTRA_TRANSITION_TYPE,
                ActivityTransition.ACTIVITY_TRANSITION_EXIT
            )

            handleActivityUpdate(activityType, transitionType)
            return START_STICKY
        }

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Location service started")
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopPeriodicSave()

        // Save any pending data
        serviceScope.launch {
            savePendingData()
        }

        serviceScope.cancel()
        Log.d(TAG, "Location service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startPeriodicSave() {
        saveRunnable = object : Runnable {
            override fun run() {
                serviceScope.launch {
                    updateCurrentActivityProgress()
                }
                saveHandler.postDelayed(this, SAVE_INTERVAL)
            }
        }
        saveHandler.postDelayed(saveRunnable!!, SAVE_INTERVAL)
    }

    private fun stopPeriodicSave() {
        saveRunnable?.let {
            saveHandler.removeCallbacks(it)
        }
    }

    private suspend fun updateCurrentActivityProgress() {
        try {
            Log.d(TAG, "Updating current activity progress...")

            // Update still location progress
            if (currentActivity == DetectedActivity.STILL && stillStartTime != null && stillStartLocation != null) {
                val duration = Date().time - stillStartTime!!.time

                if (currentStillLocationId != null) {
                    // Update existing still location with current duration
                    val existingLocation = dao.getStillLocationById(currentStillLocationId!!)
                    existingLocation?.let {
                        val updated = it.copy(
                            duration = duration,
                            latitude = lastKnownLocation?.latitude ?: it.latitude,
                            longitude = lastKnownLocation?.longitude ?: it.longitude
                        )
                        dao.updateStillLocation(updated)
                        Log.d(TAG, "Updated still location duration: ${duration / 1000}s")
                    }
                }
            }

            // Update movement activity progress
            if (currentActivity in MOVEMENT_ACTIVITIES && movementStartTime != null && movementStartLocation != null) {
                val endLocation = lastKnownLocation ?: movementStartLocation
                val distance = movementStartLocation!!.distanceTo(endLocation!!)
                val currentTime = Date()

                if (currentActivityId != null && currentMovementActivity != null) {
                    // Update existing movement activity with current progress
                    val updated = currentMovementActivity!!.copy(
                        endLatitude = endLocation.latitude,
                        endLongitude = endLocation.longitude,
                        endTime = currentTime,
                        distance = distance,
                        actuallyMoved = hasMovedBeyondThreshold || maxDistanceFromCenter > MOVEMENT_RADIUS_THRESHOLD
                    )
                    dao.updateMovementActivity(updated)
                    currentMovementActivity = updated
                    Log.d(TAG, "Updated movement activity progress, distance: ${distance}m")
                }

                // Save buffered location tracks
                if (locationBuffer.isNotEmpty() && currentActivityId != null) {
                    flushLocationBuffer()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating activity progress: ${e.message}", e)
        }
    }

    private fun handleActivityUpdate(activityType: Int, transitionType: Int) {
        val previousActivity = currentActivity
        val wasInActivity = isInActivity

        currentActivity = activityType
        isInActivity = (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        Log.d(TAG, "Activity update: ${getActivityName(activityType)}, Enter: $isInActivity")

        serviceScope.launch {
            if (isInActivity) {
                // Save any previous activity before starting new one
                savePendingData()

                // Entering a new activity - create database record immediately
                if (activityType == DetectedActivity.STILL) {
                    startStillTracking()
                } else if (activityType in MOVEMENT_ACTIVITIES) {
                    startMovementTracking(activityType)
                }
            } else {
                // Exiting an activity - finalize the record
                if (previousActivity == DetectedActivity.STILL) {
                    endStillTracking()
                } else if (previousActivity in MOVEMENT_ACTIVITIES) {
                    endMovementTracking(previousActivity)
                }
            }
        }

        // Notify UI about the activity change
        val uiIntent = Intent(ACTION_ACTIVITY_UPDATE_UI).apply {
            putExtra(EXTRA_ACTIVITY_TYPE, activityType)
            putExtra(EXTRA_TRANSITION_TYPE, transitionType)
        }
        sendBroadcast(uiIntent)

        updateLocationTrackingForActivity(activityType, isInActivity)
        updateNotification()
    }

    private suspend fun startStillTracking() {
        Log.d(TAG, "Starting still tracking - creating database record immediately")

        stillStartLocation = lastKnownLocation
        stillStartTime = Date()

        // Create database record immediately
        if (stillStartLocation != null) {
            val stillLocation = StillLocation(
                latitude = stillStartLocation!!.latitude,
                longitude = stillStartLocation!!.longitude,
                timestamp = stillStartTime!!,
                duration = 0L // Start with 0 duration, will be updated
            )
            currentStillLocationId = dao.insertStillLocation(stillLocation)
            Log.d(TAG, "Created still location record with ID: $currentStillLocationId")
        }
    }

    private suspend fun endStillTracking() {
        Log.d(TAG, "Ending still tracking - finalizing database record")

        // Finalize the still location record
        if (stillStartLocation != null && stillStartTime != null && currentStillLocationId != null) {
            val duration = Date().time - stillStartTime!!.time
            val existingLocation = dao.getStillLocationById(currentStillLocationId!!)

            existingLocation?.let {
                val finalLocation = it.copy(
                    duration = duration,
                    latitude = lastKnownLocation?.latitude ?: it.latitude,
                    longitude = lastKnownLocation?.longitude ?: it.longitude
                )
                dao.updateStillLocation(finalLocation)
                Log.d(TAG, "Finalized still location: duration ${duration / 1000}s")
            }
        }

        // Reset tracking variables
        stillStartLocation = null
        stillStartTime = null
        currentStillLocationId = null
    }

    private suspend fun startMovementTracking(activityType: Int) {
        Log.d(TAG, "Starting movement tracking for ${getActivityName(activityType)} - creating database record immediately")

        movementStartLocation = lastKnownLocation
        movementStartTime = Date()
        movementCenterLocation = lastKnownLocation
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        locationBuffer.clear()

        // Create database record immediately
        if (movementStartLocation != null) {
            val initialActivity = MovementActivity(
                activityType = getActivityName(activityType),
                startLatitude = movementStartLocation!!.latitude,
                startLongitude = movementStartLocation!!.longitude,
                endLatitude = movementStartLocation!!.latitude, // Initially same as start
                endLongitude = movementStartLocation!!.longitude, // Initially same as start
                startTime = movementStartTime!!,
                endTime = movementStartTime!!, // Initially same as start
                distance = 0f, // Start with 0 distance
                actuallyMoved = false // Initially hasn't moved
            )
            currentActivityId = dao.insertMovementActivity(initialActivity)
            currentMovementActivity = initialActivity.copy(id = currentActivityId!!)
            Log.d(TAG, "Created movement activity record with ID: $currentActivityId")
        }
    }

    private suspend fun endMovementTracking(activityType: Int) {
        Log.d(TAG, "Ending movement tracking for ${getActivityName(activityType)} - finalizing database record")

        val startLoc = movementStartLocation
        val endLoc = lastKnownLocation
        val startTime = movementStartTime ?: Date()
        val endTime = Date()

        if (startLoc != null && endLoc != null && currentActivityId != null) {
            val durationMillis = endTime.time - startTime.time
            val durationMinutes = durationMillis / (1000 * 60)

            // Handle ON_FOOT activities shorter than 15 minutes
            if (activityType == DetectedActivity.ON_FOOT && durationMinutes < 15) {
                Log.d(TAG, "ON_FOOT activity duration (${durationMinutes}m) is less than 15 minutes - converting to still location")

                // Delete the movement activity record
                dao.deleteMovementActivity(currentActivityId!!)

                // Create a still location instead
                val centerLoc = movementCenterLocation ?: startLoc
                val stillLocation = StillLocation(
                    latitude = centerLoc.latitude,
                    longitude = centerLoc.longitude,
                    timestamp = startTime,
                    duration = durationMillis,
                    wasSupposedToBeActivity = "On Foot (< 15 min)"
                )
                dao.insertStillLocation(stillLocation)
            } else if (!hasMovedBeyondThreshold && maxDistanceFromCenter < MOVEMENT_RADIUS_THRESHOLD) {
                // User didn't actually move - convert to still location
                Log.d(TAG, "User stayed within ${MOVEMENT_RADIUS_THRESHOLD}m radius - converting to still location")

                // Delete the movement activity record
                dao.deleteMovementActivity(currentActivityId!!)

                // Create a still location instead
                val centerLoc = movementCenterLocation ?: startLoc
                val stillLocation = StillLocation(
                    latitude = centerLoc.latitude,
                    longitude = centerLoc.longitude,
                    timestamp = startTime,
                    duration = durationMillis,
                    wasSupposedToBeActivity = getActivityName(activityType)
                )
                dao.insertStillLocation(stillLocation)
            } else {
                // Finalize the movement activity with actual movement
                val distance = startLoc.distanceTo(endLoc)
                val finalActivity = currentMovementActivity!!.copy(
                    endLatitude = endLoc.latitude,
                    endLongitude = endLoc.longitude,
                    endTime = endTime,
                    distance = distance,
                    actuallyMoved = true
                )
                dao.updateMovementActivity(finalActivity)
                Log.d(TAG, "Finalized movement activity: distance ${distance}m, duration ${durationMinutes}min")

                // Save any remaining location tracks
                flushLocationBuffer()
            }
        }

        // Reset tracking variables
        movementStartLocation = null
        movementStartTime = null
        movementCenterLocation = null
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        locationBuffer.clear()
        currentActivityId = null
        currentMovementActivity = null
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    lastKnownLocation = location

                    // Handle new location data based on current activity
                    val activityName = getActivityName(currentActivity)
                    Log.d(TAG, "[$activityName] Lat: ${location.latitude}, Lng: ${location.longitude}")

                    serviceScope.launch {
                        when {
                            currentActivity == DetectedActivity.STILL -> {
                                handleStillLocation(location)
                            }
                            currentActivity in MOVEMENT_ACTIVITIES -> {
                                handleMovementLocation(location)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleStillLocation(location: Location) {
        // Update the current still location if we have one
        if (stillStartLocation == null) {
            stillStartLocation = location
            stillStartTime = Date()

            // Create initial database record if it doesn't exist
            if (currentStillLocationId == null) {
                val stillLocation = StillLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = stillStartTime!!,
                    duration = 0L
                )
                currentStillLocationId = dao.insertStillLocation(stillLocation)
                Log.d(TAG, "Created still location record on first location update")
            }
        }

        // Update location for minor movements while still
        stillStartLocation = location
    }

    private suspend fun handleMovementLocation(location: Location) {
        // Track movement to detect if user stays within 100m radius
        movementCenterLocation?.let { center ->
            val distance = center.distanceTo(location)
            if (distance > maxDistanceFromCenter) {
                maxDistanceFromCenter = distance
            }
            if (distance > MOVEMENT_RADIUS_THRESHOLD) {
                hasMovedBeyondThreshold = true
                Log.d(TAG, "User moved beyond ${MOVEMENT_RADIUS_THRESHOLD}m threshold: ${distance}m")
            }
        }

        // Add to location buffer for later saving
        locationBuffer.add(
            LocationTrack(
                activityId = currentActivityId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = Date(),
                speed = if (location.hasSpeed()) location.speed else null,
                accuracy = location.accuracy
            )
        )

        // Keep buffer size manageable - save to database periodically
        if (locationBuffer.size >= 50) {
            flushLocationBuffer()
        }
    }

    private suspend fun flushLocationBuffer() {
        if (locationBuffer.isNotEmpty() && currentActivityId != null) {
            val tracks = locationBuffer.map { it.copy(activityId = currentActivityId) }
            dao.insertLocationTracks(tracks)
            locationBuffer.clear()
            Log.d(TAG, "Flushed ${tracks.size} location tracks to database")
        }
    }

    private suspend fun savePendingData() {
        // Update any pending still location
        if (currentActivity == DetectedActivity.STILL && currentStillLocationId != null) {
            updateCurrentActivityProgress()
        }

        // Update any pending movement activity
        if (currentActivity in MOVEMENT_ACTIVITIES && currentActivityId != null) {
            updateCurrentActivityProgress()
        }
    }

    private fun updateLocationTrackingForActivity(activityType: Int, entering: Boolean) {
        if (!entering) {
            // User exited activity, use default tracking
            createLocationRequest(DetectedActivity.UNKNOWN)
        } else {
            // User entered activity, adjust tracking accordingly
            createLocationRequest(activityType)
        }

        // Restart location updates with new parameters
        stopLocationUpdates()
        startLocationUpdates()
    }

    private fun createLocationRequest(activityType: Int) {
        locationRequest = when (activityType) {
            DetectedActivity.IN_VEHICLE -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    TimeUnit.SECONDS.toMillis(5)  // Update every 5 seconds
                )
                    .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(3))
                    .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(10))
                    .build()
            }
            DetectedActivity.RUNNING -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    TimeUnit.SECONDS.toMillis(10)  // Update every 10 seconds
                )
                    .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(5))
                    .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(15))
                    .build()
            }
            DetectedActivity.WALKING -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    TimeUnit.SECONDS.toMillis(15)  // Update every 15 seconds for better tracking
                )
                    .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(10))
                    .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(30))
                    .build()
            }
            DetectedActivity.ON_BICYCLE -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    TimeUnit.SECONDS.toMillis(10)  // Update every 10 seconds
                )
                    .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(5))
                    .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(20))
                    .build()
            }
            DetectedActivity.STILL -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    TimeUnit.MINUTES.toMillis(2)  // Update every 2 minutes for still
                )
                    .setMinUpdateIntervalMillis(TimeUnit.MINUTES.toMillis(1))
                    .setMaxUpdateDelayMillis(TimeUnit.MINUTES.toMillis(5))
                    .build()
            }
            else -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    TimeUnit.SECONDS.toMillis(30)
                )
                    .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(20))
                    .setMaxUpdateDelayMillis(TimeUnit.MINUTES.toMillis(1))
                    .build()
            }
        }

        Log.d(TAG, "Location request updated for activity: ${getActivityName(activityType)}")
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No location permissions")
            return
        }
        Log.d(TAG, "Starting location updates")
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText(getNotificationText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

    private fun updateNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setContentText(getNotificationText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationText(): String {
        val activityName = getActivityName(currentActivity)
        val movementStatus = if (hasMovedBeyondThreshold) " (moving)" else " (stationary)"
        return if (isInActivity) {
            "Current activity: $activityName${if (currentActivity in MOVEMENT_ACTIVITIES) movementStatus else ""}"
        } else {
            "Location Tracking Active"
        }
    }

    private fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "Driving"
            DetectedActivity.ON_BICYCLE -> "Cycling"
            DetectedActivity.ON_FOOT -> "On Foot"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.WALKING -> "Walking"
            DetectedActivity.UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
    }
}