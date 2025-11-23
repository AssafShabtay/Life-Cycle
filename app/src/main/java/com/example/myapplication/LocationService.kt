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
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.*
import java.util.Date
import java.util.concurrent.TimeUnit

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: ActivityDatabase
    private lateinit var dao: ActivityDao
    private lateinit var sleepDetectionManager: SleepDetectionManager

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

    private var placesClient: PlacesClient? = null
    private var placesInitializationAttempted = false

    // Movement detection
    private var movementCenterLocation: Location? = null
    private var maxDistanceFromCenter: Float = 0f
    private val DEFAULT_MOVEMENT_RADIUS_THRESHOLD = 100f // Fallback when we don't know the activity
    private var movementRadiusThreshold = DEFAULT_MOVEMENT_RADIUS_THRESHOLD
    private var hasMovedBeyondThreshold = false
    private var previousMovementLocation: Location? = null
    private var cumulativeMovementDistance: Float = 0f
    private val MOVEMENT_SPEED_THRESHOLD_MPS = 1.3f
    private val MIN_STEP_DISTANCE_METERS = 8f
    private val MAX_NOISE_ACCURACY_METERS = 75f

    // Periodic save interval (milliseconds)
    private val SAVE_INTERVAL = 30000L // Save every 30 seconds


    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "LocationServiceChannel"
        const val TAG = "LocationService"
        const val ACTION_ACTIVITY_UPDATE_UI = "com.example.myapplication.ACTIVITY_UPDATE_UI"
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
        const val EXTRA_ACTIVITY_NAME = "extra_activity_name"
        // Activity types that involve movement
        val MOVEMENT_ACTIVITIES = setOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.ON_BICYCLE
        )
        private const val STILL_PLACE_MATCH_RADIUS_METERS = 200.0
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        database = ActivityDatabase.getDatabase(this)
        dao = database.activityDao()
        sleepDetectionManager = SleepDetectionManager(dao)
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
                val rawDistance = movementStartLocation!!.distanceTo(endLocation!!)
                val distance = max(rawDistance, cumulativeMovementDistance)
                val currentTime = Date()

                if (currentActivityId != null && currentMovementActivity != null) {
                    // Update existing movement activity with current progress
                    val updated = currentMovementActivity!!.copy(
                        endLatitude = endLocation.latitude,
                        endLongitude = endLocation.longitude,
                        endTime = currentTime,
                        distance = distance,
                        actuallyMoved = hasMovedBeyondThreshold || maxDistanceFromCenter > movementRadiusThreshold
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
        val normalizedActivityType = normalizeActivityType(activityType)
        if (normalizedActivityType == DetectedActivity.UNKNOWN) {
            Log.d(TAG, "Ignoring unknown activity update (raw=$activityType)")
            return
        }

        val previousActivity = currentActivity
        val wasInActivity = isInActivity
        val enteringActivity = transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER

        Log.d(
            TAG,
            "Activity update: ${getActivityName(normalizedActivityType)} (raw=$activityType), enter=$enteringActivity"
        )

        if (enteringActivity) {
            if (wasInActivity && previousActivity == normalizedActivityType) {
                Log.d(TAG, "Duplicate enter for ${getActivityName(normalizedActivityType)} ignored")
                return
            }

            currentActivity = normalizedActivityType
            isInActivity = true

            serviceScope.launch {
                if (wasInActivity) {
                    finalizeActivity(previousActivity)
                }
                when {
                    normalizedActivityType == DetectedActivity.STILL -> startStillTracking()
                    normalizedActivityType in MOVEMENT_ACTIVITIES -> startMovementTracking(normalizedActivityType)
                }
            }

            sendActivityUpdate(normalizedActivityType, transitionType)
            updateLocationTrackingForActivity(normalizedActivityType, true)
        } else {
            if (!wasInActivity || previousActivity != normalizedActivityType) {
                Log.d(
                    TAG,
                    "Exit for ${getActivityName(normalizedActivityType)} ignored (current=${getActivityName(previousActivity)}, inActivity=$wasInActivity)"
                )
                return
            }

            isInActivity = false

            serviceScope.launch {
                finalizeActivity(normalizedActivityType)
            }

            currentActivity = DetectedActivity.UNKNOWN
            sendActivityUpdate(currentActivity, transitionType)
            updateLocationTrackingForActivity(currentActivity, false)
        }

        updateNotification()
    }

    private fun sendActivityUpdate(activityType: Int, transitionType: Int) {
        val uiIntent = Intent(ACTION_ACTIVITY_UPDATE_UI).apply {
            putExtra(EXTRA_ACTIVITY_TYPE, activityType)
            putExtra(EXTRA_TRANSITION_TYPE, transitionType)
            putExtra(EXTRA_ACTIVITY_NAME, getActivityName(activityType))
        }
        sendBroadcast(uiIntent)
    }

    private suspend fun finalizeActivity(activityType: Int) {
        when {
            activityType == DetectedActivity.STILL -> endStillTracking()
            activityType in MOVEMENT_ACTIVITIES -> endMovementTracking(activityType)
        }
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

        val startTime = stillStartTime
        val endTime = Date()
        var finalLatitude: Double? = lastKnownLocation?.latitude ?: stillStartLocation?.latitude
        var finalLongitude: Double? = lastKnownLocation?.longitude ?: stillStartLocation?.longitude

        if (currentStillLocationId != null && startTime != null) {
            val existingLocation = dao.getStillLocationById(currentStillLocationId!!)
            existingLocation?.let {
                if (finalLatitude == null) {
                    finalLatitude = it.latitude
                }
                if (finalLongitude == null) {
                    finalLongitude = it.longitude
                }

                val duration = endTime.time - startTime.time
                val updated = it.copy(
                    duration = duration,
                    latitude = finalLatitude ?: it.latitude,
                    longitude = finalLongitude ?: it.longitude
                )
                dao.updateStillLocation(updated)
                Log.d(TAG, "Finalized still location: duration ${duration / 1000}s")
                requestPlaceDetailsAsync(it.id, updated.latitude, updated.longitude)
            }
        }

        if (startTime != null) {
            sleepDetectionManager.processStillSession(
                startTime = startTime,
                endTime = endTime,
                latitude = finalLatitude,
                longitude = finalLongitude
            )
        }

        stillStartLocation = null
        stillStartTime = null
        currentStillLocationId = null
    }


    private suspend fun startMovementTracking(activityType: Int) {
        Log.d(TAG, "Starting movement tracking for ${getActivityName(activityType)} - creating database record immediately")

        movementStartLocation = lastKnownLocation?.let { Location(it) }
        movementStartTime = Date()
        movementCenterLocation = movementStartLocation?.let { Location(it) }
        previousMovementLocation = movementStartLocation?.let { Location(it) }
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        cumulativeMovementDistance = 0f
        movementRadiusThreshold = movementThresholdFor(activityType)
        Log.d(TAG, "Movement threshold for ${getActivityName(activityType)}: ${movementRadiusThreshold}m")
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

            val rawDistance = startLoc.distanceTo(endLoc)
            val distance = max(rawDistance, cumulativeMovementDistance)
            val finalActivity = currentMovementActivity!!.copy(
                endLatitude = endLoc.latitude,
                endLongitude = endLoc.longitude,
                endTime = endTime,
                distance = distance,
                actuallyMoved = hasMovedBeyondThreshold || maxDistanceFromCenter > movementRadiusThreshold
            )
            dao.updateMovementActivity(finalActivity)
            Log.d(TAG, "Finalized movement activity: distance ${distance}m, duration ${durationMinutes}min")

            // Save any remaining location tracks
            flushLocationBuffer()
        }

        // Reset tracking variables
        movementStartLocation = null
        movementStartTime = null
        movementCenterLocation = null
        maxDistanceFromCenter = 0f
        hasMovedBeyondThreshold = false
        previousMovementLocation = null
        cumulativeMovementDistance = 0f
        movementRadiusThreshold = DEFAULT_MOVEMENT_RADIUS_THRESHOLD
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
        if (movementCenterLocation == null) {
            movementCenterLocation = Location(location)
        }

        val accuracy = if (location.hasAccuracy()) location.accuracy else MAX_NOISE_ACCURACY_METERS
        movementCenterLocation?.let { center ->
            val rawDistance = center.distanceTo(location)
            val adjustedDistance = (rawDistance - accuracy).coerceAtLeast(0f)
            if (adjustedDistance > maxDistanceFromCenter) {
                maxDistanceFromCenter = adjustedDistance
            }
            if (!hasMovedBeyondThreshold && adjustedDistance >= movementRadiusThreshold) {
                hasMovedBeyondThreshold = true
                Log.d(
                    TAG,
                    "User moved beyond ${movementRadiusThreshold}m threshold (adjusted=${adjustedDistance}m, raw=${rawDistance}m)"
                )
            }
        }

        previousMovementLocation?.let { previous ->
            val stepDistance = previous.distanceTo(location)
            val accuracyPenalty = max(
                if (previous.hasAccuracy()) previous.accuracy else 0f,
                accuracy
            )
            val adjustedStep = (stepDistance - accuracyPenalty).coerceAtLeast(0f)
            if (adjustedStep >= MIN_STEP_DISTANCE_METERS) {
                cumulativeMovementDistance += adjustedStep
            }
        }
        previousMovementLocation = Location(location)

        if (!hasMovedBeyondThreshold && cumulativeMovementDistance >= movementRadiusThreshold) {
            hasMovedBeyondThreshold = true
            Log.d(TAG, "Cumulative distance ${cumulativeMovementDistance}m exceeded threshold ${movementRadiusThreshold}m")
        }

        if (!hasMovedBeyondThreshold && location.hasSpeed() &&
            location.speed >= MOVEMENT_SPEED_THRESHOLD_MPS && accuracy <= MAX_NOISE_ACCURACY_METERS
        ) {
            hasMovedBeyondThreshold = true
            Log.d(TAG, "Speed ${location.speed}m/s indicates movement")
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
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> {
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

    private fun movementThresholdFor(activityType: Int): Float {
        return when (activityType) {
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> 40f
            DetectedActivity.RUNNING -> 60f
            DetectedActivity.ON_BICYCLE -> 80f
            DetectedActivity.IN_VEHICLE -> 120f
            else -> DEFAULT_MOVEMENT_RADIUS_THRESHOLD
        }
    }

    private fun ensurePlacesClient(): PlacesClient? {
        placesClient?.let { return it }
        if (placesInitializationAttempted) {
            return null
        }
        placesInitializationAttempted = true
        val apiKey = getMapsApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Google Maps API key not configured; skipping place enrichment")
            return null
        }
        return try {
            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
            }
            Places.createClient(this).also { placesClient = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Places SDK: ${'$'}{e.message}")
            null
        }
    }

    private fun getMapsApiKey(): String? {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            applicationInfo.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Google Maps API key: ${'$'}{e.message}")
            null
        }
    }

    private fun requestPlaceDetailsAsync(stillLocationId: Long?, latitude: Double?, longitude: Double?) {
        if (stillLocationId == null || latitude == null || longitude == null) return
        serviceScope.launch {
            try {
                enrichStillLocationWithPlace(stillLocationId, latitude, longitude)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enrich still location ${'$'}stillLocationId: ${'$'}{e.message}")
            }
        }
    }

    private suspend fun enrichStillLocationWithPlace(
        stillLocationId: Long,
        latitude: Double,
        longitude: Double
    ) {
        val existing = dao.getStillLocationById(stillLocationId) ?: return
        if (!existing.placeId.isNullOrBlank() || !existing.placeName.isNullOrBlank() || !existing.placeCategory.isNullOrBlank()) {
            return
        }

        val client = ensurePlacesClient() ?: return

        try {
            val placeFields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES
            )
            val request = FindCurrentPlaceRequest.newInstance(placeFields)
            val response = withContext(Dispatchers.Main) {
                client.findCurrentPlace(request).await()
            }

            data class PlaceMatch(val likelihood: PlaceLikelihood, val distanceMeters: Double)

            val placeMatches = response.placeLikelihoods?.mapNotNull { likelihood ->
                val latLng = likelihood.place.latLng ?: return@mapNotNull null
                val distance = calculateDistanceMeters(
                    latitude,
                    longitude,
                    latLng.latitude,
                    latLng.longitude
                )
                PlaceMatch(likelihood, distance)
            } ?: emptyList()

            if (placeMatches.isEmpty()) {
                return
            }

            val bestMatch = placeMatches
                .filter { it.distanceMeters <= STILL_PLACE_MATCH_RADIUS_METERS }
                .maxByOrNull { it.likelihood.likelihood }
                ?: placeMatches.maxByOrNull { it.likelihood.likelihood }

            val bestPlace = bestMatch?.likelihood?.place ?: return
            val category = mapPlaceTypesToCategory(bestPlace.types)

            val enriched = existing.copy(
                placeId = bestPlace.id ?: existing.placeId,
                placeName = bestPlace.name ?: existing.placeName,
                placeCategory = category ?: existing.placeCategory,
                placeAddress = bestPlace.address ?: existing.placeAddress
            )

            if (enriched != existing) {
                dao.updateStillLocation(enriched)
                Log.d(TAG, "Enriched still location ${'$'}stillLocationId with place ${'$'}{enriched.placeName ?: enriched.placeCategory}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing location permission for Places API: ${'$'}{e.message}")
        } catch (e: ApiException) {
            Log.w(TAG, "Places API error: ${'$'}{e.statusCode} ${'$'}{e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enrich still location: ${'$'}{e.message}")
        }
    }

    private fun mapPlaceTypesToCategory(types: List<Place.Type>?): String? {
        if (types.isNullOrEmpty()) return null
        val typeSet = types.toSet()
        return when {
            typeSet.any { it == Place.Type.GYM } -> "Gym"
            typeSet.any {
                it == Place.Type.RESTAURANT ||
                        it == Place.Type.CAFE ||
                        it == Place.Type.BAR ||
                        it == Place.Type.FOOD ||
                        it == Place.Type.MEAL_TAKEAWAY ||
                        it == Place.Type.MEAL_DELIVERY
            } -> "Restaurant"
            typeSet.any { it == Place.Type.LODGING } -> "Hotel"
            typeSet.any {
                it == Place.Type.SHOPPING_MALL ||
                        it == Place.Type.STORE ||
                        it == Place.Type.DEPARTMENT_STORE ||
                        it == Place.Type.SUPERMARKET ||
                        it == Place.Type.GROCERY_OR_SUPERMARKET
            } -> "Shopping"
            typeSet.any {
                it == Place.Type.SCHOOL ||
                        it == Place.Type.UNIVERSITY ||
                        it == Place.Type.PRIMARY_SCHOOL
            } -> "Education"
            typeSet.any {
                it == Place.Type.PARK ||
                        it == Place.Type.TOURIST_ATTRACTION ||
                        it == Place.Type.CAMPGROUND
            } -> "Park"
            else -> types.firstOrNull()?.let { prettyPlaceType(it) }
        }
    }

    private fun prettyPlaceType(type: Place.Type): String {
        return type.name.lowercase().split('_').joinToString(" ") { word ->
            word.replaceFirstChar { ch -> ch.titlecase() }
        }
    }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result.firstOrNull()?.toDouble() ?: Double.MAX_VALUE
    }

    private fun normalizeActivityType(activityType: Int): Int {
        return if (activityType == DetectedActivity.UNKNOWN) {
            DetectedActivity.UNKNOWN
        } else {
            activityType
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
            DetectedActivity.UNKNOWN -> "Still"
            else -> "Unknown"
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { exception -> cont.resumeWithException(exception) }
}


