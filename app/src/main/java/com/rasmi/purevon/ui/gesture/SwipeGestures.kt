package com.rasmi.purevon.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe to archive gesture handler
 * سحب المحادثة لليسار للأرشفة
 */
@Composable
fun Modifier.swipeToArchive(
    onArchive: () -> Unit,
    threshold: Float = 200f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    
    this
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (offsetX.value < -threshold) {
                            // Swipe exceeded threshold - archive
                            offsetX.animateTo(
                                targetValue = -1000f,
                                animationSpec = tween(durationMillis = 200)
                            )
                            onArchive()
                        } else {
                            // Return to original position
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 200)
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 200)
                        )
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        // Only allow left swipe
                        val newValue = (offsetX.value + dragAmount).coerceAtMost(0f)
                        offsetX.snapTo(newValue)
                    }
                }
            )
        }
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
}

/**
 * Swipe to delete gesture handler
 * سحب لحذف الرسالة
 */
@Composable
fun Modifier.swipeToDelete(
    onDelete: () -> Unit,
    threshold: Float = 150f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    
    this
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (offsetX.value > threshold) {
                            // Swipe exceeded threshold - delete
                            offsetX.animateTo(
                                targetValue = 1000f,
                                animationSpec = tween(durationMillis = 200)
                            )
                            onDelete()
                        } else {
                            // Return to original position
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 200)
                            )
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 200)
                        )
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        // Only allow right swipe
                        val newValue = (offsetX.value + dragAmount).coerceAtLeast(0f)
                        offsetX.snapTo(newValue)
                    }
                }
            )
        }
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
}
