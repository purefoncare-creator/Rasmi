package com.rasmi.purevon.domain.model

/**
 * Status of a sent message
 */
enum class MessageStatus {
    SENDING,    // قيد الإرسال - Message is being sent
    SENT,       // تم الإرسال - Message sent to network
    DELIVERED,  // تم التسليم - Message delivered to recipient
    FAILED      // فشل الإرسال - Message failed to send
}
