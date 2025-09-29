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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import com.example.myapplication.ui.theme.MyApplicationTheme
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
    val color: Color
)

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
                activityTimeSlots = calculateActivityTimeSlots(dao, selectedDate)
            } catch (e: Exception) {
                Log.e("DatabaseViewer", "Error loading data: ${e.message}")
                activityTimeSlots = emptyList()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        TopAppBar(
            title = { Text("24-Hour Activity Clock") }
        )

        // Pie chart content
        DailyActivityPieChartWithNavigation(
            activityTimeSlots = activityTimeSlots,
            selectedDate = selectedDate,
            onDateChange = { newDate ->
                selectedDate = newDate
                scope.launch {
                    activityTimeSlots = calculateActivityTimeSlots(dao, newDate)
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
    context: Context
) {
    val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<ActivityTimeSlot?>(null) }
    var showLocationSlotDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Date Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showDatePicker = true }
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
                        text = "Selected Date",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(selectedDate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Select Date",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tap a segment to create a location slot",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Clock Labels and Pie Chart Container
        Box(
            modifier = Modifier
                .size(340.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Time labels around the clock
            Text(
                text = "00:00",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = (-15).dp)
            )
            Text(
                text = "06:00",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(x = 15.dp)
            )
            Text(
                text = "12:00",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .offset(y = 15.dp)
            )
            Text(
                text = "18:00",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = (-15).dp)
            )

            // The actual pie chart with click handler
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
            onCreateSlot = {
                showLocationSlotDialog = false
                selectedSlot = null
            }
        )
    }
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
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }

    // Store segment bounds for interaction
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
                        offset.y - centerY,
                        offset.x - centerX
                    ) * 180 / kotlin.math.PI - 90

                    if (clickAngle < 0) clickAngle += 360

                    segmentBounds.forEach { bounds ->
                        val endAngle = bounds.startAngle + bounds.sweepAngle
                        val inSegment = if (bounds.startAngle <= endAngle) {
                            clickAngle >= bounds.startAngle && clickAngle <= endAngle
                        } else {
                            clickAngle >= bounds.startAngle || clickAngle <= endAngle
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

        // Clear bounds
        segmentBounds.clear()

        // Background
        drawCircle(
            color = Color(0xFFF5F5F5),
            radius = radius * 1.05f,
            center = center
        )

        // Calculate total time
        val totalMinutes = data.sumOf { it.durationMillis } / (1000 * 60)
        val totalHours = totalMinutes / 60
        val remainingMinutes = totalMinutes % 60

        // Draw segments - SIMPLIFIED APPROACH
        val strokeWidth = radius - innerRadius
        val middleRadius = (radius + innerRadius) / 2f
        val minDisplayAngle = 8f // Minimum angle for text display

        // Process segments with minimum visual size
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

            // Only include if sweep angle is meaningful
            if (sweepAngle > 0.5f) {
                val displayAngle = if (slot.durationMillis >= 15 * 60 * 1000 && sweepAngle < minDisplayAngle) {
                    minDisplayAngle
                } else {
                    sweepAngle
                }

                visualSegments.add(
                    VisualSegment(
                        slot = slot,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        displayAngle = displayAngle
                    )
                )
                accumulatedAngle += displayAngle
            }
        }

        // Scale display angles if needed
        if (accumulatedAngle > 360f && visualSegments.isNotEmpty()) {
            val scale = 360f / accumulatedAngle
            visualSegments.replaceAll { it.copy(displayAngle = it.displayAngle * scale) }
        }

        // Draw each segment
        var currentAngle = -90f
        visualSegments.forEach { segment ->
            val animatedSweep = segment.displayAngle * animationProgress

            if (animatedSweep > 0.5f) {
                // Add small gap
                val gapSize = if (animatedSweep > 2f) 0.5f else 0f

                // Draw using simple arc
                drawArc(
                    color = segment.slot.color,
                    startAngle = currentAngle + gapSize,
                    sweepAngle = animatedSweep - (gapSize * 2),
                    useCenter = false,
                    topLeft = center - Offset(middleRadius, middleRadius),
                    size = Size(middleRadius * 2, middleRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    )
                )

                // Store bounds for click detection
                segmentBounds.add(
                    SegmentBounds(
                        slot = segment.slot,
                        startAngle = if (currentAngle < 0) currentAngle + 360 else currentAngle,
                        sweepAngle = segment.displayAngle
                    )
                )

                // Draw text for 15+ minute segments
                if (segment.slot.durationMillis >= 15 * 60 * 1000 &&
                    animationProgress > 0.8f &&
                    segment.displayAngle >= 6f) {

                    val midAngle = currentAngle + (segment.displayAngle / 2f)
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

                currentAngle += segment.displayAngle
            }
        }

        // Center circle
        drawCircle(
            color = Color.White,
            radius = innerRadius * 0.9f,
            center = center
        )

        // Center text
        val centerTextPaint = android.graphics.Paint().apply {
            textSize = 42f
            color = android.graphics.Color.BLACK
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
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
                color = android.graphics.Color.rgb(100, 100, 100)
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

    // Get all activities for the day
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

    // Process still locations
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
                )
            )
        )
    }

    // Process movement activities
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
                )
            )
        )
    }

    // Sort by start time
    val sortedSlots = allSlots.sortedBy { it.startTime }

    // CRITICAL: Merge overlapping segments and remove duplicates
    val mergedSlots = mutableListOf<ActivityTimeSlot>()
    sortedSlots.forEach { slot ->
        // Skip very small segments (less than 30 seconds)
        if (slot.durationMillis < 30000) return@forEach

        val lastSlot = mergedSlots.lastOrNull()
        if (lastSlot != null) {
            // Check for overlap
            if (slot.startTime.before(lastSlot.endTime)) {
                // If same activity type, extend the previous slot
                if (slot.activityType == lastSlot.activityType) {
                    val newEndTime = if (slot.endTime.after(lastSlot.endTime)) slot.endTime else lastSlot.endTime
                    mergedSlots[mergedSlots.size - 1] = lastSlot.copy(
                        endTime = newEndTime,
                        durationMillis = newEndTime.time - lastSlot.startTime.time
                    )
                } else if (slot.endTime.time - slot.startTime.time > 60000) {
                    // Different activity, only add if it's significant (> 1 minute)
                    // Trim the start to not overlap
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
                // No overlap, add as is (if significant duration)
                if (slot.durationMillis >= 60000) { // At least 1 minute
                    mergedSlots.add(slot)
                }
            }
        } else {
            // First slot, add if significant
            if (slot.durationMillis >= 60000) {
                mergedSlots.add(slot)
            }
        }
    }

    return mergedSlots
}

