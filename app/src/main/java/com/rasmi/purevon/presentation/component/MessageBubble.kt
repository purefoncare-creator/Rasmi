package com.rasmi.purevon.presentation.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.presentation.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Wire-style Message Bubble — flat Surface, 16dp corners, 75% max width, no elevation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true,
    onFavoriteClick: ((Message) -> Unit)? = null,
    onRetry: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
    onForward: ((Message) -> Unit)? = null,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onCancelScheduled: ((Long) -> Unit)? = null,
    onEditScheduled: ((Message) -> Unit)? = null,
    onSwipeToReply: ((Message) -> Unit)? = null,
    onLongClick: ((Message) -> Unit)? = null
) {
    val isOutgoing = message.type in listOf(
        com.rasmi.purevon.data.local.entity.MessageType.SENT.value,
        com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value,
        com.rasmi.purevon.data.local.entity.MessageType.FAILED.value,
        com.rasmi.purevon.data.local.entity.MessageType.QUEUED.value
    )
    var isFavorite by remember(message.id, message.isStarred) { mutableStateOf(message.isStarred) }

    val bubbleShape = RoundedCornerShape(MessagingDimensions.bubbleCornerRadius)
    val bubbleColor = if (isOutgoing) PurevonBubbleSent else PurevonBubbleReceived
    val textColor = if (isOutgoing) PurevonBubbleSentText else PurevonBubbleReceivedText

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && onSwipeToReply != null) {
                onSwipeToReply(message)
                false
            } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                onLongClick?.invoke(message)
                false
            } else false
        }
    )

    if (onSwipeToReply != null) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MessagingDimensions.spacing16x),
                    contentAlignment = when (direction) {
                        SwipeToDismissBoxValue.EndToStart -> if (isOutgoing) Alignment.CenterStart else Alignment.CenterEnd
                        SwipeToDismissBoxValue.StartToEnd -> if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                        else -> Alignment.Center
                    }
                ) {
                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = PurevonPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            Icons.Default.AddReaction,
                            contentDescription = "React",
                            tint = PurevonPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            modifier = modifier
        ) {
            MessageBubbleContent(
                message = message,
                isOutgoing = isOutgoing,
                isFavorite = isFavorite,
                showAuthor = showAuthor,
                onFavoriteChange = {
                    isFavorite = it
                    onFavoriteClick?.invoke(message)
                },
                onRetry = onRetry,
                onForward = onForward,
                onImageClick = onImageClick,
                onCancelScheduled = onCancelScheduled,
                onEditScheduled = onEditScheduled,
                onCancel = onCancel,
                onLongClick = onLongClick
            )
        }
    } else {
        Box(
            modifier = modifier
        ) {
            MessageBubbleContent(
                message = message,
                isOutgoing = isOutgoing,
                isFavorite = isFavorite,
                showAuthor = showAuthor,
                onFavoriteChange = {
                    isFavorite = it
                    onFavoriteClick?.invoke(message)
                },
                onRetry = onRetry,
                onForward = onForward,
                onImageClick = onImageClick,
                onCancelScheduled = onCancelScheduled,
                onEditScheduled = onEditScheduled,
                onCancel = onCancel,
                onLongClick = onLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleContent(
    message: Message,
    isOutgoing: Boolean,
    isFavorite: Boolean,
    showAuthor: Boolean = true,
    onFavoriteChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onForward: ((Message) -> Unit)?,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onCancelScheduled: ((Long) -> Unit)? = null,
    onEditScheduled: ((Message) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onLongClick: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(MessagingDimensions.bubbleCornerRadius)
    val bubbleColor = if (isOutgoing) PurevonBubbleSent else PurevonBubbleReceived
    val textColor = if (isOutgoing) PurevonBubbleSentText else PurevonBubbleReceivedText

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = MessagingDimensions.messageItemHorizontalPadding,
                end = MessagingDimensions.messageItemHorizontalPadding,
                top = if (showAuthor) MessagingDimensions.spacing6x else MessagingDimensions.spacing1x,
                bottom = MessagingDimensions.messageItemBottomPadding
            ),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = (LocalConfiguration.current.screenWidthDp * MessagingDimensions.bubbleMaxWidthFraction).dp)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onLongClick(message) },
                            role = Role.Button
                        )
                    } else Modifier
                ),
            shape = bubbleShape,
            color = bubbleColor,
            border = null
        ) {
            BubbleContent(
                message = message,
                isOutgoing = isOutgoing,
                bubbleColor = bubbleColor,
                textColor = textColor,
                isFavorite = isFavorite,
                onFavoriteChange = onFavoriteChange,
                onRetry = onRetry,
                onForward = onForward,
                onImageClick = onImageClick,
                onCancelScheduled = onCancelScheduled,
                onEditScheduled = onEditScheduled,
                onCancel = onCancel
            )
        }

        // Wire-style: Time + Status icons OUTSIDE the bubble
        BubbleFooter(
            message = message,
            isOutgoing = isOutgoing,
            textColor = textColor,
            onRetry = onRetry,
            onCancel = onCancel
        )

        if (message.isSpam) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(MessagingDimensions.corner8x))
                    .background(PurevonError.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.msg_spam),
                    modifier = Modifier.size(10.dp),
                    tint = PurevonError
                )
                Text(
                    text = stringResource(R.string.msg_spam),
                    style = MessagingTypography.label01,
                    color = PurevonError
                )
            }
        }
    }
}

