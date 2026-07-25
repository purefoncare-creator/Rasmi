package com.rasmi.purevon.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.data.local.entity.MessageType
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.data.local.entity.SpamType
import com.rasmi.purevon.data.local.entity.MultipartMessageStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Type Converters for Room Database
 */
class Converters {
    
    @TypeConverter
    fun fromMultipartMessageStatus(value: MultipartMessageStatus?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toMultipartMessageStatus(value: String?): MultipartMessageStatus? {
        return value?.let {
            try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                Log.e("Converters", "Failed to decode MultipartMessageStatus: ${e.message}")
                null
            }
        }
    }
    
    @TypeConverter
    fun fromCallType(value: CallType): String {
        return value.name
    }
    
    @TypeConverter
    fun toCallType(value: String): CallType {
        return CallType.entries.find { it.name == value } ?: run {
            Log.w("Converters", "Unknown CallType: $value, defaulting to INCOMING")
            CallType.INCOMING
        }
    }
    
    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return MessageType.entries.find { it.name == value } ?: run {
            Log.w("Converters", "Unknown MessageType: $value, defaulting to RECEIVED")
            MessageType.RECEIVED
        }
    }
    
    @TypeConverter
    fun fromMessageCategory(value: MessageCategory): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageCategory(value: String): MessageCategory {
        return MessageCategory.entries.find { it.name == value } ?: MessageCategory.PERSONAL
    }
    
    @TypeConverter
    fun fromSpamType(value: SpamType): String {
        return value.name
    }
    
    @TypeConverter
    fun toSpamType(value: String): SpamType {
        return SpamType.entries.find { it.name == value } ?: SpamType.UNKNOWN
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return value?.let {
            try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                Log.e("Converters", "Failed to decode string list: ${e.message}")
                emptyList()
            }
        } ?: emptyList()
    }
}
