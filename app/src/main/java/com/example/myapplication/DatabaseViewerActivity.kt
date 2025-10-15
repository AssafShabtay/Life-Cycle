package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

data class ActivityTimeSlot(
    val activityType: String,
    val startTime: Date,
    val endTime: Date,
    val durationMillis: Long,
    var color: Color,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isActive: Boolean = false,
    var clusterId: String? = null,
    val dayStart: Date? = null,
    val originalStartTime: Date = startTime,
    val originalEndTime: Date = endTime
)

private const val ACTIVITY_COLOR_PREFS = "activity_color_preferences"

private const val MIN_ACTIVITY_DURATION_MILLIS = 10 * 60 * 1000L
private const val ACTIVE_SLOT_GRACE_PERIOD_MILLIS = 2 * 60 * 1000L
private const val LOCATION_COLOR_RADIUS_METERS = 100.0
private const val LOCATION_PREF_PREFIX = "location:"
private const val ACTIVITY_PREF_PREFIX = "activity:"
private const val PIE_REFRESH_INTERVAL_MILLIS = 10 * 60 * 1000L

private fun saveActivityColorPreference(context: Context, slot: ActivityTimeSlot, color: Color) {
    val preferences = context.getSharedPreferences(ACTIVITY_COLOR_PREFS, Context.MODE_PRIVATE)
    val editor = preferences.edit()
    val locationKey = slot.clusterId?.let { locationPreferenceKey(it) }
    if (locationKey != null) {
        editor.putInt(locationKey, color.toArgb())
    } else {
        val activityKey = activityPreferenceKey(slot.activityType)
        editor.putInt(activityKey, color.toArgb())
        editor.putInt(slot.activityType, color.toArgb())
    }
    editor.apply()
}

