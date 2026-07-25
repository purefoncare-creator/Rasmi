package com.rasmi.purevon.presentation.screen.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.R
import com.rasmi.purevon.util.DisclosurePreferences

/**
 * Prominent in-app disclosure required by Google Play's User Data policy.
 *
 * Shown on first launch BEFORE any permission request or data access.
 * Requires an affirmative action (checkbox + button). Back press is blocked.
 * Accepted state is persisted synchronously via [DisclosurePreferences].
 */
@Composable
fun DataDisclosureScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    var consentChecked by remember { mutableStateOf(false) }

    // Block back navigation: user must explicitly act on the disclosure.
    BackHandler(enabled = true) { /* no-op */ }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.disclosure_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.disclosure_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Scrollable disclosure body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.disclosure_intro),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(16.dp))

                DisclosureItemCard(
                    icon = Icons.Default.Phone,
                    title = stringResource(R.string.disclosure_phone_title),
                    body = stringResource(R.string.disclosure_phone_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.ContactPhone,
                    title = stringResource(R.string.disclosure_contacts_title),
                    body = stringResource(R.string.disclosure_contacts_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.Sms,
                    title = stringResource(R.string.disclosure_sms_title),
                    body = stringResource(R.string.disclosure_sms_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.Call,
                    title = stringResource(R.string.disclosure_calllog_title),
                    body = stringResource(R.string.disclosure_calllog_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.disclosure_location_title),
                    body = stringResource(R.string.disclosure_location_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.PhotoCamera,
                    title = stringResource(R.string.disclosure_camera_title),
                    body = stringResource(R.string.disclosure_camera_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.disclosure_mic_title),
                    body = stringResource(R.string.disclosure_mic_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.disclosure_storage_title),
                    body = stringResource(R.string.disclosure_storage_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.Layers,
                    title = stringResource(R.string.disclosure_overlay_title),
                    body = stringResource(R.string.disclosure_overlay_body)
                )
                Spacer(Modifier.height(10.dp))
                DisclosureItemCard(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.disclosure_notifications_title),
                    body = stringResource(R.string.disclosure_notifications_body)
                )

                Spacer(Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.disclosure_no_upload),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Affirmative-action checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = consentChecked,
                    onCheckedChange = { consentChecked = it }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.disclosure_consent_checkbox),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    DisclosurePreferences.setAccepted(context, true)
                    onAccepted()
                },
                enabled = consentChecked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.disclosure_accept_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DisclosureItemCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
