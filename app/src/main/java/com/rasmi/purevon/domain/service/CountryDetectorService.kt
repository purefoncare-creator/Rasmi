package com.rasmi.purevon.domain.service

import com.rasmi.purevon.domain.model.CountryPhoneRule

/**
 * Abstraction for detecting the device's country.
 * Implementations live in the data layer (e.g., AndroidCountryDetectorService).
 */
interface CountryDetectorService {
    
    /**
     * Detect the current device country and return the matching phone rule.
     */
    fun detectCountry(): CountryPhoneRule
}
