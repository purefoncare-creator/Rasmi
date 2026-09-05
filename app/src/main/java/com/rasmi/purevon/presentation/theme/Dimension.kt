package com.rasmi.purevon.presentation.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    // Legacy names (use new names above)
    val extraSmall get() = xs
    val small get() = sm
    val medium get() = md
    val default get() = lg
    val large get() = lg
    val extraLarge get() = xl
}

object CornerRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val pill = 999.dp
    // Legacy names (use new names above)
    val Small get() = sm
    val Medium get() = md
    val Large get() = lg
    val ExtraLarge get() = xl
    val XXL get() = xl
    val Continuous get() = pill
}

object Elevation {
    val none = 0.dp
    val sm = 1.dp
    val md = 2.dp
    val lg = 4.dp
    val xl = 8.dp
}

object ButtonSize {
    val height = 48.dp
    val heightSmall = 36.dp
    val heightLarge = 56.dp
    val minWidth = 48.dp
}

object ButtonHeight {
    val Small get() = ButtonSize.heightSmall
    val Medium get() = ButtonSize.height
    val Large get() = ButtonSize.heightLarge
    val ExtraLarge get() = ButtonSize.heightLarge
}

object IconSize {
    val sm = 18.dp
    val md = 22.dp
    val lg = 28.dp
    val xl = 36.dp
    val avatar = 44.dp
    // Legacy names
    val Small get() = sm
    val Medium get() = md
    val Large get() = lg
    val ExtraLarge get() = xl
    val XXL get() = xl
}

object AppBarSize {
    val height = 56.dp
}

object BottomBarSize {
    val height = 64.dp
}

object TouchTarget {
    val min = 48.dp
}

object AnimationDuration {
    const val Fast = 150
    const val Normal = 250
    const val Slow = 350
}

// ═══════════════════════════════════════════════════════════════════
// MESSAGING DESIGN TOKENS (Wire-inspired)
// ═══════════════════════════════════════════════════════════════════

object MessagingDimensions {
    // Spacing
    val spacing1x = 1.dp
    val spacing2x = 2.dp
    val spacing4x = 4.dp
    val spacing6x = 6.dp
    val spacing8x = 8.dp
    val spacing10x = 10.dp
    val spacing12x = 12.dp
    val spacing14x = 14.dp
    val spacing16x = 16.dp
    val spacing24x = 24.dp
    val spacing48x = 48.dp
    val spacing56x = 56.dp

    // Corner radius
    val corner4x = 4.dp
    val corner8x = 8.dp
    val corner10x = 10.dp
    val corner12x = 12.dp
    val corner14x = 14.dp
    val corner16x = 16.dp
    val corner24x = 24.dp
    val corner100x = 100.dp

    // Bubble
    val bubbleMaxWidthFraction = 0.75f
    val bubbleInternalPadding = 10.dp
    val bubbleCornerRadius = 16.dp
    val messageItemBottomPadding = 6.dp
    val messageItemHorizontalPadding = 12.dp

    // Avatar
    val avatarConversationList = 48.dp
    val avatarConversationListClickablePadding = 8.dp
    val avatarTopBar = 24.dp
    val avatarTopBarClickablePadding = 8.dp
    val avatarMessageBubble = 32.dp
    val avatarBorderWidth = 1.dp

    // Composer
    val composerMaxHeight = 128.dp
    val composerDividerThickness = 1.dp

    // Unread badge
    val unreadBadgeSize = 16.dp
    val unreadBadgeCornerRadius = 6.dp
    val unreadBadgeHeight = 18.dp

    // Typing indicator
    val typingIndicatorHeight = 24.dp
    val typingIndicatorCornerRadius = 14.dp

    // Delivery status icon
    val statusIconSize = 14.dp

    // Conversation list item
    val conversationItemHeight = 80.dp
    val conversationItemDividerStartPadding = 64.dp

    // Top bar
    val topBarHeight = 64.dp
    val topBarButtonMinWidth = 40.dp
    val topBarButtonMinHeight = 32.dp
    val topBarButtonClickableHeight = 48.dp
}

object MessagingTypography {
    // Message body text
    val body01 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.05.sp
    )

    // Author name / conversation title
    val body02 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.05.sp
    )

    // Timestamps, status labels
    val subline01 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    // Typing indicator text
    val label01 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp
    )

    // Reaction pill count
    val label02 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp
    )

    // Unread badge count
    val badge01 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 11.72.sp,
        letterSpacing = 0.sp
    )

    // Top bar title
    val title02 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    // Section headers
    val title03 = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
}
