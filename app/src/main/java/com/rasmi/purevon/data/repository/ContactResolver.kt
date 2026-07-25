package com.rasmi.purevon.data.repository

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.rasmi.purevon.util.DebugLogger

/**
 * Handles contact name and photo resolution from the device's Contacts provider.
 * Extracted from MessageRepositoryImpl to improve code organization.
 */
internal class ContactResolver(private val context: Context) {

    companion object {
        private const val TAG = "ContactResolver"
    }

    /**
     * Resolve a single contact name from phone number.
     * Uses ContactsContract PhoneLookup.
     */
    fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown") return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read contacts")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact name for $phoneNumber", e)
            null
        }
    }

    /**
     * Batch resolve contact names for multiple phone numbers.
     * Uses two strategies: exact match batch query, then PhoneLookup fallback.
     */
    fun batchResolveContactNamesOptimized(phoneNumbers: Set<String>): Map<String, String> {
        if (phoneNumbers.isEmpty()) return emptyMap()

        val contactMap = mutableMapOf<String, String>()
        val cleanNumbers = phoneNumbers.filter { it.isNotBlank() && it != "Unknown" }

        if (cleanNumbers.isEmpty()) return emptyMap()

        try {
            // 1. Try exact match batch query (Fastest)
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} IN (${
                cleanNumbers.joinToString(",") { "?" }
            })"

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                selection,
                cleanNumbers.toTypedArray(),
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)
                    val name = cursor.getString(nameIndex)
                    if (number != null && name != null) {
                        contactMap[number] = name
                    }
                }
            }

            // 2. Fallback: Use PhoneLookup for unresolved numbers (Standard Android Matching)
            // This fixes the issue where saved contacts don't show up because of formatting differences
            // e.g. +966595458251 (outgoing) vs 0595458251 (incoming)
            val unresolvedNumbers = cleanNumbers.filter { !contactMap.containsKey(it) }
            if (unresolvedNumbers.isNotEmpty()) {
                DebugLogger.d(TAG, "⚠️ ${unresolvedNumbers.size} numbers not resolved by exact match, trying PhoneLookup...")

                unresolvedNumbers.forEach { phoneNumber ->
                    try {
                        // Try with original number
                        var nameFound = false
                        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
                        context.contentResolver.query(
                            uri,
                            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) {
                                    contactMap[phoneNumber] = name
                                    nameFound = true
                                }
                            }
                        }

                        // If failed and number has special chars, try stripping them (last resort)
                        if (!nameFound && phoneNumber.contains(Regex("[^0-9+]"))) {
                            val stripped = phoneNumber.replace(Regex("[^0-9+]"), "")
                            if (stripped.isNotEmpty() && stripped != phoneNumber) {
                                val strippedUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(stripped))
                                context.contentResolver.query(
                                    strippedUri,
                                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                                    null,
                                    null,
                                    null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                        val name = cursor.getString(nameIndex)
                                        if (!name.isNullOrBlank()) {
                                            contactMap[phoneNumber] = name // Map original number to found name
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resolving number via PhoneLookup: $phoneNumber", e)
                    }
                }
            }

            DebugLogger.d(TAG, "✅ Batch resolved ${contactMap.size} contacts from ${cleanNumbers.size} numbers")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error batch resolving contacts", e)
        }

        return contactMap
    }

    /**
     * Resolve a single contact's photo thumbnail URI via PhoneLookup.
     */
    fun resolveContactPhotoUri(phoneNumber: String): String? {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown") return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving photo for $phoneNumber", e)
            null
        }
    }

    /**
     * Batch resolve contact photo thumbnail URIs using PhoneLookup (same matching as names).
     */
    fun batchResolveContactPhotoUrisOptimized(phoneNumbers: Set<String>): Map<String, String?> {
        if (phoneNumbers.isEmpty()) return emptyMap()
        val photoMap = mutableMapOf<String, String?>()
        val cleanNumbers = phoneNumbers.filter { it.isNotBlank() && it != "Unknown" }
        for (phoneNumber in cleanNumbers) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                        photoMap[phoneNumber] = if (idx != -1) cursor.getString(idx) else null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving photo for $phoneNumber", e)
            }
        }
        return photoMap
    }
}
