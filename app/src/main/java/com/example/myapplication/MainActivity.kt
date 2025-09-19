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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var activityRecognitionPendingIntent: PendingIntent

    companion object {
        const val TAG = "MainActivity"
        const val ACTION_ACTIVITY_UPDATE_UI = "com.example.myapplication.ACTIVITY_UPDATE_UI"
        const val EXTRA_ACTIVITY_NAME = "extra_activity_name"
    }

    private val activityUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(EXTRA_ACTIVITY_NAME)?.let { activityName ->
                currentActivityState = activityName
            }
        }
    }

    private var currentActivityState by mutableStateOf("Unknown")
    private var isTrackingActive by mutableStateOf(false)

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we have all required permissions, if not go back to permissions screen
        if (!hasRequiredPermissions()) {
            startActivity(Intent(this, PermissionsActivity::class.java))
            finish()
            return
        }

        // Register the BroadcastReceiver
        val filter = IntentFilter(ACTION_ACTIVITY_UPDATE_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(activityUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(activityUpdateReceiver, filter)
        }

        // Setup activity recognition since permissions are granted
        setupActivityRecognition()

        setContent {
            MyApplicationTheme {
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

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // Control Buttons
                        Button(
                            onClick = {
                                startLocationTracking()
                                isTrackingActive = true
                            },
                            enabled = !isTrackingActive,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("Start Tracking")
                        }

                        Button(
                            onClick = {
                                stopLocationTracking()
                                isTrackingActive = false
                                currentActivityState = "Unknown"
                            },
                            enabled = isTrackingActive,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("Stop Tracking")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Database Viewer Button
                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, DatabaseViewerActivity::class.java))
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("View Database")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Permissions Settings Button
                        Button(
                            onClick = {
                                startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("Manage Permissions")
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
                                    "• Activities within 100m radius are marked as 'still'",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(activityUpdateReceiver)
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val hasActivityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return hasLocation && hasActivityRecognition && hasNotifications
    }

    private fun startLocationTracking() {
        // Start location service
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLocationTracking() {
        // Stop location service
        val intent = Intent(this, LocationService::class.java)
        stopService(intent)

        // Remove activity transition updates
        if (::activityRecognitionPendingIntent.isInitialized && ::activityRecognitionClient.isInitialized) {
            try {
                activityRecognitionClient.removeActivityTransitionUpdates(activityRecognitionPendingIntent)
                    .addOnSuccessListener { Log.d(TAG, "Activity transition updates removed successfully") }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove activity transition updates: ${e.message}")
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception removing activity updates: ${e.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun setupActivityRecognition() {
        try {
            activityRecognitionClient = ActivityRecognition.getClient(this)
            val intent = Intent(this, ActivityTransitionReceiver::class.java)

            // Using FLAG_MUTABLE for activity transitions
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

            // Request the activity transition updates
            activityRecognitionClient.requestActivityTransitionUpdates(
                getActivityTransitionRequest(),
                activityRecognitionPendingIntent
            )
                .addOnSuccessListener {
                    Log.d(TAG, "Activity transition updates registered successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register activity transition updates: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in setupActivityRecognition: ${e.message}")
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

        // For each activity type, add both ENTER and EXIT transitions
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}