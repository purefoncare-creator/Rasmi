package com.rasmi.purevon.util

import android.content.Context

/**
 * In-app review requester — stubbed out for F-Droid compatibility.
 * The Play In-App Review API requires proprietary Google Play Services.
 * On F-Droid builds this is a no-op; on Play Store builds it can be
 * re-enabled by replacing this file with the Play-backed implementation.
 */
object InAppReviewRequester {
    fun requestIfEligible(context: Context) {
        // No-op: F-Droid builds do not use the Play Store review flow.
    }
}
