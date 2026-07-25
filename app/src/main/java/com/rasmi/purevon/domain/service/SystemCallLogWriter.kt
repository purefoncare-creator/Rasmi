package com.rasmi.purevon.domain.service

/**
 * Abstraction for writing entries into the system call log ContentProvider.
 * Implementations live in the data layer (e.g., AndroidSystemCallLogWriter).
 */
interface SystemCallLogWriter {
    
    /**
     * Insert a single call log entry into the system call log.
     *
     * @param phoneNumber The phone number
     * @param timestamp   Call timestamp in millis
     * @param duration    Call duration in seconds
     * @param callType    Call type (1=incoming, 2=outgoing, 3=missed, 5=rejected, 6=blocked)
     * @param simSlot     Optional SIM slot
     * @return true if the entry was inserted successfully
     */
    suspend fun insertCallLogEntry(
        phoneNumber: String,
        timestamp: Long,
        duration: Long,
        callType: Int,
        simSlot: Int? = null
    ): Boolean
}
