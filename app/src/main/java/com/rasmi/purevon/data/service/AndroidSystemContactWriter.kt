package com.rasmi.purevon.data.service

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.rasmi.purevon.domain.service.SystemContactWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [SystemContactWriter].
 * Updates contacts in the system Contacts ContentProvider.
 */
@Singleton
class AndroidSystemContactWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemContactWriter {
    
    companion object {
        private const val TAG = "SystemContactWriter"
    }
    
    override suspend fun updateContactPhoneNumber(
        contactId: Long,
        originalPhoneNumber: String,
        newPhoneNumber: String
    ): Unit = withContext(Dispatchers.IO) {
        val operations = ArrayList<ContentProviderOperation>()
        
        operations.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                    arrayOf(
                        contactId.toString(),
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        originalPhoneNumber
                    )
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newPhoneNumber)
                .build()
        )
        
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact phone number for contactId=$contactId", e)
            throw e
        }
    }
}
