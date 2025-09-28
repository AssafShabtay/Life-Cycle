package com.example.myapplication

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var activityRecognitionClient: ActivityRecognitionClient? = null
    private var activityRecognitionPendingIntent: PendingIntent? = null
    private lateinit var database: ActivityDatabase
    private lateinit var dao: ActivityDao
    private var receiverRegistered = false

    companion object {
        const val TAG = "MainActivity"
        const val ACTION_ACTIVITY_UPDATE_UI = "com.example.myapplication.ACTIVITY_UPDATE_UI"
        const val EXTRA_ACTIVITY_NAME = "extra_activity_name"
    }

    private val activityUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                intent?.getStringExtra(EXTRA_ACTIVITY_NAME)?.let { activityName ->
                    currentActivityState = activityName
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in BroadcastReceiver: ${e.message}")
            }
        }
    }

    private val geofenceEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val slotId = intent?.getLongExtra("slot_id", -1) ?: return
                val eventType = intent.getStringExtra("event_type") ?: return

                Log.d(TAG, "Geofence event: $eventType for slot $slotId")

                lifecycleScope.launch {
                    updateCurrentLocationSlot(eventType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in GeofenceReceiver: ${e.message}")
            }
        }
    }

    private var currentActivityState by mutableStateOf("Unknown")
    private var isTrackingActive by mutableStateOf(false)
    private var canTrackState by mutableStateOf(false)
    private var currentLocationSlot by mutableStateOf<LocationSlot?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()

            // Initialize database
            database = ActivityDatabase.getDatabase(this)
            dao = database.activityDao()

            // Check permissions and navigate to permissions screen if needed
            if (!hasRequiredPermissions()) {
                // Go to permissions screen immediately
                startActivity(Intent(this, PermissionsActivity::class.java))
                // Set up basic UI while waiting
                setupUI(canTrack = false)
            } else {
                // Full setup with tracking capabilities
                setupUI(canTrack = true)
                setupBroadcastReceiver()
                setupActivityRecognition()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            // Still try to show basic UI
            setupUI(canTrack = false)
        }
    }

    private fun setupUI(canTrack: Boolean) {
        canTrackState = canTrack

        setContent {
            MyApplicationTheme {
                MainScreen(
                    canTrack = canTrackState,
                    currentActivityState = currentActivityState,
                    isTrackingActive = isTrackingActive,
                    onStartTracking = {
                        if (hasRequiredPermissions()) {
                            startLocationTracking()
                            isTrackingActive = true
                        } else {
                            Toast.makeText(
                                this,
                                "Please grant permissions first",
                                Toast.LENGTH_SHORT
                            ).show()
                            startActivity(Intent(this, PermissionsActivity::class.java))
                        }
                    },
                    onStopTracking = {
                        stopLocationTracking()
                        isTrackingActive = false
                        currentActivityState = "Unknown"
                    },
                    onViewDatabase = {
                        try {
                            startActivity(Intent(this, DatabaseViewerActivity::class.java))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening database viewer: ${e.message}")
                            Toast.makeText(this, "Error opening database viewer", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onManagePermissions = {
                        startActivity(Intent(this, PermissionsActivity::class.java))
                    },
                    onGenerateData = {
                        generateExampleData()
                    },
                    onManageLocationSlots = {
                        startActivity(Intent(this, LocationSlotsActivity::class.java))
                    },
                    currentLocationSlot = currentLocationSlot
                )
            }
        }
    }

    @Composable
    fun MainScreen(
        canTrack: Boolean,
        currentActivityState: String,
        isTrackingActive: Boolean,
        currentLocationSlot: LocationSlot?,
        onStartTracking: () -> Unit,
        onStopTracking: () -> Unit,
        onViewDatabase: () -> Unit,
        onManagePermissions: () -> Unit,
        onGenerateData: () -> Unit,
        onManageLocationSlots: () -> Unit
    ) {
        val scope = rememberCoroutineScope()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Activity & Location Tracker",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current Activity Display
                Text(
                    text = "Current Activity:",
                    fontSize = 16.sp
                )
                Text(
                    text = currentActivityState,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "Tracking: ${if (isTrackingActive) "Active" else "Inactive"}",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Current Location Slot Display
                currentLocationSlot?.let { slot ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(slot.color.toLong()).copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.dp, Color(slot.color.toLong()))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(slot.color.toLong()),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Currently at: ${slot.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Geofence active",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                if (!canTrack) {
                    Text(
                        text = "⚠️ Permissions needed for tracking",
                        fontSize = 14.sp,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Control Buttons
                Button(
                    onClick = onStartTracking,
                    enabled = !isTrackingActive && canTrack,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Start Tracking")
                }

                Button(
                    onClick = onStopTracking,
                    enabled = isTrackingActive,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Stop Tracking")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Database Viewer Button
                Button(
                    onClick = onViewDatabase,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("View Database")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Permissions Settings Button
                Button(
                    onClick = onManagePermissions,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("Manage Permissions")
                }

                // Location Slots Button
                Button(
                    onClick = onManageLocationSlots,
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Location Slots")
                }

                // Generate Example Data Button
                Button(
                    onClick = {
                        scope.launch {
                            onGenerateData()
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text("Generate Example Data")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info Text
                Text(
                    text = "Notes:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "• Still locations are saved automatically\n" +
                            "• Movement activities track start/end locations\n" +
                            "• Activities within 100m radius are marked as 'still'\n" +
                            "• Tap 'Generate Example Data' to test the pie chart",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Re-check permissions when returning from PermissionsActivity
        val hasPermissions = hasRequiredPermissions()

        if (hasPermissions) {
            // Update UI to show tracking is available
            setupUI(canTrack = true)

            // Set up tracking components if not already done
            if (!receiverRegistered) {
                setupBroadcastReceiver()
                setupActivityRecognition()
            }
        } else {
            // Update UI to show limited functionality
            setupUI(canTrack = false)
        }
    }

    private fun setupBroadcastReceiver() {
        try {
            if (!receiverRegistered) {
                // Register activity update receiver
                val filter = IntentFilter(ACTION_ACTIVITY_UPDATE_UI)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(activityUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(activityUpdateReceiver, filter)
                }

                // Register geofence event receiver
                val geofenceFilter = IntentFilter(GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(geofenceEventReceiver, geofenceFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(geofenceEventReceiver, geofenceFilter)
                }

                receiverRegistered = true
                Log.d(TAG, "BroadcastReceivers registered")

                // Check current location slots
                checkCurrentLocationSlot()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering BroadcastReceivers: ${e.message}")
        }
    }

    private fun generateExampleData() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val generator = ExampleDataGenerator(dao)
                    generator.generateExampleDay()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Example data generated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating example data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error generating data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            if (receiverRegistered) {
                unregisterReceiver(activityUpdateReceiver)
                unregisterReceiver(geofenceEventReceiver)
                receiverRegistered = false
            }

            // Clean up activity recognition with permission check
            if (hasRequiredPermissions()) {
                activityRecognitionPendingIntent?.let { pendingIntent ->
                    try {
                        activityRecognitionClient?.removeActivityTransitionUpdates(pendingIntent)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception removing activity updates: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }

    private fun checkCurrentLocationSlot() {
        lifecycleScope.launch {
            try {
                val geofenceDao = database.geofenceDao()

                // Check if we're currently in any active location slot
                val activeVisits = geofenceDao.getActiveVisits()
                if (activeVisits.isNotEmpty()) {
                    val slotId = activeVisits.first().slotId
                    val slot = geofenceDao.getLocationSlotById(slotId)
                    withContext(Dispatchers.Main) {
                        currentLocationSlot = slot
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        currentLocationSlot = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking current location slot: ${e.message}")
            }
        }
    }

    private suspend fun updateCurrentLocationSlot(eventType: String) {
        try {
            if (eventType == "ENTER") {
                // Refresh to show current location
                checkCurrentLocationSlot()
            } else if (eventType == "EXIT") {
                // Clear current location after short delay
                kotlinx.coroutines.delay(1000)
                withContext(Dispatchers.Main) {
                    currentLocationSlot = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location slot: ${e.message}")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return try {
            val hasLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

            val hasActivityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            hasLocation && hasActivityRecognition && hasNotifications
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            false
        }
    }

    private fun startLocationTracking() {
        try {
            val intent = Intent(this, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "Location tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking: ${e.message}")
            Toast.makeText(this, "Failed to start tracking", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationTracking() {
        try {
            val intent = Intent(this, LocationService::class.java)
            stopService(intent)

            // Remove activity transition updates with permission check
            if (hasRequiredPermissions()) {
                activityRecognitionPendingIntent?.let { pendingIntent ->
                    try {
                        activityRecognitionClient?.removeActivityTransitionUpdates(pendingIntent)
                            ?.addOnSuccessListener {
                                Log.d(TAG, "Activity transition updates removed successfully")
                            }
                            ?.addOnFailureListener { e ->
                                Log.e(TAG, "Failed to remove activity transition updates: ${e.message}")
                            }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception removing activity updates: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking: ${e.message}")
        }
    }

    private fun setupActivityRecognition() {
        try {
            // Only setup if permissions are granted
            if (!hasRequiredPermissions()) {
                Log.w(TAG, "Skipping activity recognition setup - permissions not granted")
                return
            }

            activityRecognitionClient = ActivityRecognition.getClient(this)
            val intent = Intent(this, ActivityTransitionReceiver::class.java)

            activityRecognitionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            activityRecognitionPendingIntent?.let { pendingIntent ->
                activityRecognitionClient?.requestActivityTransitionUpdates(
                    getActivityTransitionRequest(),
                    pendingIntent
                )?.addOnSuccessListener {
                    Log.d(TAG, "Activity transition updates registered successfully.")
                }?.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register activity transition updates: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in setupActivityRecognition: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupActivityRecognition: ${e.message}")
        }
    }

    private fun getActivityTransitionRequest(): ActivityTransitionRequest {
        val transitions = mutableListOf<ActivityTransition>()

        val activityTypes = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.STILL
        )

        activityTypes.forEach { activityType ->
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        return ActivityTransitionRequest(transitions)
    }
}