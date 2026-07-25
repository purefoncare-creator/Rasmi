package com.rasmi.purevon.util.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Modern replacement for LocalBroadcastManager using SharedFlow
 * Provides a simple event bus for communicating between components
 * 
 * Usage:
 * - To emit: EventBus.emit(AppEvent.SmsReceived(...))
 * - To observe: EventBus.events.collect { event -> ... }
 */
object EventBus {
    // Private mutable flow
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0, // Don't replay events to new subscribers
        extraBufferCapacity = 128 // ✅ FIX #14: Increased buffer for handling bursts (was 64)
    )
    
    // Public immutable flow
    val events = _events.asSharedFlow()
    
    /**
     * Emit an event to all subscribers
     */
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
    
    /**
     * Try to emit an event without suspending
     * Returns false if buffer is full
     */
    fun tryEmit(event: AppEvent): Boolean {
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            android.util.Log.w("EventBus", "tryEmit failed (buffer full), dropped: ${event::class.simpleName}")
        }
        return emitted
    }
}

/**
 * Base sealed class for all app events
 */
sealed class AppEvent {
    
    /**
     * SMS received event
     */
    data class SmsReceived(
        val phoneNumber: String,
        val messageBody: String,
        val timestamp: Long,
        val threadId: Long
    ) : AppEvent()
    
    /**
     * SMS sent status event
     */
    data class SmsSent(
        val phoneNumber: String,
        val success: Boolean,
        val messageUri: String?
    ) : AppEvent()
    
    /**
     * SMS delivery status event
     */
    data class SmsDelivered(
        val phoneNumber: String,
        val success: Boolean,
        val messageUri: String?
    ) : AppEvent()
    
    /**
     * Call state changed event
     */
    data class CallStateChanged(
        val state: String, // RINGING, OFFHOOK, IDLE
        val phoneNumber: String?
    ) : AppEvent()
    
    /**
     * Contact sync completed event
     */
    data object ContactSyncCompleted : AppEvent()
    
    /**
     * Message sync completed event
     */
    data object MessageSyncCompleted : AppEvent()
}

