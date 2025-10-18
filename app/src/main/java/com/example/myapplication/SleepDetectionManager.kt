package com.example.myapplication

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class SleepDetectionManager(
    private val dao: ActivityDao,
    private val nightStartHour: Int = 21,
    private val nightEndHour: Int = 6,
    private val minSleepDurationMillis: Long = TimeUnit.HOURS.toMillis(2),
    private val maxSleepDurationMillis: Long = TimeUnit.HOURS.toMillis(12),
    private val source: String = "still-night"
) {
    private val mutex = Mutex()

    companion object {
        private const val TAG = "SleepDetection"
    }

    suspend fun processStillSession(
        startTime: Date,
        endTime: Date,
        latitude: Double?,
        longitude: Double?
    ) {
        if (endTime.time <= startTime.time) {
            Log.d(TAG, "Ignoring still session with non-positive duration")
            return
        }

        val duration = endTime.time - startTime.time
        if (duration < minSleepDurationMillis) {
            Log.d(TAG, "Still session too short for sleep detection (${duration / 60000}m)")
            return
        }

        if (duration > maxSleepDurationMillis) {
            Log.d(TAG, "Still session too long for sleep detection (${duration / 60000}m)")
            return
        }

        if (!overlapsNightHours(startTime, endTime)) {
            Log.d(TAG, "Still session did not overlap night hours; skipping sleep detection")
            return
        }

        mutex.withLock {
            val overlapping = dao.getOverlappingSleepSession(startTime, endTime)
            if (overlapping != null) {
                Log.d(
                    TAG,
                    "Existing sleep session overlaps ${overlapping.startTime} - ${overlapping.endTime}, skipping"
                )
                return
            }

            val session = SleepSession(
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                latitude = latitude,
                longitude = longitude,
                source = source
            )
            dao.insertSleepSession(session)
            Log.d(TAG, "Sleep session recorded from $startTime to $endTime (duration ${duration / 60000}m)")
        }
    }

    private fun overlapsNightHours(startTime: Date, endTime: Date): Boolean {
        val startCal = Calendar.getInstance().apply { time = startTime }
        val endCal = Calendar.getInstance().apply { time = endTime }

        if (isNightHour(startCal.get(Calendar.HOUR_OF_DAY))) {
            return true
        }

        if (isNightHour(endCal.get(Calendar.HOUR_OF_DAY))) {
            return true
        }

        val startToken = startCal.get(Calendar.YEAR) * 400 + startCal.get(Calendar.DAY_OF_YEAR)
        val endToken = endCal.get(Calendar.YEAR) * 400 + endCal.get(Calendar.DAY_OF_YEAR)
        if (endToken > startToken) {
            return true
        }

        val probe = (startCal.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }
        while (probe.time.before(endTime)) {
            if (isNightHour(probe.get(Calendar.HOUR_OF_DAY))) {
                return true
            }
            probe.add(Calendar.HOUR_OF_DAY, 1)
        }
        return false
    }

    private fun isNightHour(hour: Int): Boolean {
        return hour >= nightStartHour || hour < nightEndHour
    }
}
