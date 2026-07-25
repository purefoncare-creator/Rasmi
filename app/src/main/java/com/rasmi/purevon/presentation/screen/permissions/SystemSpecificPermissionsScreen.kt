package com.rasmi.purevon.presentation.screen.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.util.DeviceUtils

/**
 * System-specific permissions screen for manufacturers requiring special permissions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSpecificPermissionsScreen(
    onComplete: () -> Unit,
    viewModel: SystemSpecificPermissionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Refresh state when returning from settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    // Navigate to next screen when completed or skipped
    LaunchedEffect(uiState.completed, uiState.skipped) {
        if (uiState.completed || uiState.skipped) {
            onComplete()
        }
    }

    // If device doesn't need special permissions, skip this screen
    if (!uiState.needsSpecialPermissions) {
        LaunchedEffect(Unit) {
            onComplete()
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_perm_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Header with device info
            item {
                DeviceInfoHeader(
                    manufacturerNameResId = uiState.manufacturerNameResId,
                    manufacturer = uiState.manufacturer
                )
            }

            // Important notice
            item {
                ImportantNoticeCard()
            }

            // Required permissions list
            items(uiState.requiredPermissions) { permission ->
                PermissionGuideCard(
                    permission = permission,
                    manufacturer = uiState.manufacturer,
                    onOpenSettings = {
                        viewModel.onEvent(
                            SystemSpecificPermissionsEvent.OpenPermissionSettings(permission)
                        )
                    }
                )
            }

            // Action buttons
            item {
                Spacer(modifier = Modifier.height(Spacing.large))
                ActionButtons(
                    onContinue = {
                        viewModel.onEvent(SystemSpecificPermissionsEvent.Continue)
                    },
                    onSkip = {
                        viewModel.onEvent(SystemSpecificPermissionsEvent.SkipForNow)
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoHeader(
    manufacturerNameResId: Int,
    manufacturer: DeviceUtils.DeviceManufacturer
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manufacturerName = if (manufacturerNameResId != 0) {
        context.getString(manufacturerNameResId)
    } else {
        context.getString(R.string.manufacturer_stock)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = stringResource(R.string.system_perm_device, manufacturerName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Text(
                text = stringResource(R.string.system_perm_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ImportantNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.system_perm_important),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Spacing.extraSmall))
                Text(
                    text = stringResource(R.string.system_perm_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PermissionGuideCard(
    permission: DeviceUtils.SpecialPermission,
    manufacturer: DeviceUtils.DeviceManufacturer,
    onOpenSettings: () -> Unit
) {
    val (icon, title, description, steps) = getPermissionInfo(permission, manufacturer)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.medium))

            // Steps
            Text(
                text = "الخطوات:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Open settings button
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("فتح الإعدادات")
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(stringResource(R.string.system_perm_continue))
        }

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.system_perm_skip))
        }
    }
}

private data class PermissionInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val steps: List<String>
)

@Composable
private fun getPermissionInfo(
    permission: DeviceUtils.SpecialPermission,
    manufacturer: DeviceUtils.DeviceManufacturer
): PermissionInfo {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    return when (permission) {
        DeviceUtils.SpecialPermission.DISPLAY_OVER_OTHER_APPS -> {
            PermissionInfo(
                icon = Icons.Default.Layers,
                title = context.getString(R.string.system_perm_display_over),
                description = context.getString(R.string.system_perm_display_over_desc),
                steps = listOf(
                    context.getString(R.string.system_perm_display_over_step1),
                    context.getString(R.string.system_perm_display_over_step2),
                    context.getString(R.string.system_perm_display_over_step3)
                )
            )
        }
        DeviceUtils.SpecialPermission.DISPLAY_ON_LOCK_SCREEN -> {
            PermissionInfo(
                icon = Icons.Default.Lock,
                title = context.getString(R.string.system_perm_display_lock),
                description = context.getString(R.string.system_perm_display_lock_desc),
                steps = listOf(
                    context.getString(R.string.system_perm_display_lock_step1),
                    context.getString(R.string.system_perm_display_lock_step2),
                    context.getString(R.string.system_perm_display_lock_step3),
                    context.getString(R.string.system_perm_display_lock_step4)
                )
            )
        }
        DeviceUtils.SpecialPermission.AUTO_START -> {
            PermissionInfo(
                icon = Icons.Default.Power,
                title = context.getString(R.string.system_perm_autostart),
                description = context.getString(R.string.system_perm_autostart_desc),
                steps = when (manufacturer) {
                    DeviceUtils.DeviceManufacturer.XIAOMI -> listOf(
                        context.getString(R.string.system_perm_autostart_step1_miui),
                        context.getString(R.string.system_perm_autostart_step2_miui),
                        context.getString(R.string.system_perm_autostart_step3_miui),
                        context.getString(R.string.system_perm_autostart_step4_miui)
                    )
                    DeviceUtils.DeviceManufacturer.OPPO, DeviceUtils.DeviceManufacturer.REALME -> listOf(
                        context.getString(R.string.system_perm_autostart_step1_oppo),
                        context.getString(R.string.system_perm_autostart_step2_oppo),
                        context.getString(R.string.system_perm_autostart_step3_oppo)
                    )
                    else -> listOf(
                        context.getString(R.string.system_perm_autostart_step1_generic),
                        context.getString(R.string.system_perm_autostart_step2_generic)
                    )
                }
            )
        }
        DeviceUtils.SpecialPermission.PROTECTED_APPS -> {
            PermissionInfo(
                icon = Icons.Default.Security,
                title = context.getString(R.string.system_perm_protected),
                description = context.getString(R.string.system_perm_protected_desc),
                steps = listOf(
                    context.getString(R.string.system_perm_protected_step1),
                    context.getString(R.string.system_perm_protected_step2),
                    context.getString(R.string.system_perm_protected_step3),
                    context.getString(R.string.system_perm_protected_step4)
                )
            )
        }
    }
}