private fun applySavedColors(context: Context, slots: List<ActivityTimeSlot>): List<ActivityTimeSlot> {
    if (slots.isEmpty()) return slots
    val preferences = context.getSharedPreferences(ACTIVITY_COLOR_PREFS, Context.MODE_PRIVATE)
    return slots.map { slot ->
        val locationKey = slot.clusterId?.let { locationPreferenceKey(it) }
        val activityKey = activityPreferenceKey(slot.activityType)
        val overrideColor = when {
            locationKey != null && preferences.contains(locationKey) ->
                Color(preferences.getInt(locationKey, slot.color.toArgb()).toLong())
            preferences.contains(activityKey) ->
                Color(preferences.getInt(activityKey, slot.color.toArgb()).toLong())
            preferences.contains(slot.activityType) ->
                Color(preferences.getInt(slot.activityType, slot.color.toArgb()).toLong())
            else -> null
        }
        if (overrideColor != null) slot.copy(color = overrideColor) else slot
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseViewer(dao: ActivityDao) {
    var selectedDate by remember { mutableStateOf(Date()) }
    var activityTimeSlots by remember { mutableStateOf<List<ActivityTimeSlot>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load data for pie chart
    LaunchedEffect(selectedDate) {
        while (true) {
            try {
                val slots = calculateActivityTimeSlots(dao, selectedDate)
                activityTimeSlots = applySavedColors(context, slots)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DatabaseViewer", "Error loading data: ${e.message}")
                activityTimeSlots = emptyList()
            }
            delay(PIE_REFRESH_INTERVAL_MILLIS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("24-Hour Activity Clock") }
        )

        DailyActivityPieChartWithNavigation(
            activityTimeSlots = activityTimeSlots,
            selectedDate = selectedDate,
            onDateChange = { newDate ->
                selectedDate = newDate
                scope.launch {
                    val slots = calculateActivityTimeSlots(dao, newDate)
                    activityTimeSlots = applySavedColors(context, slots)
                }
            },
            onColorUpdate = { slot, newColor ->
                saveActivityColorPreference(context, slot, newColor)
                val targetClusterId = slot.clusterId
                activityTimeSlots = activityTimeSlots.map { current ->
                    val sameCluster = targetClusterId != null && current.clusterId == targetClusterId
                    val sameActivity = targetClusterId == null && current.activityType == slot.activityType
                    if (sameCluster || sameActivity) {
                        current.copy(color = newColor)
                    } else {
                        current
                    }
                }
            },
            context = context
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyActivityPieChartWithNavigation(
    activityTimeSlots: List<ActivityTimeSlot>,
    selectedDate: Date,
    onDateChange: (Date) -> Unit,
    onColorUpdate: (ActivityTimeSlot, Color) -> Unit,
    context: Context
) {
    var selectedSlot by remember { mutableStateOf<ActivityTimeSlot?>(null) }
    var showLocationSlotDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Calendar Date Selector
        CalendarDateSelector(
            selectedDate = selectedDate,
            onDateSelected = { newDate ->
                onDateChange(newDate)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tap a segment to create a location slot",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Clock and Pie Chart Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedPieChart(
                data = activityTimeSlots,
                modifier = Modifier.fillMaxSize(),
                onSegmentClick = { slot ->
                    selectedSlot = slot
                    showLocationSlotDialog = true
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ActivityRecordingsList(
            slots = activityTimeSlots,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Stats
        if (activityTimeSlots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            QuickStatsCard(activityTimeSlots)
        }
    }

    // Location Slot Creation Popover
    if (showLocationSlotDialog && selectedSlot != null) {
        LocationSlotPopover(
            activitySlot = selectedSlot!!,
            onDismiss = {
                showLocationSlotDialog = false
                selectedSlot = null
            },
            onColorChange = { newColor ->
                selectedSlot?.let { slot ->
                    onColorUpdate(slot, newColor)
                    selectedSlot = slot.copy(color = newColor)
                }
            }
        )
    }
}

@Composable
fun CalendarDateSelector(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    calendar.time = selectedDate

    var currentMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var expandedCalendar by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected date header (collapsible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expandedCalendar = !expandedCalendar },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateFormat.format(selectedDate).split(",")[0],
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(selectedDate).split(",").drop(1).joinToString(",").trim(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = if (expandedCalendar) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expandedCalendar) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Expandable Calendar Grid
        if (expandedCalendar) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Month/Year header with navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        }) {
                            Icon(Icons.Default.ExpandLess, "Previous month",
                                modifier = Modifier.rotate(-90f))
                        }

                        Text(
                            text = monthFormat.format(Calendar.getInstance().apply {
                                set(Calendar.MONTH, currentMonth)
                                set(Calendar.YEAR, currentYear)
                            }.time),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        }) {
                            Icon(Icons.Default.ExpandMore, "Next month",
                                modifier = Modifier.rotate(-90f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Day of week headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar grid
                    CalendarGrid(
                        month = currentMonth,
                        year = currentYear,
                        selectedDate = selectedDate,
                        onDateSelected = { newDate ->
                            onDateSelected(newDate)
                            expandedCalendar = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarDay(
    day: Int,
    isSelected: Boolean,
    activityData: List<ActivityTimeSlot>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.toString(),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Mini pie chart preview
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 2.dp else 0.5.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        ) {
            if (!activityData.isNullOrEmpty()) {
                MiniPieChart(
                    data = activityData,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Empty state - just a light background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
fun MiniPieChart(
    data: List<ActivityTimeSlot>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val radius = canvasSize / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw background circle
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.1f),
            radius = radius,
            center = center
        )

        if (data.isNotEmpty()) {
            val gapPerSideDegrees = if (data.size > 1) 0.6f else 0f

            data.forEach { slot ->
                val calendar = Calendar.getInstance()
                val dayStartMillis = slot.dayStart?.time ?: run {
                    calendar.time = slot.startTime
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.timeInMillis
                }
                val dayEndMillis = dayStartMillis + 24 * 60 * 60 * 1000L

                val clampedStartMillis = slot.startTime.time.coerceIn(dayStartMillis, dayEndMillis)
                val clampedEndMillis = slot.endTime.time.coerceIn(dayStartMillis, dayEndMillis)

                var startMinutes = ((clampedStartMillis - dayStartMillis).toFloat() / 60000f)
                var endMinutes = ((clampedEndMillis - dayStartMillis).toFloat() / 60000f)
                if (endMinutes < startMinutes) endMinutes += 24f * 60f

                val sweepAngle = ((endMinutes - startMinutes) / (24f * 60f)) * 360f
                val slotStartAngle = (startMinutes / (24f * 60f)) * 360f - 90f
                val segmentGap = if (gapPerSideDegrees > 0f && sweepAngle > gapPerSideDegrees * 2f) gapPerSideDegrees else 0f
                val adjustedSweep = (sweepAngle - segmentGap * 2f).coerceAtLeast(0f)

                if (adjustedSweep > 0.5f) {
                    drawArc(
                        color = slot.color,
                        startAngle = slotStartAngle + segmentGap,
                        sweepAngle = adjustedSweep,
                        useCenter = true,
                        topLeft = center - Offset(radius, radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }
            }

            // Draw a small white circle in the center for donut effect
            drawCircle(
                color = Color.White,
                radius = radius * 0.3f,
                center = center
            )
        }
    }
}

@Composable
fun CalendarGrid(
    month: Int,
    year: Int,
    selectedDate: Date,
    onDateSelected: (Date) -> Unit
) {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month)
    calendar.set(Calendar.DAY_OF_MONTH, 1)

    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val selectedCal = Calendar.getInstance()
    selectedCal.time = selectedDate
    val selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH)
    val selectedMonth = selectedCal.get(Calendar.MONTH)
    val selectedYear = selectedCal.get(Calendar.YEAR)

    // Load activity data for all days in the month
    val context = LocalContext.current
    val database = remember { ActivityDatabase.getDatabase(context) }
    val dao = remember { database.activityDao() }
    var monthActivityData by remember { mutableStateOf<Map<Int, List<ActivityTimeSlot>>>(emptyMap()) }

    LaunchedEffect(month, year) {
        monthActivityData = loadMonthActivityData(dao, month, year)
    }

    val weeks = mutableListOf<List<Int>>()
    var week = mutableListOf<Int>()

    // Padding before first day
    repeat(firstDayOfWeek) {
        week.add(0)
    }

    // Fill days
    for (day in 1..daysInMonth) {
        week.add(day)
        if (week.size == 7) {
            weeks.add(week.toList())
            week = mutableListOf()
        }
    }

    // Pad last row
    if (week.isNotEmpty()) {
        while (week.size < 7) {
            week.add(0)
        }
        weeks.add(week.toList())
    }

    Column {
        weeks.forEach { weekDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekDays.forEach { day ->
                    if (day == 0) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        CalendarDay(
                            day = day,
                            isSelected = day == selectedDay &&
                                    month == selectedMonth &&
                                    year == selectedYear,
                            activityData = monthActivityData[day]?.let { applySavedColors(context, it) },
                            onClick = {
                                val newDate = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.time
                                onDateSelected(newDate)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

suspend fun calculateActivityTimeSlots(dao: ActivityDao, date: Date): List<ActivityTimeSlot> = withContext(Dispatchers.IO) {
    val calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val startOfDay = calendar.time
    calendar.add(Calendar.DAY_OF_MONTH, 1)
    val endOfDay = calendar.time

    calculateActivityTimeSlotsForRange(dao, startOfDay, endOfDay)
}

suspend fun loadMonthActivityData(
    dao: ActivityDao,
    month: Int,
    year: Int
): Map<Int, List<ActivityTimeSlot>> = withContext(Dispatchers.IO) {
    val monthData = mutableMapOf<Int, List<ActivityTimeSlot>>()

    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    for (day in 1..daysInMonth) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        val dayStart = calendar.time

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val dayEnd = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, -1) // Reset for next iteration

        try {
            val slots = calculateActivityTimeSlotsForRange(dao, dayStart, dayEnd)
            if (slots.isNotEmpty()) {
                monthData[day] = slots
            }
        } catch (e: Exception) {
            Log.e("CalendarGrid", "Error loading data for day $day: ${e.message}")
        }
    }

    monthData
}

suspend fun calculateActivityTimeSlotsForRange(dao: ActivityDao, startDate: Date, endDate: Date): List<ActivityTimeSlot> {
    val now = Date()
    val activeReference = if (!now.before(startDate) && now.before(endDate)) now else null

    val stillLocations = dao.getStillLocationsBetween(startDate, endDate)
    val movementActivities = dao.getMovementActivitiesBetween(startDate, endDate)

    val defaultActivityColors = mapOf(
        "Still" to Color(0xFF9E9E9E),
        "Walking" to Color(0xFF4CAF50),
        "Running" to Color(0xFFFF9800),
        "Driving" to Color(0xFF2196F3),
        "Cycling" to Color(0xFF9C27B0),
        "On Foot" to Color(0xFF8BC34A),
        "Unknown" to Color(0xFF607D8B)
    )

    val allSlots = mutableListOf<ActivityTimeSlot>()

    stillLocations.forEach { location ->
        val activityType = location.wasSupposedToBeActivity ?: "Still"
        val recordedDuration = (location.duration ?: 0L).coerceAtLeast(0L)
        val recordedEndTime = Date(location.timestamp.time + recordedDuration)
        val isOngoing = activeReference != null && (
                recordedDuration == 0L ||
                        kotlin.math.abs(activeReference.time - recordedEndTime.time) <= ACTIVE_SLOT_GRACE_PERIOD_MILLIS
                )
        val effectiveDuration = if (isOngoing) {
            (activeReference!!.time - location.timestamp.time).coerceAtLeast(0L)
        } else {
            recordedDuration
        }

        val rawEndTime = Date(location.timestamp.time + effectiveDuration)
        val slotStartMillis = max(location.timestamp.time, startDate.time)
        val slotEndMillis = min(rawEndTime.time, endDate.time)
        if (slotEndMillis <= slotStartMillis) return@forEach

        val slotStartTime = Date(slotStartMillis)
        val slotEndTime = Date(slotEndMillis)
        val clippedDuration = slotEndMillis - slotStartMillis

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = slotStartTime,
                endTime = slotEndTime,
                durationMillis = clippedDuration,
                color = defaultActivityColors[activityType] ?: defaultActivityColors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = location.latitude,
                longitude = location.longitude,
                isActive = isOngoing,
                clusterId = null,
                dayStart = Date(startDate.time),
                originalStartTime = Date(location.timestamp.time),
                originalEndTime = rawEndTime
            )
        )
    }


    movementActivities.forEach { activity ->
        val activityType = if (activity.actuallyMoved) {
            activity.activityType
        } else {
            "Still (${activity.activityType})"
        }

        val recordedDuration = (activity.endTime.time - activity.startTime.time).coerceAtLeast(0L)
        val isOngoing = activeReference != null && (
                recordedDuration == 0L ||
                        kotlin.math.abs(activeReference.time - activity.endTime.time) <= ACTIVE_SLOT_GRACE_PERIOD_MILLIS
                )
        val effectiveDuration = if (isOngoing) {
            (activeReference!!.time - activity.startTime.time).coerceAtLeast(0L)
        } else {
            recordedDuration
        }

        val rawEndMillis = activity.startTime.time + effectiveDuration
        val slotStartMillis = max(activity.startTime.time, startDate.time)
        val slotEndMillis = min(rawEndMillis, endDate.time)
        if (slotEndMillis <= slotStartMillis) return@forEach

        val slotStartTime = Date(slotStartMillis)
        val slotEndTime = Date(slotEndMillis)
        val clippedDuration = slotEndMillis - slotStartMillis

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = slotStartTime,
                endTime = slotEndTime,
                durationMillis = clippedDuration,
                color = defaultActivityColors[activityType] ?: defaultActivityColors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = activity.startLatitude,
                longitude = activity.startLongitude,
                isActive = isOngoing,
                clusterId = null,
                dayStart = Date(startDate.time),
                originalStartTime = Date(activity.startTime.time),
                originalEndTime = Date(rawEndMillis)
            )
        )
    }


    val sortedSlots = allSlots.sortedBy { it.startTime }
    val mergedSlots = mergeAndFilterSlots(sortedSlots)
    return assignColorsByLocation(mergedSlots, defaultActivityColors)
}
private data class LocationCluster(
    var latitude: Double,
    var longitude: Double,
    var count: Int,
    val id: String,
    val color: Color
)

private fun assignColorsByLocation(
    slots: List<ActivityTimeSlot>,
    defaultActivityColors: Map<String, Color>
): List<ActivityTimeSlot> {
    if (slots.isEmpty()) return slots

    val clusterMap = mutableMapOf<String, LocationCluster>()
    val clusters = mutableListOf<LocationCluster>()

    return slots.map { slot ->
        val lat = slot.latitude
        val lon = slot.longitude
        if (lat != null && lon != null) {
            val coarseKey = coarseLocationKey(lat, lon)
            val existingByKey = clusterMap[coarseKey]
            val cluster = existingByKey ?: clusters.firstOrNull {
                distanceMeters(it.latitude, it.longitude, lat, lon) <= LOCATION_COLOR_RADIUS_METERS
            }
            val finalCluster = if (cluster != null) {
                clusterMap[coarseKey] = cluster
                val newCount = cluster.count + 1
                cluster.latitude = (cluster.latitude * cluster.count + lat) / newCount
                cluster.longitude = (cluster.longitude * cluster.count + lon) / newCount
                cluster.count = newCount
                cluster
            } else {
                val id = coarseKey
                val color = generateColorForKey(id)
                val newCluster = LocationCluster(
                    latitude = lat,
                    longitude = lon,
                    count = 1,
                    id = id,
                    color = color
                )
                clusters.add(newCluster)
                clusterMap[coarseKey] = newCluster
                newCluster
            }
            slot.copy(color = finalCluster.color, clusterId = finalCluster.id)
        } else {
            val fallbackColor = defaultActivityColors[slot.activityType]
                ?: defaultActivityColors["Still"]
                ?: Color(0xFF9E9E9E)
            slot.copy(clusterId = null, color = fallbackColor)
        }
    }
}

private fun coarseLocationKey(lat: Double, lon: Double): String {
    val latBucket = (lat * 1000).roundToInt()
    val lonBucket = (lon * 1000).roundToInt()
    return latBucket.toString() + "_" + lonBucket.toString()
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val sinLat = kotlin.math.sin(dLat / 2)
    val sinLon = kotlin.math.sin(dLon / 2)
    val a = sinLat * sinLat + kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) * sinLon * sinLon
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadius * c
}

private fun generateColorForKey(key: String): Color {
    val random = Random(key.hashCode())
    val hue = random.nextFloat() * 360f
    val saturation = 0.55f + random.nextFloat() * 0.35f
    val value = 0.75f + random.nextFloat() * 0.25f
    return Color.hsv(hue, saturation, value)
}

private fun locationPreferenceKey(clusterId: String): String = LOCATION_PREF_PREFIX + clusterId
private fun activityPreferenceKey(activityType: String): String = ACTIVITY_PREF_PREFIX + activityType

@Composable
fun AnimatedPieChart(
    data: List<ActivityTimeSlot>,
    modifier: Modifier = Modifier,
    onSegmentClick: (ActivityTimeSlot) -> Unit = {}
) {
    var animationProgress by remember { mutableStateOf(0f) }

    val textPaint = remember {
        android.graphics.Paint().apply {
            textSize = 18f
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            isAntiAlias = true
        }
    }

    data class SegmentBounds(
        val slot: ActivityTimeSlot,
        val startAngle: Float,
        val sweepAngle: Float
    )

    data class SegmentIconInfo(
        val slot: ActivityTimeSlot,
        val center: Offset,
        val alpha: Float,
        val sizeDp: Dp,
        val sizePx: Float
    )

    val segmentBounds = remember { mutableStateListOf<SegmentBounds>() }
    val segmentIcons = remember { mutableStateListOf<SegmentIconInfo>() }

    val iconSize = 20.dp
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSize.toPx() }

    LaunchedEffect(data) {
        animationProgress = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animationProgress = value
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = remember(backgroundColor) {
        val luminance = 0.299 * backgroundColor.red +
                0.587 * backgroundColor.green +
                0.114 * backgroundColor.blue
        if (luminance > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }

    Box(
        modifier = modifier.pointerInput(data) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val distance = kotlin.math.sqrt(
                    (offset.x - centerX) * (offset.x - centerX) +
                            (offset.y - centerY) * (offset.y - centerY)
                )

                val radius = min(size.width, size.height) / 2f * 0.85f
                val innerRadius = radius * 0.55f

                if (distance in innerRadius..radius) {
                    var clickAngle = kotlin.math.atan2(
                        offset.x - centerX,
                        -(offset.y - centerY)
                    ) * 180 / kotlin.math.PI

                    if (clickAngle < 0) clickAngle += 360

                    segmentBounds.forEach { bounds ->
                        val startAngle = bounds.startAngle
                        val endAngle = bounds.startAngle + bounds.sweepAngle

                        val inSegment = if (endAngle > startAngle) {
                            clickAngle >= startAngle && clickAngle <= endAngle
                        } else {
                            clickAngle >= startAngle || clickAngle <= endAngle
                        }

                        if (inSegment) {
                            onSegmentClick(bounds.slot)
                            return@detectTapGestures
                        }
                    }
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasSize = size.minDimension
            val radius = canvasSize / 2f * 0.85f
            val innerRadius = radius * 0.55f
            val center = Offset(size.width / 2f, size.height / 2f)

            segmentBounds.clear()
            segmentIcons.clear()

            val totalMinutes = if (data.isEmpty()) 0L else data.sumOf { it.durationMillis } / (1000 * 60)
            val totalHours = totalMinutes / 60
            val remainingMinutes = totalMinutes % 60

            val strokeWidth = radius - innerRadius
            val middleRadius = (radius + innerRadius) / 2f

            val millisPerDay = 24 * 60 * 60 * 1000L
            val minRenderableAngle = 0.5f
            val minAngleForIcon = 10f
            val minAngleGeneral = 4f
            val iconDisplayThreshold = 7.5f
            val gapDetectionThreshold = 60 * 1000L

            data class BaseSegment(
                val slot: ActivityTimeSlot,
                val startAngle: Float,
                val baseAngle: Float
            )

            data class VisualSegment(
                val slot: ActivityTimeSlot,
                val startAngle: Float,
                val displayAngle: Float,
                val originalAngle: Float
            )

            val calendar = Calendar.getInstance()
            val baseSegments = data
                .sortedBy { it.startTime }
                .mapNotNull { slot ->
                    val dayStartMillis = slot.dayStart?.time ?: run {
                        calendar.time = slot.startTime
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        calendar.timeInMillis
                    }
                    val dayEndMillis = dayStartMillis + millisPerDay

                    val clampedStartMillis = slot.startTime.time.coerceIn(dayStartMillis, dayEndMillis)
                    val clampedEndMillis = slot.endTime.time.coerceIn(dayStartMillis, dayEndMillis)

                    var startMinutes = ((clampedStartMillis - dayStartMillis).toFloat() / 60000f)
                    var endMinutes = ((clampedEndMillis - dayStartMillis).toFloat() / 60000f)
                    if (endMinutes < startMinutes) endMinutes += 24f * 60f

                    val rawAngle = ((endMinutes - startMinutes) / (24f * 60f)) * 360f
                    val normalizedAngle = when {
                        slot.isActive -> max(rawAngle, minAngleGeneral)
                        else -> rawAngle
                    }
                    if (normalizedAngle < minRenderableAngle && !slot.isActive) {
                        null
                    } else {
                        val startAngle = (startMinutes / (24f * 60f)) * 360f - 90f
                        BaseSegment(slot, startAngle, normalizedAngle)
                    }
                }

            val visualSegments = mutableListOf<VisualSegment>()
            if (baseSegments.isNotEmpty()) {
                val boostedSegments = MutableList(baseSegments.size) { false }
                val targetAngles = MutableList(baseSegments.size) { index ->
                    val slot = baseSegments[index].slot
                    val baseAngle = baseSegments[index].baseAngle
                    if (slot.durationMillis >= 15 * 60 * 1000L && baseAngle < minAngleForIcon) {
                        boostedSegments[index] = true
                        minAngleForIcon
                    } else {
                        baseAngle
                    }
                }

                var totalAngle = targetAngles.sum()
                if (totalAngle > 360f) {
                    var excess = totalAngle - 360f

                    fun reduce(indices: List<Int>, floor: Float) {
                        if (indices.isEmpty() || excess <= 0f) return
                        var available = 0f
                        indices.forEach { idx ->
                            available += (targetAngles[idx] - floor).coerceAtLeast(0f)
                        }
                        if (available <= 0f) return
                        val reduction = excess.coerceAtMost(available)
                        indices.forEach { idx ->
                            val adjustable = (targetAngles[idx] - floor).coerceAtLeast(0f)
                            if (adjustable > 0f) {
                                val share = (adjustable / available) * reduction
                                targetAngles[idx] -= share
                            }
                        }
                        excess -= reduction
                    }

                    val adjustable = baseSegments.indices.filter { !boostedSegments[it] && targetAngles[it] > minAngleGeneral }
                    reduce(adjustable, minAngleGeneral)

                    val allCandidates = baseSegments.indices.filter { targetAngles[it] > minAngleGeneral }
                    reduce(allCandidates, minAngleGeneral)

                    val boosted = baseSegments.indices.filter { boostedSegments[it] && targetAngles[it] > minAngleForIcon }
                    reduce(boosted, minAngleForIcon)
                }

                var currentAngle = baseSegments.first().startAngle
                baseSegments.indices.forEach { index ->
                    val base = baseSegments[index]
                    if (index > 0) {
                        val desired = base.startAngle
                        if (desired > currentAngle) {
                            currentAngle = desired
                        }
                    }
                    val displayAngle = targetAngles[index]
                    visualSegments.add(
                        VisualSegment(
                            slot = base.slot,
                            startAngle = currentAngle,
                            displayAngle = displayAngle,
                            originalAngle = base.baseAngle
                        )
                    )
                    currentAngle += displayAngle
                }
            }

            var angleRemaining = 360f * animationProgress
            val gapPerSideDegrees = if (visualSegments.size > 1) 0.6f else 0f
            visualSegments.forEach { segment ->
                val segmentDisplay = segment.displayAngle
                val visibleFraction = when {
                    angleRemaining <= 0f -> 0f
                    angleRemaining >= segmentDisplay -> 1f
                    segmentDisplay > 0f -> angleRemaining / segmentDisplay
                    else -> 0f
                }
                angleRemaining = (angleRemaining - segmentDisplay).coerceAtLeast(0f)

                val animatedSweep = segment.displayAngle * visibleFraction
                val targetGap = if (gapPerSideDegrees > 0f && segment.displayAngle > gapPerSideDegrees * 2f) gapPerSideDegrees else 0f
                val animatedGap = if (targetGap > 0f && animatedSweep > targetGap * 2f) targetGap else 0f
                val trimmedSweep = (animatedSweep - animatedGap * 2f).coerceAtLeast(0f)

                if (trimmedSweep > 0.5f) {
                    val arcStartAngle = segment.startAngle + animatedGap

                    drawArc(
                        color = segment.slot.color,
                        startAngle = arcStartAngle,
                        sweepAngle = trimmedSweep,
                        useCenter = false,
                        topLeft = center - Offset(middleRadius, middleRadius),
                        size = Size(middleRadius * 2, middleRadius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Butt
                        )
                    )

                    val detectionGap = targetGap
                    val trimmedDisplayAngle = (segment.displayAngle - detectionGap * 2f).coerceAtLeast(0f)
                    if (trimmedDisplayAngle > 0f) {
                        segmentBounds.add(
                            SegmentBounds(
                                slot = segment.slot,
                                startAngle = segment.startAngle + 90 + detectionGap,
                                sweepAngle = trimmedDisplayAngle
                            )
                        )
                    }

                    if (
                        segment.slot.durationMillis >= 15 * 60 * 1000 &&
                        visibleFraction > 0.9f &&
                        trimmedDisplayAngle >= iconDisplayThreshold
                    ) {
                        val midAngle = segment.startAngle + detectionGap + (trimmedDisplayAngle / 2f)
                        val midAngleRad = Math.toRadians(midAngle.toDouble())

                        val textRadiusForSegment = innerRadius + strokeWidth * 0.45f
                        val iconRadiusPreferred = innerRadius + strokeWidth * 0.75f
                        val angleRad = Math.toRadians(trimmedDisplayAngle.toDouble())
                        val halfAngleSin = sin(angleRad / 2.0).toFloat()
                        val chordText = 2f * textRadiusForSegment * halfAngleSin
                        val chordIcon = 2f * iconRadiusPreferred * halfAngleSin

                        val dayStartMillisForSlot = segment.slot.dayStart?.time ?: run {
                            calendar.time = segment.slot.startTime
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            calendar.timeInMillis
                        }
                        val totalDurationCandidate = segment.slot.originalEndTime.time - segment.slot.originalStartTime.time
                        val totalDurationMillis = max(segment.slot.durationMillis, max(totalDurationCandidate, 0L))
                        val durationMillisForLabel = if (segment.slot.originalStartTime.time < dayStartMillisForSlot) {
                            totalDurationMillis
                        } else {
                            segment.slot.durationMillis
                        }
                        val durationMinutes = durationMillisForLabel / (1000 * 60)
                        val durationText = when {
                            durationMinutes >= 60 -> "${durationMinutes / 60}h${if (durationMinutes % 60 > 0) "${durationMinutes % 60}m" else ""}"
                            else -> "${durationMinutes}m"
                        }

                        val textPaintSegment = android.graphics.Paint(textPaint)
                        var textWidth = textPaintSegment.measureText(durationText)
                        val maxTextWidth = (chordText - 10f).coerceAtLeast(0f)
                        if (maxTextWidth > 0f && textWidth > maxTextWidth) {
                            val scale = (maxTextWidth / textWidth).coerceIn(0.5f, 1f)
                            textPaintSegment.textSize *= scale
                            textWidth = textPaintSegment.measureText(durationText)
                        }
                        val metrics = textPaintSegment.fontMetrics
                        val textYOffset = (metrics.ascent + metrics.descent) / 2f
                        val minTextRadius = innerRadius + textPaintSegment.textSize
                        val maxTextRadius = radius - textPaintSegment.textSize

                        if (maxTextWidth > 0f && textWidth <= chordText + 1f && minTextRadius < maxTextRadius) {
                            val safeTextRadius = textRadiusForSegment.coerceIn(minTextRadius, maxTextRadius)
                            val textX = center.x + (safeTextRadius * cos(midAngleRad)).toFloat()
                            val textY = center.y + (safeTextRadius * sin(midAngleRad)).toFloat() - textYOffset
                            drawContext.canvas.nativeCanvas.drawText(
                                durationText,
                                textX,
                                textY,
                                textPaintSegment
                            )
                        }

                        val iconAlpha = ((animationProgress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        val availableIcon = chordIcon - 8f
                        if (iconAlpha > 0f && visibleFraction > 0.9f && availableIcon > 4f) {
                            var iconSizePxAdjusted = iconSizePx.coerceAtMost(availableIcon)
                            val minIconPx = iconSizePx * 0.4f
                            if (iconSizePxAdjusted < minIconPx) {
                                iconSizePxAdjusted = if (availableIcon <= minIconPx) availableIcon else minIconPx
                            }

                            val maxIconRadius = radius - iconSizePxAdjusted / 2f
                            val minIconRadius = innerRadius + iconSizePxAdjusted / 2f
                            val iconRadius = iconRadiusPreferred.coerceIn(minIconRadius, maxIconRadius)

                            val iconX = center.x + (iconRadius * cos(midAngleRad)).toFloat()
                            val iconY = center.y + (iconRadius * sin(midAngleRad)).toFloat()
                            val iconSizeDpAdjusted = with(density) { iconSizePxAdjusted.toDp() }

                            segmentIcons.add(
                                SegmentIconInfo(
                                    slot = segment.slot,
                                    center = Offset(iconX, iconY),
                                    alpha = iconAlpha,
                                    sizeDp = iconSizeDpAdjusted,
                                    sizePx = iconSizePxAdjusted
                                )
                            )
                        }
                    }
                }
            }


            val centerTextPaint = android.graphics.Paint().apply {
                textSize = 42f
                color = textColor
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.drawText(
                if (totalHours > 0 || remainingMinutes > 0) {
                    "${totalHours}h ${remainingMinutes}m"
                } else {
                    "No data"
                },
                center.x,
                center.y - 5,
                centerTextPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                "tracked",
                center.x,
                center.y + 30,
                android.graphics.Paint().apply {
                    textSize = 24f
                    color = textColor
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }

        segmentIcons.forEach { iconInfo ->
            val iconVector = getIconVectorForActivity(iconInfo.slot.activityType)

            Icon(
                imageVector = iconVector,
                contentDescription = iconInfo.slot.activityType,
                modifier = Modifier
                    .size(iconInfo.sizeDp)
                    .offset {
                        IntOffset(
                            (iconInfo.center.x - iconInfo.sizePx / 2f).roundToInt(),
                            (iconInfo.center.y - iconInfo.sizePx / 2f).roundToInt()
                        )
                    }
                    .alpha(iconInfo.alpha),
                tint = Color.White
            )
        }
    }
}


fun getIconVectorForActivity(activityType: String): ImageVector {
    val normalized = activityType.substringBefore(" (")
    return when (normalized) {
        "Still" -> Icons.Default.Home
        "Walking" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "Running" -> Icons.AutoMirrored.Filled.DirectionsRun
        "Driving" -> Icons.Default.DirectionsCar
        "Cycling" -> Icons.AutoMirrored.Filled.DirectionsBike
        "On Foot" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "Unknown" -> Icons.AutoMirrored.Filled.Help
        else -> Icons.Default.Place
    }
}


private fun mergeAndFilterSlots(sortedSlots: List<ActivityTimeSlot>): List<ActivityTimeSlot> {
    val mergedSlots = mutableListOf<ActivityTimeSlot>()
    for (slot in sortedSlots) {
        if (slot.durationMillis < 30000 && !slot.isActive) continue

        val lastSlot = mergedSlots.lastOrNull()
        if (lastSlot != null) {
            if (slot.startTime.before(lastSlot.endTime)) {
                if (slot.activityType == lastSlot.activityType) {
                    val newEndTime = if (slot.endTime.after(lastSlot.endTime)) slot.endTime else lastSlot.endTime
                    mergedSlots[mergedSlots.size - 1] = lastSlot.copy(
                        endTime = newEndTime,
                        durationMillis = newEndTime.time - lastSlot.startTime.time,
                        isActive = lastSlot.isActive || slot.isActive,
                        originalEndTime = if (slot.originalEndTime.after(lastSlot.originalEndTime)) slot.originalEndTime else lastSlot.originalEndTime
                    )
                } else if (slot.endTime.time - slot.startTime.time > 60000) {
                    val adjustedStartTime = lastSlot.endTime
                    if (slot.endTime.after(adjustedStartTime)) {
                        mergedSlots.add(
                            slot.copy(
                                startTime = adjustedStartTime,
                                durationMillis = slot.endTime.time - adjustedStartTime.time,
                                isActive = slot.isActive
                            )
                        )
                    }
                }
            } else {
                if (slot.durationMillis >= 60000) {
                    mergedSlots.add(slot)
                }
            }
        } else {
            if (slot.durationMillis >= 60000) {
                mergedSlots.add(slot)
            }
        }
    }

    val filteredSlots = mutableListOf<ActivityTimeSlot>()
    for (slot in mergedSlots) {
        val duration = slot.durationMillis
        if (duration < MIN_ACTIVITY_DURATION_MILLIS && !slot.isActive) {
            val isStationaryWalking = slot.activityType.startsWith("Still (", ignoreCase = true) &&
                    slot.activityType.contains("Walking", ignoreCase = true)
            val lastIndex = filteredSlots.lastIndex
            if (isStationaryWalking && lastIndex >= 0) {
                val lastSlot = filteredSlots[lastIndex]
                if (lastSlot.activityType == "Still") {
                    val extendedEndTime = if (slot.endTime.after(lastSlot.endTime)) slot.endTime else lastSlot.endTime
                    filteredSlots[lastIndex] = lastSlot.copy(
                        endTime = extendedEndTime,
                        durationMillis = extendedEndTime.time - lastSlot.startTime.time,
                        isActive = lastSlot.isActive || slot.isActive,
                        originalEndTime = if (slot.originalEndTime.after(lastSlot.originalEndTime)) slot.originalEndTime else lastSlot.originalEndTime
                    )
                }
            }
            continue
        }
        filteredSlots.add(slot)
    }

    val walkMergedSlots = mutableListOf<ActivityTimeSlot>()
    var index = 0
    while (index < filteredSlots.size) {
        val slot = filteredSlots[index]

        if (!slot.activityType.equals("Walking", ignoreCase = true)) {
            walkMergedSlots.add(slot)
            index += 1
            continue
        }

        var mergedEndTime = slot.endTime
        var mergedOriginalEndTime = slot.originalEndTime
        var mergedIsActive = slot.isActive
        var consumedIndex = index
        var nextIndex = index + 1

        while (nextIndex < filteredSlots.size) {
            val candidate = filteredSlots[nextIndex]

            if (candidate.activityType.equals("Walking", ignoreCase = true)) {
                val gapMillis = candidate.startTime.time - mergedEndTime.time
                if (gapMillis <= MIN_ACTIVITY_DURATION_MILLIS) {
                    if (candidate.endTime.after(mergedEndTime)) {
                        mergedEndTime = candidate.endTime
                    }
                    if (candidate.originalEndTime.after(mergedOriginalEndTime)) {
                        mergedOriginalEndTime = candidate.originalEndTime
                    }
                    mergedIsActive = mergedIsActive || candidate.isActive
                    consumedIndex = nextIndex
                    nextIndex += 1
                    continue
                }
                break
            }

            var lookahead = nextIndex
            var accumulatedStopDuration = 0L
            while (lookahead < filteredSlots.size && !filteredSlots[lookahead].activityType.equals("Walking", ignoreCase = true)) {
                val stopSlot = filteredSlots[lookahead]
                val stopDuration = stopSlot.durationMillis
                if (stopDuration > MIN_ACTIVITY_DURATION_MILLIS) {
                    accumulatedStopDuration = MIN_ACTIVITY_DURATION_MILLIS + 1
                    break
                }
                accumulatedStopDuration += stopDuration
                if (accumulatedStopDuration > MIN_ACTIVITY_DURATION_MILLIS) {
                    break
                }
                lookahead += 1
            }

            if (accumulatedStopDuration <= MIN_ACTIVITY_DURATION_MILLIS &&
                lookahead < filteredSlots.size &&
                filteredSlots[lookahead].activityType.equals("Walking", ignoreCase = true)
            ) {
                val nextWalking = filteredSlots[lookahead]
                val gapMillis = nextWalking.startTime.time - mergedEndTime.time
                if (gapMillis <= MIN_ACTIVITY_DURATION_MILLIS) {
                    if (nextWalking.endTime.after(mergedEndTime)) {
                        mergedEndTime = nextWalking.endTime
                    }
                    if (nextWalking.originalEndTime.after(mergedOriginalEndTime)) {
                        mergedOriginalEndTime = nextWalking.originalEndTime
                    }
                    mergedIsActive = mergedIsActive || nextWalking.isActive
                    consumedIndex = lookahead
                    nextIndex = lookahead + 1
                    continue
                }
            }

            break
        }

        val mergedSlot = slot.copy(
            endTime = mergedEndTime,
            durationMillis = mergedEndTime.time - slot.startTime.time,
            isActive = mergedIsActive,
            originalEndTime = mergedOriginalEndTime
        )
        walkMergedSlots.add(mergedSlot)
        index = consumedIndex + 1
    }

    return walkMergedSlots
}

@Composable
fun ActivityRecordingsList(
    slots: List<ActivityTimeSlot>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Activity Records",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (slots.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No recordings for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

                Spacer(modifier = Modifier.height(12.dp))

                slots.forEachIndexed { index, slot ->
                    ActivityRecordingRow(slot = slot, timeFormatter = timeFormatter)

                    if (index < slots.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityRecordingRow(
    slot: ActivityTimeSlot,
    timeFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val timeRange = "${timeFormatter.format(slot.startTime)} - ${timeFormatter.format(slot.endTime)}"
    val durationText = formatDuration(slot.durationMillis)
    val locationText = if (slot.latitude != null && slot.longitude != null) {
        String.format(Locale.getDefault(), "%.4f, %.4f", slot.latitude, slot.longitude)
    } else {
        "Location unavailable"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(slot.color)
        )

        Spacer(modifier = Modifier.width(12.dp))

        val activityTitle = if (slot.isActive) "${slot.activityType} (recording)" else slot.activityType

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activityTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = timeRange,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Duration: $durationText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Location: $locationText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickStatsCard(activityTimeSlots: List<ActivityTimeSlot>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick Stats",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val mostActiveType = activityTimeSlots.maxByOrNull { it.durationMillis }
            val totalActiveTime = activityTimeSlots
                .filter { it.activityType != "Still" }
                .sumOf { it.durationMillis }

            mostActiveType?.let {
                InfoRow("Most Common Activity", it.activityType)
            }

            InfoRow("Total Active Time", formatDuration(totalActiveTime))

            InfoRow("Activities Recorded", activityTimeSlots.size.toString())
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSlotPopover(
    activitySlot: ActivityTimeSlot,
    onDismiss: () -> Unit,
    onColorChange: (Color) -> Unit,
    anchorPosition: Offset? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    var slotName by remember { mutableStateOf(activitySlot.activityType) }
    var selectedIcon by remember { mutableStateOf(getDefaultIconForActivity(activitySlot.activityType)) }
    var selectedColor by remember { mutableStateOf(activitySlot.color) }
    var radius by remember { mutableStateOf(100f) }
    var notifyOnEnter by remember { mutableStateOf(true) }
    var notifyOnExit by remember { mutableStateOf(false) }
    var expandAdvanced by remember { mutableStateOf(false) }

    val icons = listOf(
        "home" to Icons.Default.Home,
        "work" to Icons.Default.Work,
        "gym" to Icons.Default.FitnessCenter,
        "food" to Icons.Default.Restaurant,
        "shop" to Icons.Default.ShoppingCart,
        "star" to Icons.Default.Star
    )

    val colors = listOf(
        Color(0xFF2196F3),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFF9C27B0),
        Color(0xFFE91E63),
        Color(0xFF00BCD4)
    )

    val mapLatLng = remember(activitySlot.latitude, activitySlot.longitude) {
        val lat = activitySlot.latitude
        val lon = activitySlot.longitude
        if (lat != null && lon != null) LatLng(lat, lon) else null
    }
    val showLocationMap = remember(activitySlot.activityType, mapLatLng) {
        mapLatLng != null && activitySlot.activityType.startsWith("Still")
    }

    val autoSave: suspend () -> Unit = {
        delay(500)
        val target = mapLatLng
        saveLocationSlot(
            context = context,
            name = slotName,
            icon = selectedIcon,
            color = selectedColor.toArgb(),
            radius = radius,
            notifyOnEnter = true,
            notifyOnExit = false,
            latitude = target?.latitude ?: 0.0,
            longitude = target?.longitude ?: 0.0
        )
    }

    LaunchedEffect(slotName, selectedIcon, selectedColor, radius) {
        if (slotName.isNotBlank()) {
            autoSave()
        }
    }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 280.dp, max = 320.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activitySlot.activityType,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${timeFormat.format(activitySlot.startTime)} - ${timeFormat.format(activitySlot.endTime)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(selectedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icons.find { it.first == selectedIcon }?.second
                            ?: Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            if (showLocationMap) {
                val target = mapLatLng!!
                Text("Location", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(target, 16f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            myLocationButtonEnabled = true,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = true,
                            tiltGesturesEnabled = false,
                        )
                    ) {
                        Marker(state = rememberMarkerState(position = target), title = slotName)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = slotName,
                onValueChange = { slotName = it },
                label = { Text("Name", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                icons.forEach { (id, icon) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedIcon == id) selectedColor
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = id },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = id,
                            tint = if (selectedIcon == id) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (selectedColor == color) BorderStroke(2.dp, Color.Black)
                                else BorderStroke(0.5.dp, Color.Gray),
                                CircleShape
                            )
                            .clickable {
                                selectedColor = color
                                onColorChange(color)
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandAdvanced = !expandAdvanced },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Settings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (expandAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expandAdvanced) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expandAdvanced) {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Radius:",
                                fontSize = 12.sp,
                                modifier = Modifier.width(45.dp)
                            )
                            Slider(
                                value = radius,
                                onValueChange = { radius = it },
                                valueRange = 50f..300f,
                                modifier = Modifier.weight(1f).height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = selectedColor,
                                    activeTrackColor = selectedColor
                                )
                            )
                            Text(
                                text = "${radius.roundToInt()}m",
                                fontSize = 12.sp,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getDefaultIconForActivity(activityType: String): String {
    return when (activityType) {
        "Still" -> "home"
        "Walking" -> "place"
        "Running" -> "gym"
        "Driving" -> "work"
        "Cycling" -> "favorite"
        else -> "place"
    }
}

suspend fun saveLocationSlot(
    context: Context,
    name: String,
    icon: String,
    color: Int,
    radius: Float,
    notifyOnEnter: Boolean,
    notifyOnExit: Boolean,
    latitude: Double,
    longitude: Double
) = withContext(Dispatchers.IO) {
    try {
        val database = ActivityDatabase.getDatabase(context)
        val geofenceDao = database.geofenceDao()

        val slot = LocationSlot(
            name = name,
            icon = icon,
            color = color.toString(),
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            address = null,
            notifyOnEnter = notifyOnEnter,
            notifyOnExit = notifyOnExit
        )

        val slotId = geofenceDao.insertLocationSlot(slot)

        if (slot.isActive) {
            GeofenceManager.addGeofence(context, slot.copy(id = slotId))
        }
    } catch (e: Exception) {
        Log.e("LocationSlot", "Error saving location slot: ${e.message}")
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

