package com.rasmi.purevon.domain.usecase.phone

import javax.inject.Inject

/**
 * Use case for validating phone numbers
 * Implements comprehensive phone number validation rules
 */
class ValidatePhoneNumberUseCase @Inject constructor() {
    
    /**
     * Validate phone number
     * @param number Phone number to validate
     * @return ValidationResult with success/error
     */
    operator fun invoke(number: String): ValidationResult {
        if (number.isBlank()) {
            return ValidationResult.Error("Phone number cannot be empty")
        }
        
        // Extract digits only
        val digits = number.filter { it.isDigit() }
        
        // Allow carrier and government short codes such as 937 and 911.
        if (digits.length < 3) {
            return ValidationResult.Error("Phone number is too short (minimum 3 digits)")
        }
        
        // Check maximum length
        if (digits.length > 15) {
            return ValidationResult.Error("Phone number is too long (maximum 15 digits)")
        }
        
        // Check for valid characters (only digits, +, -, spaces, parentheses)
        val validChars = number.all { it.isDigit() || it in "+- ()" }
        if (!validChars) {
            return ValidationResult.Error("Phone number contains invalid characters")
        }
        
        // Check international format if starts with +
        if (number.startsWith("+")) {
            if (digits.length < 10) {
                return ValidationResult.Error("International number is too short")
            }
        }
        
        return ValidationResult.Success(digits)
    }
    
    /**
     * Check if number is valid (simple boolean check)
     */
    fun isValid(number: String): Boolean {
        return invoke(number) is ValidationResult.Success
    }
    
    /**
     * Check if number is Egyptian number
     */
    fun isEgyptianNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return when {
            number.startsWith("+20") && digits.length == 12 -> true
            number.startsWith("0020") && digits.length == 13 -> true
            !number.startsWith("+") && !number.startsWith("00") && 
                digits.length in 10..11 -> true
            else -> false
        }
    }
    
    /**
     * Check if number is emergency number
     * Comprehensive global list covering all major regions
     */
    fun isEmergencyNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits in EMERGENCY_NUMBERS
    }

    companion object {
        /**
         * Global emergency numbers — covers all major regions worldwide.
         * Source: ITU, country-specific emergency services databases.
         */
        private val EMERGENCY_NUMBERS = setOf(
            // Universal / GSM
            "112",   // GSM universal (works on any mobile phone worldwide)
            "911",   // USA, Canada, Philippines, Malaysia, Mexico, etc.
            "999",   // UK, Malaysia, Bangladesh, Pakistan, Bahrain, etc.

            // North America
            "911",   // USA/Canada
            "108",   // India (also used in some US states)
            "112",   // EU/GSM

            // Europe
            "112",   // EU-wide emergency number
            "999",   // UK
            "110",   // Germany, Japan
            "113",   // Japan, Norway, Sweden, Denmark
            "114",   // Japan
            "115",   // Japan
            "117",   // Thailand, Switzerland
            "118",   // Italy (fire), France (fire)
            "119",   // Japan, South Korea, Indonesia, Thailand
            "120",   // China (ambulance), Japan
            "122",   // Egypt (traffic), China (police)
            "123",   // Egypt (ambulance), Pakistan
            "125",   // Russia
            "130",   // Russia
            "131",   // Turkey
            "140",   // Germany (fire)
            "141",   // Scotland (non-emergency)
            "144",   // Austria
            "147",   // Austria (police)
            "150",   // Turkey
            "155",   // Turkey
            "170",   // Germany (police)
            "175",   // Germany (fire)
            "180",   // Egypt (fire), Germany
            "190",   // Portugal, Brazil
            "191",   // Egypt, Thailand (police)
            "192",   // Nigeria, Brazil (fire)
            "193",   // Brazil (ambulance)
            "194",   // Brazil (civil defense)
            "195",   // Brazil (military police)

            // Middle East
            "911",   // Saudi Arabia, UAE, Qatar, Bahrain, Oman
            "999",   // Saudi Arabia (fire/ambulance), Bahrain
            "997",   // UAE (ambulance)
            "998",   // UAE (fire)
            "999",   // Malaysia, Brunei
            "112",   // All GSM countries
            "122",   // Egypt (traffic police)
            "123",   // Egypt (ambulance)
            "180",   // Egypt (fire)
            "191",   // Egypt (emergency)
            "199",   // Lebanon (fire)
            "140",   // Israel (police)
            "100",   // Israel (police)
            "101",   // Israel (ambulance)
            "102",   // Israel (fire)

            // Asia
            "110",   // Japan (police), China (police), Germany
            "113",   // Japan (ambulance/fire), Vietnam, Sweden, Norway, Denmark
            "119",   // Japan (fire/ambulance), South Korea, Indonesia, Thailand
            "120",   // China (ambulance)
            "122",   // China (traffic)
            "119",   // South Korea (fire/ambulance)
            "112",   // South Korea, India, all GSM
            "100",   // India (police)
            "101",   // India (fire)
            "102",   // India (ambulance)
            "108",   // India (ambulance/emergency)
            "1090",  // India (disaster)
            "181",   // India (women helpline)
            "1930",  // India (cyber crime)
            "911",   // Philippines
            "117",   // Philippines (police)
            "911",   // Malaysia
            "999",   // Malaysia (ambulance/fire)
            "110",   // Thailand (police)
            "1669",  // Thailand (medical emergency)
            "191",   // Thailand (police)
            "199",   // Thailand (fire)
            "112",   // Indonesia, all GSM
            "110",   // Indonesia (police)
            "113",   // Indonesia (fire)
            "118",   // Indonesia (ambulance)
            "119",   // Indonesia (emergency)
            "999",   // Brunei
            "112",   // Vietnam (GSM)
            "113",   // Vietnam (police)
            "114",   // Vietnam (fire)
            "115",   // Vietnam (ambulance)

            // Oceania
            "000",   // Australia (Triple Zero)
            "112",   // Australia (mobile)
            "111",   // New Zealand

            // Africa
            "10111", // South Africa (police)
            "10177", // South Africa (ambulance)
            "10111", // Nigeria (police)
            "112",   // All GSM countries
            "199",   // Nigeria (fire)
            "122",   // Ghana (police)
            "193",   // Ghana (fire)
            "191",   // Ghana (ambulance)

            // South America
            "190",   // Brazil (police)
            "192",   // Brazil (fire)
            "193",   // Brazil (ambulance)
            "125",   // Argentina (police)
            "107",   // Argentina (ambulance)
            "100",   // Argentina (fire)
            "123",   // Colombia (police)
            "119",   // Colombia (fire)
            "125",   // Colombia (ambulance)
            "131",   // Chile (ambulance)
            "132",   // Chile (fire)
            "133",   // Chile (police)
            "105",   // Peru (police)
            "116",   // Peru (fire)
            "117",   // Peru (ambulance)

            // Additional regional
            "100",   // Pakistan (police), Nepal
            "115",   // Pakistan (rescue)
            "116",   // Pakistan (fire)
            "192",   // Pakistan (emergency)
            "15",    // Bangladesh (police)
            "16",    // Bangladesh (fire)
            "999",   // Bangladesh (ambulance)
            "112",   // All GSM countries (universal fallback)
        )
    }
    
    /**
     * Validation result
     */
    sealed class ValidationResult {
        data class Success(val cleanedNumber: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}

