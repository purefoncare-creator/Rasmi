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
        
        // Check minimum length
        if (digits.length < 7) {
            return ValidationResult.Error("Phone number is too short (minimum 7 digits)")
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
     */
    fun isEmergencyNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits in listOf(
            "911",   // USA
            "112",   // Europe/GSM
            "999",   // UK
            "122",   // Egypt - Traffic Police
            "123",   // Egypt - Ambulance  
            "180",   // Egypt - Fire Department
            "191"    // Egypt - Emergency
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


