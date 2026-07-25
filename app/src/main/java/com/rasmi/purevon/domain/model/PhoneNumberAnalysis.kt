package com.rasmi.purevon.domain.model

/**
 * Result of analyzing a phone number
 * Contains original number, normalized version, and metadata
 */
data class PhoneNumberAnalysis(
    /**
     * Original phone number as stored in contacts
     */
    val original: String,
    
    /**
     * Normalized phone number in international format (+XXX...)
     */
    val normalized: String,
    
    /**
     * Detected or assigned country code (e.g., "SA", "EG")
     */
    val countryCode: String,
    
    /**
     * Country name for display
     */
    val countryName: String,
    
    /**
     * Whether this number needs to be updated
     */
    val needsUpdate: Boolean,
    
    /**
     * Confidence level of the normalization (0.0 to 1.0)
     * - 1.0: Has country code, valid format
     * - 0.8: No country code but matches local format
     * - 0.5: Uncertain, may need user confirmation
     * - 0.0: Invalid or cannot normalize
     */
    val confidence: Double,
    
    /**
     * Issues detected with this phone number
     */
    val issues: List<PhoneNumberIssue>,
    
    /**
     * Flag emoji for the country
     */
    val flagEmoji: String
) {
    /**
     * Whether normalization is confident enough to apply automatically
     */
    fun isConfident(): Boolean = confidence >= 0.8
    
    /**
     * Whether this number might be from a different country
     */
    fun needsCountryConfirmation(): Boolean = confidence < 0.8 && confidence > 0.0
}

/**
 * Types of issues that can be detected in phone numbers
 */
enum class PhoneNumberIssue {
    /**
     * Number contains Arabic digits (٠١٢٣٤٥٦٧٨٩)
     */
    ARABIC_DIGITS,
    
    /**
     * Number is missing country code
     */
    MISSING_COUNTRY_CODE,
    
    /**
     * Number uses 00 prefix instead of +
     */
    HAS_00_PREFIX,
    
    /**
     * Number contains spaces, dashes, or parentheses
     */
    HAS_FORMATTING,
    
    /**
     * Number has leading zero after country code (e.g., +966 0564...)
     */
    LEADING_ZERO_WITH_COUNTRY,
    
    /**
     * Number length doesn't match expected format
     */
    INVALID_LENGTH,
    
    /**
     * Number prefix doesn't match known patterns for the country
     */
    INVALID_PREFIX,
    
    /**
     * Cannot determine country with confidence
     */
    UNCERTAIN_COUNTRY
}
