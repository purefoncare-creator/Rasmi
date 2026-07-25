package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.util.message.SmsCharacterCounter

/**
 * SMS Character Counter Display
 * Shows: "1 part • 150 chars left" or "2 parts • 45 chars left (Unicode)"
 */
@Composable
fun SmsCharCounter(
    text: String,
    characterCounter: SmsCharacterCounter,
    modifier: Modifier = Modifier,
    alwaysShow: Boolean = false
) {
    if (text.isEmpty() && !alwaysShow) return
    
    val segmentInfo = characterCounter.calculateSegments(text)
    
    // Only show when near segment limit or multipart
    if (!alwaysShow && segmentInfo.remainingInSegment > 20 && !segmentInfo.isMultipart) {
        return
    }
    
    val displayText = characterCounter.formatSegmentInfo(segmentInfo)
    
    // Change color based on remaining chars
    val textColor = when {
        segmentInfo.remainingInSegment < 10 -> MaterialTheme.colorScheme.error
        segmentInfo.remainingInSegment < 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    
    Text(
        text = displayText,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp,
            fontWeight = if (segmentInfo.remainingInSegment < 10) FontWeight.Medium else FontWeight.Normal
        ),
        color = textColor,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
