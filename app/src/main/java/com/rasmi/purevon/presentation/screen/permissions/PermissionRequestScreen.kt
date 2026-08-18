package com.rasmi.purevon.presentation.screen.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rasmi.purevon.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.util.DefaultAppManager

/**
 * Permission Request Screen - Comprehensive permission setup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onPermissionsGranted: () -> Unit,
    onRequestDefaultDialer: () -> Unit = {},
    onRequestDefaultSms: () -> Unit = {},
    viewModel: PermissionRequestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Track a refresh key to force recomposition after default app changes
    var refreshKey by remember { mutableIntStateOf(0) }
    
    fun permissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshKey++
    }

    val permissionsList = remember {
        buildList {
            // Phone permissions
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            
            // Contacts permissions
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            
            // SMS permissions
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.RECEIVE_MMS)
            
            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val allRuntimePermissionsGranted = remember(permissionsList, refreshKey) {
        permissionsList.all { permissionGranted(it) }
    }
    val defaultAppsSet = uiState.isDefaultDialer && uiState.isDefaultSms

    // Refresh status when returning from settings (manual overlay/battery/full-screen flows).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(PermissionRequestUiEvent.RefreshStatus)
        refreshKey++
    }
    // Check if all permissions are granted independently
    LaunchedEffect(
        allRuntimePermissionsGranted,
        uiState.isDefaultDialer,
        uiState.isDefaultSms,
        uiState.isOverlayGranted,
        uiState.isBatteryOptimizationIgnored,
        uiState.isFullScreenIntentGranted
    ) {
        // All conditions must be met separately
        val runtimeOk = allRuntimePermissionsGranted
        val specialPermissionsGranted = uiState.isOverlayGranted && 
            uiState.isBatteryOptimizationIgnored && 
            uiState.isFullScreenIntentGranted
        
        if (runtimeOk && defaultAppsSet && specialPermissionsGranted) {
            onPermissionsGranted()
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perm_setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Header
            item {
                PermissionHeaderCard()
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.perm_manual_special_intro),
                        modifier = Modifier.padding(Spacing.medium),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Default Apps Section
            item {
                DefaultAppsSection(
                    isDefaultDialer = uiState.isDefaultDialer,
                    isDefaultSms = uiState.isDefaultSms,
                    onRequestDefaultDialer = {
                        onRequestDefaultDialer()
                        viewModel.onEvent(PermissionRequestUiEvent.RefreshStatus)
                    },
                    onRequestDefaultSms = {
                        onRequestDefaultSms()
                        viewModel.onEvent(PermissionRequestUiEvent.RefreshStatus)
                    }
                )
            }
            
            // Autostart Permission Section
            item {
                AutostartPermissionCard(
                    onOpenAutostartSettings = {
                        try {
                            // Try to open autostart settings (manufacturer-specific)
                            val manufacturer = Build.MANUFACTURER.lowercase()
                            val intent = when {
                                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                                    Intent().apply {
                                        setClassName(
                                            "com.miui.securitycenter",
                                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                        )
                                    }
                                }
                                manufacturer.contains("oppo") -> {
                                    Intent().apply {
                                        setClassName(
                                            "com.coloros.safecenter",
                                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                        )
                                    }
                                }
                                manufacturer.contains("vivo") -> {
                                    Intent().apply {
                                        setClassName(
                                            "com.vivo.permissionmanager",
                                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                                        )
                                    }
                                }
                                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                                    Intent().apply {
                                        setClassName(
                                            "com.huawei.systemmanager",
                                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                                        )
                                    }
                                }
                                manufacturer.contains("samsung") -> {
                                    // Samsung: Battery optimization settings
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                }
                                else -> {
                                    // Fallback to battery optimization
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to app settings
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } catch (e2: Exception) {
                                android.util.Log.e("PermissionRequest", "Failed to open autostart settings", e2)
                            }
                        }
                    }
                )
            }
            
            // Special Permissions Section
            item {
                SpecialPermissionsSection(
                    isOverlayGranted = uiState.isOverlayGranted,
                    isBatteryOptimizationIgnored = uiState.isBatteryOptimizationIgnored,
                    isFullScreenIntentGranted = uiState.isFullScreenIntentGranted,
                    onRequestOverlay = {
                        try {
                            // Try specific overlay permission settings first
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to general app settings if not found
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    },
                    onRequestBatteryOptimization = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to battery optimization settings
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    onRequestFullScreenIntent = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                // For older versions, go to app notification settings
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            // Fallback to general app settings
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                )
            }
            
            // Permissions Groups
            item {
                PermissionGroupCard(
                    title = stringResource(R.string.perm_header_phone),
                    icon = Icons.Default.Phone,
                    description = stringResource(R.string.perm_desc_phone),
                    permissions = listOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.WRITE_CALL_LOG,
                        Manifest.permission.CALL_PHONE
                    ),
                    isGranted = { permissionGranted(it) }
                )
            }
            
            item {
                PermissionGroupCard(
                    title = stringResource(R.string.perm_header_contacts),
                    icon = Icons.Default.Contacts,
                    description = stringResource(R.string.perm_desc_contacts),
                    permissions = listOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS
                    ),
                    isGranted = { permissionGranted(it) }
                )
            }
            
            item {
                PermissionGroupCard(
                    title = stringResource(R.string.perm_header_sms),
                    icon = Icons.Default.Sms,
                    description = stringResource(R.string.perm_desc_sms),
                    permissions = listOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.RECEIVE_MMS
                    ),
                    isGranted = { permissionGranted(it) }
                )
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionGroupCard(
                        title = stringResource(R.string.perm_header_notification),
                        icon = Icons.Default.Notifications,
                        description = stringResource(R.string.perm_desc_notification),
                        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                        isGranted = { permissionGranted(it) }
                    )
                }
            }
            
            // Grant All Button
            item {
                Spacer(modifier = Modifier.height(Spacing.medium))
                
                Button(
                    onClick = {
                        if (defaultAppsSet && !allRuntimePermissionsGranted) {
                            runtimePermissionLauncher.launch(permissionsList.toTypedArray())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = defaultAppsSet && !allRuntimePermissionsGranted
                ) {
                    Icon(
                        imageVector = if (allRuntimePermissionsGranted) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Security
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        text = if (allRuntimePermissionsGranted) {
                            stringResource(R.string.perm_button_granted)
                        } else {
                            stringResource(R.string.perm_button_grant_runtime)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (!defaultAppsSet) {
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = stringResource(R.string.perm_default_apps_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Progress indicator
            item {
                val grantedCount = permissionsList.count { permissionGranted(it) }
                val totalCount = permissionsList.size
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { grantedCount.toFloat() / totalCount.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = stringResource(R.string.perm_progress_granted, grantedCount, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.medium))
            Text(
                text = stringResource(R.string.perm_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = stringResource(R.string.perm_welcome_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DefaultAppsSection(
    isDefaultDialer: Boolean,
    isDefaultSms: Boolean,
    onRequestDefaultDialer: () -> Unit,
    onRequestDefaultSms: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium)
        ) {
            Text(
                text = stringResource(R.string.perm_header_default_apps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = stringResource(R.string.perm_default_apps_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Default Dialer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isDefaultDialer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Column {
                        Text(
                            text = stringResource(R.string.perm_default_dialer),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isDefaultDialer) stringResource(R.string.perm_status_active) else stringResource(R.string.perm_status_not_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefaultDialer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (isDefaultDialer) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Set",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Button(onClick = onRequestDefaultDialer) {
                        Text(stringResource(R.string.perm_button_set))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.medium))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Default SMS Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isDefaultSms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Column {
                        Text(
                            text = stringResource(R.string.perm_default_sms),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isDefaultSms) stringResource(R.string.perm_status_active) else stringResource(R.string.perm_status_not_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefaultSms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (isDefaultSms) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Set",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Button(onClick = onRequestDefaultSms) {
                        Text(stringResource(R.string.perm_button_set))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupCard(
    title: String,
    icon: ImageVector,
    description: String,
    permissions: List<String>,
    isGranted: (String) -> Boolean
) {
    val allGranted = permissions.all { permission -> isGranted(permission) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (allGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SpecialPermissionsSection(
    isOverlayGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isFullScreenIntentGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestFullScreenIntent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium)
        ) {
            Text(
                text = stringResource(R.string.perm_header_special),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = stringResource(R.string.perm_desc_special),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.medium))
            
            // Display over other apps
            Button(
                onClick = onRequestOverlay,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isOverlayGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isOverlayGranted) Icons.Default.Check else Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isOverlayGranted) stringResource(R.string.perm_display_over_apps_granted) else stringResource(R.string.perm_display_over_apps))
            }
            
            Spacer(modifier = Modifier.height(Spacing.small))
            
            // Full screen intent (for lock screen calls)
            if (!isFullScreenIntentGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Warning card for lock screen calls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ بدون هذا الإذن لن تظهر المكالمات على شاشة القفل!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.small))
            }
            
            Button(
                onClick = onRequestFullScreenIntent,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFullScreenIntentGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFullScreenIntentGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isFullScreenIntentGranted) Icons.Default.Check else Icons.Default.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isFullScreenIntentGranted) stringResource(R.string.perm_lock_screen_calls_granted) else stringResource(R.string.perm_lock_screen_calls))
            }
            
            Spacer(modifier = Modifier.height(Spacing.small))
            
            // Battery optimization
            Button(
                onClick = onRequestBatteryOptimization,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBatteryOptimizationIgnored) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isBatteryOptimizationIgnored) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isBatteryOptimizationIgnored) Icons.Default.Check else Icons.Default.BatteryAlert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isBatteryOptimizationIgnored) stringResource(R.string.perm_ignore_battery_granted) else stringResource(R.string.perm_ignore_battery))
            }
        }
    }
}

@Composable
private fun AutostartPermissionCard(
    onOpenAutostartSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = Spacing.small)
            ) {
                Text(
                    text = "⚙️",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = Spacing.small)
                )
                Text(
                    text = stringResource(R.string.perm_autostart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = stringResource(R.string.perm_autostart_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.small)
            )
            
            Column(
                modifier = Modifier.padding(start = Spacing.medium, bottom = Spacing.small)
            ) {
                Text(
                    text = stringResource(R.string.perm_autostart_step1, Build.MANUFACTURER),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = stringResource(R.string.perm_autostart_step2),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = stringResource(R.string.perm_autostart_step3),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            
            Text(
                text = stringResource(R.string.perm_autostart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(bottom = Spacing.small)
            )
            
            Button(
                onClick = onOpenAutostartSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(stringResource(R.string.perm_button_open_autostart))
            }
        }
    }
}

@Composable
private fun PermissionGuideItem(title: String, description: String) {
    Column(
        modifier = Modifier.padding(vertical = Spacing.extraSmall)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.medium)
        )
    }
}
