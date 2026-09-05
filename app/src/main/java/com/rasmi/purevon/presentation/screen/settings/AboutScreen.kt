package com.rasmi.purevon.presentation.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.BuildConfig
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.Spacing
import com.rasmi.purevon.util.InAppReviewRequester
import kotlinx.coroutines.delay

/**
 * About Screen - App information and credits
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(context) {
        delay(800)
        InAppReviewRequester.requestIfEligible(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(Spacing.large)
    ) {
        // App Icon and Name
        item {
            Spacer(modifier = Modifier.height(Spacing.medium))
                
                // App Icon
                Image(
                    painter = painterResource(id = R.drawable.icon1),
                    contentDescription = "Rasmi",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                )
                
                Spacer(modifier = Modifier.height(Spacing.medium))
                
                Text(
                    text = "Rasmi",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = stringResource(R.string.about_build, BuildConfig.VERSION_CODE.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(Spacing.large))
            }
            
            // Description
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(Spacing.small))
                        
                        Text(
                            text = stringResource(R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.large))
            }
            
            // Features Section
            item {
                Text(
                    text = stringResource(R.string.about_key_features),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(Spacing.small))
            }
            
            item {
                FeatureItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.about_feature_blocking),
                    description = stringResource(R.string.about_feature_blocking_desc)
                )
            }
            
            item {
                FeatureItem(
                    icon = Icons.Default.Password,
                    title = stringResource(R.string.about_feature_otp),
                    description = stringResource(R.string.about_feature_otp_desc)
                )
            }
            
            item {
                FeatureItem(
                    icon = Icons.Default.Widgets,
                    title = stringResource(R.string.about_feature_bubble),
                    description = stringResource(R.string.about_feature_bubble_desc)
                )
            }
            
            item {
                FeatureItem(
                    icon = Icons.Default.SimCard,
                    title = stringResource(R.string.about_feature_sim),
                    description = stringResource(R.string.about_feature_sim_desc)
                )
            }
            

            
            item {
                Spacer(modifier = Modifier.height(Spacing.large))
            }
            

            
            // Contact
            item {
                val email = "purefon.care@gmail.com"
                
                Text(
                    text = stringResource(R.string.about_contact_us),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$email")
                                putExtra(Intent.EXTRA_SUBJECT, "Purevon - Contact")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                )
                
                Spacer(modifier = Modifier.height(Spacing.small))
                
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(Spacing.extraLarge))
            }
        }
    }


@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.extraSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(Spacing.medium))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


