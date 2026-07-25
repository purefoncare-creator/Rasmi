package com.rasmi.purevon.presentation.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * vCard contact attachment bubble.
 * Displays a contact card with avatar, name, phone and action buttons.
 *
 * Extracted from MessageBubble.kt for maintainability.
 */
@Composable
fun ContactMessageBubble(
    uri: String,
    fileName: String,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var vCardInfo by remember { mutableStateOf<Triple<String?, String?, String?>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri) {
        vCardInfo = withContext(Dispatchers.IO) {
            try {
                val parsedUri = android.net.Uri.parse(uri)
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    val fn = Regex("FN:(.*)").find(content)?.groupValues?.get(1)?.trim()
                        ?.replace("\\\\;", ";")?.replace("\\\\,", ",")?.replace("\\\\n", " ")
                    val phone = Regex("TEL[^:]*:(.*)").find(content)?.groupValues?.get(1)?.trim()
                    Triple(fn, phone, content)
                }
            } catch (e: Exception) {
                null
            }
        }
        isLoading = false
    }

    val contactName = vCardInfo?.first
        ?: fileName.replace(".vcf", "").replace(".vcard", "").replace("_", " ")
    val contactPhone = vCardInfo?.second

    val backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contactName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contactPhone != null) {
                    Text(
                        text = contactPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = stringResource(R.string.msg_attachment_contact),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        val parsedUri = android.net.Uri.parse(uri)
                        val finalUri = if (parsedUri.scheme == "file") {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(parsedUri.path!!)
                            )
                        } else {
                            parsedUri
                        }
                        intent.setDataAndType(finalUri, "text/vcard")
                        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("ContactMessageBubble", "Error opening contact", e)
                        Toast.makeText(context, context.getString(R.string.contact_open_error), Toast.LENGTH_SHORT).show()
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = stringResource(R.string.action_view), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                        intent.type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                        if (contactName.isNotBlank()) {
                            intent.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, contactName)
                        }
                        if (contactPhone != null) {
                            intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, contactPhone)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("ContactMessageBubble", "Error adding contact", e)
                        Toast.makeText(context, context.getString(R.string.msg_error_open_contact), Toast.LENGTH_SHORT).show()
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary,
                    contentColor = if (isOutgoing) MaterialTheme.colorScheme.primary else Color.White
                )
            ) {
                Text(text = stringResource(R.string.action_add_contact), fontSize = 12.sp)
            }
        }
    }
}
