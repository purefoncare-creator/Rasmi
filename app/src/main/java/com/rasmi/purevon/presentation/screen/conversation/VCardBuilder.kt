package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.util.Log
import com.rasmi.purevon.domain.model.Contact

/**
 * Builds RFC 6350-compliant vCard strings from Contact data.
 * Extracted from ConversationViewModel to reduce class size.
 *
 * Features:
 * - CRLF line endings per RFC 6350
 * - Escapes special characters
 * - Queries all phone numbers + emails from ContactsContract
 * - Includes contact photo (base64) if available and under 50KB
 */
internal class VCardBuilder(private val context: Context) {

    companion object {
        private const val TAG = "VCardBuilder"
        private const val CR = "\r\n" // RFC 6350 requires CRLF
    }

    fun buildVCardString(contact: Contact): String {
        val vCard = StringBuilder()
        vCard.append("BEGIN:VCARD").append(CR)
        vCard.append("VERSION:3.0").append(CR)

        // FN (formatted name)
        vCard.append("FN:").append(escapeVCard(contact.name)).append(CR)

        // N (structured name): Family;Given;Middle;Prefix;Suffix
        val parts = contact.name.trim().split(" ")
        val nValue = if (parts.size > 1) {
            val family = escapeVCard(parts.last())
            val given = escapeVCard(parts.dropLast(1).joinToString(" "))
            "$family;$given;;;"
        } else {
            ";".plus(escapeVCard(contact.name)).plus(";;;;")
        }
        vCard.append("N:").append(nValue).append(CR)

        // Query ALL phone numbers for this contact from ContactsContract
        try {
            val phoneCursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contact.id.toString()),
                null
            )
            phoneCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(0) ?: continue
                    val type = cursor.getInt(1)
                    val typeLabel = when (type) {
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "CELL"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "HOME"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "WORK"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "FAX"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "FAX"
                        else -> "CELL"
                    }
                    vCard.append("TEL;TYPE=$typeLabel:").append(number).append(CR)
                }
            }
        } catch (e: Exception) {
            // Fallback: use the single phone from Contact model
            val phoneType = contact.phoneType ?: "CELL"
            vCard.append("TEL;TYPE=$phoneType:").append(contact.phoneNumber).append(CR)
        }

        // Query ALL email addresses
        try {
            val emailCursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS,
                    android.provider.ContactsContract.CommonDataKinds.Email.TYPE
                ),
                "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contact.id.toString()),
                null
            )
            emailCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val email = cursor.getString(0) ?: continue
                    val type = cursor.getInt(1)
                    val typeLabel = when (type) {
                        android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "HOME"
                        android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "WORK"
                        else -> "INTERNET"
                    }
                    vCard.append("EMAIL;TYPE=$typeLabel:").append(escapeVCard(email)).append(CR)
                }
            }
        } catch (e: Exception) {
            // Fallback: use single email from Contact model
            contact.email?.let {
                vCard.append("EMAIL:").append(escapeVCard(it)).append(CR)
            }
        }

        // Add company if available
        contact.company?.let {
            vCard.append("ORG:").append(escapeVCard(it)).append(CR)
        }

        // Include contact photo if available
        if (contact.photoUri != null) {
            try {
                val photoUri = android.net.Uri.parse(contact.photoUri)
                context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    if (bytes.size <= 50 * 1024) { // Only include if <50KB (MMS size concern)
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        vCard.append("PHOTO;ENCODING=b;TYPE=JPEG:").append(base64).append(CR)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not include contact photo in vCard", e)
            }
        }

        vCard.append("END:VCARD")
        return vCard.toString()
    }

    /** Escape special characters per RFC 6350 vCard spec. */
    fun escapeVCard(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
