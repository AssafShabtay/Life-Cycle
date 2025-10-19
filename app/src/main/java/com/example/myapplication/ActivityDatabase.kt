package com.example.myapplication

import androidx.room.*
import android.content.Context
import java.util.Date

// Converters for Room to handle Date objects
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// Entity for still locations
@Entity(tableName = "still_locations")
data class StillLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date,
    val duration: Long? = null, // Duration in milliseconds
    val wasSupposedToBeActivity: String? = null, // If this was detected during a movement activity
    val placeId: String? = null,
    val placeName: String? = null,
    val placeCategory: String? = null,
    val placeAddress: String? = null
)


// Entity for detected sleep sessions
@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date,
    val endTime: Date,
    val duration: Long,
    val latitude: Double?,
    val longitude: Double?,
    val source: String = "auto"
)
// Entity for movement activities
@Entity(tableName = "movement_activities")
data class MovementActivity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityType: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val startTime: Date,
    val endTime: Date,
    val distance: Float = 0f, // Distance in meters
    val actuallyMoved: Boolean = false // false if stayed within 100m radius
)

// Entity for tracking location points during movement
@Entity(tableName = "location_tracks")
data class LocationTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityId: Long?, // Foreign key to MovementActivity
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date,
    val speed: Float?,
    val accuracy: Float
)

// Entity for location slots with geofences
@Entity(tableName = "location_slots")
data class LocationSlot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String, // Icon identifier (e.g., "home", "work", "gym")
    val color: String, // Hex color code
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 100f, // Radius in meters (default 100m)
    val address: String? = null,
    val createdAt: Date = Date(),
    val isActive: Boolean = true,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = false
)

// Entity for tracking geofence events
@Entity(tableName = "geofence_events")
data class GeofenceEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slotId: Long,
    val eventType: String, // "ENTER" or "EXIT"
    val timestamp: Date = Date(),
    val latitude: Double,
    val longitude: Double
)

// Entity for visit statistics
@Entity(tableName = "location_visits")
data class LocationVisit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slotId: Long,
    val entryTime: Date,
    val exitTime: Date? = null,
    val duration: Long? = null, // Duration in milliseconds
    val entryLatitude: Double,
    val entryLongitude: Double,
    val exitLatitude: Double? = null,
    val exitLongitude: Double? = null
)

// DAO for activity database operations
@Dao
interface ActivityDao {
    @Insert
    suspend fun insertStillLocation(stillLocation: StillLocation): Long

    @Insert
    suspend fun insertSleepSession(session: SleepSession): Long

    @Insert
    suspend fun insertMovementActivity(activity: MovementActivity): Long

    @Insert
    suspend fun insertLocationTrack(track: LocationTrack)

    @Insert
    suspend fun insertLocationTracks(tracks: List<LocationTrack>)

    @Update
    suspend fun updateMovementActivity(activity: MovementActivity)

    @Update
    suspend fun updateStillLocation(stillLocation: StillLocation)

    @Query("SELECT * FROM still_locations WHERE id = :id")
    suspend fun getStillLocationById(id: Long): StillLocation?

    @Query("SELECT * FROM movement_activities WHERE id = :id")
    suspend fun getMovementActivityById(id: Long): MovementActivity?

    @Query("DELETE FROM movement_activities WHERE id = :id")
    suspend fun deleteMovementActivity(id: Long)

    @Query("SELECT * FROM still_locations ORDER BY timestamp DESC")
    suspend fun getAllStillLocations(): List<StillLocation>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    suspend fun getAllSleepSessions(): List<SleepSession>

    @Query("SELECT * FROM sleep_sessions WHERE startTime < :endTime AND endTime > :startTime LIMIT 1")
    suspend fun getOverlappingSleepSession(startTime: Date, endTime: Date): SleepSession?

    @Query("SELECT * FROM movement_activities ORDER BY startTime DESC")
    suspend fun getAllMovementActivities(): List<MovementActivity>

    @Query("SELECT * FROM location_tracks WHERE activityId = :activityId ORDER BY timestamp")
    suspend fun getLocationTracksForActivity(activityId: Long): List<LocationTrack>

