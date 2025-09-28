package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.random.Random

class ExampleDataGenerator(private val dao: ActivityDao) {

    suspend fun generateExampleDay(targetDate: Date = Date()) = withContext(Dispatchers.IO) {
        // Clear existing data for the target date (optional)
        // clearDataForDate(targetDate)

        val calendar = Calendar.getInstance()
        calendar.time = targetDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        var currentTime = calendar.timeInMillis

        // Define a typical day's activities
        val dayActivities = listOf(
            ActivityPeriod("Still", 420, true),      // 00:00-07:00 - Sleep (7 hours)
            ActivityPeriod("Still", 30, true),       // 07:00-07:30 - Morning routine
            ActivityPeriod("Walking", 15, true),     // 07:30-07:45 - Morning walk
            ActivityPeriod("Still", 45, true),       // 07:45-08:30 - Breakfast
            ActivityPeriod("Driving", 30, true),     // 08:30-09:00 - Commute to work
            ActivityPeriod("Still", 120, true),      // 09:00-11:00 - Work (at desk)
            ActivityPeriod("Walking", 10, true),     // 11:00-11:10 - Coffee break walk
            ActivityPeriod("Still", 110, true),      // 11:10-13:00 - Work continued
            ActivityPeriod("Walking", 15, true),     // 13:00-13:15 - Walk to lunch
            ActivityPeriod("Still", 45, true),       // 13:15-14:00 - Lunch
            ActivityPeriod("Walking", 15, true),     // 14:00-14:15 - Walk back
            ActivityPeriod("Still", 165, true),      // 14:15-17:00 - Afternoon work
            ActivityPeriod("Walking", 5, false),     // 17:00-17:05 - False positive (didn't move)
            ActivityPeriod("Still", 25, true),       // 17:05-17:30 - Wrapping up
            ActivityPeriod("Driving", 35, true),     // 17:30-18:05 - Commute home
            ActivityPeriod("Still", 25, true),       // 18:05-18:30 - Rest at home
            ActivityPeriod("Running", 30, true),     // 18:30-19:00 - Evening run
            ActivityPeriod("Still", 30, true),       // 19:00-19:30 - Shower and rest
            ActivityPeriod("Walking", 10, false),    // 19:30-19:40 - False activity (stayed home)
            ActivityPeriod("Still", 50, true),       // 19:40-20:30 - Dinner
            ActivityPeriod("Still", 90, true),       // 20:30-22:00 - TV/Relaxation
            ActivityPeriod("Walking", 10, true),     // 22:00-22:10 - Evening walk with dog
            ActivityPeriod("Still", 110, true),      // 22:10-24:00 - Bedtime routine and sleep
        )

        // Generate data for each activity period
        for (activity in dayActivities) {
            val startTime = Date(currentTime)
            val endTime = Date(currentTime + activity.durationMinutes * 60 * 1000)

            when (activity.type) {
                "Still" -> {
                    // Generate still location
                    val location = generateRandomLocation()
                    dao.insertStillLocation(
                        StillLocation(
                            latitude = location.first,
                            longitude = location.second,
                            timestamp = startTime,
                            duration = activity.durationMinutes * 60 * 1000L,
                            wasSupposedToBeActivity = null
                        )
                    )
                }
                else -> {
                    // Generate movement activity
                    val startLocation = generateRandomLocation()
                    val endLocation = if (activity.actuallyMoved) {
                        generateLocationAtDistance(startLocation,
                            when(activity.type) {
                                "Walking" -> Random.nextDouble(200.0, 1000.0)
                                "Running" -> Random.nextDouble(1000.0, 5000.0)
                                "Driving" -> Random.nextDouble(5000.0, 20000.0)
                                "Cycling" -> Random.nextDouble(2000.0, 10000.0)
                                else -> Random.nextDouble(100.0, 500.0)
                            }
                        )
                    } else {
                        // Stayed within 100m - generate nearby location
                        generateLocationAtDistance(startLocation, Random.nextDouble(10.0, 80.0))
                    }

                    val distance = calculateDistance(startLocation, endLocation)

                    if (!activity.actuallyMoved) {
                        // Save as still location instead
                        dao.insertStillLocation(
                            StillLocation(
                                latitude = startLocation.first,
                                longitude = startLocation.second,
                                timestamp = startTime,
                                duration = activity.durationMinutes * 60 * 1000L,
                                wasSupposedToBeActivity = activity.type
                            )
                        )
                    } else {
                        // Save as movement activity
                        val activityId = dao.insertMovementActivity(
                            MovementActivity(
                                activityType = activity.type,
                                startLatitude = startLocation.first,
                                startLongitude = startLocation.second,
                                endLatitude = endLocation.first,
                                endLongitude = endLocation.second,
                                startTime = startTime,
                                endTime = endTime,
                                distance = distance,
                                actuallyMoved = activity.actuallyMoved
                            )
                        )

                        // Generate some location tracks for this activity
                        if (activity.actuallyMoved) {
                            generateLocationTracks(activityId, startLocation, endLocation, startTime, endTime)
                        }
                    }
                }
            }

            currentTime += activity.durationMinutes * 60 * 1000
        }
    }

