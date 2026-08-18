package com.rasmi.purevon.service

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import com.rasmi.purevon.presentation.theme.CornerRadius
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.presentation.theme.iOSGreen
import com.rasmi.purevon.presentation.theme.iOSRed

@Composable
fun BlockedCallBubbleContent(
    phoneNumber: String,
    contactName: String?,
    contactPhotoUri: String?,
    onCall: () -> Unit,
    onAddToWhitelist: () -> Unit,
    onDismiss: () -> Unit
) {
    var isPulsing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        isPulsing = false
    }

    val displayName = contactName ?: phoneNumber.ifBlank { stringResource(R.string.label_unknown) }
    val hasNumber = phoneNumber.isNotBlank()

    PurevonTheme {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Card(
                modifier = Modifier
                    .wrapContentWidth()
                    .shadow(16.dp, RoundedCornerShape(CornerRadius.ExtraLarge)),
                shape = RoundedCornerShape(CornerRadius.ExtraLarge),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isPulsing) 1.05f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(CornerRadius.ExtraLarge))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = iOSRed.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(CornerRadius.ExtraLarge)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    ) {
                        // Avatar: contact photo or initials fallback
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(iOSRed.copy(alpha = 0.18f)),
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
                                    color = iOSRed,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = iOSRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.blocked_call_bubble_content_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = displayName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasNumber) {
                                Text(
                                    text = phoneNumber,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (hasNumber) {
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onCall,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = iOSGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.blocked_call_bubble_call), fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onAddToWhitelist,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.blocked_call_bubble_whitelist), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Dismiss
                    Box(
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = stringResource(R.string.blocked_call_bubble_dismiss),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
