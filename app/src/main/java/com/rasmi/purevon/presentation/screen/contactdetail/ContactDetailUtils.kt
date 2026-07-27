package com.rasmi.purevon.presentation.screen.contactdetail

import android.content.Context
import android.provider.ContactsContract

internal fun getRawContactId(context: Context, contactId: Long): Long? {
    val projection = arrayOf(ContactsContract.RawContacts._ID)
    val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
    val selectionArgs = arrayOf(contactId.toString())

    context.contentResolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
    }
    return null
}

internal fun getContactStructuredName(context: Context, contactId: Long): Pair<String, String> {
    val rawContactId = getRawContactId(context, contactId) ?: return Pair("", "")

    val projection = arrayOf(
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
    )
    val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = arrayOf(
        rawContactId.toString(),
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
    )

    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val givenNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val familyNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            val displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)

            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) else null
            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) else null
            val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) else null

            if (!givenName.isNullOrBlank() || !familyName.isNullOrBlank()) {
                return Pair(givenName ?: "", familyName ?: "")
            }

            if (!displayName.isNullOrBlank()) {
                val parts = displayName.split(" ", limit = 2)
                return Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
            }
        }
    }

    return Pair("", "")
}