@Composable
fun ActivityLegendItem(slot: ActivityTimeSlot) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(slot.color, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = slot.activityType,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${timeFormat.format(slot.startTime)} - ${timeFormat.format(slot.endTime)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatDuration(slot.durationMillis),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
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
    onCreateSlot: () -> Unit,
    anchorPosition: Offset? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // State for customization options
    var slotName by remember { mutableStateOf(activitySlot.activityType) }
    var selectedIcon by remember { mutableStateOf(getDefaultIconForActivity(activitySlot.activityType)) }
    var selectedColor by remember { mutableStateOf(activitySlot.color) }
    var radius by remember { mutableStateOf(100f) }
    var notifyOnEnter by remember { mutableStateOf(true) }
    var notifyOnExit by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Available icons (compact set)
    val icons = listOf(
        "home" to Icons.Default.Home,
        "work" to Icons.Default.Work,
        "gym" to Icons.Default.FitnessCenter,
        "food" to Icons.Default.Restaurant,
        "shop" to Icons.Default.ShoppingCart,
        "star" to Icons.Default.Star
    )

    // Available colors (compact set)
    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4)  // Cyan
    )

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 280.dp, max = 320.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            // Compact Header
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
            Spacer(modifier = Modifier.height(12.dp))

            // Compact name input
            OutlinedTextField(
                value = slotName,
                onValueChange = { slotName = it },
                label = { Text("Name", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Compact icon row
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

            // Compact color row
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
                            .clickable { selectedColor = color }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact radius control
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

            // Compact notification toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = notifyOnEnter,
                        onCheckedChange = { notifyOnEnter = it },
                        modifier = Modifier.scale(0.8f),
                        colors = CheckboxDefaults.colors(checkedColor = selectedColor)
                    )
                    Text("Entry", fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = notifyOnExit,
                        onCheckedChange = { notifyOnExit = it },
                        modifier = Modifier.scale(0.8f),
                        colors = CheckboxDefaults.colors(checkedColor = selectedColor)
                    )
                    Text("Exit", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (slotName.isNotBlank()) {
                            isSaving = true
                            scope.launch {
                                saveLocationSlot(
                                    context = context,
                                    name = slotName,
                                    icon = selectedIcon,
                                    color = selectedColor.toArgb(),
                                    radius = radius,
                                    notifyOnEnter = notifyOnEnter,
                                    notifyOnExit = notifyOnExit,
                                    latitude = 0.0, // You'll need to get current location
                                    longitude = 0.0  // You'll need to get current location
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Location slot created: $slotName",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onCreateSlot()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    enabled = slotName.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = selectedColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 13.sp)
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

        // Get current location if available (you'll need to implement this)
        // For now using placeholder coordinates

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

        // Add geofence
        if (slot.isActive) {
            GeofenceManager.addGeofence(context, slot.copy(id = slotId))
        }
    } catch (e: Exception) {
        Log.e("LocationSlot", "Error saving location slot: ${e.message}")
    }
}

fun getIconForActivity(activityType: String): ImageVector {
    return when (activityType) {
        "Still" -> Icons.Default.Home
        "Walking" -> Icons.AutoMirrored.Filled.DirectionsWalk
        "Running" -> Icons.AutoMirrored.Filled.DirectionsRun
        "Driving" -> Icons.Default.DirectionsCar
        "Cycling" -> Icons.AutoMirrored.Filled.DirectionsBike
        "On Foot" -> Icons.AutoMirrored.Filled.DirectionsWalk
        else -> Icons.AutoMirrored.Filled.Help
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