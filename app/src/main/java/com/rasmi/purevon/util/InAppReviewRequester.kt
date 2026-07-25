package com.rasmi.purevon.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.atomic.AtomicBoolean

object InAppReviewRequester {
    private const val PREFS_NAME = "purevon_in_app_review"
    private const val LAST_REQUEST_KEY = "last_review_request_at"
    private const val REQUEST_INTERVAL_MS = 90L * 24 * 60 * 60 * 1000

    private val requestInProgress = AtomicBoolean(false)

    fun requestIfEligible(context: Context) {
        val activity = context.findActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val preferences = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val now = System.currentTimeMillis()
        val lastRequestAt = preferences.getLong(LAST_REQUEST_KEY, 0L)
        if (lastRequestAt > 0 && now - lastRequestAt < REQUEST_INTERVAL_MS) return
        if (!requestInProgress.compareAndSet(false, true)) return

        val reviewManager = ReviewManagerFactory.create(context.applicationContext)
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful || activity.isFinishing || activity.isDestroyed) {
                requestInProgress.set(false)
                return@addOnCompleteListener
            }

            preferences.edit().putLong(LAST_REQUEST_KEY, now).apply()
            reviewManager.launchReviewFlow(activity, request.result)
                .addOnCompleteListener {
                    requestInProgress.set(false)
                }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
