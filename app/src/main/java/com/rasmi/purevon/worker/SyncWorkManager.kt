package com.rasmi.purevon.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling background sync workers
 */
@Singleton
class SyncWorkManager @Inject constructor(
    private val context: Context
) {
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule all periodic sync workers
     */
    fun scheduleAllSyncWorkers() {
        scheduleOtpAutoDelete()
    }
    
    /**
     * Schedule OTP auto-delete - every 30 minutes
     */
    fun scheduleOtpAutoDelete() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<OtpAutoDeleteWorker>(
            30, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SYNC_TAG)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            OtpAutoDeleteWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    /**
     * Cancel all sync workers
     */
    fun cancelAllSyncWorkers() {
        workManager.cancelAllWorkByTag(SYNC_TAG)
    }
    
    companion object {
        private const val SYNC_TAG = "purevon_sync"
    }
}
