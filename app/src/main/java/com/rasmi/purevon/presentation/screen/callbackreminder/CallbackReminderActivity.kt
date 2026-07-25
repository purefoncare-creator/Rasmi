package com.rasmi.purevon.presentation.screen.callbackreminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rasmi.purevon.util.sim.SimCallRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*

/**
 * نافذة منبثقة للتذكير بإعادة الاتصال
 * تظهر فوق جميع التطبيقات عند حلول موعد التذكير
 */
@AndroidEntryPoint
class CallbackReminderActivity : AppCompatActivity() {
    
    @Inject lateinit var simCallRouter: SimCallRouter
    @Inject lateinit var settingsDataStore: com.rasmi.purevon.data.preferences.SettingsDataStore
    
    private var resolvedSubscriptionId: Int? = null
    
    companion object {
        private const val EXTRA_PHONE_NUMBER = "phone_number"
        private const val EXTRA_CONTACT_NAME = "contact_name"
        
        fun createIntent(context: Context, phoneNumber: String, contactName: String?): Intent {
            return Intent(context, CallbackReminderActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // إظهار النافذة فوق شاشة القفل وجميع التطبيقات
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // حل شريحة الاتصال الافتراضية من إعدادات التطبيق
        lifecycleScope.launch {
            resolvedSubscriptionId = simCallRouter.resolveSubscriptionId()
        }

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        
        setContent {
            val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = "system")
            val systemLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)
            val activeLanguage = if (appLanguage == "system") (systemLocale?.language ?: "en") else appLanguage
            val isRtl = activeLanguage == "ar" || activeLanguage == "fa" || activeLanguage == "ur" || activeLanguage == "he"
            
            PurevonTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                    CallbackReminderScreen(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        onCallNow = {
                            makeCall(phoneNumber)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }
    
    private fun makeCall(phoneNumber: String) {
        val subId = resolvedSubscriptionId ?: run {
            try {
                runBlocking { simCallRouter.resolveSubscriptionId() }
            } catch (_: Exception) { null }
        }
        com.rasmi.purevon.util.PhoneUtil.playClickSound(this)
        com.rasmi.purevon.util.PhoneUtil.makeCall(this, phoneNumber, subId)
    }
}

@Composable
private fun CallbackReminderScreen(
    phoneNumber: String,
    contactName: String?,
    onCallNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(
                    2.dp,
                    if (isLightTheme) LightBorder else iOSGreen,
                    RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // أيقونة التذكير
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(iOSGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = iOSGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // العنوان
                Text(
                    text = stringResource(R.string.callback_reminder_alert_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // معلومات جهة الاتصال
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = contactName ?: stringResource(R.string.label_unknown),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // الأزرار
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // زر الإلغاء
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = iOSRed
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.callback_dismiss),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    // زر الاتصال
                    Button(
                        onClick = onCallNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = iOSGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.callback_call_now),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
