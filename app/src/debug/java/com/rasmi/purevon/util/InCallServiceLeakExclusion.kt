package com.rasmi.purevon.util

import leakcanary.LeakCanary
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern

/**
 * ✅ FIX M30: silence the known AOSP InCallService framework retention.
 *
 * The framework superclass `android.telecom.InCallService` creates a non-static
 * inner binder (`InCallServiceBinder`) whose implicit `this$0` points at every
 * service instance. While Telecom (system_server) still holds a proxy to that
 * binder, libbinder keeps a JNI global reference to it in OUR process, so
 * destroyed service instances cannot be collected. LeakCanary reports this as:
 *
 *     GC Root: Global variable in native code
 *       → android.telecom.InCallService$InCallServiceBinder (this$0)
 *       → PurevonInCallService (Leaking: YES, onDestroy called)
 *
 * Facts verified in this project (FIX M30 investigation):
 *  - The path contains NO app-owned object: Hilt bridge unregisters correctly,
 *    serviceScope is cancelled, and Call callbacks are detached in onDestroy.
 *  - The retention is bounded (~5.7 KB per instance) and freed when Telecom
 *    releases its remote proxy or the process dies.
 *  - This pattern is NOT in Shark's AndroidReferenceMatchers as of 2.14.
 *
 * We therefore mark ONLY the framework field as ignored — any genuine app-side
 * leak reaching these instances through other paths is still reported.
 */
object InCallServiceLeakExclusion {

    private const val OUTER_THIS_FIELD = "this\$0"

    // ✅ FIX M38: أضيف CallScreeningBinder — نفس النمط الإطاري تماماً
    // (رُصد في السجلات: CallScreeningServiceImpl يتسرب بنفس المسار)
    private val FRAMEWORK_BINDER_CLASSES = listOf(
        "android.telecom.InCallService\$InCallServiceBinder",
        "android.telecom.CallScreeningService\$CallScreeningBinder"
    )

    fun apply() {
        val frameworkBindersOuterThis = FRAMEWORK_BINDER_CLASSES.map { binderClass ->
            IgnoredReferenceMatcher(
                pattern = ReferencePattern.InstanceFieldPattern(binderClass, OUTER_THIS_FIELD)
            )
        }
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = frameworkBindersOuterThis + LeakCanary.config.referenceMatchers
        )
    }
}
