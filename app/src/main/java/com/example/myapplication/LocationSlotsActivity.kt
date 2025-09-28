package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

class LocationSlotsActivity : ComponentActivity() {
    private lateinit var database: ActivityDatabase
    private lateinit var geofenceDao: GeofenceDao
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    private var locationSlots by mutableStateOf<List<LocationSlot>>(emptyList())
    private var currentLocation by mutableStateOf<Location?>(null)
    private var showCreateDialog by mutableStateOf(false)
    private var editingSlot by mutableStateOf<LocationSlot?>(null)

    // Available icons for slots
    private val availableIcons = listOf(
        "home" to Icons.Default.Home,
        "work" to Icons.Default.BusinessCenter,
        "gym" to Icons.Default.FitnessCenter,
        "school" to Icons.Default.School,
        "shopping" to Icons.Default.ShoppingCart,
        "restaurant" to Icons.Default.Restaurant,
        "park" to Icons.Default.Park,
        "medical" to Icons.Default.LocalHospital,
        "friend" to Icons.Default.People,
        "favorite" to Icons.Default.Favorite,
        "star" to Icons.Default.Star,
        "coffee" to Icons.Default.Coffee
    )

    // Available colors for slots
    private val availableColors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFF795548), // Brown
        Color(0xFF607D8B), // Blue Grey
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF009688), // Teal
        Color(0xFFFFC107), // Amber
        Color(0xFF3F51B5)  // Indigo
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ActivityDatabase.getDatabase(this)
        geofenceDao = database.geofenceDao()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        loadLocationSlots()
        getCurrentLocation()

        setContent {
            MyApplicationTheme {
                LocationSlotsScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LocationSlotsScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Location Slots") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (currentLocation != null) {
                            showCreateDialog = true
                        } else {
                            Toast.makeText(
                                this@LocationSlotsActivity,
                                "Getting current location...",
                                Toast.LENGTH_SHORT
                            ).show()
                            getCurrentLocation()
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Location Slot")
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (locationSlots.isEmpty()) {
                    EmptyState()
                } else {
                    LocationSlotsList(
                        slots = locationSlots,
                        currentLocation = currentLocation,
                        onEdit = { slot ->
                            editingSlot = slot
                            showCreateDialog = true
                        },
                        onDelete = { slot ->
                            deleteLocationSlot(slot)
                        },
                        onToggleActive = { slot ->
                            toggleSlotActive(slot)
                        }
                    )
                }

                // Create/Edit Dialog
                if (showCreateDialog) {
                    CreateEditSlotDialog(
                        slot = editingSlot,
                        currentLocation = currentLocation,
                        onDismiss = {
                            showCreateDialog = false
                            editingSlot = null
                        },
                        onSave = { name, icon, color, radius, notifyOnEnter, notifyOnExit ->
                            saveLocationSlot(
                                name, icon, color, radius,
                                notifyOnEnter, notifyOnExit,
                                editingSlot
                            )
                            showCreateDialog = false
                            editingSlot = null
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun EmptyState() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Location Slots Yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to create your first location slot",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LocationSlotsList(
        slots: List<LocationSlot>,
        currentLocation: Location?,
        onEdit: (LocationSlot) -> Unit,
        onDelete: (LocationSlot) -> Unit,
        onToggleActive: (LocationSlot) -> Unit
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show nearby slots first if we have location
            currentLocation?.let { loc ->
                val nearbySlots = slots.filter { slot ->
                    val distance = FloatArray(1)
                    Location.distanceBetween(
                        loc.latitude, loc.longitude,
                        slot.latitude, slot.longitude,
                        distance
                    )
                    distance[0] <= slot.radius
                }

                if (nearbySlots.isNotEmpty()) {
                    item {
                        Text(
                            text = "Currently At",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(nearbySlots) { slot ->
                        LocationSlotCard(
                            slot = slot,
                            distance = 0f,
                            isNearby = true,
                            onEdit = { onEdit(slot) },
                            onDelete = { onDelete(slot) },
                            onToggleActive = { onToggleActive(slot) }
                        )
                    }

                    item {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "All Locations",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            items(slots) { slot ->
                val distance = currentLocation?.let { loc ->
                    val result = FloatArray(1)
                    Location.distanceBetween(
                        loc.latitude, loc.longitude,
                        slot.latitude, slot.longitude,
                        result
                    )
                    result[0]
                }

                LocationSlotCard(
                    slot = slot,
                    distance = distance,
                    isNearby = distance?.let { it <= slot.radius } ?: false,
                    onEdit = { onEdit(slot) },
                    onDelete = { onDelete(slot) },
                    onToggleActive = { onToggleActive(slot) }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LocationSlotCard(
        slot: LocationSlot,
        distance: Float?,
        isNearby: Boolean,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onToggleActive: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isNearby) {
                    Color(slot.color.toLong()).copy(alpha = 0.15f)
                } else if (!slot.isActive) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
            border = if (isNearby) {
                BorderStroke(2.dp, Color(slot.color.toLong()))
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(slot.color.toLong())),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForSlot(slot.icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = slot.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isNearby) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = Color(slot.color.toLong())
                            ) {
                                Text("HERE", fontSize = 10.sp)
                            }
                        }
                    }

                    Text(
                        text = slot.address ?: "${slot.latitude.format(5)}, ${slot.longitude.format(5)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (slot.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (slot.isActive) "Active" else "Inactive",
                            fontSize = 11.sp
                        )

                        distance?.let { dist ->
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = formatDistance(dist),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Radius: ${slot.radius.roundToInt()}m",
                            fontSize = 11.sp
                        )
                    }
                }

                // Actions
                Row {
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            if (slot.isActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Active"
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CreateEditSlotDialog(
        slot: LocationSlot?,
        currentLocation: Location?,
        onDismiss: () -> Unit,
        onSave: (String, String, Int, Float, Boolean, Boolean) -> Unit
    ) {
        var name by remember { mutableStateOf(slot?.name ?: "") }
        var selectedIcon by remember { mutableStateOf(slot?.icon ?: "home") }
        var selectedColor by remember { mutableStateOf(
            slot?.color?.toLong()?.let { Color(it) } ?: availableColors[0]
        ) }
        var radius by remember { mutableStateOf(slot?.radius ?: 100f) }
        var notifyOnEnter by remember { mutableStateOf(slot?.notifyOnEnter ?: true) }
        var notifyOnExit by remember { mutableStateOf(slot?.notifyOnExit ?: false) }

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (slot == null) "Create Location Slot" else "Edit Location Slot",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name input
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Location Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Icon selection
                    Text("Select Icon", fontWeight = FontWeight.Medium)
                    LazyRow(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableIcons.size) { index ->
                            val (iconId, icon) = availableIcons[index]
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedIcon == iconId) selectedColor
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedIcon = iconId },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = iconId,
                                    tint = if (selectedIcon == iconId) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Color selection
                    Text("Select Color", fontWeight = FontWeight.Medium)
                    LazyRow(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableColors.size) { index ->
                            val color = availableColors[index]
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selectedColor == color) BorderStroke(3.dp, Color.Black)
                                        else BorderStroke(1.dp, Color.Gray),
                                        CircleShape
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Radius slider
                    Text("Geofence Radius: ${radius.roundToInt()}m", fontWeight = FontWeight.Medium)
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 50f..500f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = notifyOnEnter,
                            onCheckedChange = { notifyOnEnter = it }
                        )
                        Text("Notify when entering", modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = notifyOnExit,
                            onCheckedChange = { notifyOnExit = it }
                        )
                        Text("Notify when leaving", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(
                                        name,
                                        selectedIcon,
                                        selectedColor.toArgb(),
                                        radius,
                                        notifyOnEnter,
                                        notifyOnExit
                                    )
                                }
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text(if (slot == null) "Create" else "Save")
                        }
                    }
                }
            }
        }
    }

    private fun getIconForSlot(iconId: String): ImageVector {
        return availableIcons.find { it.first == iconId }?.second ?: Icons.Default.LocationOn
    }

    private fun formatDistance(meters: Float): String {
        return when {
            meters < 1000 -> "${meters.roundToInt()}m away"
            else -> "${String.format("%.1f", meters / 1000)}km away"
        }
    }

    private fun Double.format(decimals: Int): String {
        return String.format("%.${decimals}f", this)
    }

    private fun loadLocationSlots() {
        lifecycleScope.launch {
            locationSlots = geofenceDao.getAllLocationSlots()
        }
    }

    private fun saveLocationSlot(
        name: String,
        icon: String,
        color: Int,
        radius: Float,
        notifyOnEnter: Boolean,
        notifyOnExit: Boolean,
        existingSlot: LocationSlot?
    ) {
        lifecycleScope.launch {
            val location = currentLocation ?: return@launch

            // Get address using Geocoder
            val address = try {
                val geocoder = Geocoder(this@LocationSlotsActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0)
            } catch (e: Exception) {
                null
            }

            val slot = if (existingSlot != null) {
                existingSlot.copy(
                    name = name,
                    icon = icon,
                    color = color.toString(),
                    radius = radius,
                    notifyOnEnter = notifyOnEnter,
                    notifyOnExit = notifyOnExit
                )
            } else {
                LocationSlot(
                    name = name,
                    icon = icon,
                    color = color.toString(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radius = radius,
                    address = address,
                    notifyOnEnter = notifyOnEnter,
                    notifyOnExit = notifyOnExit
                )
            }

            if (existingSlot != null) {
                geofenceDao.updateLocationSlot(slot)
                removeGeofence(slot.id.toString())
            } else {
                val id = geofenceDao.insertLocationSlot(slot)
                slot.copy(id = id)
            }

            if (slot.isActive) {
                addGeofence(slot)
            }

            loadLocationSlots()
            Toast.makeText(
                this@LocationSlotsActivity,
                if (existingSlot != null) "Location updated" else "Location saved",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteLocationSlot(slot: LocationSlot) {
        lifecycleScope.launch {
            removeGeofence(slot.id.toString())
            geofenceDao.deleteLocationSlot(slot)
            loadLocationSlots()
            Toast.makeText(
                this@LocationSlotsActivity,
                "Location deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun toggleSlotActive(slot: LocationSlot) {
        lifecycleScope.launch {
            val updated = slot.copy(isActive = !slot.isActive)
            geofenceDao.updateLocationSlot(updated)

            if (updated.isActive) {
                addGeofence(updated)
            } else {
                removeGeofence(updated.id.toString())
            }

            loadLocationSlots()
        }
    }

    private fun addGeofence(slot: LocationSlot) {
        // Implementation will be in GeofenceManager
        GeofenceManager.addGeofence(this, slot)
    }

    private fun removeGeofence(id: String) {
        GeofenceManager.removeGeofence(this, id)
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
            }
        }
    }
}