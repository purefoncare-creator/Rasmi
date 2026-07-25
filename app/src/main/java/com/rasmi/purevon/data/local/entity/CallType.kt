package com.rasmi.purevon.data.local.entity

/**
 * Type alias pointing to domain-level CallType.
 * Keeps all existing imports working without changes.
 */
typealias CallType = com.rasmi.purevon.domain.model.CallType

/**
 * Android-specific mapping utilities for CallType.
 * These depend on android.provider.CallLog.Calls and belong in the data layer.
 */
fun CallType.toInt(): Int {
    return when (this) {
        CallType.INCOMING -> android.provider.CallLog.Calls.INCOMING_TYPE
        CallType.OUTGOING -> android.provider.CallLog.Calls.OUTGOING_TYPE
        CallType.MISSED -> android.provider.CallLog.Calls.MISSED_TYPE
        CallType.REJECTED -> android.provider.CallLog.Calls.REJECTED_TYPE
        CallType.BLOCKED -> android.provider.CallLog.Calls.BLOCKED_TYPE
        CallType.VOICEMAIL -> android.provider.CallLog.Calls.VOICEMAIL_TYPE
    }
}

fun callTypeFromInt(type: Int): CallType {
    return when (type) {
        android.provider.CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        android.provider.CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        android.provider.CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        android.provider.CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        android.provider.CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        android.provider.CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.MISSED
    }
}
