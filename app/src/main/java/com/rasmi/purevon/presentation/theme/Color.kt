package com.rasmi.purevon.presentation.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// PUREVON — Modern Dark Mode (Bluesky-inspired)
// Single dark palette — No Light mode
// ═══════════════════════════════════════════════════════════════════

// ── Gray Scale (Wire exact — for reference) ─────────────────────
val WireGray10             = Color(0xFFFAFAFA)
val WireGray20             = Color(0xFFEDEFF0)
val WireGray30             = Color(0xFFE5E8EA)
val WireGray40             = Color(0xFFDCE0E3)
val WireGray50             = Color(0xFFCBCED1)
val WireGray60             = Color(0xFF9FA1A7)
val WireGray70             = Color(0xFF676B71)
val WireGray80             = Color(0xFF54585F)
val WireGray90             = Color(0xFF34373D)
val WireGray95             = Color(0xFF26272C)
val WireGray100            = Color(0xFF17181A)

// ── Blue Scale (Wire exact — for reference) ─────────────────────
val WireLightBlue400       = Color(0xFF76B8FF)
val WireLightBlue500       = Color(0xFF0667C8)
val WireLightBlue800       = Color(0xFF022950)
val WireLightBlue900       = Color(0xFF021F3C)
val WireDarkBlue300        = Color(0xFF98CAFF)
val WireDarkBlue400        = Color(0xFF76B8FF)
val WireDarkBlue500        = Color(0xFF54A6FF)
val WireDarkBlue600        = Color(0xFF4385CC)
val WireDarkBlue800        = Color(0xFF224266)

// ── Red Scale (Wire exact — for reference) ──────────────────────
val WireDarkRed500         = Color(0xFFFF7770)
val WireLightRed900        = Color(0xFF3A0006)

// ── Green Scale (Wire exact — for reference) ────────────────────
val WireDarkGreen500       = Color(0xFF30DB5B)
val WireDarkGreen900       = Color(0xFF0E421B)

// ── Amber Scale (Wire exact — for reference) ────────────────────
val WireDarkAmber500       = Color(0xFFFFD426)
val WireDarkAmber300       = Color(0xFFFFE57D)

// ── Backgrounds ─────────────────────────────────────────────────
val PurevonBackground     = Color(0xFF101721)   // Dark Navy (app background)
val PurevonBackgroundAlt  = Color(0xFF0C111A)   // Deeper navy
val PurevonSurface        = Color(0xFF16202C)   // Elevated surface / cards / keys
val PurevonSurfaceAlt     = Color(0xFF101721)   // Pane surface (= background)
val PurevonSurfaceMuted   = Color(0xFF16202C)   // Muted surface

// ── Primary — Bluesky Blue ──────────────────────────────────────
val PurevonPrimary        = Color(0xFF1185FE)   // Bluesky blue
val PurevonPrimaryLight   = Color(0xFF4BA3FE)   // Light blue
val PurevonPrimaryDark    = Color(0xFF0C6FD8)   // Dark blue
val PurevonPrimaryContainer = Color(0xFF16314F) // Dark blue tint (selected bg)
val PurevonOnPrimary      = Color(0xFFFFFFFF)   // White on primary
val PurevonOnPrimaryContainer = Color(0xFFFFFFFF) // White on container
val PurevonPrimaryVariant = WireDarkBlue800      // #224266 (own reactions highlight)

// ── Secondary — Calm Green ──────────────────────────────────────
val PurevonSecondary      = Color(0xFF2ECC71)   // Calm green (positive)
val PurevonSecondaryLight = Color(0xFF5FD99A)   // Light green
val PurevonSecondaryDark  = Color(0xFF25A85C)   // Dark green
val PurevonSecondaryContainer = Color(0xFF10331F) // Dark green tint
val PurevonOnSecondary    = Color(0xFF06210F)   // Dark text on green
val PurevonOnSecondaryContainer = Color(0xFFD6F5E4) // Light text on green container

// ── Tertiary — Blue (links / copy) ─────────────────────────────
val PurevonTertiary       = Color(0xFF1185FE)   // Blue
val PurevonTertiaryLight  = Color(0xFF4BA3FE)   // Light blue
val PurevonTertiaryDark   = Color(0xFF0C6FD8)   // Dark blue
val PurevonTertiaryContainer = Color(0xFF16314F) // Dark blue tint
val PurevonOnTertiary     = Color(0xFFFFFFFF)   // White on tertiary
val PurevonOnTertiaryContainer = Color(0xFFFFFFFF) // White on container

