package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rasmi.purevon.domain.model.Contact

/**
 * Contact Selection Dialog for Forwarding Messages
 * WhatsApp/Telegram style multi-select contact picker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionDialog(
    contacts: List<Contact>,
    onContactsSelected: (List<Contact>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "إعادة التوجيه إلى"
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf<Set<Contact>>(emptySet()) }
    val isLoading = contacts.isEmpty() // Show loading when contacts are empty initially
    
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                contact.phoneNumber.contains(searchQuery)
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(
                        onClick = { 
                            if (selectedContacts.isNotEmpty()) {
                                onContactsSelected(selectedContacts.toList())
                            }
                        },
                        enabled = selectedContacts.isNotEmpty()
                    ) {
                        Text("إرسال (${selectedContacts.size})")
                    }
                }
                
                HorizontalDivider()
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("بحث عن جهة اتصال...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                
                // Selected Contacts Chips
                if (selectedContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedContacts.take(3).forEach { contact ->
                            AssistChip(
                                onClick = { selectedContacts = selectedContacts - contact },
                                label = { 
                                    Text(
                                        contact.name,
                                        maxLines = 1,
                                        fontSize = 13.sp
                                    ) 
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        
                        if (selectedContacts.size > 3) {
                            AssistChip(
                                onClick = { },
                                label = { Text("+${selectedContacts.size - 3}") }
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Contacts List or Loading
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "جاري تحميل جهات الاتصال...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            ContactSelectionItem(
                                contact = contact,
                                isSelected = contact in selectedContacts,
                                onToggle = {
                                    selectedContacts = if (contact in selectedContacts) {
                                        selectedContacts - contact
                                    } else {
                                        selectedContacts + contact
                                    }
                                }
                            )
                        }
                        
                        if (filteredContacts.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "لا توجد نتائج" else "لا توجد جهات اتصال",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSelectionItem(
    contact: Contact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else 
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            UnifiedContactAvatar(
                size = 48.dp,
                photoUri = contact.photoUri
            )
            
            // Contact Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            
            // Checkbox
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}
