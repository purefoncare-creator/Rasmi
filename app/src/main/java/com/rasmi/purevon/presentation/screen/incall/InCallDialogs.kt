package com.rasmi.purevon.presentation.screen.incall

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.DarkSurfaceVariant
import com.rasmi.purevon.presentation.theme.iOSGreen

/**
 * ✅ حوار تذكير إعادة الاتصال
 */
@Composable
internal fun CallbackReminderDialog(
    onDismiss: () -> Unit,
    onSetReminder: (Int) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(10) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.callback_reminder_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.callback_select_duration))
                
                listOf(5, 10, 15, 30, 60).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMinutes = minutes }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (minutes >= 60) 
                                stringResource(R.string.callback_hour, minutes/60)
                            else 
                                stringResource(R.string.callback_minutes, minutes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // ✅ فقط نستدعي onSetReminder — هو يغلق الحوار في ViewModel
                onSetReminder(selectedMinutes)
            }) {
                Text(stringResource(R.string.callback_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Quick message dialog for incoming/active calls
 */
@Composable
internal fun QuickMessageDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onSelectMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showCustomMessageInput by remember { mutableStateOf(false) }
    var customMessage by remember { mutableStateOf("") }
    
    val quickMessages = listOf(
        stringResource(R.string.quick_reply_call_back),
        stringResource(R.string.quick_reply_in_meeting),
        stringResource(R.string.quick_reply_cant_talk),
        stringResource(R.string.quick_reply_on_my_way),
        stringResource(R.string.quick_reply_give_me_5)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.quick_reply_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                if (!showCustomMessageInput) {
                    // ✅ Show predefined messages
                    quickMessages.forEach { message ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // Send SMS using in-app functionality
                                    try {
                                        val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                                            putExtra("navigate_to", "new_conversation")
                                            putExtra("phone_number", phoneNumber)
                                            putExtra("quick_message", message)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("QuickMessage", "Error opening conversation", e)
                                    }
                                    onSelectMessage(message)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant.copy(alpha = 0.8f) // ✅ Dark surface
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    // ✅ Custom message button
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomMessageInput = true },
                        shape = RoundedCornerShape(8.dp),
                        color = iOSGreen.copy(alpha = 0.15f), // ✅ iPhone green
                        border = BorderStroke(1.dp, iOSGreen) // ✅ iPhone green border
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = iOSGreen, // ✅ iPhone green
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.quick_reply_custom),
                                style = MaterialTheme.typography.bodyMedium,
                                color = iOSGreen, // ✅ iPhone green
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // ✅ Show custom message input
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.quick_reply_type_hint)) },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (showCustomMessageInput && customMessage.isNotBlank()) {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                                putExtra("navigate_to", "new_conversation")
                                putExtra("phone_number", phoneNumber)
                                putExtra("quick_message", customMessage)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("QuickMessage", "Error opening conversation", e)
                        }
                        onSelectMessage(customMessage)
                    }
                ) {
                    Text(stringResource(R.string.quick_reply_send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (showCustomMessageInput) {
                        showCustomMessageInput = false
                        customMessage = ""
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (showCustomMessageInput) stringResource(R.string.quick_reply_back) else stringResource(R.string.cancel))
            }
        }
    )
}