@Composable
private fun BubbleContent(
    message: Message,
    isOutgoing: Boolean,
    bubbleColor: Color,
    textColor: Color,
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onForward: ((Message) -> Unit)?,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onCancelScheduled: ((Long) -> Unit)? = null,
    onEditScheduled: ((Message) -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(MessagingDimensions.bubbleInternalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (message.isMms && message.attachmentUris.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

                    val isVideo = mimeType?.startsWith("video/") == true ||
                            lowerUri.endsWith(".mp4") || lowerUri.endsWith(".3gp") ||
                            lowerUri.endsWith(".mkv") || lowerUri.endsWith(".mov") ||
                            lowerUri.endsWith(".avi") || lowerUri.endsWith(".webm") ||
                            mimeType?.contains("video") == true

                    val isAudio = mimeType?.startsWith("audio/") == true ||
                            lowerUri.endsWith(".mp3") || lowerUri.endsWith(".m4a") ||
                            lowerUri.endsWith(".aac") || lowerUri.endsWith(".wav") ||
                            lowerUri.endsWith(".ogg") || lowerUri.endsWith(".amr") ||
                            mimeType?.contains("audio") == true

                    val isImage = mimeType?.startsWith("image/") == true ||
                            lowerUri.endsWith(".jpg") || lowerUri.endsWith(".jpeg") ||
                            lowerUri.endsWith(".png") || lowerUri.endsWith(".gif") ||
                            lowerUri.endsWith(".webp")

                    val isContact = mimeType?.contains("vcard") == true ||
                            lowerUri.endsWith(".vcf") || lowerUri.endsWith(".vcard")

                    when {
                        isVideo -> VideoMessageBubble(
                            videoUri = uri,
                            isOutgoing = isOutgoing,
                            modifier = Modifier.fillMaxWidth()
                        )
                        isContact -> ContactMessageBubble(
                            uri = uri,
                            fileName = android.net.Uri.parse(uri).lastPathSegment ?: "جهات الاتصال",
                            isOutgoing = isOutgoing,
                            modifier = Modifier.fillMaxWidth()
                        )
                        isImage -> {
                            val imageIndex = allImageUris.indexOf(uri).coerceAtLeast(0)
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(android.net.Uri.parse(uri))
                                    .crossfade(300)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = stringResource(R.string.msg_cd_attached_image),
                                modifier = Modifier
                                    .size(width = 310.dp, height = 240.dp)
                                    .clip(RoundedCornerShape(MessagingDimensions.corner10x))
                                    .clickable { onImageClick?.invoke(allImageUris, imageIndex) },
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(PurevonSurfaceMuted.copy(alpha = 0.3f)),
                                error = androidx.compose.ui.graphics.painter.ColorPainter(PurevonErrorContainer.copy(alpha = 0.3f))
                            )
                        }
                        isAudio -> AudioMessageBubble(
                            audioUri = uri,
                            durationMs = 0,
                            isOutgoing = isOutgoing,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = if (isOutgoing) PurevonBubbleSentText.copy(alpha = 0.7f) else PurevonTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.msg_attachment),
                                style = MessagingTypography.subline01,
                                color = if (isOutgoing) PurevonBubbleSentText.copy(alpha = 0.7f) else PurevonTextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (!message.body.isNullOrBlank()) {
            val geoPattern = remember { Regex("""geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""") }
            val geoMatch = remember(message.body) { geoPattern.find(message.body) }

            if (geoMatch != null) {
                val lat = geoMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val lng = geoMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                val addressLine = remember(message.body) {
                    message.body.substringBefore("geo:")
                        .replace("\uD83D\uDCCD", "").trim()
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
                val annotatedString: AnnotatedString = remember(message.body) {
                    buildAnnotatedString {
                        append(message.body)
                        val matcher = android.util.Patterns.WEB_URL.matcher(message.body)
                        while (matcher.find()) {
                            val url = matcher.group() ?: continue
                            val start = matcher.start()
                            val end = matcher.end()
                            addStyle(
                                style = SpanStyle(color = PurevonTertiary, textDecoration = TextDecoration.Underline),
                                start = start, end = end
                            )
                            addStringAnnotation(tag = "URL", annotation = url, start = start, end = end)
                        }
                    }
                }

                ClickableText(
                    text = annotatedString,
                    style = MessagingTypography.body01.copy(color = textColor),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    val url = annotation.item
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                                        )
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("MessageBubble", "Error opening URL", e)
                                }
                            }
                    }
                )
            }
        }
    }
}

