package com.rasmi.purevon.domain.usecase.phone

import javax.inject.Inject

/**
 * Use case for formatting phone numbers for display
 * Uses generic, country-agnostic formatting for all numbers
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
            cleaned.startsWith("+") -> formatGenericInternational(cleaned)
            
            // International format with 00
            cleaned.startsWith("00") -> formatGenericInternational("+" + cleaned.substring(2))
            
            // Local format (10 digits) — generic grouping
            cleaned.length == 10 -> formatGenericLocal(cleaned)
            
            // Local format (11 digits with leading 0) — generic grouping
            cleaned.length == 11 && cleaned.startsWith("0") -> formatGenericLocal(cleaned)
            
            // International without prefix
            cleaned.length > 11 -> formatGenericInternational("+" + cleaned)
            
            // Short codes (3-5 digits) — return as is
            else -> number
        }
    }
    
    /**
     * Format local number with generic grouping: XX XXX XXXX
     * Works for any country's local format.
     */
    private fun formatGenericLocal(digits: String): String {
        val clean = digits.removePrefix("0")
        return when {
            clean.length >= 10 -> "0${clean.substring(0, 2)} ${clean.substring(2, 5)} ${clean.substring(5, 10)}"
            clean.length >= 9 -> "0${clean.substring(0, 2)} ${clean.substring(2, 5)} ${clean.substring(5)}"
            else -> digits
        }
    }
    
    /**
     * Format international number with generic grouping.
     * Detects 1-3 digit country code, then groups the local part in 3s.
     * Examples:
     *   +201234567890  -> +20 123 456 7890
     *   +966501234567  -> +966 501 234 567
     *   +12125551234   -> +1 212 555 1234
     *   +447123456789  -> +44 712 345 6789
     *   +8613912345678 -> +86 139 1234 5678
     */
    private fun formatGenericInternational(number: String): String {
        if (!number.startsWith("+")) return number
        
        val digits = number.substring(1)
        if (digits.length <= 3) return number
        
        // Heuristic: country codes are 1-3 digits; local numbers are 6-11 digits.
        // Try 1, 2, then 3 digit country code and pick the best fit.
        for (codeLen in 1..3) {
            if (codeLen >= digits.length) break
            val localPart = digits.substring(codeLen)
            if (localPart.length in 6..11) {
                val countryCode = digits.substring(0, codeLen)
                val grouped = localPart.chunked(3).joinToString(" ")
                return "+$countryCode $grouped"
            }
        }
        
        // Fallback: treat first 1-3 digits as country code, group rest
        val countryCode = digits.substring(0, minOf(3, digits.length))
        val localPart = digits.substring(countryCode.length)
        if (localPart.isNotEmpty()) {
            return "+$countryCode ${localPart.chunked(3).joinToString(" ")}"
        }
        return "+$digits"
    }
    
    /**
     * Get clean digits only (no formatting)
     */
    fun getCleanDigits(number: String): String {
        return number.filter { it.isDigit() }
    }
    
    /**
     * Format as E.164 (standard international format)
     * Example: 0123456789 with defaultCountryCode "20" -> +201234567890
     * If defaultCountryCode is null, returns raw digits without country code prefix.
     */
    fun toE164(number: String, defaultCountryCode: String? = null): String {
        val digits = getCleanDigits(number)
        
        return when {
            number.startsWith("+") -> number.filter { it.isDigit() || it == '+' }
            number.startsWith("00") -> "+" + digits.substring(2)
            number.startsWith("0") -> {
                val code = defaultCountryCode ?: return digits
                "+$code${digits.substring(1)}"
            }
            else -> {
                val code = defaultCountryCode ?: return digits
                "+$code$digits"
            }
        }
    }
}