    suspend fun generateMultipleDays(numberOfDays: Int = 7) = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()

        for (i in 0 until numberOfDays) {
            calendar.add(Calendar.DAY_OF_MONTH, -i)
            generateExampleDay(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, i) // Reset
        }
    }

    private suspend fun generateLocationTracks(
        activityId: Long,
        startLocation: Pair<Double, Double>,
        endLocation: Pair<Double, Double>,
        startTime: Date,
        endTime: Date
    ) {
        val tracks = mutableListOf<LocationTrack>()
        val numberOfPoints = 5 + Random.nextInt(10) // 5-15 track points
        val timeDiff = endTime.time - startTime.time

        for (i in 0..numberOfPoints) {
            val progress = i.toFloat() / numberOfPoints
            val lat = startLocation.first + (endLocation.first - startLocation.first) * progress
            val lon = startLocation.second + (endLocation.second - startLocation.second) * progress
            val timestamp = Date(startTime.time + (timeDiff * progress).toLong())

            tracks.add(
                LocationTrack(
                    activityId = activityId,
                    latitude = lat + Random.nextDouble(-0.0001, 0.0001), // Add slight variation
                    longitude = lon + Random.nextDouble(-0.0001, 0.0001),
                    timestamp = timestamp,
                    speed = when {
                        i == 0 || i == numberOfPoints -> 0f
                        else -> Random.nextFloat() * 10f
                    },
                    accuracy = Random.nextFloat() * 10f + 5f
                )
            )
        }

        dao.insertLocationTracks(tracks)
    }

    private fun generateRandomLocation(): Pair<Double, Double> {
        // Generate random location around a central point (example: San Francisco area)
        val centerLat = 37.7749
        val centerLon = -122.4194

        return Pair(
            centerLat + Random.nextDouble(-0.1, 0.1),
            centerLon + Random.nextDouble(-0.1, 0.1)
        )
    }

    private fun generateLocationAtDistance(
        origin: Pair<Double, Double>,
        distanceMeters: Double
    ): Pair<Double, Double> {
        // Approximate calculation for small distances
        val earthRadius = 6371000.0 // meters
        val lat1Rad = Math.toRadians(origin.first)

        // Random bearing
        val bearing = Random.nextDouble(0.0, 360.0)
        val bearingRad = Math.toRadians(bearing)

        // Calculate new position
        val lat2Rad = Math.asin(
            Math.sin(lat1Rad) * Math.cos(distanceMeters / earthRadius) +
                    Math.cos(lat1Rad) * Math.sin(distanceMeters / earthRadius) * Math.cos(bearingRad)
        )

        val lon2Rad = Math.toRadians(origin.second) + Math.atan2(
            Math.sin(bearingRad) * Math.sin(distanceMeters / earthRadius) * Math.cos(lat1Rad),
            Math.cos(distanceMeters / earthRadius) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
        )

        return Pair(
            Math.toDegrees(lat2Rad),
            Math.toDegrees(lon2Rad)
        )
    }

    private fun calculateDistance(
        loc1: Pair<Double, Double>,
        loc2: Pair<Double, Double>
    ): Float {
        val results = FloatArray(1)
        val earthRadius = 6371000f // meters

        val lat1Rad = Math.toRadians(loc1.first)
        val lat2Rad = Math.toRadians(loc2.first)
        val deltaLat = Math.toRadians(loc2.first - loc1.first)
        val deltaLon = Math.toRadians(loc2.second - loc1.second)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return (earthRadius * c).toFloat()
    }

    private data class ActivityPeriod(
        val type: String,
        val durationMinutes: Int,
        val actuallyMoved: Boolean
    )

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        // Get all data and delete
        val allStillLocations = dao.getAllStillLocations()
        val allMovementActivities = dao.getAllMovementActivities()

        // Delete old data (30 days ago)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        dao.deleteOldStillLocations(calendar.time)
        dao.deleteOldMovementActivities(calendar.time)
    }
}

// Extension function to add to MainActivity or DatabaseViewerActivity
suspend fun populateExampleData(dao: ActivityDao) {
    val generator = ExampleDataGenerator(dao)

    // Generate data for today
    generator.generateExampleDay()

    // Or generate data for multiple days
    // generator.generateMultipleDays(7)
}