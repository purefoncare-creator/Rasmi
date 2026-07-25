package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.RepeatInterval
import com.rasmi.purevon.presentation.screen.conversation.EditScheduledMessageData
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.presentation.theme.*

/**
 * Dialog for editing a scheduled message.
 * Shows a text field for the message body and a button to change time/repeat via ScheduleMessageDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduledMessageDialog(
    data: EditScheduledMessageData,
    onDismiss: () -> Unit,
    onConfirm: (newBody: String, newScheduledTime: Long, newRepeatInterval: RepeatInterval) -> Unit
) {
    var editedBody by remember(data.scheduleId) { mutableStateOf(data.body) }
    var editedTime by remember(data.scheduleId) { mutableStateOf(data.scheduledTime) }
    var editedRepeat by remember(data.scheduleId) { mutableStateOf(data.repeatInterval) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Time picker sub-dialog
    if (showTimePicker) {
        ScheduleMessageDialog(
            onDismiss = { showTimePicker = false },
            onSchedule = { millis, repeat ->
                editedTime = millis
                editedRepeat = repeat
                showTimePicker = false
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.scheduled_message_edit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Message body editor
            OutlinedTextField(
                value = editedBody,
                onValueChange = { editedBody = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.conversation_message_hint)) },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp)
            )

            // Schedule time button
            val timeFormatted = remember(editedTime) {
                java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(editedTime))
            }
            val repeatLabel = when (editedRepeat) {
                RepeatInterval.NONE -> stringResource(R.string.schedule_repeat_never)
                RepeatInterval.DAILY -> stringResource(R.string.schedule_repeat_daily)
                RepeatInterval.WEEKLY -> stringResource(R.string.schedule_repeat_weekly)
                RepeatInterval.MONTHLY -> stringResource(R.string.schedule_repeat_monthly)
            }

            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("$timeFormatted • $repeatLabel")
            }

            // Confirm / Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.scheduled_message_cancel))
                }
                Button(
                    onClick = {
                        onConfirm(editedBody.trim(), editedTime, editedRepeat)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = editedBody.isNotBlank() && editedTime > System.currentTimeMillis(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScheduleBlue
                    )
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
