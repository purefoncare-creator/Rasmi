package com.rasmi.purevon.domain.usecase.contact

import android.util.Log
import com.rasmi.purevon.domain.model.CleanupFailure
import com.rasmi.purevon.domain.model.CleanupResult
import com.rasmi.purevon.domain.model.ContactPhoneUpdate
import com.rasmi.purevon.domain.service.SystemContactWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case to clean up and normalize contact phone numbers
 */
class CleanupContactsUseCase @Inject constructor(
    private val systemContactWriter: SystemContactWriter
) {
    companion object {
        private const val TAG = "CleanupContactsUseCase"
    }
    
    /**
     * Clean up contact phone numbers
     * 
     * @param contactsToClean List of contacts to update
     * @return Flow emitting progress (0.0 to 1.0) and final result
     */
    operator fun invoke(
        contactsToClean: List<ContactPhoneUpdate>
    ): Flow<CleanupProgress> = flow {
        try {
            var successCount = 0
            var failedCount = 0
            val failures = mutableListOf<CleanupFailure>()
            
            contactsToClean.forEachIndexed { index, contact ->
                try {
                    systemContactWriter.updateContactPhoneNumber(
                        contactId = contact.contactId,
                        originalPhoneNumber = contact.analysis.original,
                        newPhoneNumber = contact.analysis.normalized
                    )
                    successCount++
                    
                } catch (e: Exception) {
                    failedCount++
                    failures.add(
                        CleanupFailure(
                            contactId = contact.contactId,
                            displayName = contact.displayName,
                            phoneNumber = contact.analysis.original,
                            errorMessage = e.message ?: "خطأ غير معروف"
                        )
                    )
                    Log.e(TAG, "Failed to update contact ${contact.displayName}", e)
                }
                
                val progress = (index + 1).toFloat() / contactsToClean.size
                emit(
                    CleanupProgress.Processing(
                        progress = progress,
                        current = index + 1,
                        total = contactsToClean.size,
                        successCount = successCount,
                        failedCount = failedCount
                    )
                )
            }
            
            val result = CleanupResult(
                totalProcessed = contactsToClean.size,
                successCount = successCount,
                failedCount = failedCount,
                failures = failures
            )
            
            emit(CleanupProgress.Complete(result))
            
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            emit(CleanupProgress.Error(e.message ?: "Cleanup failed"))
        }
    }
}

/**
 * Progress states for cleanup operation
 */
sealed class CleanupProgress {
    data class Processing(
        val progress: Float,
        val current: Int,
        val total: Int,
        val successCount: Int,
        val failedCount: Int
    ) : CleanupProgress()
    
    /**
     * Cleanup complete
     */
    data class Complete(val result: CleanupResult) : CleanupProgress()
    
    /**
     * Error occurred
     */
    data class Error(val message: String) : CleanupProgress()
}
