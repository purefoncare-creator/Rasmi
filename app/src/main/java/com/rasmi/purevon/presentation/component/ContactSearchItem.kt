package com.rasmi.purevon.presentation.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.presentation.theme.*

/**
 * iOS-style Contact Search Result Item with T9 highlighting
 */
@Composable
fun ContactSearchItem(
    contact: Contact,
    highlightedName: String,
    highlightedNumber: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isLightTheme = !androidx.compose.foundation.isSystemInDarkTheme()
    
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (isLightTheme) androidx.compose.ui.graphics.Color.White
               else androidx.compose.ui.graphics.Color.Transparent,
        border = if (isLightTheme) androidx.compose.foundation.BorderStroke(1.dp, LightBorder) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        onClick = onClick
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        ContactAvatar(
            name = contact.name,
            photoUri = contact.photoUri,
            modifier = Modifier.size(44.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Contact info
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 110.dp)
        ) {
            // Check if text is Arabic
            val isArabic = contact.name.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
            
            // Name with highlighting - Allow wrapping for Arabic names with better line height
            Text(
                text = buildHighlightedText(highlightedName, iOSBlue, MaterialTheme.colorScheme.onSurface),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = if (isArabic) 24.sp else 20.sp
                ),
                maxLines = if (isArabic) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                softWrap = true
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Phone number with highlighting
            Text(
                text = buildHighlightedText(
                    highlightedNumber, 
                    iOSBlue, 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    highlightWeight = FontWeight.SemiBold
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = true
            )
        }
    }
    }
}

/**
 * Build annotated string with highlighting support
 * Uses bracket notation: [highlighted text] for highlighting
 * Fixed: Appends text segments instead of individual characters to preserve Arabic ligatures
 */
@Composable
private fun buildHighlightedText(
    text: String,
    highlightColor: androidx.compose.ui.graphics.Color,
    normalColor: androidx.compose.ui.graphics.Color,
    highlightWeight: FontWeight = FontWeight.Bold
) = buildAnnotatedString {
    var currentSegment = StringBuilder()
    var isHighlighted = false
    var index = 0
    
    fun flushSegment() {
        if (currentSegment.isNotEmpty()) {
            withStyle(
                style = SpanStyle(
                    fontWeight = if (isHighlighted) highlightWeight else FontWeight.Normal,
                    color = if (isHighlighted) highlightColor else normalColor
                )
            ) {
                append(currentSegment.toString())
            }
            currentSegment.clear()
        }
    }
    
    while (index < text.length) {
        when (text[index]) {
            '[' -> {
                flushSegment()
                isHighlighted = true
                index++
            }
            ']' -> {
                flushSegment()
                isHighlighted = false
                index++
            }
            else -> {
                currentSegment.append(text[index])
                index++
            }
        }
    }
    
    flushSegment()
}

/**
 * Contact Avatar with initials
 */
@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    UnifiedContactAvatar(
        size = size,
        photoUri = photoUri,
        modifier = modifier
    )
}

/**
 * Simple Contact List Item (for recent contacts)
 */
@Composable
fun ContactListItem(
    contact: Contact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                role = Role.Button
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            name = contact.name,
            photoUri = contact.photoUri,
            modifier = Modifier.size(44.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = subtitle ?: contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
