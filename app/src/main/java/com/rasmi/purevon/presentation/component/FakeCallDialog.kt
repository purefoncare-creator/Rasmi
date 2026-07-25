package com.rasmi.purevon.presentation.component

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.receiver.FakeCallReceiver
import com.rasmi.purevon.util.FakeCallScheduleManager
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "FakeCallDialog"

private val fakeCallRequestCounter = AtomicInteger(100000)

/**
 * حوار الاتصال الوهمي - يظهر عند الضغط المطول على زر النجمة
 * يسمح للمستخدم بإدخال اسم المتصل واختيار وقت الاتصال
 */
@Composable
fun FakeCallDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var callerName by remember { mutableStateOf("") }
    var selectedDelay by remember { mutableStateOf<FakeCallDelay?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    
    val isLightTheme = !isSystemInDarkTheme()
    
    // طلب Focus تلقائياً عند الفتح
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) Color.White 
                    else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // العنوان مع أيقونة الإغلاق
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(iOSGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = iOSGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.fake_call_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.fake_call_close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // حقل اسم المتصل
                OutlinedTextField(
                    value = callerName,
                    onValueChange = { callerName = it },
                    label = { Text(stringResource(R.string.fake_call_caller_name)) },
                    placeholder = { Text(stringResource(R.string.fake_call_caller_name_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = iOSGreen
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iOSGreen,
                        cursorColor = iOSGreen,
                        focusedLabelColor = iOSGreen
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // عنوان اختيار الوقت
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.fake_call_delay_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // شبكة خيارات الوقت 3×2
                val delays = FakeCallDelay.entries
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    delays.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { delay ->
                                DelayChip(
                                    delay = delay,
                                    isSelected = selectedDelay == delay,
                                    onClick = { selectedDelay = delay },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // زر البدء
                Button(
                    onClick = {
                        if (callerName.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.fake_call_name_required),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (selectedDelay == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.fake_call_delay_required),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        
                        val isExact = scheduleFakeCall(context, callerName.trim(), selectedDelay!!)
                        onDismiss()
                        
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.fake_call_scheduled,
                                selectedDelay!!.getDisplayText(context)
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // ✅ تحذير Android 12+ عند عدم دقة التوقيت
                        if (!isExact) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.fake_call_inexact_alarm_warning),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = callerName.isNotBlank() && selectedDelay != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iOSGreen,
                        disabledContainerColor = iOSGreen.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.fake_call_start),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * شريحة اختيار التأخير الزمني
 */
@Composable
private fun DelayChip(
    delay: FakeCallDelay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> iOSGreen.copy(alpha = 0.15f)
            isLightTheme -> WireOtherBubbleLight
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, iOSGreen)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = delay.getDisplayText(LocalContext.current),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) iOSGreen 
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * خيارات التأخير الزمني للاتصال الوهمي
 */
enum class FakeCallDelay(val seconds: Long) {
    SECONDS_30(30),
    MINUTE_1(60),
    MINUTES_2(120),
    MINUTES_5(300),
    MINUTES_10(600),
    MINUTES_30(1800);
    
    fun getDisplayText(context: Context): String {
        return when (this) {
            SECONDS_30 -> context.getString(R.string.fake_call_delay_30s)
            MINUTE_1 -> context.getString(R.string.fake_call_delay_1m)
            MINUTES_2 -> context.getString(R.string.fake_call_delay_2m)
            MINUTES_5 -> context.getString(R.string.fake_call_delay_5m)
            MINUTES_10 -> context.getString(R.string.fake_call_delay_10m)
            MINUTES_30 -> context.getString(R.string.fake_call_delay_30m)
        }
    }
}

/**
 * جدولة الاتصال الوهمي باستخدام AlarmManager
 * تعيد الدالة true إذا كان التوقيت دقيقاً ، false إذا كان غير دقيق (Android 12+)
 */
private fun scheduleFakeCall(
    context: Context,
    callerName: String,
    delay: FakeCallDelay
): Boolean {
    return try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = fakeCallRequestCounter.incrementAndGet()
        val triggerTime = System.currentTimeMillis() + (delay.seconds * 1000L)

        val broadcastIntent = Intent(context, FakeCallReceiver::class.java).apply {
            putExtra(FakeCallReceiver.EXTRA_CALLER_NAME, callerName)
            putExtra(FakeCallReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        var isExact = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                isExact = false
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        // ✅ حفظ الاتصال في سجل الجدولة
        FakeCallScheduleManager.add(context, requestCode, callerName, triggerTime)
        
        Log.d(TAG, "Fake call scheduled: caller=$callerName, code=$requestCode, delay=${delay.seconds}s, trigger=$triggerTime, exact=$isExact")
        isExact
    } catch (e: Exception) {
        Log.e(TAG, "Failed to schedule fake call: ${e.message}")
        Toast.makeText(
            context,
            context.getString(R.string.fake_call_schedule_error),
            Toast.LENGTH_SHORT
        ).show()
        true // قيمة افتراضية عند الخطأ
    }
}
