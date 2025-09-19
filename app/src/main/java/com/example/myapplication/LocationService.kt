package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
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

    // Current activity tracking
    private var currentActivity: Int = DetectedActivity.UNKNOWN
    private var isInActivity: Boolean = false

    // Movement tracking variables
    private var movementStartLocation: Location? = null
    private var movementStartTime: Date? = null
    private var currentActivityId: Long? = null
    private val locationBuffer: MutableList<LocationTrack> = mutableListOf()
    private var lastKnownLocation: Location? = null

    // Still location tracking
    private var stillStartLocation: Location? = null
    private var stillStartTime: Date? = null

    // Movement detection
    private var movementCenterLocation: Location? = null
    private var maxDistanceFromCenter: Float = 0f
    private val MOVEMENT_RADIUS_THRESHOLD = 100f // 100 meters
    private var hasMovedBeyondThreshold = false

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

    private fun handleActivityUpdate(activityType: Int, transitionType: Int) {
        val previousActivity = currentActivity
        val wasInActivity = isInActivity

        currentActivity = activityType
        isInActivity = (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        Log.d(TAG, "Activity update: ${getActivityName(activityType)}, Enter: $isInActivity")

        serviceScope.launch {
            if (isInActivity) {
                // Entering a new activity
                if (activityType == DetectedActivity.STILL) {
                    startStillTracking()
                } else if (activityType in MOVEMENT_ACTIVITIES) {
                    startMovementTracking(activityType)
                }
            } else {
                // Exiting an activity
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
        Log.d(TAG, "Starting still tracking")
        stillStartLocation = lastKnownLocation
        stillStartTime = Date()
    }

    private suspend fun endStillTracking() {
        Log.d(TAG, "Ending still tracking")
        stillStartLocation?.let { startLoc ->
            val stillLocation = StillLocation(
                latitude = startLoc.latitude,
                longitude = startLoc.longitude,
                timestamp = stillStartTime ?: Date(),
                duration = stillStartTime?.let { Date().time - it.time }
            )
            dao.insertStillLocation(stillLocation)
            Log.d(TAG, "Saved still location: ${startLoc.latitude}, ${startLoc.longitude}")
        }
        stillStartLocation = null
        stillStartTime = null
    }

    private suspend fun startMovementTracking(activityType: Int) {
        Log.d(TAG, "Starting movement tracking for ${getActivityName(activityType)}")
        movementStartLocation = lastKnownLocation
        movementStartTime = Date()
        movementCenterLocation = lastKnownLocation
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        locationBuffer.clear()
    }

    private suspend fun endMovementTracking(activityType: Int) {
        Log.d(TAG, "Ending movement tracking for ${getActivityName(activityType)}")

        val startLoc = movementStartLocation
        val endLoc = lastKnownLocation
        val startTime = movementStartTime ?: Date()
        val endTime = Date()

        if (startLoc != null && endLoc != null) {
            // Check if user actually moved beyond 100m radius
            if (!hasMovedBeyondThreshold && maxDistanceFromCenter < MOVEMENT_RADIUS_THRESHOLD) {
                // User didn't actually move - save as still location
                Log.d(TAG, "User stayed within ${MOVEMENT_RADIUS_THRESHOLD}m radius - saving as still location")
                val centerLoc = movementCenterLocation ?: startLoc
                val stillLocation = StillLocation(
                    latitude = centerLoc.latitude,
                    longitude = centerLoc.longitude,
                    timestamp = startTime,
                    duration = endTime.time - startTime.time,
                    wasSupposedToBeActivity = getActivityName(activityType)
                )
                dao.insertStillLocation(stillLocation)
            } else {
                // User actually moved - save as movement activity
                val distance = startLoc.distanceTo(endLoc)
                val movementActivity = MovementActivity(
                    activityType = getActivityName(activityType),
                    startLatitude = startLoc.latitude,
                    startLongitude = startLoc.longitude,
                    endLatitude = endLoc.latitude,
                    endLongitude = endLoc.longitude,
                    startTime = startTime,
                    endTime = endTime,
                    distance = distance,
                    actuallyMoved = true
                )
                val activityId = dao.insertMovementActivity(movementActivity)

                // Save location tracks
                if (locationBuffer.isNotEmpty()) {
                    val tracks = locationBuffer.map { it.copy(activityId = activityId) }
                    dao.insertLocationTracks(tracks)
                    Log.d(TAG, "Saved ${tracks.size} location tracks for activity")
                }
            }
        } else {
            Log.w(TAG, "Missing location data for movement tracking")
        }

        // Reset tracking variables
        movementStartLocation = null
        movementStartTime = null
        movementCenterLocation = null
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        locationBuffer.clear()
        currentActivityId = null
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
        // If we don't have a still start location, set it
        if (stillStartLocation == null) {
            stillStartLocation = location
            stillStartTime = Date()
        }
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
        if (locationBuffer.size >= 100) {
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
        // Save any pending still location
        if (currentActivity == DetectedActivity.STILL) {
            endStillTracking()
        }

        // Save any pending movement activity
        if (currentActivity in MOVEMENT_ACTIVITIES) {
            endMovementTracking(currentActivity)
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