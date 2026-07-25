package com.rasmi.purevon.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// iOS-inspired Primary Colors (Light)
val iOSBlue = Color(0xFF5865F2)
val iOSBluePressed = Color(0xFF3F46C8)
val iOSGreen = Color(0xFF22C55E)
val iOSRed = Color(0xFFEF4444)
val iOSOrange = Color(0xFFF59E0B)
val iOSYellow = Color(0xFFEAB308)
val iOSPurple = Color(0xFF8B5CF6)
val iOSPink = Color(0xFFEC4899)
val iOSTeal = Color(0xFF22C7A9)
val iOSIndigo = Color(0xFF6366F1)
val iOSGray = Color(0xFFE8EAF2) // For message bubbles

// iOS-inspired Primary Colors (Dark Mode Variants)
val iOSBlueDark = Color(0xFF7C83FF)
val iOSGreenDark = Color(0xFF4ADE80)
val iOSRedDark = Color(0xFFF87171)
val iOSOrangeDark = Color(0xFFFBBF24)
val iOSYellowDark = Color(0xFFFACC15)
val iOSPurpleDark = Color(0xFFA78BFA)
val iOSPinkDark = Color(0xFFF472B6)
val iOSTealDark = Color(0xFF5EEAD4)
val iOSIndigoDark = Color(0xFF818CF8)

// Light Theme Colors
val LightBackground = Color(0xFFF7F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFF2FA)
val LightOnBackground = Color(0xFF111827)
val LightOnSurface = Color(0xFF1F2937)
val LightSecondary = Color(0xFF6B7280)
val LightTertiary = Color(0xFFD8DDEA)
val LightDivider = Color(0xFFE4E7F0)

// Dark Theme Colors
val DarkBackground = Color(0xFF0F1117)
val DarkSurface = Color(0xFF171A22)
val DarkSurfaceVariant = Color(0xFF222633)
val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFE5E7EB)
val DarkSecondary = Color(0xFF9CA3AF)
val DarkTertiary = Color(0xFF374151)
val DarkDivider = Color(0xFF2D3340)

object ColorTokens {
    val OnBrand = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFE8EAFF)
    val LightSecondaryContainer = Color(0xFFEAF7F4)
    val LightTertiaryContainer = Color(0xFFF1EDFF)
    val LightErrorContainer = Color(0xFFFEE2E2)
    val DarkPrimaryContainer = Color(0xFF252B63)
    val DarkSecondaryContainer = Color(0xFF173A35)
    val DarkTertiaryContainer = Color(0xFF312A4F)
    val DarkErrorContainer = Color(0xFF4A1F25)
}

// Backward compatibility (Legacy colors - deprecated)
val Success = iOSGreen
val Error = iOSRed
val Warning = iOSOrange
val Info = iOSBlue

// Call Type Colors (Legacy)
val IncomingCall = iOSGreen
val OutgoingCall = iOSBlue
val MissedCall = iOSRed
val RejectedCall = iOSOrange

// SIM Colors (Legacy)
val Sim1Color = iOSBlue
val Sim2Color = iOSGreen

// Spam Detection Colors
val SpamHigh = Color(0xFFFF3B30)
val SpamMedium = Color(0xFFFF9500)
val SpamLow = Color(0xFFFFCC00)
val SpamSafe = Color(0xFF34C759)

// Message Categories (Legacy)
val PersonalMessage = iOSBlue
val OTPMessage = iOSPurple
val PromotionMessage = iOSOrange
val SpamMessage = iOSRed

// Extended UI Colors
val LightBackgroundAlt = Color(0xFFF1F4FA)
val LightBorder = Color(0xFFE4E7F0)
val FavoriteGold = Color(0xFFFFD700)
val AmberLight = Color(0xFFFFB74D)
val ScheduleBlue = Color(0xFF2CB5E0)
val PlaceholderGrey = Color(0xFFD0D0D0)
val iOSSystemGray = Color(0xFF636366)
val iOSSystemGray2 = Color(0xFF98989D)

