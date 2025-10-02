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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.scale
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
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
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
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

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val longitude: Double? = null
)

private const val ACTIVITY_COLOR_PREFS = "activity_color_preferences"

private fun saveActivityColorPreference(context: Context, activityType: String, color: Color) {
    context.getSharedPreferences(ACTIVITY_COLOR_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(activityType, color.toArgb())
        .apply()
}

private fun applySavedColors(context: Context, slots: List<ActivityTimeSlot>): List<ActivityTimeSlot> {
    if (slots.isEmpty()) return slots
    val preferences = context.getSharedPreferences(ACTIVITY_COLOR_PREFS, Context.MODE_PRIVATE)
    return slots.map { slot ->
        if (preferences.contains(slot.activityType)) {
            slot.copy(color = Color(preferences.getInt(slot.activityType, 0).toLong()))
        } else {
            slot
        }
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
        scope.launch {
            try {
                val slots = calculateActivityTimeSlots(dao, selectedDate)
                activityTimeSlots = applySavedColors(context, slots)
            } catch (e: Exception) {
                Log.e("DatabaseViewer", "Error loading data: ${e.message}")
                activityTimeSlots = emptyList()
            }
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
                saveActivityColorPreference(context, slot.activityType, newColor)
                activityTimeSlots = activityTimeSlots.map {
                    if (it.activityType == slot.activityType) {
                        it.copy(color = newColor)
                    } else {
                        it
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
            var startAngle = -90f

            data.forEach { slot ->
                val calendar = Calendar.getInstance()
                calendar.time = slot.startTime
                val startHour = calendar.get(Calendar.HOUR_OF_DAY)
                val startMinute = calendar.get(Calendar.MINUTE)
                val startMinutes = startHour * 60 + startMinute

                calendar.time = slot.endTime
                val endHour = calendar.get(Calendar.HOUR_OF_DAY)
                val endMinute = calendar.get(Calendar.MINUTE)
                var endMinutes = endHour * 60 + endMinute
                if (endMinutes < startMinutes) endMinutes += 24 * 60

                val sweepAngle = ((endMinutes - startMinutes) / (24f * 60f)) * 360f
                val slotStartAngle = (startMinutes / (24f * 60f)) * 360f - 90f

                if (sweepAngle > 0.5f) {
                    drawArc(
                        color = slot.color,
                        startAngle = slotStartAngle,
                        sweepAngle = sweepAngle,
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
    val stillLocations = dao.getStillLocationsBetween(startDate, endDate)
    val movementActivities = dao.getMovementActivitiesBetween(startDate, endDate)

    val colors = mapOf(
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
        val duration = location.duration ?: 0L
        val endTime = Date(location.timestamp.time + duration)

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = location.timestamp,
                endTime = endTime,
                durationMillis = duration,
                color = colors[activityType] ?: colors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = location.latitude,
                longitude = location.longitude
            )
        )
    }

    movementActivities.forEach { activity ->
        val activityType = if (activity.actuallyMoved) {
            activity.activityType
        } else {
            "Still (${activity.activityType})"
        }

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = activity.startTime,
                endTime = activity.endTime,
                durationMillis = activity.endTime.time - activity.startTime.time,
                color = colors[activityType] ?: colors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = activity.startLatitude,
                longitude = activity.startLongitude
            )
        )
    }

    return allSlots.sortedBy { it.startTime }
}

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

    val segmentBounds = remember { mutableStateListOf<SegmentBounds>() }

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

    Canvas(
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
        val canvasSize = size.minDimension
        val radius = canvasSize / 2f * 0.85f
        val innerRadius = radius * 0.55f
        val center = Offset(size.width / 2f, size.height / 2f)

        segmentBounds.clear()

        val totalMinutes = data.sumOf { it.durationMillis } / (1000 * 60)
        val totalHours = totalMinutes / 60
        val remainingMinutes = totalMinutes % 60

        val strokeWidth = radius - innerRadius
        val middleRadius = (radius + innerRadius) / 2f
        val minDisplayAngle = 8f

        data class VisualSegment(
            val slot: ActivityTimeSlot,
            val startAngle: Float,
            val sweepAngle: Float,
            val displayAngle: Float
        )

        val visualSegments = mutableListOf<VisualSegment>()
        var accumulatedAngle = 0f

        data.forEach { slot ->
            val calendar = Calendar.getInstance()
            calendar.time = slot.startTime
            val startHour = calendar.get(Calendar.HOUR_OF_DAY)
            val startMinute = calendar.get(Calendar.MINUTE)
            val startMinutes = startHour * 60 + startMinute

            calendar.time = slot.endTime
            val endHour = calendar.get(Calendar.HOUR_OF_DAY)
            val endMinute = calendar.get(Calendar.MINUTE)
            var endMinutes = endHour * 60 + endMinute
            if (endMinutes < startMinutes) endMinutes += 24 * 60

            val sweepAngle = ((endMinutes - startMinutes) / (24f * 60f)) * 360f
            val startAngle = (startMinutes / (24f * 60f)) * 360f - 90f

            if (sweepAngle > 0.5f) {
                val displayAngle = if (slot.durationMillis >= 15 * 60 * 1000 && sweepAngle < minDisplayAngle) {
                    minDisplayAngle
                } else {
                    sweepAngle
                }

                visualSegments.add(
                    VisualSegment(slot, startAngle, sweepAngle, displayAngle)
                )
                accumulatedAngle += displayAngle
            }
        }

        if (accumulatedAngle > 360f && visualSegments.isNotEmpty()) {
            val scale = 360f / accumulatedAngle
            visualSegments.replaceAll { it.copy(displayAngle = it.displayAngle * scale) }
        }

        visualSegments.forEach { segment ->
            val animatedSweep = segment.sweepAngle * animationProgress

            if (animatedSweep > 0.5f) {
                val gapSize = if (animatedSweep > 2f) 0.5f else 0f

                drawArc(
                    color = segment.slot.color,
                    startAngle = segment.startAngle + gapSize,
                    sweepAngle = animatedSweep - (gapSize * 2),
                    useCenter = false,
                    topLeft = center - Offset(middleRadius, middleRadius),
                    size = Size(middleRadius * 2, middleRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    )
                )

                segmentBounds.add(
                    SegmentBounds(
                        slot = segment.slot,
                        startAngle = segment.startAngle + 90,
                        sweepAngle = segment.sweepAngle
                    )
                )

                if (segment.slot.durationMillis >= 15 * 60 * 1000 &&
                    animationProgress > 0.8f &&
                    segment.sweepAngle >= 6f) {

                    val midAngle = segment.startAngle + (segment.sweepAngle / 2f)
                    val midAngleRad = Math.toRadians(midAngle.toDouble())

                    val textX = center.x + (middleRadius * cos(midAngleRad)).toFloat()
                    val textY = center.y + (middleRadius * sin(midAngleRad)).toFloat()

                    val durationMinutes = segment.slot.durationMillis / (1000 * 60)
                    val durationText = when {
                        durationMinutes >= 60 -> "${durationMinutes / 60}h${if (durationMinutes % 60 > 0) "${durationMinutes % 60}m" else ""}"
                        else -> "${durationMinutes}m"
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        durationText,
                        textX,
                        textY + 6,
                        textPaint
                    )
                }
            }
        }

        // Center text
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
}

suspend fun calculateActivityTimeSlots(dao: ActivityDao, date: Date): List<ActivityTimeSlot> {
    val calendar = Calendar.getInstance()
    calendar.time = date
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.time

    calendar.add(Calendar.DAY_OF_MONTH, 1)
    val endOfDay = calendar.time

    val stillLocations = dao.getStillLocationsBetween(startOfDay, endOfDay)
    val movementActivities = dao.getMovementActivitiesBetween(startOfDay, endOfDay)

    val colors = mapOf(
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
        val duration = location.duration ?: 0L
        val endTime = Date(location.timestamp.time + duration)

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = location.timestamp,
                endTime = endTime,
                durationMillis = duration,
                color = colors[activityType] ?: colors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = location.latitude,
                longitude = location.longitude
            )
        )
    }

    movementActivities.forEach { activity ->
        val activityType = if (activity.actuallyMoved) {
            activity.activityType
        } else {
            "Still (${activity.activityType})"
        }

        allSlots.add(
            ActivityTimeSlot(
                activityType = activityType,
                startTime = activity.startTime,
                endTime = activity.endTime,
                durationMillis = activity.endTime.time - activity.startTime.time,
                color = colors[activityType] ?: colors.getOrDefault(
                    activityType.substringBefore(" ("),
                    Color(0xFF795548)
                ),
                latitude = activity.startLatitude,
                longitude = activity.startLongitude
            )
        )
    }

    val sortedSlots = allSlots.sortedBy { it.startTime }

    val mergedSlots = mutableListOf<ActivityTimeSlot>()
    sortedSlots.forEach { slot ->
        if (slot.durationMillis < 30000) return@forEach

        val lastSlot = mergedSlots.lastOrNull()
        if (lastSlot != null) {
            if (slot.startTime.before(lastSlot.endTime)) {
                if (slot.activityType == lastSlot.activityType) {
                    val newEndTime = if (slot.endTime.after(lastSlot.endTime)) slot.endTime else lastSlot.endTime
                    mergedSlots[mergedSlots.size - 1] = lastSlot.copy(
                        endTime = newEndTime,
                        durationMillis = newEndTime.time - lastSlot.startTime.time
                    )
                } else if (slot.endTime.time - slot.startTime.time > 60000) {
                    val adjustedStartTime = lastSlot.endTime
                    if (slot.endTime.after(adjustedStartTime)) {
                        mergedSlots.add(
                            slot.copy(
                                startTime = adjustedStartTime,
                                durationMillis = slot.endTime.time - adjustedStartTime.time
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

    return mergedSlots
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
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
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