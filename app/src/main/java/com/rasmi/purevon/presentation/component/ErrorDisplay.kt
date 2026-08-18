package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.util.error.AppError
import com.rasmi.purevon.util.error.ErrorAction
import com.rasmi.purevon.util.error.ErrorType

/**
 * Display error message with action buttons
 */
@Composable
fun ErrorDisplay(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onGoToSettings: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Error Icon
            Icon(
                imageVector = when (error.type) {
                    ErrorType.PERMISSION -> Icons.Default.Lock
                    ErrorType.NETWORK -> Icons.Default.CloudOff
                    ErrorType.DATABASE -> Icons.Default.Storage
                    ErrorType.VALIDATION -> Icons.Default.Warning
                    ErrorType.STATE -> Icons.Default.Info
                    ErrorType.BUSINESS_LOGIC -> Icons.Default.Warning
                    ErrorType.UNKNOWN -> Icons.Default.ErrorOutline
                },
                contentDescription = "Error icon",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            // Error Message
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                when (error.action) {
                    ErrorAction.RETRY -> {
                        Button(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                    ErrorAction.REQUEST_PERMISSION -> {
                        Button(onClick = onRequestPermission) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Permission")
                        }
                    }
                    ErrorAction.GO_TO_SETTINGS -> {
                        Button(onClick = onGoToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Settings")
                        }
                    }
                    ErrorAction.RESTART_APP -> {
                        Button(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restart App")
                        }
                    }
                    ErrorAction.DISMISS, null -> {
                        // No primary action button
                    }
                }
                
                // Always show dismiss button
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

/**
 * Error snackbar for quick error display
 */
@Composable
fun ErrorSnackbar(
    @Suppress("UNUSED_PARAMETER") error: AppError,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") onActionClick: () -> Unit = {}
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            actionColor = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Full screen error display
 */
@Composable
fun FullScreenError(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            if (error.action == ErrorAction.RETRY) {
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}
