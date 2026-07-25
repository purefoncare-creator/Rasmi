package com.rasmi.purevon.presentation.component

import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    size: Dp = 40.dp,
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
 * The default avatar: gray circle with white person silhouette,
 * matching the reference image (profile-picture.png).
 */
@Composable
fun DefaultAvatar(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_default_avatar),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}
