package com.rasmi.purevon.presentation.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.domain.model.MessageTemplate

/**
 * Template Picker Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    templates: List<MessageTemplate>,
    onTemplateSelected: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قوالب الرسائل",
                    style = MaterialTheme.typography.titleLarge
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (templates.isEmpty()) {
                EmptyTemplatesView()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { onTemplateSelected(template) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Single Template Card
 */
@Composable
fun TemplateCard(
    template: MessageTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji icon
            template.emoji?.let { emoji ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = template.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                
                if (template.useCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "استخدمت ${template.useCount} مرة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Favorite icon
            if (template.isFavorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Empty templates view
 */
@Composable
fun EmptyTemplatesView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Message,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        
        Text(
            text = "لا توجد قوالب",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Text(
            text = "يمكنك إضافة قوالب للرسائل المتكررة",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Delayed Send Picker Dialog
 */
@Composable
fun DelayedSendPickerDialog(
    onTimeSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال متأخر") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DelayOption("بعد دقيقة", com.rasmi.purevon.util.message.DelayedSendManager.DelayPresets.ONE_MINUTE, onTimeSelected, onDismiss)
                DelayOption("بعد 5 دقائق", com.rasmi.purevon.util.message.DelayedSendManager.DelayPresets.FIVE_MINUTES, onTimeSelected, onDismiss)
                DelayOption("بعد 10 دقائق", com.rasmi.purevon.util.message.DelayedSendManager.DelayPresets.TEN_MINUTES, onTimeSelected, onDismiss)
                DelayOption("بعد 30 دقيقة", com.rasmi.purevon.util.message.DelayedSendManager.DelayPresets.THIRTY_MINUTES, onTimeSelected, onDismiss)
                DelayOption("بعد ساعة", com.rasmi.purevon.util.message.DelayedSendManager.DelayPresets.ONE_HOUR, onTimeSelected, onDismiss)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun DelayOption(
    label: String,
    delayMillis: Long,
    onTimeSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                onTimeSelected(delayMillis)
                onDismiss()
            },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
