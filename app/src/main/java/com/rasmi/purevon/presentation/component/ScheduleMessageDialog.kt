package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.RepeatInterval
import java.text.SimpleDateFormat
import java.util.*
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.presentation.theme.*

/**
 * Schedule Message Dialog — scroll-wheel picker style
 *
 * Three side-by-side vertical scroll pickers:
 *   • Day   (today, tomorrow, …14 days)
 *   • Hour  (00-23)
 *   • Minute (00-59)
 *
 * Repeat chip (cycles NONE → DAILY → WEEKLY → MONTHLY) + confirm button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMessageDialog(
    onDismiss: () -> Unit,
    onSchedule: (Long, RepeatInterval) -> Unit
) {
    // ── state ────────────────────────────────────────────────
    val now = remember { Calendar.getInstance() }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)

    // Resolve locale-aware strings in composable scope
    val todayLabel = stringResource(R.string.schedule_today)
    val sendAtFormat = stringResource(R.string.schedule_send_at)

    // Day labels for next 14 days
    val dayItems = remember(todayLabel) {
        val fmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        (0 until 14).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, offset) }
            val label = if (offset == 0) todayLabel else fmt.format(cal.time)
            DayItem(label = label, offset = offset)
        }
    }

    var selectedDayIndex  by remember { mutableIntStateOf(0) }
    var selectedHour      by remember { mutableIntStateOf((currentHour + 1) % 24) }
    var selectedMinute    by remember { mutableIntStateOf(0) }
    var selectedRepeat    by remember { mutableStateOf(RepeatInterval.NONE) }

    // derived scheduled millis
    val scheduledMillis by remember(selectedDayIndex, selectedHour, selectedMinute) {
        derivedStateOf {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, dayItems[selectedDayIndex].offset)
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    // Past-time validation — disable confirm when scheduled time ≤ now
    val isInPast by remember(scheduledMillis) {
        derivedStateOf { scheduledMillis <= System.currentTimeMillis() }
    }

    // Confirm label — e.g. "Send Today at 03:00"
    val confirmLabel by remember(scheduledMillis, selectedDayIndex) {
        derivedStateOf {
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeStr = timeFmt.format(Date(scheduledMillis))
            val dayLabel = dayItems[selectedDayIndex].label
            String.format(sendAtFormat, dayLabel, timeStr)
        }
    }

    // Repeat chip label
    val repeatLabel = when (selectedRepeat) {
        RepeatInterval.NONE    -> stringResource(R.string.schedule_repeat_never)
        RepeatInterval.DAILY   -> stringResource(R.string.schedule_repeat_daily)
        RepeatInterval.WEEKLY  -> stringResource(R.string.schedule_repeat_weekly)
        RepeatInterval.MONTHLY -> stringResource(R.string.schedule_repeat_monthly)
    }

    // ── bottom-sheet style dialog ────────────────────────────
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Title ───────────────────────────────────────
            Text(
                text = stringResource(R.string.schedule_message_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )

            // ── Scroll Pickers ──────────────────────────────
            val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // blue selection band behind the center row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.Center)
                        .background(highlightColor, RoundedCornerShape(12.dp))
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day picker (weight 2)
                    WheelPicker(
                        items = dayItems.map { it.label },
                        initialIndex = selectedDayIndex,
                        onSelectedChanged = { selectedDayIndex = it },
                        modifier = Modifier.weight(2f),
                        fadeColor = surfaceColor
                    )

                    // Hour picker
                    WheelPicker(
                        items = (0..23).map { "%02d".format(it) },
                        initialIndex = selectedHour,
                        onSelectedChanged = { selectedHour = it },
                        modifier = Modifier.weight(1f),
                        fadeColor = surfaceColor
                    )

                    // Minute picker
                    WheelPicker(
                        items = (0..59).map { "%02d".format(it) },
                        initialIndex = selectedMinute,
                        onSelectedChanged = { selectedMinute = it },
                        modifier = Modifier.weight(1f),
                        fadeColor = surfaceColor
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Repeat chip (cycles through intervals) ──────
            AssistChip(
                onClick = {
                    selectedRepeat = when (selectedRepeat) {
                        RepeatInterval.NONE    -> RepeatInterval.DAILY
                        RepeatInterval.DAILY   -> RepeatInterval.WEEKLY
                        RepeatInterval.WEEKLY  -> RepeatInterval.MONTHLY
                        RepeatInterval.MONTHLY -> RepeatInterval.NONE
                    }
                },
                label = {
                    Text(
                        repeatLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Confirm button ──────────────────────────────
            Button(
                onClick = { onSchedule(scheduledMillis, selectedRepeat) },
                enabled = !isInPast,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScheduleBlue
                )
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Reusable scroll-wheel picker
// ═══════════════════════════════════════════════════════════════

private const val WHEEL_VISIBLE_ITEMS = 5          // visible rows (odd for center symmetry)
private val WHEEL_ITEM_HEIGHT = 48.dp

@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelectedChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fadeColor: Color = Color.Transparent
) {
    val halfVisible = WHEEL_VISIBLE_ITEMS / 2       // padding rows above & below
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Report selected index after scroll settles
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val selected = listState.firstVisibleItemIndex.coerceIn(0, items.size - 1)
            onSelectedChanged(selected)
        }
    }

    // Scroll to initial position
    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex)
    }

    Box(modifier = modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(fadeColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT * halfVisible)
        ) {
            items(items.size) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    val isCenter = index == listState.firstVisibleItemIndex

                    Text(
                        text = items[index],
                        textAlign = TextAlign.Center,
                        fontSize = if (isCenter) 20.sp else 15.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── fade modifier ───────────────────────────────────────────
private fun Modifier.fadingEdge(color: Color): Modifier = this.drawWithContent {
    drawContent()
    // top fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color, Color.Transparent),
            startY = 0f,
            endY = size.height * 0.25f
        )
    )
    // bottom fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color),
            startY = size.height * 0.75f,
            endY = size.height
        )
    )
}

// ── helpers ─────────────────────────────────────────────────
private data class DayItem(val label: String, val offset: Int)
