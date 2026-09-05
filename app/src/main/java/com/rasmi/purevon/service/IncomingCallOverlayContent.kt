package com.rasmi.purevon.service

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.presentation.theme.iOSGreen
import com.rasmi.purevon.presentation.theme.iOSRed

/**
 * بطاقة علوية صغيرة تُعرض عند وصول مكالمة واردة أثناء استخدام التطبيق
 * (كما في ديالر أندرويد الأصلي) — بدون مغادرة الصفحة الحالية.
 */
@Composable
fun IncomingCallOverlayContent(
    phoneNumber: String,
    contactName: String?,
    contactPhotoUri: String?,
    isSilenced: Boolean = false,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onSilence: () -> Unit
) {
    val displayName = contactName ?: phoneNumber.ifBlank { stringResource(R.string.label_unknown) }

    PurevonTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 1f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        val pulse by rememberInfiniteTransition(label = "pulse")
                            .animateFloat(
                                initialValue = 1f,
                                targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = EaseInOut),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "avatar_scale"
                            )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(iOSGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!contactPhotoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(contactPhotoUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val initial = (contactName?.trim()?.firstOrNull()
                                    ?: phoneNumber.firstOrNull { it.isDigit() }?.toString()?.firstOrNull()
                                    ?: '?')
                                Text(
                                    text = initial.toString(),
                                    color = iOSGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.call_status_incoming),
                                fontSize = 12.sp,
                                color = iOSGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (phoneNumber.isNotBlank() && contactName != phoneNumber) {
                                Text(
                                    text = phoneNumber,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline
                        OverlayActionButton(
                            icon = { tint ->
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = stringResource(R.string.action_decline),
                            containerColor = iOSRed,
                            onClick = onReject,
                            modifier = Modifier.weight(1f)
                        )

                        // Answer
                        OverlayActionButton(
                            icon = { tint ->
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = stringResource(R.string.action_accept),
                            containerColor = iOSGreen,
                            onClick = onAnswer,
                            modifier = Modifier.weight(1f)
                        )

                        if (!isSilenced) {
                            // Silence
                            OverlayActionButton(
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = stringResource(R.string.incall_btn_silence),
                                containerColor = Color(0xFFF5A623),
                                onClick = onSilence,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayActionButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            icon(androidx.compose.ui.graphics.Color.White)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
