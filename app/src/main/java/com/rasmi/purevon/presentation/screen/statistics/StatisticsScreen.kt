package com.rasmi.purevon.presentation.screen.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.util.CallGrouper
import com.rasmi.purevon.util.ContactFrequency
import kotlin.OptIn

/**
 * Statistics Screen showing call and message statistics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
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
            // Call Statistics
            item {
                Text(
                    text = "Call Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                uiState.callStats?.let { stats ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            StatRow(
                                icon = Icons.Default.Phone,
                                label = "Total Calls",
                                value = stats.totalCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.CallReceived,
                                label = "Incoming",
                                value = stats.incomingCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.CallMade,
                                label = "Outgoing",
                                value = stats.outgoingCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.PhoneMissed,
                                label = "Missed",
                                value = stats.missedCalls.toString()
                            )
                            if (stats.blockedCalls > 0) {
                                StatRow(
                                    icon = Icons.Default.Block,
                                    label = "Blocked",
                                    value = stats.blockedCalls.toString()
                                )
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
                            
                            StatRow(
                                icon = Icons.Default.Timer,
                                label = "Total Duration",
                                value = formatDuration(stats.totalDuration)
                            )
                            StatRow(
                                icon = Icons.Default.AvTimer,
                                label = "Average Duration",
                                value = formatDuration(stats.averageDuration)
                            )
                        }
                    }
                }
            }
            
            // Most Frequent Contacts
            item {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    text = "Most Frequent Contacts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            items(uiState.frequentContacts) { contact ->
                FrequentContactCard(contact)
            }
            
            // Message Statistics
            item {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    text = "Message Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium)
                    ) {
                        StatRow(
                            icon = Icons.Filled.Message,
                            label = "Total Messages",
                            value = uiState.totalMessages.toString()
                        )
                        StatRow(
                            icon = Icons.Default.Inbox,
                            label = "Received",
                            value = uiState.receivedMessages.toString()
                        )
                        StatRow(
                            icon = Icons.AutoMirrored.Filled.Send,
                            label = "Sent",
                            value = uiState.sentMessages.toString()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun FrequentContactCard(contact: ContactFrequency) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name ?: contact.number,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${contact.callCount} calls • ${formatDuration(contact.totalDuration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Badge {
                Text(contact.callCount.toString())
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        minutes > 0 -> String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, secs)
        else -> "${secs}s"
    }
}
