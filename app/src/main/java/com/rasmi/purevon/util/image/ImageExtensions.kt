package com.rasmi.purevon.util.image

import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation

/**
 * Extension functions for optimized image loading with Coil
 */

/**
 * Load contact photo by contact ID with caching
 */
@Composable
fun ContactAvatar(
    contactId: Long?,
    contactName: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
        size = size,
        contactId = contactId,
        modifier = modifier
    )
}

/**
 * Show contact initials in a circular avatar
 */
@Composable
fun ContactInitials(
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    com.rasmi.purevon.presentation.component.DefaultAvatar(
        size = size,
        modifier = modifier
    )
}

/**
 * Generate consistent color for name
 */
private fun getColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF1976D2), // Blue
        Color(0xFFD32F2F), // Red
        Color(0xFF388E3C), // Green
        Color(0xFFF57C00), // Orange
        Color(0xFF7B1FA2), // Purple
        Color(0xFF0097A7), // Cyan
        Color(0xFFC2185B), // Pink
        Color(0xFF5D4037), // Brown
        Color(0xFF455A64), // Blue Grey
        Color(0xFF00796B)  // Teal
    )
    
    val hash = name.hashCode()
    val index = (hash % colors.size).let { if (it < 0) it + colors.size else it }
    return colors[index]
}

/**
 * Load MMS attachment image with caching
 */
@Composable
fun AttachmentImage(
    uri: Uri?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .build()
    )
    
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
