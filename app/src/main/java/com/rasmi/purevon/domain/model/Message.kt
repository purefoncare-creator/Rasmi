package com.rasmi.purevon.domain.model

/**
 * Domain model for Message
 */
data class Message(
    val id: Long,
    val threadId: Long,
    val phoneNumber: String, // Phone number or address
    val contactName: String?,
    val body: String?,
    val timestamp: Long,
    val type: Int, // Type from system Telephony.Sms.TYPE
    val category: MessageCategory,
    val isRead: Boolean,
    val isSent: Boolean,
    val isDelivered: Boolean,
    val simSlot: Int?,
    val isSpam: Boolean,
    val spamScore: Float,
    val isMms: Boolean,
    val attachmentUris: List<String>,
    val attachmentTypes: List<String>,
    val status: MessageStatus? = null, // حالة الرسالة: قيد الإرسال، تم الإرسال، تم التسليم، فشل
    val isScheduled: Boolean = false, // هل الرسالة مجدولة؟
    val scheduledTime: Long? = null, // وقت الإرسال المجدول
    val scheduleId: Long? = null, // معرف الرسالة المجدولة في قاعدة البيانات
    val isStarred: Boolean = false // هل الرسالة مفضلة؟
) {
    // Aliases for compatibility
    val address: String get() = phoneNumber
    val conversationId: Long get() = threadId
}
