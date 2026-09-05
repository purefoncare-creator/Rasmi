package com.rasmi.purevon.presentation.component

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.util.video.VideoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Video Message Bubble Component
 * Shows video with thumbnail and play controls
 */
@Composable
fun VideoMessageBubble(
    videoUri: String,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    durationMs: Long = 0,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoUtils = remember { VideoUtils(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var thumbnail by remember { mutableStateOf(thumbnailBitmap) }
    var duration by remember { mutableLongStateOf(durationMs) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    
    // ✅ FIX #32: Create ExoPlayer once and keep alive — no more destroy on pause
    // ✅ FIX #34: Set audio attributes with handleAudioFocus = true
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
            playWhenReady = false
        }
    }
    
    // ✅ FIX: Single DisposableEffect handles listener, lifecycle observation, and release.
    // This prevents listener accumulation and ensures release() is called AFTER observer removal.
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    exoPlayer.seekTo(0)
                    exoPlayer.playWhenReady = false
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isPlaying = false
                exoPlayer.playWhenReady = false
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (isPlaying) {
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        exoPlayer.playWhenReady = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // ✅ FIX #32: Update progress while playing, sync playWhenReady
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            progress = if (dur > 0) currentPosition.toFloat() / dur.toFloat() else 0f
            delay(100)
        }
    }
    
    // Load thumbnail if not provided
    LaunchedEffect(videoUri) {
        if (thumbnail == null) {
            videoUtils.generateThumbnail(videoUri).onSuccess { bitmap ->
                thumbnail = bitmap
            }
        }
        if (duration == 0L) {
            duration = videoUtils.getVideoDuration(videoUri)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    val backgroundColor = when {
        isOutgoing -> PurevonBubbleSent
        else -> PurevonBubbleReceived
    }
    
    val contentColor = if (isOutgoing) PurevonBubbleSentText else PurevonBubbleReceivedText
    
    Surface(
        modifier = modifier.widthIn(min = 240.dp, max = 240.dp)
            .heightIn(min = 220.dp, max = 310.dp),
        color = Color.Black,
        shape = RoundedCornerShape(MessagingDimensions.corner10x)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    showControls = !showControls
                }
        ) {
            // ✅ FIX #32: Always keep VideoPlayer composed — show thumbnail overlay when paused
            VideoPlayer(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )
            
            // Show thumbnail overlay when not playing
            if (!isPlaying) {
                thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
            
            // Error overlay
            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠ تعذر تشغيل الفيديو",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            
            // Overlay gradient for better text visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            
            // Controls overlay
            if (showControls && !hasError) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Play/Pause button (center)
                    IconButton(
                        onClick = { 
                            isPlaying = !isPlaying
                            showControls = true
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Progress bar
                    if (isPlaying) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = PurevonTertiary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                    
                    // Time and controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPlaying) {
                                "${videoUtils.formatDuration(currentPosition)} / ${videoUtils.formatDuration(duration)}"
                            } else {
                                videoUtils.formatDuration(duration)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Fullscreen button
                        if (onFullscreenClick != null) {
                            IconButton(
                                onClick = onFullscreenClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Video Player using ExoPlayer — receives pre-created player instance
 * ✅ FIX #32: ExoPlayer no longer destroyed on pause
 */
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // We use custom controls
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
