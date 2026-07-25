package com.rasmi.purevon.domain.model

/**
 * Result of analyzing all contacts for phone number issues
 */
data class ContactAnalysisResult(
    /**
     * Total number of contacts analyzed
     */
    val totalContacts: Int,
    
    /**
     * Number of contacts that need cleanup
     */
    val needsCleanup: Int,
    
    /**
     * Breakdown of issues by type
     */
    val byIssueType: Map<PhoneNumberIssue, Int>,
    
    /**
     * Sample phone number analyses for preview
     */
    val samples: List<PhoneNumberAnalysis>,
    
    /**
     * All phone numbers that need update (for detailed view)
     */
    val allNeedingUpdate: List<ContactPhoneUpdate>,
    
    /**
     * Estimated time to complete cleanup (in seconds)
     */
    val estimatedSeconds: Int,
    
    /**
     * Country used for analysis
     */
    val countryUsed: CountryPhoneRule
) {
    /**
     * Get percentage of contacts needing cleanup
     */
    fun getCleanupPercentage(): Int {
        return if (totalContacts > 0) {
            (needsCleanup * 100) / totalContacts
        } else 0
    }
    
    /**
     * Get formatted estimated time
     */
    fun getEstimatedTimeString(): String {
        return when {
            estimatedSeconds < 60 -> "~$estimatedSeconds ثانية"
            estimatedSeconds < 120 -> "~دقيقة واحدة"
            else -> "~${estimatedSeconds / 60} دقائق"
        }
    }
}

/**
 * Represents a contact that needs phone number update
 */
data class ContactPhoneUpdate(
    /**
     * Contact ID
     */
    val contactId: Long,
    
    /**
     * Contact display name
     */
    val displayName: String,
    
    /**
     * Photo URI if available
     */
    val photoUri: String?,
    
    /**
     * Phone number analysis
     */
    val analysis: PhoneNumberAnalysis,
    
    /**
     * Whether user has selected this for update
     */
    var isSelected: Boolean = true
)
