package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DatabaseViewerActivity : ComponentActivity() {
    private lateinit var database: ActivityDatabase
    private lateinit var dao: ActivityDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ActivityDatabase.getDatabase(this)
        dao = database.activityDao()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DatabaseViewer(dao)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseViewer(dao: ActivityDao) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Still Locations", "Movement Activities")

    var stillLocations by remember { mutableStateOf<List<StillLocation>>(emptyList()) }
    var movementActivities by remember { mutableStateOf<List<MovementActivity>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    // Load data
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                stillLocations = dao.getAllStillLocations() ?: emptyList()
                movementActivities = dao.getAllMovementActivities() ?: emptyList()
            } catch (e: Exception) {
                Log.e("DatabaseViewer", "Error loading data: ${e.message}")
                stillLocations = emptyList()
                movementActivities = emptyList()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        TopAppBar(
            title = { Text("Activity Database Viewer") }
        )

        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        // Content
        when (selectedTab) {
            0 -> StillLocationsTab(stillLocations, dateFormat)
            1 -> MovementActivitiesTab(movementActivities, dateFormat)
        }
    }
}

@Composable
fun StillLocationsTab(
    stillLocations: List<StillLocation>,
    dateFormat: SimpleDateFormat
) {
    if (stillLocations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No still locations recorded yet")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Total Still Locations: ${stillLocations.size}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(stillLocations) { location ->
                StillLocationCard(location, dateFormat)
            }
        }
    }
}

@Composable
fun StillLocationCard(
    location: StillLocation,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(location.timestamp),
                    fontWeight = FontWeight.Bold
                )
                location.wasSupposedToBeActivity?.let { activity ->
                    Text(
                        text = "Was: $activity",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Location: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}",
                fontSize = 14.sp
            )

            location.duration?.let { duration ->
                Text(
                    text = "Duration: ${formatDuration(duration)}",
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MovementActivitiesTab(
    activities: List<MovementActivity>,
    dateFormat: SimpleDateFormat
) {
    if (activities.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No movement activities recorded yet")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val actualMovements = activities.count { it.actuallyMoved }
                Text(
                    "Total Activities: ${activities.size} (Actually moved: $actualMovements)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(activities) { activity ->
                MovementActivityCard(activity, dateFormat)
            }
        }
    }
}

@Composable
fun MovementActivityCard(
    activity: MovementActivity,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (!activity.actuallyMoved) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activity.activityType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (!activity.actuallyMoved) {
                    Text(
                        text = "Didn't Move",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Start: ${dateFormat.format(activity.startTime)}",
                fontSize = 14.sp
            )
            Text(
                text = "End: ${dateFormat.format(activity.endTime)}",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Distance: ${String.format("%.1f", activity.distance)} meters",
                fontSize = 14.sp
            )

            val duration = activity.endTime.time - activity.startTime.time
            Text(
                text = "Duration: ${formatDuration(duration)}",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Start: ${String.format("%.6f", activity.startLatitude)}, ${String.format("%.6f", activity.startLongitude)}",
                fontSize = 12.sp
            )
            Text(
                text = "End: ${String.format("%.6f", activity.endLatitude)}, ${String.format("%.6f", activity.endLongitude)}",
                fontSize = 12.sp
            )
        }
    }
}

fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}