package com.rasmi.purevon.presentation.screen.contactdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val iOSBlue = Color(0xFF007AFF)
internal val iOSGreen = Color(0xFF34C759)
internal val iOSRed = Color(0xFFFF3B30)
internal val iOSOrange = Color(0xFFFF9500)
internal val iOSPurple = Color(0xFFAF52DE)
internal val iOSYellow = Color(0xFFFFCC00)
internal val MaterialBlue500 = Color(0xFF2196F3)
internal val MaterialGreen500 = Color(0xFF4CAF50)
internal val MaterialOrange500 = Color(0xFFFF9800)
internal val MaterialRed500 = Color(0xFFF44336)
internal val MaterialPurple500 = Color(0xFF9C27B0)
internal val MaterialYellow500 = Color(0xFFFFEB3B)
internal val WhatsAppGreen = Color(0xFF25D366)
internal val TelegramBlue = Color(0xFF0088CC)
internal val TelegramLightBlue = Color(0xFF2CA5E0)
internal val MaterialDeepPurple500 = Color(0xFF673AB7)
internal val MaterialTeal500 = Color(0xFF009688)
internal val MaterialCyan500 = Color(0xFF00BCD4)
internal val MaterialLightGreen500 = Color(0xFF8BC34A)
internal val MaterialAmber500 = Color(0xFFFFC107)
internal val MaterialBrown500 = Color(0xFF795548)
internal val MaterialGrey500 = Color(0xFF9E9E9E)
internal val MaterialBlueGrey500 = Color(0xFF607D8B)
internal val MaterialDeepOrange500 = Color(0xFFFF5722)
internal val MaterialIndigo500 = Color(0xFF3F51B5)
internal val MaterialPink500 = Color(0xFFE91E63)
internal val MaterialLightBlue500 = Color(0xFF03A9F4)

internal fun isPackageInstalled(context: android.content.Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
internal fun ContactHeader(
    contact: Contact,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
            size = 100.dp,
            photoUri = contact.photoUri
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        contact.company?.let { company ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = company,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun QuickActionsRow(
    onCall: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
            label = stringResource(R.string.contact_detail_message),
            color = iOSBlue,
            onClick = onMessage
        )

        QuickActionButton(
            icon = rememberVectorPainter(Icons.Filled.Call),
            label = stringResource(R.string.contact_detail_call),
            color = iOSGreen,
            onClick = onCall
        )
    }
}

@Composable
internal fun EnhancedQuickActionsRow(
    phoneNumber: String,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val whatsAppInstalled = remember(context) {
        isPackageInstalled(context, "com.whatsapp") ||
        isPackageInstalled(context, "com.whatsapp.w4b")
    }

    val telegramInstalled = remember(context) {
        isPackageInstalled(context, "org.telegram.messenger") ||
        isPackageInstalled(context, "org.thunderdog.challegram")
    }

    val hasWhatsApp = whatsAppInstalled
    val hasTelegram = telegramInstalled

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
            label = stringResource(R.string.contact_detail_message),
            color = iOSBlue,
            onClick = onMessage
        )

        QuickActionButton(
            icon = rememberVectorPainter(Icons.Filled.Call),
            label = stringResource(R.string.contact_detail_call),
            color = iOSGreen,
            onClick = onCall
        )

        if (hasWhatsApp) {
            QuickActionButton(
                icon = painterResource(R.drawable.ic_whatsapp),
                label = stringResource(R.string.contact_detail_whatsapp),
                color = WhatsAppGreen,
                tintIcon = false,
                onClick = {
                    try {
                        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/$cleanNumber")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            )
        }

        if (hasTelegram) {
            QuickActionButton(
                icon = painterResource(R.drawable.ic_telegram),
                label = stringResource(R.string.contact_detail_telegram),
                color = TelegramBlue,
                tintIcon = false,
                onClick = {
                    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("tg://resolve?phone=$cleanNumber")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val altIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://t.me/$cleanNumber")
                            }
                            context.startActivity(altIntent)
                        } catch (_: Exception) { }
                    }
                }
            )
        }
    }
}

@Composable
internal fun QuickActionButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    color: Color,
    tintIcon: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.13f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = if (tintIcon) color else Color.Unspecified,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
            fontSize = 12.sp
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (isLightTheme) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLightTheme) 0.42f else 0.28f)
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun ContactInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onTap
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        trailingActions?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                it()
            }
        }
    }
}

@Composable
internal fun SmallActionButton(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .size(38.dp)
            .semantics {
                this.contentDescription = contentDescription ?: context.getString(R.string.contact_detail_action_button)
            },
        shape = CircleShape,
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun MoreActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}

internal fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

internal fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
