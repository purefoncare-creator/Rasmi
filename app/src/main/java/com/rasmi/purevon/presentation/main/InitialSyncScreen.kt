package com.rasmi.purevon.presentation.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.domain.repository.SyncRepository

/**
 * Loading screen shown during initial sync
 * Displays progress and sync status
 */
@Composable
fun InitialSyncScreen(
    syncProgress: SyncRepository.SyncProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "جاري المزامنة...",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            when (syncProgress) {
                is SyncRepository.SyncProgress.Running -> {
                    if (syncProgress.indeterminate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { 
                                    if (syncProgress.max > 0) {
                                        syncProgress.progress.toFloat() / syncProgress.max.toFloat()
                                    } else {
                                        0f
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            
                            Text(
                                text = if (syncProgress.max > 0) {
                                    "${syncProgress.progress} / ${syncProgress.max}"
                                } else {
                                    "جاري التحميل..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    if (syncProgress.stage.isNotEmpty()) {
                        Text(
                            text = syncProgress.stage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                is SyncRepository.SyncProgress.Idle -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "يتم مزامنة رسائلك للمرة الأولى.\nهذا يحدث مرة واحدة فقط وسيجعل التطبيق أسرع بعد ذلك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("إلغاء")
            }
        }
    }
}
