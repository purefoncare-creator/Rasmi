package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.presentation.theme.Spacing

/**
 * Reusable empty state component for lists and screens
 * Provides consistent UX when no data is available
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    actionButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = Spacing.large),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Spacing.small)
        )
        
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = Spacing.large)
            )
        }
        
        if (actionButton != null) {
            actionButton()
        }
    }
}

/**
 * Predefined empty states for common scenarios
 */
object EmptyStates {
    
    @Composable
    fun NoContacts(
        onAddContact: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.ContactPhone,
            title = "No Contacts",
            description = "Add contacts to start making calls",
            actionButton = onAddContact?.let { action ->
                {
                    Button(onClick = action) {
                        Text("Add Contact")
                    }
                }
            },
            modifier = modifier
        )
    }
    
    @Composable
    fun NoMessages(
        onNewMessage: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.Message,
            title = "No Messages",
            description = "Start a conversation by sending a message",
            actionButton = onNewMessage?.let { action ->
                {
                    Button(onClick = action) {
                        Text("New Message")
                    }
                }
            },
            modifier = modifier
        )
    }
    
    @Composable
    fun NoCallHistory(
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.Phone,
            title = "No Call History",
            description = "Your call history will appear here",
            modifier = modifier
        )
    }
    
    @Composable
    fun NoSearchResults(
        searchQuery: String,
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = "No Results",
            description = "No results found for \"$searchQuery\"",
            modifier = modifier
        )
    }
    
    @Composable
    fun Error(
        errorMessage: String,
        onRetry: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.ErrorOutline,
            title = "Something went wrong",
            description = errorMessage,
            actionButton = onRetry?.let { action ->
                {
                    Button(onClick = action) {
                        Text("Retry")
                    }
                }
            },
            modifier = modifier
        )
    }
    
    @Composable
    fun NoPermission(
        permissionName: String,
        onOpenSettings: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        EmptyState(
            icon = Icons.Default.Lock,
            title = "Permission Required",
            description = "Please grant $permissionName permission to use this feature",
            actionButton = {
                Button(onClick = onOpenSettings) {
                    Text("Open Settings")
                }
            },
            modifier = modifier
        )
    }
}

/**
 * Loading state component
 */
@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = Spacing.medium)
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