// ── Text ────────────────────────────────────────────────────────
val PurevonTextPrimary    = Color(0xFFFFFFFF)   // White
val PurevonTextSecondary  = Color(0xFF8B98A5)   // Muted grey
val PurevonTextTertiary   = Color(0xFF6B7D90)   // Inactive icons / dim
val PurevonTextDisabled   = Color(0xFF4A5663)   // Disabled
val PurevonTextInverse    = Color(0xFFFFFFFF)   // White text

// ── Status ──────────────────────────────────────────────────────
val PurevonSuccess        = Color(0xFF2ECC71)   // Green
val PurevonSuccessContainer = Color(0xFF10331F) // Dark green tint
val PurevonError          = Color(0xFFFF5C5C)   // Red
val PurevonErrorContainer = Color(0xFF3A1A1A)   // Dark red tint
val PurevonWarning        = Color(0xFFF5A623)   // Amber
val PurevonWarningContainer = Color(0xFF3A2A10) // Dark amber tint
val PurevonInfo           = Color(0xFF1185FE)   // Blue
val PurevonInfoContainer  = Color(0xFF16314F)   // Dark blue tint

// ── Borders & Dividers ──────────────────────────────────────────
val PurevonBorder         = Color(0xFF1F2D3D)   // Divider / outline
val PurevonBorderMedium   = Color(0xFF26323F)   // Medium border
val PurevonBorderStrong   = Color(0xFF324252)   // Strong border
val PurevonDivider        = Color(0xFF1F2D3D)   // Divider

// ── Message Bubbles (Wire exact match) ──────────────────────────
val PurevonBubbleSent     = WireLightBlue800     // #022950 (Wire selfBubble)
val PurevonBubbleSentText = Color(0xFFFFFFFF)    // White (Wire onPrimary)
val PurevonBubbleReceived = WireGray90           // #34373D (Wire otherBubble)
val PurevonBubbleReceivedText = Color(0xFFFFFFFF) // White (Wire onPrimary)
val PurevonBubbleSentSecondary     = Color(0xFF0D6FD0) // Sent bubble secondary (quoted, etc.)
val PurevonBubbleReceivedSecondary = Color(0xFF16202C) // Received bubble secondary
val PurevonMessageBackground       = Color(0xFF0D1420) // Message list background
val PurevonComposerBackground      = Color(0xFF16202C) // Composer surface
val PurevonMessageHighlight        = Color(0xFF16314F) // Selected message highlight
val PurevonMessageDivider          = Color(0xFF1F2D3D) // Message footer divider

// ── Spam Detection ──────────────────────────────────────────────
val PurevonSpamHigh       = Color(0xFFFF5C5C)   // High: red
val PurevonSpamMedium     = Color(0xFFF5A623)   // Medium: amber
val PurevonSpamLow        = Color(0xFF8B98A5)   // Low: grey
val PurevonSpamSafe       = Color(0xFF2ECC71)   // Safe: green

// ── Message Status (Wire exact match) ───────────────────────────
val PurevonStatusSent     = WireGray60           // #9FA1A7 (Wire secondaryText)
val PurevonStatusDelivered = WireGray60          // #9FA1A7
val PurevonStatusRead     = WireDarkBlue500      // #54A6FF (Wire primary)
val PurevonStatusFailed   = WireDarkRed500       // #FF7770

// ── Action Colors ───────────────────────────────────────────────
val PurevonActionCall     = Color(0xFF1185FE)   // Blue call
val PurevonActionDelete   = Color(0xFFFF5C5C)   // Red delete
val PurevonActionBlock    = Color(0xFFFF5C5C)   // Red block
val PurevonActionCopy     = Color(0xFF1185FE)   // Blue copy
val PurevonActionShare    = Color(0xFF2ECC71)   // Green share