@Composable
private fun BubbleFooter(
    message: Message,
    isOutgoing: Boolean,
    textColor: Color,
    onRetry: () -> Unit,
    onCancel: (() -> Unit)?
) {
    val mutedTextColor = textColor.copy(alpha = 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Wire-style: Time label with 0.7 alpha (OUTSIDE bubble)
        Text(
            text = com.rasmi.purevon.presentation.screen.conversation.formatMessageTimeWire(message.timestamp),
            style = MessagingTypography.subline01,
            color = mutedTextColor
        )

        if (isOutgoing) {
            Spacer(modifier = Modifier.width(4.dp))
            when {
                message.isScheduled -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = stringResource(R.string.msg_cd_scheduled),
                        modifier = Modifier.size(MessagingDimensions.statusIconSize),
                        tint = mutedTextColor
                    )
                    message.scheduledTime?.let { time ->
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(time)),
                            style = MessagingTypography.subline01,
                            color = mutedTextColor
                        )
                    }
                }
                message.type == com.rasmi.purevon.data.local.entity.MessageType.FAILED.value -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = stringResource(R.string.msg_cd_failed),
                        modifier = Modifier.size(MessagingDimensions.statusIconSize),
                        tint = PurevonError
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.msg_status_failed),
                        style = MessagingTypography.subline01,
                        color = PurevonError
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.material3.TextButton(
                        onClick = { onRetry() },
                        modifier = Modifier.height(20.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.msg_action_retry),
                            style = MessagingTypography.subline01,
                            color = PurevonError
                        )
                    }
                }
                message.type == com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = textColor.copy(alpha = 0.7f),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (onCancel != null) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.msg_action_cancel),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onCancel() },
                            tint = mutedTextColor
                        )
                    }
                }
                message.type == com.rasmi.purevon.data.local.entity.MessageType.SENT.value -> {
                    when {
                        message.isRead && message.isDelivered -> {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = stringResource(R.string.msg_cd_delivered),
                                modifier = Modifier.size(MessagingDimensions.statusIconSize),
                                tint = PurevonStatusRead
                            )
                        }
                        message.isDelivered -> {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = stringResource(R.string.msg_cd_delivered),
                                modifier = Modifier.size(MessagingDimensions.statusIconSize),
                                tint = mutedTextColor
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.msg_cd_sent),
                                modifier = Modifier.size(MessagingDimensions.statusIconSize),
                                tint = mutedTextColor
                            )
                        }
                    }
                }
                else -> {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = stringResource(R.string.msg_cd_pending),
                        modifier = Modifier.size(MessagingDimensions.statusIconSize),
                        tint = mutedTextColor
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long, yesterdayLabel: String): String {
    val dateTime = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val messageDate = dateTime.toLocalDate()

    val datePrefix = if (messageDate == today) {
        ""
    } else {
        val year = dateTime.year
        val month = String.format(Locale.ENGLISH, "%02d", dateTime.monthValue)
        val day = String.format(Locale.ENGLISH, "%02d", dateTime.dayOfMonth)
        "$year/$month/$day "
    }

    val timeFormat = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    return datePrefix + timeFormat
}

/**
 * Message Date Separator — Wire-style simple centered text, no background pill
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
            style = MessagingTypography.subline01,
            color = PurevonTextSecondary
        )
    }
}

/**
 * Typing Indicator — Wire-style with avatar + animated pen + text
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    contactName: String? = null
) {
    Row(
        modifier = modifier.padding(
            start = MessagingDimensions.messageItemHorizontalPadding,
            end = MessagingDimensions.messageItemHorizontalPadding,
            bottom = MessagingDimensions.spacing4x
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small avatar
        Surface(
            shape = CircleShape,
            color = PurevonPrimary.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = PurevonPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(MessagingDimensions.corner14x),
            color = PurevonBubbleReceived,
            modifier = Modifier.defaultMinSize(minHeight = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated pen icon
                val infiniteTransition = rememberInfiniteTransition(label = "typing_pen")
                val penOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pen_offset"
                )
                val penAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pen_alpha"
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = PurevonTextSecondary.copy(alpha = penAlpha),
                    modifier = Modifier
                        .size(14.dp)
                        .offset(y = penOffset.dp)
                )

                // Animated dots
                repeat(3) { index ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, delayMillis = index * 200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_alpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(PurevonTextSecondary.copy(alpha = dotAlpha))
                    )
                }

                // "typing" text
                val textAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "text_alpha"
                )
                Text(
                    text = "typing",
                    style = MessagingTypography.subline01,
                    color = PurevonTextSecondary.copy(alpha = textAlpha)
                )
            }
        }
    }
}
