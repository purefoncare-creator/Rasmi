package com.rasmi.purevon.domain.model

/**
 * Defines phone number formatting rules for a specific country
 * Used for normalizing and validating international phone numbers
 */
data class CountryPhoneRule(
    /**
     * ISO 3166-1 alpha-2 country code (e.g., "SA", "EG", "US")
     */
    val countryCode: String,
    
    /**
     * Country name in English and Arabic
     */
    val countryName: String,
    val countryNameAr: String,
    
    /**
     * International dialing code with + prefix (e.g., "+966", "+20", "+1")
     */
    val dialCode: String,
    
    /**
     * Expected length of phone number AFTER removing country code and leading zero
     * For example, Saudi Arabia: 9 digits after removing +966 and 0
     */
    val phoneLength: Int,
    
    /**
     * Whether local numbers start with zero
     * - true: Saudi Arabia (05xxxxxxxx), Egypt (01xxxxxxxxx)
     * - false: Kuwait (xxxxxxxx), USA (xxxxxxxxxx)
     */
    val startsWithZero: Boolean,
    
    /**
     * Valid prefixes for mobile/landline numbers (without leading zero if applicable)
     * For Saudi Arabia: ["5"] (mobile prefixes like 050, 053, 054, 055, 056, 057, 058, 059)
     * For Egypt: ["1"] (mobile prefixes like 010, 011, 012, 015)
     */
    val validPrefixes: List<String>,
    
    /**
     * Example formatted number for this country
     */
    val exampleNumber: String,
    
    /**
     * Flag emoji for UI display
     */
    val flagEmoji: String
) {
    /**
     * Get the numeric dial code without + prefix
     */
    fun getNumericDialCode(): String = dialCode.removePrefix("+")
    
    /**
     * Check if a number matches this country's format
     */
    fun matchesFormat(phoneNumber: String): Boolean {
        val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
        
        // Check if starts with this country's dial code
        if (digitsOnly.startsWith(getNumericDialCode())) {
            val localPart = digitsOnly.removePrefix(getNumericDialCode())
            return localPart.length == phoneLength || 
                   (startsWithZero && localPart.length == phoneLength + 1)
        }
        
        // Check if it's a local number
        if (startsWithZero && digitsOnly.startsWith("0")) {
            return digitsOnly.length == phoneLength + 1
        } else if (!startsWithZero) {
            return digitsOnly.length == phoneLength
        }
        
        return false
    }
}
