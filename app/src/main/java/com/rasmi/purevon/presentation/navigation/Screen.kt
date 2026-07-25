package com.rasmi.purevon.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for all screens in the app.
 * Uses kotlinx-serialization + Navigation 2.8+ type-safe API.
 */
@Serializable
sealed interface Screen {
    // Setup
    @Serializable data object PermissionRequest : Screen
    @Serializable data object SystemSpecificPermissions : Screen

    // Main tabs
    @Serializable data object Dialer : Screen
    @Serializable data object Contacts : Screen
    @Serializable data object CallHistory : Screen
    @Serializable data object Messages : Screen
    @Serializable data object Settings : Screen

    // Detail screens
    @Serializable data class ContactDetail(val contactId: Long) : Screen
    @Serializable data class AddContact(
        val phoneNumber: String? = null,
        val name: String? = null,
        val email: String? = null,
        val contactId: Long = -1L // -1 means new contact (no edit)
    ) : Screen
    @Serializable data class NewConversation(val phoneNumber: String? = null) : Screen
    @Serializable data class Conversation(
        val conversationId: Long,
        val scrollToMessageId: Long = -1L // -1 means no scroll
    ) : Screen
    @Serializable data object InCall : Screen

    // Settings sub-screens
    @Serializable data object AboutScreen : Screen
    @Serializable data object Statistics : Screen
    @Serializable data object ScheduledMessages : Screen // ✅ Fix #5: Scheduled messages screen
}
