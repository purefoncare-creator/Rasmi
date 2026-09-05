package com.rasmi.purevon.presentation.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.*
import com.rasmi.purevon.presentation.theme.*

/**
 * Calculate OpenStreetMap tile coordinates from lat/lng.
 * Returns tile URL for a 256×256 PNG map tile.
 */
internal fun getOsmTileUrl(lat: Double, lng: Double, zoom: Int = 15): String {
    val n = 1 shl zoom // 2^zoom
    val x = ((lng + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
}

/**
 * Calculate the pixel offset of the exact lat/lng within the 256×256 tile.
 */
internal fun getTilePixelOffset(lat: Double, lng: Double, zoom: Int = 15): Pair<Float, Float> {
    val n = 1 shl zoom
    val xExact = ((lng + 180.0) / 360.0 * n)
    val latRad = Math.toRadians(lat)
    val yExact = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n)
    val pixelX = ((xExact - xExact.toInt()) * 256).toFloat()
    val pixelY = ((yExact - yExact.toInt()) * 256).toFloat()
    return Pair(pixelX, pixelY)
}

/**
 * Location mini-map card shown inside message bubbles.
 * Shows an OSM tile with a pin overlay, address, and tap-to-open-maps.
 *
 * Extracted from MessageBubble.kt for maintainability.
 */
@Composable
internal fun LocationMapCard(
    latitude: Double,
    longitude: Double,
    address: String?,
    isOutgoing: Boolean,
    context: Context
) {
    val tileUrl = remember(latitude, longitude) { getOsmTileUrl(latitude, longitude) }
    val pixelOffset = remember(latitude, longitude) { getTilePixelOffset(latitude, longitude) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                onClickLabel = context.getString(R.string.msg_cd_open_location)
            ) {
                try {
                    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    } else {
                        val webUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    }
                } catch (e: Exception) {
                    Log.e("LocationMapCard", "Error opening map", e)
                    Toast.makeText(context, context.getString(R.string.msg_error_open_map), Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        // ── Map tile with pin overlay ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(WireOtherBubblePressed)
        ) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(tileUrl)
                    .crossfade(300)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .addHeader("User-Agent", "Purevon-SMS/1.0")
                    .build(),
                contentDescription = stringResource(R.string.location_view_map),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(LightBorder),
                error = androidx.compose.ui.graphics.painter.ColorPainter(PlaceholderGrey)
            )

            // Pin icon overlay
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialRed600,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .offset(y = (-8).dp)
            )

            // Pin shadow dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.Center)
                    .offset(y = 8.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            )

            // "Open in Maps" overlay badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = stringResource(R.string.msg_cd_open_location),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Address & coordinates footer ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isOutgoing) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialRed600,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = address ?: stringResource(R.string.msg_shared_location),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = if (isOutgoing) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = String.format(java.util.Locale.getDefault(), "%.5f, %.5f", latitude, longitude),
                style = MaterialTheme.typography.labelSmall,
                color = (if (isOutgoing) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}