    @Query("SELECT * FROM still_locations WHERE timestamp <= :endTime AND (duration IS NULL OR (timestamp + duration) >= :startTime)")
    suspend fun getStillLocationsBetween(startTime: Date, endTime: Date): List<StillLocation>

    @Query("SELECT * FROM movement_activities WHERE startTime < :endTime AND endTime > :startTime")
    suspend fun getMovementActivitiesBetween(startTime: Date, endTime: Date): List<MovementActivity>

    @Query("DELETE FROM still_locations WHERE timestamp < :beforeDate")
    suspend fun deleteOldStillLocations(beforeDate: Date): Int

    @Query("DELETE FROM movement_activities WHERE endTime < :beforeDate")
    suspend fun deleteOldMovementActivities(beforeDate: Date): Int
}

// DAO for geofence operations
@Dao
interface GeofenceDao {
    // Location Slots
    @Insert
    suspend fun insertLocationSlot(slot: LocationSlot): Long

    @Update
    suspend fun updateLocationSlot(slot: LocationSlot)

    @Delete
    suspend fun deleteLocationSlot(slot: LocationSlot)

    @Query("SELECT * FROM location_slots WHERE id = :id")
    suspend fun getLocationSlotById(id: Long): LocationSlot?

    @Query("SELECT * FROM location_slots WHERE isActive = 1 ORDER BY name")
    suspend fun getActiveLocationSlots(): List<LocationSlot>

    @Query("SELECT * FROM location_slots ORDER BY name")
    suspend fun getAllLocationSlots(): List<LocationSlot>

    @Query("SELECT * FROM location_slots WHERE " +
            "((latitude - :lat) * (latitude - :lat) + (longitude - :lon) * (longitude - :lon)) " +
            "<= (radius * 0.00001 * radius * 0.00001) AND isActive = 1")
    suspend fun getNearbySlots(lat: Double, lon: Double): List<LocationSlot>

    // Geofence Events
    @Insert
    suspend fun insertGeofenceEvent(event: GeofenceEvent): Long

    @Query("SELECT * FROM geofence_events WHERE slotId = :slotId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsForSlot(slotId: Long, limit: Int = 10): List<GeofenceEvent>

    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 20): List<GeofenceEvent>

    // Location Visits
    @Insert
    suspend fun insertLocationVisit(visit: LocationVisit): Long

    @Update
    suspend fun updateLocationVisit(visit: LocationVisit)

    @Query("SELECT * FROM location_visits WHERE slotId = :slotId AND exitTime IS NULL ORDER BY entryTime DESC LIMIT 1")
    suspend fun getCurrentVisitForSlot(slotId: Long): LocationVisit?

    @Query("SELECT * FROM location_visits WHERE exitTime IS NULL")
    suspend fun getActiveVisits(): List<LocationVisit>

    @Query("SELECT * FROM location_visits WHERE slotId = :slotId ORDER BY entryTime DESC LIMIT :limit")
    suspend fun getVisitsForSlot(slotId: Long, limit: Int = 50): List<LocationVisit>

    @Query("SELECT COUNT(*) FROM location_visits WHERE slotId = :slotId")
    suspend fun getVisitCountForSlot(slotId: Long): Int

    @Query("SELECT SUM(duration) FROM location_visits WHERE slotId = :slotId AND duration IS NOT NULL")
    suspend fun getTotalTimeAtSlot(slotId: Long): Long?

    // Cleanup
    @Query("DELETE FROM geofence_events WHERE timestamp < :beforeDate")
    suspend fun deleteOldEvents(beforeDate: Date): Int

    @Query("DELETE FROM location_visits WHERE entryTime < :beforeDate")
    suspend fun deleteOldVisits(beforeDate: Date): Int
}

// Room Database
@Database(
    entities = [
        StillLocation::class,
        SleepSession::class,
        MovementActivity::class,
        LocationTrack::class,
        LocationSlot::class,
        GeofenceEvent::class,
        LocationVisit::class
    ],
    version = 4, // Incremented for new tables
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ActivityDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun geofenceDao(): GeofenceDao

    companion object {
        @Volatile
        private var INSTANCE: ActivityDatabase? = null

        fun getDatabase(context: Context): ActivityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ActivityDatabase::class.java,
                    "activity_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
