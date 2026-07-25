package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.presentation.theme.iOSBlue

/**
 * iOS-style Fast Scroll Index
 */
@Composable
fun FastScrollIndex(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var indexHeight by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onGloballyPositioned { coordinates ->
                indexHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val index = calculateLetterIndex(offset.y, indexHeight, letters.size)
                        if (index in letters.indices) {
                            selectedLetter = letters[index]
                            onLetterSelected(letters[index])
                        }
                    },
                    onDrag = { change, _ ->
                        val index = calculateLetterIndex(change.position.y, indexHeight, letters.size)
                        if (index in letters.indices) {
                            val letter = letters[index]
                            if (selectedLetter != letter) {
                                selectedLetter = letter
                                onLetterSelected(letter)
                            }
                        }
                    },
                    onDragEnd = {
                        selectedLetter = null
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = if (selectedLetter == letter) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedLetter == letter) iOSBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Selected letter popup
        selectedLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .offset(x = (-40).dp)
                    .clip(CircleShape)
                    .background(iOSBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun calculateLetterIndex(y: Float, height: Float, letterCount: Int): Int {
    val position = (y / height).coerceIn(0f, 1f)
    return (position * letterCount).toInt().coerceIn(0, letterCount - 1)
}
