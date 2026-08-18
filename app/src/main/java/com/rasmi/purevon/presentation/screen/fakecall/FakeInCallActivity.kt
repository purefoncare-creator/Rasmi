package com.rasmi.purevon.presentation.screen.fakecall

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * شاشة الاتصال الوهمي — مستقلة تماماً عن شاشة المكالمة الحقيقية
 *
 * المراحل:
 *  1. RINGING — يرن الهاتف مع حركات نابضة وزري "رد" و"رفض"
 *  2. ACTIVE  — عداد تصاعدي لمدة دقيقتين ثم ينتهي الاتصال تلقائياً
 */
@AndroidEntryPoint
class FakeInCallActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: com.rasmi.purevon.data.preferences.SettingsDataStore

    companion object {
        private const val TAG = "FakeInCallActivity"
        const val EXTRA_CALLER_NAME = "fake_caller_name"

        /** مدة الاتصال النشط بالثواني (دقيقتان) */
        internal const val ACTIVE_CALL_DURATION_SECONDS = 120
    }

    // ────── رنين واهتزاز ──────
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        configureWindowForFakeCall()
        super.onCreate(savedInstanceState)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: getString(R.string.unknown_caller)

        // رفض الضغط على "رجوع" — يجب استخدام زر الإنهاء
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* متجاهَل */ }
        })

        enableEdgeToEdge()

        startFakeRinging()

        setContent {
            val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = "system")
            val systemLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)
            val activeLanguage = if (appLanguage == "system") (systemLocale?.language ?: "en") else appLanguage
            val isRtl = activeLanguage == "ar" || activeLanguage == "fa" || activeLanguage == "ur" || activeLanguage == "he"
            
            PurevonTheme(darkTheme = true) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    FakeCallScreen(
                        callerName = callerName,
                        onAnswer = {
                            stopFakeRinging()
                        },
                        onReject = {
                            stopFakeRinging()
                            finish()
                        },
                        onCallEnded = {
                            stopFakeRinging()
                            finish()
                        }
                    )
                }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFakeRinging()
    }

    // ────── إعداد النافذة ──────
    @Suppress("DEPRECATION")
    private fun configureWindowForFakeCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    // ────── رنين واهتزاز ──────
    private val ringtoneRestartHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ringtoneRestartRunnable: Runnable? = null

    private fun startFakeRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                } else {
                    val rt = this
                    ringtoneRestartRunnable = object : Runnable {
                        override fun run() {
                            if (rt.isPlaying.not()) {
                                try { rt.play() } catch (_: Exception) {}
                            }
                            ringtoneRestartHandler.postDelayed(this, 2000)
                        }
                    }
                    ringtoneRestartHandler.postDelayed(ringtoneRestartRunnable!!, 3000)
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 400)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting fake ringtone: ${e.message}")
        }
    }

    private fun stopFakeRinging() {
        try {
            ringtone?.stop(); ringtone = null
            vibrator?.cancel(); vibrator = null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping fake ringtone: ${e.message}")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  واجهة الاتصال الوهمي (Compose)
// ═══════════════════════════════════════════════════════════════

private enum class FakePhase { RINGING, ACTIVE }

@Composable
private fun FakeCallScreen(
    callerName: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onCallEnded: () -> Unit,
) {
    var phase by remember { mutableStateOf(FakePhase.RINGING) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // ── عداد الاتصال النشط ──
    LaunchedEffect(phase) {
        if (phase == FakePhase.ACTIVE) {
            while (elapsedSeconds < FakeInCallActivity.ACTIVE_CALL_DURATION_SECONDS) {
                delay(1_000L)
                elapsedSeconds++
            }
            onCallEnded()
        }
    }

    // خلفية متدرجة داكنة
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(FakeCallDark, FakeCallDarkAlt, FakeCallDark)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── الجزء العلوي: معلومات المتصل ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))

                // حالة المكالمة
                Text(
                    text = if (phase == FakePhase.RINGING)
                        stringResource(R.string.fake_call_incoming)
                    else
                        formatElapsed(elapsedSeconds),
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // الصورة الرمزية مع تأثير النبض
                Box(contentAlignment = Alignment.Center) {
                    if (phase == FakePhase.RINGING) {
                        PulsingRings(ringColor = iOSGreen)
                    }
                    CallerAvatar(name = callerName, size = 96.dp)
                }

                Spacer(Modifier.height(24.dp))

                // اسم المتصل
                Text(
                    text = callerName,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            // ── الجزء السفلي: أزرار التحكم ──
            if (phase == FakePhase.RINGING) {
                RingingControls(
                    onAnswer = {
                        onAnswer()
                        phase = FakePhase.ACTIVE
                    },
                    onReject = onReject
                )
            } else {
                ActiveCallControls(onEndCall = onCallEnded)
            }
        }
    }
}

// ── حلقات النبض ──
@Composable
private fun PulsingRings(ringColor: Color) {
    val rings = listOf(0, 400, 800)          // تأخير بالمللي ثانية لكل حلقة
    rings.forEach { delayMs ->
        val transition = rememberInfiniteTransition(label = "pulse_$delayMs")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, delayMillis = delayMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale_$delayMs"
        )
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, delayMillis = delayMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha_$delayMs"
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.25f))
        )
    }
}

// ── صورة الاسم الرمزية ──
@Composable
private fun CallerAvatar(name: String, size: Dp) {
    com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
        size = size
    )
}

// ── أزرار الرنين ──
@Composable
private fun RingingControls(onAnswer: () -> Unit, onReject: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(80.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        // زر الرفض
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onReject,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = iOSRed
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Reject",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.fake_call_decline),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }

        // زر الرد
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onAnswer,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = iOSGreen
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Answer",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.fake_call_answer),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
    }
}

// ── أزرار المكالمة النشطة ──
@Composable
private fun ActiveCallControls(onEndCall: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        IconButton(
            onClick = onEndCall,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = iOSRed
            )
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "End Call",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.incall_end_call),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
    }
}

// ── تنسيق الوقت MM:SS ──
private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
