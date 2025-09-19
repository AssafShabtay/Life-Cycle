package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme

class PermissionsActivity : ComponentActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val BACKGROUND_PERMISSION_REQUEST_CODE = 101
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 102
        const val TAG = "PermissionsActivity"
    }

    private val requiredPermissions = mutableMapOf<String, Boolean>()
    private var permissionStates by mutableStateOf(mapOf<String, Boolean>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize permission states
        updatePermissionStates()

        setContent {
            MyApplicationTheme {
                PermissionsScreen(
                    permissionStates = permissionStates,
                    onRequestPermissions = { requestAllPermissions() },
                    onOpenSettings = { openAppSettings() },
                    onContinue = { navigateToMainActivity() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        val states = mutableMapOf<String, Boolean>()

        // Foreground Location
        states["location"] = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // Activity Recognition
        states["activity"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }

        // Notifications
        states["notifications"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }

        // Background Location (optional but recommended)
        states["background_location"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }

        permissionStates = states
    }

    private fun areRequiredPermissionsGranted(): Boolean {
        return permissionStates["location"] == true &&
                permissionStates["activity"] == true &&
                permissionStates["notifications"] == true
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Add location permissions if not granted
        if (permissionStates["location"] != true) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Add activity recognition if not granted and required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && permissionStates["activity"] != true) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Add notifications if not granted and required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissionStates["notifications"] != true) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // All required permissions granted, request background location if needed
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            permissionStates["location"] == true &&
            permissionStates["background_location"] != true) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "Permission result: requestCode=$requestCode, permissions=${permissions.contentToString()}, results=${grantResults.contentToString()}")

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                updatePermissionStates()

                // If required permissions are granted, try to get background location
                if (areRequiredPermissionsGranted()) {
                    requestBackgroundLocationIfNeeded()
                }
            }

            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                updatePermissionStates()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun navigateToMainActivity() {
        if (areRequiredPermissionsGranted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    permissionStates: Map<String, Boolean>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    val allRequiredGranted = permissionStates["location"] == true &&
            permissionStates["activity"] == true &&
            permissionStates["notifications"] == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon and Title
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Activity & Location Tracker",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Permissions Required",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission Cards
            PermissionCard(
                icon = Icons.Default.LocationOn,
                title = "Location Access",
                description = "Required to track your location and detect when you're moving or staying still",
                isGranted = permissionStates["location"] == true,
                isRequired = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                icon = Icons.Default.Person,
                title = "Activity Recognition",
                description = "Detects when you're walking, driving, or staying still to improve location tracking",
                isGranted = permissionStates["activity"] == true,
                isRequired = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Shows persistent notification while tracking and alerts about location changes",
                isGranted = permissionStates["notifications"] == true,
                isRequired = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                icon = Icons.Default.Lock,
                title = "Background Location",
                description = "Allows tracking even when the app is closed (recommended for best experience)",
                isGranted = permissionStates["background_location"] == true,
                isRequired = false
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Important Instructions",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Tap 'Grant Permissions' below\n" +
                                "• Accept ALL required permissions when prompted\n" +
                                "• For background location, choose 'Allow all the time'\n" +
                                "• If permissions are denied, use 'Open Settings' to grant them manually",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (!allRequiredGranted) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant Permissions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Settings",
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue to App",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                Color(0xFFE8F5E8)
            } else if (isRequired) {
                Color(0xFFFFF3E0)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isRequired && !isGranted) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isRequired) {
                        Text(
                            text = "REQUIRED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Color(0xFFFF5722),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else {
                        Text(
                            text = "OPTIONAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Color(0xFF9E9E9E),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF5722),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}