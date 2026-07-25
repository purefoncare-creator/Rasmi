package com.rasmi.purevon.presentation.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.util.sim.SimInfo

/**
 * Dialog for selecting SIM card for call/message
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
@Composable
fun SimSelectorDialog(
    availableSims: List<SimInfo>,
    selectedSimId: Int?,
    onSimSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Select SIM Card",
    allowAllSimsOption: Boolean = false,
    allSimsText: String = "All SIMs",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SimCard, contentDescription = "SIM") },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                if (availableSims.isEmpty()) {
                    Text(
                        text = "No SIM cards available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (allowAllSimsOption) {
                        SimItem(
                            sim = SimInfo(-1, -1, allSimsText, "", "Apply to all SIMs", false),
                            isSelected = selectedSimId == -1,
                            onClick = { onSimSelected(-1) }
                        )
                    }
                    availableSims.forEach { sim ->
                        SimItem(
                            sim = sim,
                            isSelected = sim.subscriptionId == selectedSimId,
                            onClick = { onSimSelected(sim.subscriptionId) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SimItem(
    sim: SimInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Text(
                        text = sim.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (sim.isDefault) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (sim.carrierName.isNotBlank()) {
                    Text(
                        text = sim.carrierName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (sim.phoneNumber != "") {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = sim.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
