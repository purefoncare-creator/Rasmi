package com.rasmi.purevon.presentation.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.presentation.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * iOS-style Message Bubble with action buttons
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onFavoriteClick: ((Message) -> Unit)? = null,
    onRetry: () -> Unit = {},
    onForward: ((Message) -> Unit)? = null, // ✅ P2: Forward callback
    onImageClick: ((List<String>, Int) -> Unit)? = null, // ✅ Image viewer callback
    onCancelScheduled: ((Long) -> Unit)? = null, // ✅ Cancel scheduled message
    onEditScheduled: ((Message) -> Unit)? = null // ✅ Fix #14: Edit scheduled message
) {
    // Outgoing: SENT, OUTBOX, FAILED, QUEUED
    val isOutgoing = message.type in listOf(
        com.rasmi.purevon.data.local.entity.MessageType.SENT.value,
        com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value,
        com.rasmi.purevon.data.local.entity.MessageType.FAILED.value,
        com.rasmi.purevon.data.local.entity.MessageType.QUEUED.value
    )
    var isFavorite by remember(message.id, message.isStarred) { mutableStateOf(message.isStarred) }

    val cardShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isOutgoing) 18.dp else 6.dp,
        bottomEnd = if (isOutgoing) 6.dp else 18.dp
    )
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = cardShape,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isOutgoing) 2.dp else 1.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = bubbleColor
            )
        ) {
            CardMessageBody(
                message = message,
                isOutgoing = isOutgoing,
                isFavorite = isFavorite,
                onFavoriteChange = {
                    isFavorite = it
                    onFavoriteClick?.invoke(message)
                },
                onRetry = onRetry,
                onForward = onForward,
                onImageClick = onImageClick,
                onCancelScheduled = onCancelScheduled,
                onEditScheduled = onEditScheduled
            )
        }

        // ── Spam indicator ──
        if (message.isSpam) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpamHigh.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.msg_spam),
                    modifier = Modifier.size(10.dp),
                    tint = SpamHigh
                )
                Text(
                    text = stringResource(R.string.msg_spam),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpamHigh,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * ✅ Internal card body — shared between incoming (with accent bar) and outgoing cards.
 */
@Composable
private fun CardMessageBody(
    message: Message,
    isOutgoing: Boolean,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onForward: ((Message) -> Unit)?,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onCancelScheduled: ((Long) -> Unit)? = null,
    onEditScheduled: ((Message) -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
                        // Show MMS attachments if present
                        if (message.isMms && message.attachmentUris.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                // ✅ Pre-compute image URIs once (was O(n²) inside loop)
                                val allImageUris = message.attachmentUris.filterIndexed { i, _ ->
                                    val mt = message.attachmentTypes.getOrNull(i)
                                    val lu = message.attachmentUris[i].lowercase()
                                    mt?.startsWith("image/") == true ||
                                            lu.endsWith(".jpg") || lu.endsWith(".jpeg") ||
                                            lu.endsWith(".png") || lu.endsWith(".gif") || lu.endsWith(".webp")
                                }
                                
                                message.attachmentUris.forEachIndexed { index, uri ->
                                    val mimeType = message.attachmentTypes.getOrNull(index)
                                    val lowerUri = uri.lowercase()
                                    
                                    // Check if it's a video file
                                    val isVideo = mimeType?.startsWith("video/") == true ||
                                            lowerUri.endsWith(".mp4") ||
                                            lowerUri.endsWith(".3gp") ||
                                            lowerUri.endsWith(".mkv") ||
                                            lowerUri.endsWith(".mov") ||
                                            lowerUri.endsWith(".avi") ||
                                            lowerUri.endsWith(".webm") ||
                                            mimeType?.contains("video") == true
                                    
                                    // Check if it's an audio file by MIME type or file extension
                                    val isAudio = mimeType?.startsWith("audio/") == true ||
                                            lowerUri.endsWith(".mp3") ||
                                            lowerUri.endsWith(".m4a") ||
                                            lowerUri.endsWith(".aac") ||
                                            lowerUri.endsWith(".wav") ||
                                            lowerUri.endsWith(".ogg") ||
                                            lowerUri.endsWith(".amr") ||
                                            mimeType?.contains("audio") == true
                                    
                                    // Check if it's an image
                                    val isImage = mimeType?.startsWith("image/") == true ||
                                            lowerUri.endsWith(".jpg") ||
                                            lowerUri.endsWith(".jpeg") ||
                                            lowerUri.endsWith(".png") ||
                                            lowerUri.endsWith(".gif") ||
                                            lowerUri.endsWith(".webp")
                                            
                                    // Check if it's a contact (vCard)
                                    val isContact = mimeType?.contains("vcard") == true || 
                                                    lowerUri.endsWith(".vcf") || 
                                                    lowerUri.endsWith(".vcard")
                                    
                                    when {
                                        // Video attachment
                                        isVideo -> {
                                            VideoMessageBubble(
                                                videoUri = uri,
                                                isOutgoing = isOutgoing,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        // Contact (vCard) attachment
                                        isContact -> {
                                            ContactMessageBubble(
                                                uri = uri,
                                                fileName = android.net.Uri.parse(uri).lastPathSegment ?: "جهات الاتصال",
                                                isOutgoing = isOutgoing,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        // Image attachment - ✅ محسّن مع Placeholder + Crossfade + قابل للنقر
                                        isImage -> {
                                            val imageIndex = allImageUris.indexOf(uri).coerceAtLeast(0)

                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(android.net.Uri.parse(uri))
                                                    .crossfade(300) // ✅ Smooth transition
                                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .build(),
                                                contentDescription = stringResource(R.string.msg_cd_attached_image),
                                                modifier = Modifier
                                                    .heightIn(max = 160.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        onImageClick?.invoke(allImageUris, imageIndex)
                                                    },
                                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                                // ✅ Placeholder أثناء التحميل
                                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                ),
                                                // ✅ Error fallback
                                                error = androidx.compose.ui.graphics.painter.ColorPainter(
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                        // Audio attachment - show enhanced audio message bubble
                                        isAudio -> {
                                            AudioMessageBubble(
                                                audioUri = uri,
                                                durationMs = 0, // Duration will be auto-detected
                                                isOutgoing = isOutgoing,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        // Other attachments
                                        else -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    tint = if (isOutgoing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.msg_attachment),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isOutgoing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Message Text (if present)
                        if (!message.body.isNullOrBlank()) {
                            // ✅ Detect location message with geo: URI
                            val geoPattern = remember { Regex("""geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""") }
                            val geoMatch = remember(message.body) { geoPattern.find(message.body) }
                            
                            if (geoMatch != null) {
                                // ═══ Location Message → Mini-Map Card ═══
                                val lat = geoMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                                val lng = geoMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                                
                                // Extract address line (text before geo: URI)
                                val addressLine = remember(message.body) {
                                    message.body.substringBefore("geo:")
                                        .replace("📍", "").trim()
                                        .takeIf { it.isNotBlank() }
                                }
                                
                                LocationMapCard(
                                    latitude = lat,
                                    longitude = lng,
                                    address = addressLine,
                                    isOutgoing = isOutgoing,
                                    context = context
                                )
                            } else {
                                // ═══ Regular Text → Clickable URLs ═══
                            val annotatedString: AnnotatedString = remember(message.body) {
                                buildAnnotatedString {
                                    val text = message.body
                                    append(text)
                                    
                                    // Detect URLs using Android patterns
                                    val matcher = android.util.Patterns.WEB_URL.matcher(text)
                                    while (matcher.find()) {
                                        val url: String? = matcher.group()
                                        val start: Int = matcher.start()
                                        val end: Int = matcher.end()
                                        
                                        // Add URL annotation
                                        addStyle(
                                            style = SpanStyle(
                                                color = iOSBlue,
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            start = start,
                                            end = end
                                        )
                                        
                                        addStringAnnotation(
                                            tag = "URL",
                                            annotation = url ?: "",
                                            start = start,
                                            end = end
                                        )
                                    }
                                }
                            }
                            
                            ClickableText(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp
                                ),
                                onClick = { offset ->
                                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            val url = annotation.item
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(
                                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                                        "https://$url"
                                                    } else {
                                                        url
                                                    }
                                                ))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("MessageBubble", "Error opening URL: $url", e)
                                            }
                                        }
                                }
                            )
                            } // end else (non-location)
                        }
                        
        // ── Footer: time + status + actions ──
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Action buttons ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        message.body?.let { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Message", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.msg_copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.msg_cd_copy),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { onFavoriteChange(!isFavorite) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.msg_cd_favorite),
                        modifier = Modifier.size(14.dp),
                        tint = if (isFavorite) iOSYellow
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                onForward?.let { fwd ->
                    androidx.compose.material3.IconButton(
                        onClick = { fwd(message) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.msg_cd_forward),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                if (message.type == com.rasmi.purevon.data.local.entity.MessageType.FAILED.value) {
                    androidx.compose.material3.IconButton(
                        onClick = { onRetry() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.msg_cd_retry),
                            modifier = Modifier.size(14.dp),
                            tint = iOSRedDark
                        )
                    }
                }
                // ✅ Cancel button for scheduled messages
                if (message.isScheduled && message.scheduleId != null) {
                    onEditScheduled?.let { edit ->
                        androidx.compose.material3.IconButton(
                            onClick = { edit(message) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.scheduled_message_edit),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    onCancelScheduled?.let { cancel ->
                        message.scheduleId?.let { scheduleId ->
                        androidx.compose.material3.IconButton(
                            onClick = { cancel(scheduleId) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.scheduled_message_cancel),
                                modifier = Modifier.size(14.dp),
                                tint = iOSRedDark
                            )
                        }
                        }
                    }
                }
            }

            // ── Timestamp + delivery status ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                            
                            // ✅ Show SIM slot if available (same pattern as call history)
                            if (message.simSlot != null) {
                                if (isFavorite) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.history_sim, (message.simSlot ?: 0) + 1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp
                                )
                            }
                            
                            Text(
                                text = formatTimestamp(
                                    timestamp = message.timestamp,
                                    yesterdayLabel = stringResource(R.string.msg_date_yesterday)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        
                            // Delivery status for outgoing messages
                            if (isOutgoing) {
                                    when {
                                        message.isScheduled -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = stringResource(R.string.msg_cd_scheduled),
                                                    modifier = Modifier.size(11.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                message.scheduledTime?.let { time ->
                                                    Text(
                                                        text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(time)),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                        }
                                        // ✅ تحسين: حالة فشل الإرسال
                                        message.type == com.rasmi.purevon.data.local.entity.MessageType.FAILED.value -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Error,
                                                    contentDescription = stringResource(R.string.msg_cd_failed),
                                                    modifier = Modifier.size(11.dp),
                                                    tint = iOSRedDark
                                                )
                                                Text(
                                                    text = stringResource(R.string.msg_status_failed),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = iOSRedDark,
                                                    fontSize = 9.sp
                                                )
                                                // زر إعادة الإرسال
                                                androidx.compose.material3.TextButton(
                                                    onClick = { onRetry() },
                                                    modifier = Modifier.height(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.msg_action_retry),
                                                        fontSize = 9.sp,
                                                        color = iOSRedDark
                                                    )
                                                }
                                            }
                                        }
                                        // ✅ جديد: حالة جاري الإرسال (OUTBOX)
                                        message.type == com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value -> {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(10.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 1.5.dp
                                                )
                                                Text(
                                                    text = stringResource(R.string.msg_status_sending),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                        // حالة تم الإرسال (SENT)
                                        message.type == com.rasmi.purevon.data.local.entity.MessageType.SENT.value -> {
                                            // ✅ P2: Blue Read Receipts
                                            when {
                                                // تمت القراءة - علامتان زرقاء
                                                message.isRead && message.isDelivered -> {
                                                    Icon(
                                                        Icons.Default.DoneAll,
                                                        contentDescription = stringResource(R.string.msg_cd_delivered),
                                                        modifier = Modifier.size(11.dp),
                                                        tint = MessengerBlue // WhatsApp blue
                                                    )
                                                }
                                                // تم التسليم - علامتان رمادي
                                                message.isDelivered -> {
                                                    Icon(
                                                        Icons.Default.DoneAll,
                                                        contentDescription = stringResource(R.string.msg_cd_delivered),
                                                        modifier = Modifier.size(11.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                    )
                                                }
                                                // تم الإرسال - علامة واحدة رمادي
                                                else -> {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.msg_cd_sent),
                                                        modifier = Modifier.size(11.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    )
                                                }
                                            }
                                        }
                                        // حالة افتراضية (معلق)
                                        else -> {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = stringResource(R.string.msg_cd_pending),
                                                modifier = Modifier.size(11.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                            }
                        }
                    } // end Row (footer right)
                } // end Row (full footer)
            } // end Column (card body)

private fun formatTimestamp(timestamp: Long, yesterdayLabel: String): String {
    // Use message-time offset (not current offset) to handle DST transitions correctly
    val dateTime = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val messageDate = dateTime.toLocalDate()
    
    // تحديد التاريخ
    val datePrefix = if (messageDate == today) {
        "" // اليوم - لا نعرض التاريخ
    } else {
        val year = dateTime.year
        val month = String.format(Locale.ENGLISH, "%02d", dateTime.monthValue)
        val day = String.format(Locale.ENGLISH, "%02d", dateTime.dayOfMonth)
        "$year\\$month\\$day " // من الأمس وما قبل
    }
    
    // تنسيق الوقت بنظام 12 ساعة مع AM/PM
    val timeFormat = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    
    return datePrefix + timeFormat
}

/**
 * Message Date Separator - Compact iOS style
 */
@Composable
fun MessageDateSeparator(
    date: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/**
 * Typing Indicator - Compact iOS style
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

// ContactMessageBubble → extracted to ContactMessageBubble.kt
// LocationMapCard → extracted to LocationMapCard.kt
