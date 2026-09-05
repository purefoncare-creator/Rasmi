package com.rasmi.purevon.presentation.component

import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import android.content.ContentUris
import com.rasmi.purevon.R
import com.rasmi.purevon.presentation.theme.PurevonBorder
import com.rasmi.purevon.presentation.theme.PurevonError
import com.rasmi.purevon.presentation.theme.PurevonTextSecondary
import com.rasmi.purevon.presentation.theme.PurevonWarning

/**
 * Unified Contact Avatar used throughout the entire app.
 *
 * When a real photo is available (via [photoUri] or [contactId]),
 * it shows the photo clipped to a circle.
 *
 * When no photo is available, it shows the default avatar icon
 * (ic_default_avatar — a person silhouette on a gray circle).
 *
 * @param size        diameter of the circle
 * @param photoUri    string content URI of the contact photo (from conversations, etc.)
 * @param contactId   system contact ID — used to build a photo URI when [photoUri] is null
 * @param modifier    additional modifiers
 */
@Composable
fun UnifiedContactAvatar(
    size: Dp = 32.dp,
    photoUri: String? = null,
    contactId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Resolve photo URI: prefer explicit photoUri, fallback to contactId lookup
    val resolvedUri: Any? = photoUri
        ?: contactId?.let {
            ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, it
            ).let { contactUri ->
                Uri.withAppendedPath(
                    contactUri,
                    ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
                )
            }
        }

    if (resolvedUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolvedUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_default_avatar),
            error = painterResource(R.drawable.ic_default_avatar),
            fallback = painterResource(R.drawable.ic_default_avatar),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        DefaultAvatar(size = size, modifier = modifier)
    }
}

/**
 * The default avatar: dark (#1F2D3D) circle with a grey (#8B98A5) person
 * silhouette, matching the dark-mode design system.
 */
@Composable
fun DefaultAvatar(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(PurevonBorder),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = PurevonTextSecondary,
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

/**
 * Contact avatar wrapped in the gold "favorite" frame with a star badge,
 * matching the contact-list styling:
 *  - [isFavorite]: draws a PurevonWarning (gold) circular border + a white star
 *    badge at the bottom-end.
 *  - [isBlocked]: draws a red border + a block badge.
 *  - [showFilledBadge]: when a favorite photo/avatar is shown, fill the circle.
 *
 * @param size     diameter of the avatar (the whole frame).
 * @param photoUri string content URI of the contact photo.
 * @param contactId system contact id (fallback for building the photo URI).
 * @param isFavorite whether to show the gold border + star badge.
 * @param isBlocked whether to show the red border + block badge.
 * @param showBadge whether to overlay the favorite/blocked badge dot.
 */
@Composable
fun FavoriteContactAvatar(
    size: Dp = 32.dp,
    photoUri: String? = null,
    contactId: Long? = null,
    isFavorite: Boolean = false,
    isBlocked: Boolean = false,
    showBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    val avatarBorder = when {
        isFavorite -> BorderStroke(2.dp, PurevonWarning)
        isBlocked -> BorderStroke(2.dp, PurevonError)
        else -> null
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (avatarBorder != null) Modifier.border(avatarBorder, CircleShape)
                    else Modifier
                )
                .padding(if (avatarBorder != null) 2.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            UnifiedContactAvatar(
                size = if (avatarBorder != null) (size - 4.dp) else size,
                photoUri = photoUri,
                contactId = contactId
            )
        }

        if (showBadge) {
            val badgeIcon = when {
                isBlocked -> Icons.Default.Block
                isFavorite -> Icons.Filled.Star
                else -> null
            }
            if (badgeIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isBlocked) PurevonError else PurevonWarning)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}