// ── Call Type Colors ────────────────────────────────────────────
val PurevonCallIncoming   = Color(0xFF2ECC71)   // Green
val PurevonCallOutgoing   = Color(0xFF1185FE)   // Blue
val PurevonCallMissed     = Color(0xFF1185FE)   // Blue (per spec: blue dot)
val PurevonCallRejected   = Color(0xFF6B7D90)   // Grey

// ── SIM Colors ──────────────────────────────────────────────────
val PurevonSim1           = Color(0xFF1185FE)   // Blue
val PurevonSim2           = Color(0xFF2ECC71)   // Green

// ── Interactive ─────────────────────────────────────────────────
val PurevonInteractive    = Color(0xFF1185FE)   // Interactive elements
val PurevonInteractiveDisabled = Color(0xFF26323F) // Disabled
val PurevonPressOverlay   = Color(0x1AFFFFFF)   // 10% white overlay on press

// ── Miscellaneous ───────────────────────────────────────────────
val PurevonFavorite       = Color(0xFFF5A623)   // Star / favorite (amber)
val PurevonScheduled      = Color(0xFF1185FE)   // Scheduled items (blue)
val PurevonAccent         = Color(0xFF1185FE)   // Accent highlights (blue)
val PurevonOverlay        = Color(0x99000000)   // 60% black overlay (dark scrim)
val PurevonScrim          = Color(0x66000000)   // 40% black scrim
val PurevonShadow         = Color(0x40000000)   // Dark shadow

// ═══════════════════════════════════════════════════════════════════
// BACKWARD COMPATIBILITY ALIASES
// These map old color names to the new palette.
// Gradually remove these as files are updated.
// ═══════════════════════════════════════════════════════════════════

// iOS colors → New palette
val iOSBlue = PurevonTertiary
val iOSBluePressed = PurevonTertiaryDark
val iOSGreen = PurevonSecondary
val iOSRed = PurevonError
val iOSOrange = PurevonWarning
val iOSYellow = PurevonWarning
val iOSPurple = PurevonTertiary
val iOSPink = PurevonPrimary
val iOSTeal = PurevonSecondary
val iOSIndigo = PurevonTertiary
val iOSGray = PurevonBubbleReceived

// Dark variants → Same as base (no dark mode)
val iOSBlueDark = PurevonTertiary
val iOSGreenDark = PurevonSecondary
val iOSRedDark = PurevonError
val iOSOrangeDark = PurevonWarning
val iOSYellowDark = PurevonWarning
val iOSPurpleDark = PurevonTertiary
val iOSPinkDark = PurevonPrimary
val iOSTealDark = PurevonSecondary
val iOSIndigoDark = PurevonTertiary

// Background aliases
val LightBackground = PurevonBackground
val LightSurface = PurevonSurface
val LightSurfaceVariant = PurevonSurfaceMuted
val LightOnBackground = PurevonTextPrimary
val LightOnSurface = PurevonTextPrimary
val LightSecondary = PurevonTextSecondary
val LightTertiary = PurevonBorderMedium
val LightDivider = PurevonDivider

// Dark aliases → Same as light (no dark mode)
val DarkBackground = PurevonBackground
val DarkSurface = PurevonSurface
val DarkSurfaceVariant = PurevonSurfaceMuted
val DarkOnBackground = PurevonTextPrimary
val DarkOnSurface = PurevonTextPrimary
val DarkSecondary = PurevonTextSecondary
val DarkTertiary = PurevonBorderMedium
val DarkDivider = PurevonDivider

// ColorTokens aliases
object ColorTokens {
    val OnBrand = PurevonOnPrimary
    val LightPrimaryContainer = PurevonPrimaryContainer
    val LightSecondaryContainer = PurevonSecondaryContainer
    val LightTertiaryContainer = PurevonTertiaryContainer
    val LightErrorContainer = PurevonErrorContainer
    val DarkPrimaryContainer = PurevonPrimaryContainer
    val DarkSecondaryContainer = PurevonSecondaryContainer
    val DarkTertiaryContainer = PurevonTertiaryContainer
    val DarkErrorContainer = PurevonErrorContainer
}

// Legacy aliases
val Success = PurevonSuccess
val Error = PurevonError
val Warning = PurevonWarning
val Info = PurevonInfo

val IncomingCall = PurevonCallIncoming
val OutgoingCall = PurevonCallOutgoing
val MissedCall = PurevonCallMissed
val RejectedCall = PurevonCallRejected

