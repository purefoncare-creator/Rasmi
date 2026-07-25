package com.rasmi.purevon.util

import com.rasmi.purevon.data.model.CountryPhoneRules
import com.rasmi.purevon.domain.model.CountryPhoneRule
import com.rasmi.purevon.domain.model.PhoneNumberAnalysis
import com.rasmi.purevon.domain.model.PhoneNumberIssue

/**
 * Utility for normalizing phone numbers to international format
 * Handles Arabic digits, country codes, formatting, etc.
 */
object PhoneNumberNormalizer {
    
    // Arabic to Latin digit mapping
    private val arabicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    
    /**
     * Normalize a phone number to international format
     * 
     * @param rawNumber Original phone number from contacts
     * @param defaultCountryRule Country rule to use if country cannot be detected
     * @return PhoneNumberAnalysis with normalized number and metadata
     */
    fun normalizePhoneNumber(
        rawNumber: String,
        defaultCountryRule: CountryPhoneRule
    ): PhoneNumberAnalysis {
        val issues = mutableListOf<PhoneNumberIssue>()
        
        // Step 1: Convert Arabic digits to Latin
        val withLatinDigits = convertArabicToLatin(rawNumber)
        if (withLatinDigits != rawNumber) {
            issues.add(PhoneNumberIssue.ARABIC_DIGITS)
        }
        
        // Step 2: Remove formatting (spaces, dashes, parentheses)
        val hasFormatting = withLatinDigits.any { it in " -()" }
        if (hasFormatting) {
            issues.add(PhoneNumberIssue.HAS_FORMATTING)
        }
        val digitsAndPlus = withLatinDigits.replace(Regex("[^0-9+]"), "")
        
        // Step 3: Handle 00 prefix
        val normalized00 = if (digitsAndPlus.startsWith("00")) {
            issues.add(PhoneNumberIssue.HAS_00_PREFIX)
            "+" + digitsAndPlus.substring(2)
        } else {
            digitsAndPlus
        }
        
        // Step 4: Detect country
        val detectedCountry = CountryPhoneRules.detectCountry(normalized00)
        
        if (detectedCountry == null) {
            issues.add(PhoneNumberIssue.MISSING_COUNTRY_CODE)
            issues.add(PhoneNumberIssue.UNCERTAIN_COUNTRY)
        }
        
        // Use detected country or default (but mark as uncertain if not detected)
        val countryRule = detectedCountry ?: defaultCountryRule
        
        // Step 5: Extract local number
        val digitsOnly = normalized00.replace(Regex("[^0-9]"), "")
        val localNumber = extractLocalNumber(digitsOnly, countryRule)
        
        // Step 6: Check for leading zero with country code
        if (detectedCountry != null && localNumber.startsWith("0") && countryRule.startsWithZero) {
            issues.add(PhoneNumberIssue.LEADING_ZERO_WITH_COUNTRY)
        }
        
        // Step 7: Normalize local number (add/remove leading zero)
        val normalizedLocal = normalizeLocalNumber(localNumber, countryRule)
        
        // Step 8: Validate length
        val expectedLength = if (countryRule.startsWithZero) {
            countryRule.phoneLength // Without the 0
        } else {
            countryRule.phoneLength
        }
        
        if (normalizedLocal.length != expectedLength) {
            issues.add(PhoneNumberIssue.INVALID_LENGTH)
        }
        
        // Step 9: Validate prefix
        if (!isValidPrefix(normalizedLocal, countryRule)) {
            issues.add(PhoneNumberIssue.INVALID_PREFIX)
        }
        
        // Step 10: Build final number
        val finalNumber = "${countryRule.dialCode}${normalizedLocal}"
        
        // Step 11: Calculate confidence
        val confidence = calculateConfidence(
            hasCountryCode = detectedCountry != null,
            hasValidLength = normalizedLocal.length == expectedLength,
            hasValidPrefix = isValidPrefix(normalizedLocal, countryRule),
            issuesCount = issues.size
        )
        
        return PhoneNumberAnalysis(
            original = rawNumber,
            normalized = finalNumber,
            countryCode = countryRule.countryCode,
            countryName = countryRule.countryName,
            needsUpdate = rawNumber != finalNumber,
            confidence = confidence,
            issues = issues,
            flagEmoji = countryRule.flagEmoji
        )
    }
    
    /**
     * Convert Arabic digits to Latin digits
     */
    private fun convertArabicToLatin(text: String): String {
        return text.map { arabicDigits[it] ?: it }.joinToString("")
    }
    
    /**
     * Extract local number from full number with country code
     */
    private fun extractLocalNumber(digitsOnly: String, countryRule: CountryPhoneRule): String {
        val dialCode = countryRule.getNumericDialCode()
        
        return when {
            // Has country code at start
            digitsOnly.startsWith(dialCode) -> {
                digitsOnly.substring(dialCode.length)
            }
            // Local number (no country code)
            else -> digitsOnly
        }
    }
    
    /**
     * Normalize local number according to country rules
     * - For countries that start with 0: remove leading 0
     * - For countries that don't: keep as is
     */
    private fun normalizeLocalNumber(localNumber: String, countryRule: CountryPhoneRule): String {
        return if (countryRule.startsWithZero && localNumber.startsWith("0")) {
            // Remove leading zero (e.g., 0564286081 -> 564286081)
            localNumber.substring(1)
        } else if (!countryRule.startsWithZero && localNumber.startsWith("0")) {
            // Country doesn't use leading zero, but number has it - remove
            localNumber.substring(1)
        } else {
            localNumber
        }
    }
    
    /**
     * Check if number has a valid prefix for this country
     */
    private fun isValidPrefix(localNumber: String, countryRule: CountryPhoneRule): Boolean {
        if (countryRule.validPrefixes.isEmpty()) return true
        
        return countryRule.validPrefixes.any { prefix ->
            localNumber.startsWith(prefix)
        }
    }
    
    /**
     * Calculate confidence score for normalization
     */
    private fun calculateConfidence(
        hasCountryCode: Boolean,
        hasValidLength: Boolean,
        hasValidPrefix: Boolean,
        issuesCount: Int
    ): Double {
        var score = 0.0
        
        // Country code detection: +40%
        if (hasCountryCode) score += 0.4
        
        // Valid length: +30%
        if (hasValidLength) score += 0.3
        
        // Valid prefix: +20%
        if (hasValidPrefix) score += 0.2
        
        // No country code but valid format: +10%
        if (!hasCountryCode && hasValidLength && hasValidPrefix) {
            score += 0.1
        }
        
        // Penalty for issues (max -0.3)
        val penalty = minOf(issuesCount * 0.05, 0.3)
        score -= penalty
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * Batch normalize phone numbers
     */
    fun normalizePhoneNumbers(
        numbers: List<String>,
        defaultCountryRule: CountryPhoneRule
    ): List<PhoneNumberAnalysis> {
        return numbers.map { normalizePhoneNumber(it, defaultCountryRule) }
    }
    
    /**
     * Quick check if a number needs normalization
     */
    fun needsNormalization(phoneNumber: String): Boolean {
        return phoneNumber.any { it in arabicDigits.keys } ||
                phoneNumber.startsWith("00") ||
                phoneNumber.any { it in " -()" } ||
                (!phoneNumber.startsWith("+") && phoneNumber.length > 10)
    }
}
