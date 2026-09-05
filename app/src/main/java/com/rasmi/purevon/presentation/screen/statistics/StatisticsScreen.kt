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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.rasmi.purevon.R.string.stat_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.rasmi.purevon.R.string.nav_back))
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
                    text = stringResource(com.rasmi.purevon.R.string.stat_call_statistics),
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
                                label = stringResource(com.rasmi.purevon.R.string.stat_total_calls),
                                value = stats.totalCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.CallReceived,
                                label = stringResource(com.rasmi.purevon.R.string.stat_incoming),
                                value = stats.incomingCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.CallMade,
                                label = stringResource(com.rasmi.purevon.R.string.stat_outgoing),
                                value = stats.outgoingCalls.toString()
                            )
                            StatRow(
                                icon = Icons.Filled.PhoneMissed,
                                label = stringResource(com.rasmi.purevon.R.string.stat_missed),
                                value = stats.missedCalls.toString()
                            )
                            if (stats.blockedCalls > 0) {
                                StatRow(
                                    icon = Icons.Default.Block,
                                    label = stringResource(com.rasmi.purevon.R.string.stat_blocked),
                                    value = stats.blockedCalls.toString()
                                )
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.small))
                            
                            StatRow(
                                icon = Icons.Default.Timer,
                                label = stringResource(com.rasmi.purevon.R.string.stat_total_duration),
                                value = formatDuration(stats.totalDuration)
                            )
                            StatRow(
                                icon = Icons.Default.AvTimer,
                                label = stringResource(com.rasmi.purevon.R.string.stat_average_duration),
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
                    text = stringResource(com.rasmi.purevon.R.string.stat_frequent_contacts),
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
                    text = stringResource(com.rasmi.purevon.R.string.stat_message_statistics),
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
                            label = stringResource(com.rasmi.purevon.R.string.stat_total_messages),
                            value = uiState.totalMessages.toString()
                        )
                        StatRow(
                            icon = Icons.Default.Inbox,
                            label = stringResource(com.rasmi.purevon.R.string.stat_received),
                            value = uiState.receivedMessages.toString()
                        )
                        StatRow(
                            icon = Icons.AutoMirrored.Filled.Send,
                            label = stringResource(com.rasmi.purevon.R.string.stat_sent),
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
                    text = stringResource(com.rasmi.purevon.R.string.stat_contact_calls_duration, contact.callCount, formatDuration(contact.totalDuration)),
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
        else -> String.format(java.util.Locale.getDefault(), "%ds", secs)
    }
}
