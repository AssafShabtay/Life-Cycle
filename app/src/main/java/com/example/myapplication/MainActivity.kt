package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Date
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.myapplication.ActivityTimeSlot
import com.example.myapplication.AnimatedPieChart
import com.example.myapplication.applySavedColors
import com.example.myapplication.calculateActivityTimeSlots
import com.example.myapplication.calculateActivityTimeSlotsForRange
import com.example.myapplication.formatDuration

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
                // Automatically start tracking when app opens with permissions
                startLocationTracking()
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
                    onViewDatabase = {
                        try {
                            startActivity(Intent(this, DatabaseViewerActivity::class.java))
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening database viewer: ${e.message}")
                            Toast.makeText(this, "Error opening database viewer", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGenerateData = {
                        generateExampleData()
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
        currentLocationSlot: LocationSlot?,
        onViewDatabase: () -> Unit,
        onGenerateData: suspend () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selectedTab by remember { mutableStateOf(0) }
        val overviewScrollState = rememberScrollState()
        val trendScrollState = rememberScrollState()

        var activityTimeSlots by remember { mutableStateOf<List<ActivityTimeSlot>>(emptyList()) }
        var isSummaryLoading by remember { mutableStateOf(false) }
        var summaryError by remember { mutableStateOf<String?>(null) }

        var trendSummary by remember { mutableStateOf<TrendSummary?>(null) }
        var isTrendLoading by remember { mutableStateOf(false) }
        var trendError by remember { mutableStateOf<String?>(null) }

        suspend fun loadSummary() {
            isSummaryLoading = true
            summaryError = null
            try {
                val slots = calculateActivityTimeSlots(dao, Date())
                activityTimeSlots = applySavedColors(context, slots)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading activity summary: ", e)
                summaryError = "Unable to load activity summary."
            } finally {
                isSummaryLoading = false
            }
        }

        suspend fun loadTrends(force: Boolean = false) {
            if (isTrendLoading) return
            if (!force && trendSummary != null) return

            isTrendLoading = true
            trendError = null
            try {
                val endCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val endDate = endCalendar.time
                val startCalendar = (endCalendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startDate = startCalendar.time

                val slots = calculateActivityTimeSlotsForRange(dao, startDate, endDate)
                val coloredSlots = applySavedColors(context, slots)
                trendSummary = buildTrendSummary(coloredSlots, startDate, endDate)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading trends: ", e)
                trendError = "Unable to load trends right now."
            } finally {
                isTrendLoading = false
            }
        }

        LaunchedEffect(canTrack, currentActivityState) {
            loadSummary()
        }

        LaunchedEffect(selectedTab, canTrack) {
            if (selectedTab == 1) {
                loadTrends()
            }
        }

        val onRefreshSummary: () -> Unit = {
            scope.launch { loadSummary() }
        }

        val onRetryTrends: () -> Unit = {
            scope.launch { loadTrends(force = true) }
        }

        val onGenerateExample: () -> Unit = {
            scope.launch {
                try {
                    onGenerateData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating example data", e)
                }
                loadSummary()
                if (selectedTab == 1) {
                    loadTrends(force = true)
                } else {
                    trendSummary = null
                }
            }
        }

        val tabs = listOf("Overview", "Trends")

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> OverviewTabContent(
                        scrollState = overviewScrollState,
                        currentActivityState = currentActivityState,
                        canTrack = canTrack,
                        currentLocationSlot = currentLocationSlot,
                        activityTimeSlots = activityTimeSlots,
                        isSummaryLoading = isSummaryLoading,
                        summaryError = summaryError,
                        onRefreshSummary = onRefreshSummary,
                        onViewDatabase = onViewDatabase,
                        onGenerateDataClick = onGenerateExample
                    )
                    else -> TrendsTabContent(
                        scrollState = trendScrollState,
                        trendSummary = trendSummary,
                        isLoading = isTrendLoading,
                        errorMessage = trendError,
                        onRetry = onRetryTrends
                    )
                }
            }
        }
    }
    @Composable
    private fun OverviewTabContent(
        scrollState: ScrollState,
        currentActivityState: String,
        canTrack: Boolean,
        currentLocationSlot: LocationSlot?,
        activityTimeSlots: List<ActivityTimeSlot>,
        isSummaryLoading: Boolean,
        summaryError: String?,
        onRefreshSummary: () -> Unit,
        onViewDatabase: () -> Unit,
        onGenerateDataClick: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Activity & Location Tracker",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                text = if (canTrack) "Tracking: Active" else "Tracking: Inactive",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp),
                color = if (canTrack) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )

            ActivitySummaryCard(
                slots = activityTimeSlots,
                isLoading = isSummaryLoading,
                errorMessage = summaryError,
                onRefresh = onRefreshSummary
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    text = "Permissions needed for tracking",
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Button(
                onClick = onViewDatabase,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text("View Database")
            }

            Button(
                onClick = onGenerateDataClick,
                modifier = Modifier.padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                Text("Generate Example Data")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Notes:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = """- Tracking starts automatically when permissions are granted
- Still locations are saved automatically
- Movement activities track start/end locations
- Activities within 100m radius are marked as 'still'
- Tap 'Generate Example Data' to test the pie chart""",
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    @Composable
    private fun TrendsTabContent(
        scrollState: ScrollState,
        trendSummary: TrendSummary?,
        isLoading: Boolean,
        errorMessage: String?,
        onRetry: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Trends & Insights",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                trendSummary == null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tracked activity in the last week yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    ActivityTrendChart(entries = trendSummary.activityTotals)
                    Spacer(modifier = Modifier.height(24.dp))
                    DailyTrendChart(entries = trendSummary.dailyTotals)
                    Spacer(modifier = Modifier.height(24.dp))
                    LocationHighlightCard(summary = trendSummary.locationSummary)
                    Spacer(modifier = Modifier.height(24.dp))
                    InterestingFactsList(facts = trendSummary.interestingFacts)
                }
            }
        }
    }

    @Composable
    private fun ActivitySummaryCard(
        slots: List<ActivityTimeSlot>,
        isLoading: Boolean,
        errorMessage: String?,
        onRefresh: () -> Unit,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onRefresh,
                        enabled = !isLoading
                    ) {
                        Text("Refresh")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    slots.isEmpty() -> {
                        Text(
                            text = "No activity recorded yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        AnimatedPieChart(
                            data = slots,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val totalMillis = slots.sumOf { it.durationMillis }
                        val totalMinutes = totalMillis / (1000 * 60)
                        val totalHours = totalMinutes / 60
                        val remainingMinutes = totalMinutes % 60
                        Text(
                            text = "Total tracked: ${totalHours}h ${remainingMinutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ActivityTrendChart(
        entries: List<ActivityTrendEntry>,
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "No activity distribution available yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Activity distribution (last 7 days)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val maxMinutes = entries.maxOf { it.totalMinutes }.coerceAtLeast(1)
            entries.forEach { entry ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.label,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatDuration(entry.totalMinutes * 60L * 1000L),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(entry.totalMinutes / maxMinutes.toFloat())
                                .clip(RoundedCornerShape(6.dp))
                                .background(entry.color)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DailyTrendChart(entries: List<DailyTrendEntry>) {
        if (entries.isEmpty()) {
            Text(
                text = "No daily data yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        val maxMinutes = entries.maxOf { it.totalMinutes }.coerceAtLeast(1)
        val maxBarHeight = 120.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Daily totals (last 7 days)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                entries.forEach { entry ->
                    val fraction = entry.totalMinutes / maxMinutes.toFloat()
                    val barHeight = (maxBarHeight.value * fraction).coerceAtLeast(4f).dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(maxBarHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(20.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LocationHighlightCard(summary: LocationTrendSummary?) {
        if (summary == null) return

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Frequent location",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Time spent: ${formatDuration(summary.totalMinutes * 60L * 1000L)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun InterestingFactsList(facts: List<String>) {
        if (facts.isEmpty()) return

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Interesting facts",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            facts.forEach { fact ->
                Text(
                    text = "- $fact",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }

    private data class TrendSummary(
        val activityTotals: List<ActivityTrendEntry>,
        val dailyTotals: List<DailyTrendEntry>,
        val locationSummary: LocationTrendSummary?,
        val interestingFacts: List<String>,
    )

    private data class ActivityTrendEntry(
        val label: String,
        val totalMinutes: Int,
        val color: Color,
    )

    private data class DailyTrendEntry(
        val label: String,
        val totalMinutes: Int,
    )

    private data class LocationTrendSummary(
        val label: String,
        val totalMinutes: Int,
    )

    private fun buildTrendSummary(
        slots: List<ActivityTimeSlot>,
        startDate: Date,
        endDate: Date,
    ): TrendSummary {
        val activityGroups = mutableMapOf<String, Pair<Long, Color>>()
        slots.forEach { slot ->
            val key = slot.activityType.substringBefore(" (").ifBlank { slot.activityType }
            val existing = activityGroups[key]
            val updatedDuration = (existing?.first ?: 0L) + slot.durationMillis
            val color = existing?.second ?: slot.color
            activityGroups[key] = updatedDuration to color
        }

        val activityTotals = activityGroups.entries
            .sortedByDescending { it.value.first }
            .map { entry ->
                ActivityTrendEntry(
                    label = entry.key,
                    totalMinutes = (entry.value.first / 60000L).toInt(),
                    color = entry.value.second
                )
            }

        val dailyTotalsMap = mutableMapOf<Long, Long>()
        slots.forEach { slot ->
            val dayStartMillis = slot.dayStart?.time ?: Calendar.getInstance().apply {
                time = slot.startTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            dailyTotalsMap[dayStartMillis] = (dailyTotalsMap[dayStartMillis] ?: 0L) + slot.durationMillis
        }

        val dailyEntries = mutableListOf<DailyTrendEntry>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayCursor = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCursor = Calendar.getInstance().apply {
            time = endDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (!dayCursor.after(endCursor)) {
            val key = dayCursor.timeInMillis
            val minutes = ((dailyTotalsMap[key] ?: 0L) / 60000L).toInt()
            dailyEntries += DailyTrendEntry(
                label = dayFormat.format(dayCursor.time),
                totalMinutes = minutes
            )
            dayCursor.add(Calendar.DAY_OF_YEAR, 1)
        }

        val locationEntries = slots.filter { it.latitude != null && it.longitude != null }
        val locationSummary = if (locationEntries.isNotEmpty()) {
            val grouped = locationEntries.groupBy {
                val lat = it.latitude!!
                val lon = it.longitude!!
                String.format(Locale.getDefault(), "%.3f, %.3f", lat, lon)
            }
            val (label, duration) = grouped.maxByOrNull { group ->
                group.value.sumOf { slot -> slot.durationMillis }
            }?.let { group ->
                val total = group.value.sumOf { slot -> slot.durationMillis }
                group.key to total
            } ?: run { null to null }
            if (label != null && duration != null) {
                LocationTrendSummary(
                    label = label,
                    totalMinutes = (duration / 60000L).toInt()
                )
            } else {
                null
            }
        } else {
            null
        }

        val interestingFacts = mutableListOf<String>()
        activityTotals.firstOrNull()?.let { top ->
            interestingFacts += "Most time spent ${top.label.lowercase()} (${formatDuration(top.totalMinutes * 60L * 1000L)})"
        }
        val busiestDay = dailyEntries.maxByOrNull { it.totalMinutes }
        if (busiestDay != null && busiestDay.totalMinutes > 0) {
            interestingFacts += "Busiest day: ${busiestDay.label} (${formatDuration(busiestDay.totalMinutes * 60L * 1000L)})"
        }
        val longestSlot = slots.maxByOrNull { it.durationMillis }
        if (longestSlot != null && longestSlot.durationMillis > 0) {
            interestingFacts += "Longest session: ${longestSlot.activityType} (${formatDuration(longestSlot.durationMillis)})"
        }
        if (interestingFacts.isEmpty()) {
            interestingFacts += "No tracked activity in the last 7 days."
        }

        return TrendSummary(
            activityTotals = activityTotals,
            dailyTotals = dailyEntries,
            locationSummary = locationSummary,
            interestingFacts = interestingFacts
        )
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

            // Ensure tracking is running
            startLocationTracking()
        } else {
            // Update UI to show limited functionality
            setupUI(canTrack = false)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

    private suspend fun generateExampleData() {
        try {
            withContext(Dispatchers.IO) {
                val generator = ExampleDataGenerator(dao)
                generator.generateExampleDay()
            }
            Toast.makeText(
                this@MainActivity,
                "Example data generated successfully!",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating example data: ${e.message}", e)
            Toast.makeText(
                this@MainActivity,
                "Failed to generate example data",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        try {
            if (receiverRegistered) {
                unregisterReceiver(activityUpdateReceiver)
                unregisterReceiver(geofenceEventReceiver)
                receiverRegistered = false
            }

            // Note: We don't stop the location service on destroy to keep it running
            // The service will continue running in the background
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
            Log.d(TAG, "Location tracking started automatically")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking: ${e.message}")
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






