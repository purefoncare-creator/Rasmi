package com.rasmi.purevon.presentation.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.presentation.theme.*

@Composable
internal fun SettingsCard(
    // ✅ FIX M41: العنوان اختياري — الهوية موحدة فلا حاجة لعناوين البطاقات
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLightTheme = false
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = PurevonTextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLightTheme) PurevonSurfaceAlt
                else PurevonSurfaceMuted
            ),
            border = BorderStroke(
                width = 1.dp,
                color = PurevonBorder
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun CardItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 18.dp),
        color = PurevonBorder
    )
}

@Composable
internal fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    iconTint: Color = Indigo400
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.13f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PurevonTextPrimary.copy(alpha = alpha)
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PurevonTextSecondary.copy(alpha = 0.78f * alpha)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
internal fun ClickableSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    tint: Color? = null,
    iconTint: Color = Indigo400
) {
    val resolvedIconTint = tint ?: iconTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(resolvedIconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = resolvedIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint ?: PurevonTextPrimary
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PurevonTextSecondary.copy(alpha = 0.78f)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = PurevonTextTertiary
        )
    }
}

@Composable
internal fun ContactPickerItem(
    name: String,
    phoneNumber: String,
    isSelected: Boolean,
    selectedIcon: ImageVector = Icons.Default.Check,
    selectedColor: Color = PurevonPrimary,
    callTypeIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelected, onClick = onClick)
            .alpha(if (isSelected) 0.5f else 1f),
        color = if (isSelected) selectedColor.copy(alpha = 0.1f) else PurevonSurfaceAlt
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isSelected) selectedColor.copy(alpha = 0.2f)
                       else PurevonPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            selectedIcon,
                            contentDescription = null,
                            tint = selectedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (callTypeIcon != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        callTypeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when (callTypeIcon) {
                            Icons.Default.CallMissed -> PurevonError
                            else -> PurevonTextSecondary
                        }
                    )
                    }
                }
                if (phoneNumber.isNotEmpty()) {
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = PurevonTextSecondary
                    )
                }
            }

            if (!isSelected) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    tint = PurevonPrimary
                )
            }
        }
    }
    HorizontalDivider()
}
