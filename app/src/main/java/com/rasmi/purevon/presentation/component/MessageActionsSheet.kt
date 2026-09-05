package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.presentation.theme.PurevonBorder
import com.rasmi.purevon.presentation.theme.PurevonError
import com.rasmi.purevon.presentation.theme.PurevonPrimaryVariant
import com.rasmi.purevon.presentation.theme.PurevonSurface
import com.rasmi.purevon.presentation.theme.PurevonTextPrimary
import com.rasmi.purevon.presentation.theme.PurevonTextSecondary
import com.rasmi.purevon.presentation.theme.MessagingTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onMessageDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMoreEmojis by remember { mutableStateOf(false) }
    val defaultEmojis = listOf("\uD83D\uDC4D", "\uD83D\uDE42", "\u2764\uFE0F", "\u2639\uFE0F", "\uD83D\uDC4E")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = PurevonSurface,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Wire-style: Reactions row at top
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "REACTIONS",
                    style = MessagingTypography.label01,
                    color = PurevonTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    defaultEmojis.forEach { emoji ->
                        Button(
                            onClick = {
                                onReact(emoji)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                            contentPadding = PaddingValues(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurevonSurface,
                                contentColor = PurevonTextPrimary
                            )
                        ) {
                            Text(emoji, style = TextStyle(fontSize = 28.sp))
                        }
                    }
                    IconButton(
                        onClick = { showMoreEmojis = true }
                    ) {
                        Icon(
                            Icons.Default.AddReaction,
                            contentDescription = "More reactions",
                            tint = PurevonTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = PurevonBorder, thickness = 0.5.dp)

            // Wire-style: Action items (exact order: Details, Copy, Reply, Edit, Delete)
            SheetActionItem(
                icon = Icons.Default.Info,
                title = "Message Details",
                onClick = { onMessageDetails(); onDismiss() }
            )
            if (!message.body.isNullOrBlank()) {
                SheetActionItem(
                    icon = Icons.Default.ContentCopy,
                    title = "Copy text",
                    onClick = { onCopy(); onDismiss() }
                )
            }
            SheetActionItem(
                icon = Icons.AutoMirrored.Filled.Reply,
                title = "Reply",
                onClick = { onReply(); onDismiss() }
            )
            SheetActionItem(
                icon = Icons.Default.Edit,
                title = "Edit text",
                onClick = { onDismiss() }
            )
            SheetActionItem(
                icon = Icons.Default.Delete,
                title = "Delete",
                destructive = true,
                onClick = { onDelete(); onDismiss() }
            )
        }
    }

    if (showMoreEmojis) {
        MoreEmojisSheet(
            onEmojiSelected = { emoji ->
                onReact(emoji)
                showMoreEmojis = false
                onDismiss()
            },
            onDismiss = { showMoreEmojis = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreEmojisSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allEmojis = listOf(
        "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE22",
        "\uD83D\uDE31", "\uD83D\uDE4C", "\uD83C\uDF89", "\uD83D\uDC4F", "\uD83D\uDE4F",
        "\uD83D\uDC4A", "\uD83D\uDCAA", "\uD83D\uDCA5", "\uD83D\uDE0D", "\uD83E\uDD14",
        "\uD83D\uDE0E", "\uD83E\uDD73", "\uD83D\uDE2D", "\uD83D\uDCA9", "\uD83D\uDC80",
        "\uD83D\uDE21", "\uD83E\uDD2F", "\uD83D\uDE33", "\uD83E\uDD11", "\uD83E\uDD17",
        "\uD83D\uDC40", "\uD83D\uDCA4", "\uD83D\uDCAB", "\uD83C\uDF1F", "\uD83D\uDE80"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PurevonSurface,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select reaction",
                style = MessagingTypography.body01,
                color = PurevonTextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val columns = 7
            for (row in allEmojis.chunked(columns)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        Button(
                            onClick = { onEmojiSelected(emoji) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
                            contentPadding = PaddingValues(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurevonSurface,
                                contentColor = PurevonTextPrimary
                            )
                        ) {
                            Text(emoji, style = TextStyle(fontSize = 28.sp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    title: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val itemColor = if (destructive) PurevonError else PurevonTextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = itemColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MessagingTypography.body01,
            color = itemColor,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = PurevonBorder, thickness = 0.5.dp)
}