val Sim1Color = PurevonSim1
val Sim2Color = PurevonSim2

// Spam aliases
val SpamHigh = PurevonSpamHigh
val SpamMedium = PurevonSpamMedium
val SpamLow = PurevonSpamLow
val SpamSafe = PurevonSpamSafe

// Message categories
val PersonalMessage = PurevonTertiary
val OTPMessage = PurevonTertiary
val PromotionMessage = PurevonWarning
val SpamMessage = PurevonError

// Extended UI aliases
val LightBackgroundAlt = PurevonBackgroundAlt
val LightBorder = PurevonBorder
val FavoriteGold = PurevonFavorite
val AmberLight = PurevonPrimaryLight
val ScheduleBlue = PurevonScheduled
val PlaceholderGrey = PurevonBorderMedium
val iOSSystemGray = PurevonTextTertiary
val iOSSystemGray2 = PurevonTextTertiary

// Material aliases
val MaterialOrange = PurevonWarning
val MaterialRed400 = PurevonError
val MaterialRed500 = PurevonError
val MaterialRed600 = PurevonPrimaryDark
val MaterialBlue400 = PurevonTertiary
val MaterialBlue500 = PurevonTertiary
val MaterialGreen500 = PurevonSecondary
val MaterialGreen400 = PurevonSecondaryLight
val MaterialCyan400 = PurevonSecondary
val MaterialCyan500 = PurevonSecondary
val MaterialPurple400 = PurevonTertiary
val MaterialPurple500 = PurevonTertiary
val DeepPurpleAccent = PurevonTertiary
val Indigo400 = PurevonTertiary
val Teal400 = PurevonSecondary
val BlueGrey400 = PurevonTextTertiary
val MaterialGrey500 = PurevonTextTertiary

// Brand aliases
val WhatsAppGreen = PurevonSecondary
val TelegramBlue = PurevonTertiary
val MessengerBlue = PurevonTertiary

// Dialpad aliases
val DialpadGreen = PurevonActionCall
val DialpadGreenLight = PurevonSecondary

// Fake call aliases
val FakeCallDark = PurevonTextPrimary
val FakeCallDarkAlt = PurevonSurfaceMuted

// Wire bubble aliases
val WireSelfBubbleLight = PurevonBubbleSent
val WireSelfBubbleDark = PurevonBubbleSent
val WireSelfBubblePressed = PurevonPrimaryDark
val WireOtherBubbleLight = PurevonBubbleReceived
val WireOtherBubbleDark = PurevonBubbleReceived
val WireOtherBubblePressed = PurevonBorderMedium
val WireSelfTextLight = PurevonBubbleSentText
val WireSelfTextDark = PurevonBubbleSentText
val WireOtherTextLight = PurevonBubbleReceivedText
val WireOtherTextDark = PurevonBubbleReceivedText
val WireTimestampLight = PurevonTextTertiary
val WireTimestampDark = PurevonTextTertiary
val WireMessageSent = PurevonStatusSent
val WireMessageDelivered = PurevonStatusDelivered
val WireMessageRead = PurevonStatusRead
val WireMessageFailed = PurevonStatusFailed
val WireDividerLight = PurevonDivider
val WireDividerDark = PurevonDivider
val WireSelectionLight = PurevonTertiaryContainer
val WireSelectionDark = PurevonTertiaryContainer
val WireReactionBackground = PurevonSurface
val WireReactionBorder = PurevonBorder
val WireReactionSelected = PurevonTertiaryContainer

// DarkSurfaceVariant alias
val DarkSurfaceVariant2 = PurevonSurfaceMuted

// Backward-compatible adaptive functions (now return fixed colors)
@Suppress("UNUSED")
val adaptiveBlue = PurevonTertiary
@Suppress("UNUSED")
val adaptiveGreen = PurevonSecondary
@Suppress("UNUSED")
val adaptiveRed = PurevonError
@Suppress("UNUSED")
val adaptiveOrange = PurevonWarning
@Suppress("UNUSED")
val adaptiveYellow = PurevonWarning
@Suppress("UNUSED")
val adaptivePurple = PurevonTertiary
@Suppress("UNUSED")
val adaptiveTeal = PurevonSecondary


