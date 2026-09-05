package com.rasmi.purevon.presentation.screen.conversation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact

/**
 * Dialog for creating a new message template.
 * Extracted from ConversationScreen.kt for maintainability.
 */
@Composable
internal fun CreateTemplateDialog(
    onConfirm: (title: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTemplateTitle by remember { mutableStateOf("") }
    var newTemplateContent by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.create_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTemplateTitle,
                    onValueChange = { newTemplateTitle = it },
                    label = { Text(stringResource(R.string.template_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newTemplateContent,
                    onValueChange = { newTemplateContent = it },
                    label = { Text(stringResource(R.string.template_content)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newTemplateTitle.isNotBlank() && newTemplateContent.isNotBlank()) {
                        onConfirm(newTemplateTitle.trim(), newTemplateContent.trim())
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Preview dialog shown before sharing a contact as vCard.
 * Displays avatar, name, phone, email, company with send/cancel buttons.
 * Extracted from ConversationScreen.kt for maintainability.
 */
@Composable
internal fun ContactPreviewDialog(
    contact: Contact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.contact_share_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Contact avatar
                com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
                    size = 64.dp,
                    photoUri = contact.photoUri,
                    isFavorite = contact.isFavorite
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Contact name
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Phone
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Email if present
                contact.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Company if present
                contact.company?.let { company ->
                    Text(
                        text = company,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.contact_share_cancel))
                    }

                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.contact_share_send))
                    }
                }
            }
        }
    }
}