// Material-inspired Accent Colors
val MaterialOrange = Color(0xFFFF9800)
val MaterialRed400 = Color(0xFFEF5350)
val MaterialRed500 = Color(0xFFF44336)
val MaterialRed600 = Color(0xFFE53935)
val MaterialBlue400 = Color(0xFF42A5F5)
val MaterialBlue500 = Color(0xFF2196F3)
val MaterialGreen500 = Color(0xFF4CAF50)
val MaterialGreen400 = Color(0xFF66BB6A)
val MaterialCyan400 = Color(0xFF26C6DA)
val MaterialCyan500 = Color(0xFF00BCD4)
val MaterialPurple400 = Color(0xFFAB47BC)
val MaterialPurple500 = Color(0xFF9C27B0)
val DeepPurpleAccent = Color(0xFF7C4DFF)
val Indigo400 = Color(0xFF5C6BC0)
val Teal400 = Color(0xFF26A69A)
val BlueGrey400 = Color(0xFF78909C)
val MaterialGrey500 = Color(0xFF9E9E9E)

// Brand Colors
val WhatsAppGreen = Color(0xFF25D366)
val TelegramBlue = Color(0xFF0088CC)
val MessengerBlue = Color(0xFF0084FF)

// Dialpad Colors
val DialpadGreen = Color(0xFF00C853)
val DialpadGreenLight = Color(0xFF22C55E)

// Fake Call Colors
val FakeCallDark = Color(0xFF0D1117)
val FakeCallDarkAlt = Color(0xFF1A1F2E)

// ========================================
// Wire-Inspired Message Bubble Colors
// ========================================

// Self Message Bubbles (الرسائل المرسلة)
val WireSelfBubbleLight = Color(0xFF0091FF)      // Wire blue
val WireSelfBubbleDark = Color(0xFF0A84FF)       // Slightly lighter for dark mode
val WireSelfBubblePressed = Color(0xFF0077D4)    // Pressed state

// Other Message Bubbles (الرسائل المستلمة)
val WireOtherBubbleLight = Color(0xFFF5F5F5)     // Very light gray
val WireOtherBubbleDark = Color(0xFF2C2C2E)      // Dark gray for dark mode
val WireOtherBubblePressed = Color(0xFFE8E8E8)   // Pressed state

// Text Colors on Bubbles
val WireSelfTextLight = Color(0xFFFFFFFF)        // White on blue
val WireSelfTextDark = Color(0xFFFFFFFF)         // White on blue (same)
val WireOtherTextLight = Color(0xFF000000)       // Black on gray
val WireOtherTextDark = Color(0xFFFFFFFF)        // White on dark gray

// Timestamp Colors
val WireTimestampLight = Color(0xFF999999)       // Medium gray
val WireTimestampDark = Color(0xFF8E8E93)        // iOS secondary gray

// Message Metadata (Status indicators)
val WireMessageSent = Color(0xFF999999)          // Gray checkmark
val WireMessageDelivered = Color(0xFF999999)     // Gray double checkmark
val WireMessageRead = Color(0xFF0091FF)          // Blue double checkmark
val WireMessageFailed = Color(0xFFFF3B30)        // Red

// Divider Colors (for date separators)
val WireDividerLight = Color(0xFFE5E5EA)
val WireDividerDark = Color(0xFF38383A)

// Selection/Highlight Colors
val WireSelectionLight = Color(0xFFE3F2FD)       // Very light blue
val WireSelectionDark = Color(0xFF1E3A5F)        // Dark blue

// Reaction Colors
val WireReactionBackground = Color(0xFFFFFFFF)
val WireReactionBorder = Color(0xFFE5E5EA)
val WireReactionSelected = Color(0xFFE3F2FD)

// ✅ دوال للحصول على الألوان المناسبة حسب الوضع (Dark/Light)
@Composable
fun adaptiveBlue() = if (isSystemInDarkTheme()) iOSBlueDark else iOSBlue

@Composable
fun adaptiveGreen() = if (isSystemInDarkTheme()) iOSGreenDark else iOSGreen

@Composable
fun adaptiveRed() = if (isSystemInDarkTheme()) iOSRedDark else iOSRed

@Composable
fun adaptiveOrange() = if (isSystemInDarkTheme()) iOSOrangeDark else iOSOrange

@Composable
fun adaptiveYellow() = if (isSystemInDarkTheme()) iOSYellowDark else iOSYellow

@Composable
fun adaptivePurple() = if (isSystemInDarkTheme()) iOSPurpleDark else iOSPurple

@Composable
fun adaptiveTeal() = if (isSystemInDarkTheme()) iOSTealDark else iOSTeal
