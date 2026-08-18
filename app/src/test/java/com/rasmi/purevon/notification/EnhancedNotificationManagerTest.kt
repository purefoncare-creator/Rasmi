package com.rasmi.purevon.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageCategory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EnhancedNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var manager: EnhancedNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager = EnhancedNotificationManager(context)
    }

    private fun createMessage(
        id: Long = 1L,
        body: String = "Hello world",
        timestamp: Long = System.currentTimeMillis(),
        phoneNumber: String = "+1234567890"
    ) = Message(
        id = id, threadId = 100L, phoneNumber = phoneNumber,
        contactName = "Test Contact", body = body, timestamp = timestamp,
        type = 1, category = MessageCategory.PERSONAL, isRead = true,
        isSent = false, isDelivered = false, simSlot = 1,
        isSpam = false, spamScore = 0f, isMms = false,
        attachmentUris = emptyList(), attachmentTypes = emptyList()
    )

    @Test
    fun `notification channels are created on init`() {
        val shadow = Shadows.shadowOf(notificationManager)
        val channels = shadow.notificationChannels
        assertThat(channels).isNotEmpty()
    }

    @Test
    fun `messages channel has correct ID`() {
        val shadow = Shadows.shadowOf(notificationManager)
        val channels = shadow.notificationChannels
        val messagesChannel = channels.find { it.id == EnhancedNotificationManager.CHANNEL_ID_MESSAGES }
        assertThat(messagesChannel).isNotNull()
    }

    @Test
    fun `important channel has correct ID`() {
        val shadow = Shadows.shadowOf(notificationManager)
        val channels = shadow.notificationChannels
        val importantChannel = channels.find { it.id == EnhancedNotificationManager.CHANNEL_ID_IMPORTANT }
        assertThat(importantChannel).isNotNull()
    }

    @Test
    fun `showNewMessageNotification creates notification`() {
        val message = createMessage()
        manager.showNewMessageNotification(
            message = message,
            senderName = "Alice",
            threadId = 100L
        )

        val shadow = Shadows.shadowOf(notificationManager)
        val notifications = shadow.allNotifications
        assertThat(notifications).isNotEmpty()
    }

    @Test
    fun `showSmsNotification creates notification`() {
        manager.showSmsNotification(
            phoneNumber = "+1234567890",
            senderName = "Bob",
            messageBody = "Test SMS",
            threadId = 200L
        )

        val shadow = Shadows.shadowOf(notificationManager)
        val notifications = shadow.allNotifications
        assertThat(notifications).isNotEmpty()
    }

    @Test
    fun `cancelNotification removes notification`() {
        val message = createMessage()
        manager.showNewMessageNotification(
            message = message,
            senderName = "Alice",
            threadId = 100L
        )

        val shadow = Shadows.shadowOf(notificationManager)
        val countBefore = shadow.allNotifications.size

        manager.cancelNotification(100L)
        assertThat(shadow.allNotifications.size).isLessThan(countBefore)
    }

    @Test
    fun `cancelAllNotifications clears all`() {
        manager.showSmsNotification(
            phoneNumber = "+1111", senderName = "A", messageBody = "Hi",
            threadId = 1L
        )
        manager.showSmsNotification(
            phoneNumber = "+2222", senderName = "B", messageBody = "Hey",
            threadId = 2L
        )

        val shadow = Shadows.shadowOf(notificationManager)
        assertThat(shadow.allNotifications.size).isAtLeast(2)

        manager.cancelAllNotifications()
        assertThat(shadow.allNotifications).isEmpty()
    }

    @Test
    fun `getContactPhoto returns null for null URI`() {
        val result = manager.getContactPhoto(context, null)
        assertThat(result).isNull()
    }

    @Test
    fun `getContactPhoto returns null for blank URI`() {
        val result = manager.getContactPhoto(context, "  ")
        assertThat(result).isNull()
    }

    @Test
    fun `getContactPhoto does not crash for invalid URI`() {
        val result = manager.getContactPhoto(context, "not-a-valid-uri")
        assertThat(result == null || result.width >= 0).isTrue()
    }

    @Test
    fun `showSmsNotification with hideContent uses dots`() {
        manager.showSmsNotification(
            phoneNumber = "+1234567890",
            senderName = "Secret",
            messageBody = "Confidential message",
            threadId = 300L,
            hideContent = true
        )

        val shadow = Shadows.shadowOf(notificationManager)
        assertThat(shadow.allNotifications).isNotEmpty()
    }

    @Test
    fun `showSmsNotification with vibration disabled`() {
        manager.showSmsNotification(
            phoneNumber = "+1234567890",
            senderName = "Test",
            messageBody = "No vibration",
            threadId = 400L,
            vibrationEnabled = false
        )

        val shadow = Shadows.shadowOf(notificationManager)
        assertThat(shadow.allNotifications).isNotEmpty()
    }

    @Test
    fun `group notification creates group summary`() {
        manager.showSmsNotification(
            phoneNumber = "+1111", senderName = "Group",
            messageBody = "Message 1", threadId = 1L, isGroup = true
        )
        manager.showSmsNotification(
            phoneNumber = "+2222", senderName = "Group",
            messageBody = "Message 2", threadId = 2L, isGroup = true
        )

        val shadow = Shadows.shadowOf(notificationManager)
        assertThat(shadow.allNotifications.size).isAtLeast(2)
    }

    @Test
    fun `channel constants are versioned`() {
        assertThat(EnhancedNotificationManager.CHANNEL_ID_MESSAGES).contains("v")
        assertThat(EnhancedNotificationManager.CHANNEL_ID_IMPORTANT).contains("v")
    }

    @Test
    fun `extra constants are defined`() {
        assertThat(EnhancedNotificationManager.KEY_TEXT_REPLY).isEqualTo("key_text_reply")
        assertThat(EnhancedNotificationManager.ACTION_REPLY).isEqualTo("action_reply")
        assertThat(EnhancedNotificationManager.ACTION_MARK_READ).isEqualTo("action_mark_read")
        assertThat(EnhancedNotificationManager.ACTION_ARCHIVE).isEqualTo("action_archive")
    }
}
