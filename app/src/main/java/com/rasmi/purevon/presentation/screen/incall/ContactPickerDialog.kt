package com.rasmi.purevon.presentation.screen.incall

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Contact

/**
 * ✅ Unified Contact Picker Dialog - دمج AddCallDialog + ContactsQuickViewDialog
 * ✅ FIX: Uses string resources (Issue #15)
 * ✅ FIX: Shows loading indicator (Issue #9)
 * ✅ FIX: Always dismisses after selection (Issue #19)
 */
@Composable
fun ContactPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit,
    onManualNumber: ((String) -> Unit)? = null,
    contacts: List<Contact>,
    showCallIcon: Boolean = true,
    allowManualNumber: Boolean = false,
    isLoading: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.contact_share_cancel)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            if (allowManualNumber) stringResource(R.string.contact_picker_search_or_number) 
                            else stringResource(R.string.contact_picker_search)
                        ) 
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Manual number input button (if enabled)
                if (allowManualNumber && 
                    searchQuery.matches(Regex("^[0-9+\\-()\\s]+$")) && 
                    searchQuery.isNotEmpty() &&
                    onManualNumber != null) {
                    
                    Button(
                        onClick = { 
                            onManualNumber(searchQuery.filter { it.isDigit() || it == '+' })
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call $searchQuery")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // ✅ FIX: Show loading indicator while contacts are being loaded (Issue #9)
                if (isLoading || (contacts.isEmpty() && searchQuery.isEmpty())) {
                    // Show loading if explicitly loading or if contacts haven't arrived yet
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.contact_picker_loading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Show empty state
                        ShowEmptyState()
                    }
                } else {
                    // Contacts list
                    val filteredContacts by remember {
                        derivedStateOf {
                            if (searchQuery.isEmpty()) {
                                contacts
                            } else {
                                contacts.filter { contact ->
                                    contact.name.contains(searchQuery, ignoreCase = true) ||
                                    contact.phoneNumber.contains(searchQuery)
                                }
                            }
                        }
                    }
                    
                    if (filteredContacts.isEmpty()) {
                        ShowEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredContacts) { contact ->
                                ContactPickerItem(
                                    contact = contact,
                                    showCallIcon = showCallIcon,
                                    onClick = {
                                        onContactSelected(contact)
                                        // ✅ FIX: Always dismiss after selection (Issue #19)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.contact_picker_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactPickerItem(
    contact: Contact,
    showCallIcon: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
                size = 48.dp,
                photoUri = contact.photoUri,
                isFavorite = contact.isFavorite
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Contact info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = if (showCallIcon) Icons.Default.Call else Icons.Default.Info,
                contentDescription = if (showCallIcon) "Call" else "Contact Details",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    HorizontalDivider()
}
