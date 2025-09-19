package com.example.myapplication

import androidx.room.*
import android.content.Context
import androidx.room.Entity
import androidx.room.RoomDatabase
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
    val wasSupposedToBeActivity: String? = null // If this was detected during a movement activity
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

// DAO for database operations
@Dao
interface ActivityDao {
    @Insert
    suspend fun insertStillLocation(stillLocation: StillLocation): Long

    @Insert
    suspend fun insertMovementActivity(activity: MovementActivity): Long

    @Insert
    suspend fun insertLocationTrack(track: LocationTrack)

    @Insert
    suspend fun insertLocationTracks(tracks: List<LocationTrack>)

    @Update
    suspend fun updateMovementActivity(activity: MovementActivity)

    @Query("SELECT * FROM still_locations ORDER BY timestamp DESC")
    suspend fun getAllStillLocations(): List<StillLocation>

    @Query("SELECT * FROM movement_activities ORDER BY startTime DESC")
    suspend fun getAllMovementActivities(): List<MovementActivity>

    @Query("SELECT * FROM location_tracks WHERE activityId = :activityId ORDER BY timestamp")
    suspend fun getLocationTracksForActivity(activityId: Long): List<LocationTrack>

    @Query("SELECT * FROM still_locations WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getStillLocationsBetween(startTime: Date, endTime: Date): List<StillLocation>

    @Query("SELECT * FROM movement_activities WHERE startTime BETWEEN :startTime AND :endTime")
    suspend fun getMovementActivitiesBetween(startTime: Date, endTime: Date): List<MovementActivity>

    @Query("DELETE FROM still_locations WHERE timestamp < :beforeDate")
    suspend fun deleteOldStillLocations(beforeDate: Date): Int

    @Query("DELETE FROM movement_activities WHERE endTime < :beforeDate")
    suspend fun deleteOldMovementActivities(beforeDate: Date): Int
}

// Room Database
@Database(
    entities = [StillLocation::class, MovementActivity::class, LocationTrack::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ActivityDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

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