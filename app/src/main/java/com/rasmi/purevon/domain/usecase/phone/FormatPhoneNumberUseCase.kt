package com.rasmi.purevon.domain.usecase.phone

import javax.inject.Inject

/**
 * Use case for formatting phone numbers for display
 * Handles different formats (local, international, etc.)
 */
class FormatPhoneNumberUseCase @Inject constructor() {
    
    /**
     * Format phone number for display
     * @param number Raw phone number
     * @return Formatted phone number
     */
    operator fun invoke(number: String): String {
        if (number.isBlank()) return number
        
        // Remove non-digit characters except + at the start
        val cleaned = if (number.startsWith("+")) {
            "+" + number.substring(1).filter { it.isDigit() }
        } else {
            number.filter { it.isDigit() }
        }
        
        return when {
            // International format with +
            cleaned.startsWith("+") -> formatInternational(cleaned)
            
            // International format with 00
            cleaned.startsWith("00") -> formatInternational("+" + cleaned.substring(2))
            
            // Local format (10 digits) - Egyptian format
            cleaned.length == 10 -> formatEgyptian(cleaned)
            
            // Local format (11 digits with 0)
            cleaned.length == 11 && cleaned.startsWith("0") -> formatEgyptian(cleaned)
            
            // International without prefix
            cleaned.length > 11 -> formatInternational("+" + cleaned)
            
            // Unknown format - return as is
            else -> number
        }
    }
    
    /**
     * Format Egyptian phone number
     * Example: 0123456789 -> 012 345 6789
     */
    private fun formatEgyptian(digits: String): String {
        val hasLeadingZero = digits.startsWith("0")
        val clean = digits.removePrefix("0")
        return when (clean.length) {
            10 -> "0${clean.substring(0, 2)} ${clean.substring(2, 5)} ${clean.substring(5)}"
            9 -> "0${clean.substring(0, 2)} ${clean.substring(2, 5)} ${clean.substring(5)}"
            else -> digits
        }
    }
    
    /**
     * Format international number
     * Example: +201234567890 -> +20 123 456 7890
     */
    private fun formatInternational(number: String): String {
        if (!number.startsWith("+")) return number
        
        val digits = number.substring(1)
        
        return when {
            // Egypt +20
            digits.startsWith("20") && digits.length == 12 -> {
                "+20 ${digits.substring(2, 5)} ${digits.substring(5, 8)} ${digits.substring(8)}"
            }
            
            // Saudi Arabia +966
            digits.startsWith("966") && digits.length == 12 -> {
                "+966 ${digits.substring(3, 5)} ${digits.substring(5, 8)} ${digits.substring(8)}"
            }
            
            // UAE +971
            digits.startsWith("971") && digits.length == 12 -> {
                "+971 ${digits.substring(3, 5)} ${digits.substring(5, 8)} ${digits.substring(8)}"
            }
            
            // USA/Canada +1
            digits.startsWith("1") && digits.length == 11 -> {
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            }
            
            // Generic international
            digits.length > 10 -> {
                val countryCode = digits.substring(0, digits.length - 10)
                val rest = digits.substring(digits.length - 10)
                "+$countryCode ${rest.substring(0, 3)} ${rest.substring(3, 6)} ${rest.substring(6)}"
            }
            
            else -> number
        }
    }
    
    /**
     * Get clean digits only (no formatting)
     */
    fun getCleanDigits(number: String): String {
        return number.filter { it.isDigit() }
    }
    
    /**
     * Format as E.164 (standard international format)
     * Example: 0123456789 -> +201234567890
     */
    fun toE164(number: String, defaultCountryCode: String = "20"): String {
        val digits = getCleanDigits(number)
        
        return when {
            number.startsWith("+") -> number.filter { it.isDigit() || it == '+' }
            number.startsWith("00") -> "+" + digits.substring(2)
            number.startsWith("0") -> "+$defaultCountryCode${digits.substring(1)}"
            else -> "+$defaultCountryCode$digits"
        }
    }
}


