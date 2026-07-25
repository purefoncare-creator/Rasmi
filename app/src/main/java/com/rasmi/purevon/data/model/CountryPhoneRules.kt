package com.rasmi.purevon.data.model

import com.rasmi.purevon.domain.model.CountryPhoneRule

/**
 * Database of phone number formatting rules for different countries
 * Supports normalization of international phone numbers
 */
object CountryPhoneRules {
    
    /**
     * Complete list of supported countries with their phone formatting rules
     */
    val allCountries: List<CountryPhoneRule> = listOf(
        // GCC Countries
        CountryPhoneRule(
            countryCode = "SA",
            countryName = "Saudi Arabia",
            countryNameAr = "السعودية",
            dialCode = "+966",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("5"), // Mobile: 05x
            exampleNumber = "+966501234567",
            flagEmoji = "🇸🇦"
        ),
        CountryPhoneRule(
            countryCode = "AE",
            countryName = "United Arab Emirates",
            countryNameAr = "الإمارات",
            dialCode = "+971",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("5"), // Mobile: 05x
            exampleNumber = "+971501234567",
            flagEmoji = "🇦🇪"
        ),
        CountryPhoneRule(
            countryCode = "KW",
            countryName = "Kuwait",
            countryNameAr = "الكويت",
            dialCode = "+965",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("5", "6", "9"), // Mobile: 5xxx, 6xxx, 9xxx
            exampleNumber = "+96551234567",
            flagEmoji = "🇰🇼"
        ),
        CountryPhoneRule(
            countryCode = "QA",
            countryName = "Qatar",
            countryNameAr = "قطر",
            dialCode = "+974",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("3", "5", "6", "7"), // Mobile & landline
            exampleNumber = "+97433123456",
            flagEmoji = "🇶🇦"
        ),
        CountryPhoneRule(
            countryCode = "BH",
            countryName = "Bahrain",
            countryNameAr = "البحرين",
            dialCode = "+973",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("3", "6"), // Mobile: 3xxx, 6xxx
            exampleNumber = "+97333123456",
            flagEmoji = "🇧🇭"
        ),
        CountryPhoneRule(
            countryCode = "OM",
            countryName = "Oman",
            countryNameAr = "عمان",
            dialCode = "+968",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("7", "9"), // Mobile: 7xxx, 9xxx
            exampleNumber = "+96891234567",
            flagEmoji = "🇴🇲"
        ),
        
        // Arab Countries
        CountryPhoneRule(
            countryCode = "EG",
            countryName = "Egypt",
            countryNameAr = "مصر",
            dialCode = "+20",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("1"), // Mobile: 01x
            exampleNumber = "+201012345678",
            flagEmoji = "🇪🇬"
        ),
        CountryPhoneRule(
            countryCode = "JO",
            countryName = "Jordan",
            countryNameAr = "الأردن",
            dialCode = "+962",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("7"), // Mobile: 07x
            exampleNumber = "+962791234567",
            flagEmoji = "🇯🇴"
        ),
        CountryPhoneRule(
            countryCode = "LB",
            countryName = "Lebanon",
            countryNameAr = "لبنان",
            dialCode = "+961",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("3", "7"), // Mobile: 3xxx, 7xxx
            exampleNumber = "+96171123456",
            flagEmoji = "🇱🇧"
        ),
        CountryPhoneRule(
            countryCode = "SY",
            countryName = "Syria",
            countryNameAr = "سوريا",
            dialCode = "+963",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("9"), // Mobile: 09x
            exampleNumber = "+963912345678",
            flagEmoji = "🇸🇾"
        ),
        CountryPhoneRule(
            countryCode = "IQ",
            countryName = "Iraq",
            countryNameAr = "العراق",
            dialCode = "+964",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("7"), // Mobile: 07x
            exampleNumber = "+9647912345678",
            flagEmoji = "🇮🇶"
        ),
        CountryPhoneRule(
            countryCode = "YE",
            countryName = "Yemen",
            countryNameAr = "اليمن",
            dialCode = "+967",
            phoneLength = 9,
            startsWithZero = false,
            validPrefixes = listOf("7"), // Mobile: 7xx
            exampleNumber = "+967712345678",
            flagEmoji = "🇾🇪"
        ),
        CountryPhoneRule(
            countryCode = "PS",
            countryName = "Palestine",
            countryNameAr = "فلسطين",
            dialCode = "+970",
            phoneLength = 9,
            startsWithZero = false,
            validPrefixes = listOf("5"), // Mobile: 5xx
            exampleNumber = "+970591234567",
            flagEmoji = "🇵🇸"
        ),
        CountryPhoneRule(
            countryCode = "MA",
            countryName = "Morocco",
            countryNameAr = "المغرب",
            dialCode = "+212",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("6", "7"), // Mobile: 06x, 07x
            exampleNumber = "+212612345678",
            flagEmoji = "🇲🇦"
        ),
        CountryPhoneRule(
            countryCode = "DZ",
            countryName = "Algeria",
            countryNameAr = "الجزائر",
            dialCode = "+213",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("5", "6", "7"), // Mobile: 05x, 06x, 07x
            exampleNumber = "+213551234567",
            flagEmoji = "🇩🇿"
        ),
        CountryPhoneRule(
            countryCode = "TN",
            countryName = "Tunisia",
            countryNameAr = "تونس",
            dialCode = "+216",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("2", "3", "4", "5", "9"), // Mobile & landline
            exampleNumber = "+21621234567",
            flagEmoji = "🇹🇳"
        ),
        CountryPhoneRule(
            countryCode = "LY",
            countryName = "Libya",
            countryNameAr = "ليبيا",
            dialCode = "+218",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("9"), // Mobile: 9xx
            exampleNumber = "+218912345678",
            flagEmoji = "🇱🇾"
        ),
        CountryPhoneRule(
            countryCode = "SD",
            countryName = "Sudan",
            countryNameAr = "السودان",
            dialCode = "+249",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("9"), // Mobile: 09x
            exampleNumber = "+249912345678",
            flagEmoji = "🇸🇩"
        ),
        
        // Major International Countries
        CountryPhoneRule(
            countryCode = "US",
            countryName = "United States",
            countryNameAr = "الولايات المتحدة",
            dialCode = "+1",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("2", "3", "4", "5", "6", "7", "8", "9"),
            exampleNumber = "+12125551234",
            flagEmoji = "🇺🇸"
        ),
        CountryPhoneRule(
            countryCode = "GB",
            countryName = "United Kingdom",
            countryNameAr = "المملكة المتحدة",
            dialCode = "+44",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("7"), // Mobile: 07xxx
            exampleNumber = "+447123456789",
            flagEmoji = "🇬🇧"
        ),
        CountryPhoneRule(
            countryCode = "FR",
            countryName = "France",
            countryNameAr = "فرنسا",
            dialCode = "+33",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("6", "7"), // Mobile: 06x, 07x
            exampleNumber = "+33612345678",
            flagEmoji = "🇫🇷"
        ),
        CountryPhoneRule(
            countryCode = "DE",
            countryName = "Germany",
            countryNameAr = "ألمانيا",
            dialCode = "+49",
            phoneLength = 10, // Variable, but typically 10-11
            startsWithZero = true,
            validPrefixes = listOf("1"), // Mobile: 01xx
            exampleNumber = "+4915123456789",
            flagEmoji = "🇩🇪"
        ),
        CountryPhoneRule(
            countryCode = "TR",
            countryName = "Turkey",
            countryNameAr = "تركيا",
            dialCode = "+90",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("5"), // Mobile: 05xx
            exampleNumber = "+905321234567",
            flagEmoji = "🇹🇷"
        ),
        CountryPhoneRule(
            countryCode = "IN",
            countryName = "India",
            countryNameAr = "الهند",
            dialCode = "+91",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("6", "7", "8", "9"), // Mobile
            exampleNumber = "+919876543210",
            flagEmoji = "🇮🇳"
        ),
        CountryPhoneRule(
            countryCode = "PK",
            countryName = "Pakistan",
            countryNameAr = "باكستان",
            dialCode = "+92",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("3"), // Mobile: 03xx
            exampleNumber = "+923001234567",
            flagEmoji = "🇵🇰"
        ),
        CountryPhoneRule(
            countryCode = "BD",
            countryName = "Bangladesh",
            countryNameAr = "بنغلاديش",
            dialCode = "+880",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("1"), // Mobile: 01xxx
            exampleNumber = "+8801712345678",
            flagEmoji = "🇧🇩"
        ),
        CountryPhoneRule(
            countryCode = "PH",
            countryName = "Philippines",
            countryNameAr = "الفلبين",
            dialCode = "+63",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("9"), // Mobile: 09xx
            exampleNumber = "+639171234567",
            flagEmoji = "🇵🇭"
        ),
        CountryPhoneRule(
            countryCode = "CN",
            countryName = "China",
            countryNameAr = "الصين",
            dialCode = "+86",
            phoneLength = 11,
            startsWithZero = false,
            validPrefixes = listOf("1"), // Mobile: 1xx
            exampleNumber = "+8613912345678",
            flagEmoji = "🇨🇳"
        ),
        
        // North America
        CountryPhoneRule(
            countryCode = "CA",
            countryName = "Canada",
            countryNameAr = "كندا",
            dialCode = "+1",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("2", "3", "4", "5", "6", "7", "8", "9"),
            exampleNumber = "+14165551234",
            flagEmoji = "🇨🇦"
        ),
        
        // Europe
        CountryPhoneRule(
            countryCode = "IT",
            countryName = "Italy",
            countryNameAr = "إيطاليا",
            dialCode = "+39",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("3"), // Mobile: 3xx
            exampleNumber = "+393201234567",
            flagEmoji = "🇮🇹"
        ),
        CountryPhoneRule(
            countryCode = "ES",
            countryName = "Spain",
            countryNameAr = "إسبانيا",
            dialCode = "+34",
            phoneLength = 9,
            startsWithZero = false,
            validPrefixes = listOf("6", "7"), // Mobile: 6xx, 7xx
            exampleNumber = "+34612345678",
            flagEmoji = "🇪🇸"
        ),
        CountryPhoneRule(
            countryCode = "NL",
            countryName = "Netherlands",
            countryNameAr = "هولندا",
            dialCode = "+31",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("6"), // Mobile: 06x
            exampleNumber = "+31612345678",
            flagEmoji = "🇳🇱"
        ),
        CountryPhoneRule(
            countryCode = "PL",
            countryName = "Poland",
            countryNameAr = "بولندا",
            dialCode = "+48",
            phoneLength = 9,
            startsWithZero = false,
            validPrefixes = listOf("5", "6", "7", "8"), // Mobile
            exampleNumber = "+48501234567",
            flagEmoji = "🇵🇱"
        ),
        
        // Asia
        CountryPhoneRule(
            countryCode = "JP",
            countryName = "Japan",
            countryNameAr = "اليابان",
            dialCode = "+81",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("7", "8", "9"), // Mobile: 070, 080, 090
            exampleNumber = "+819012345678",
            flagEmoji = "🇯🇵"
        ),
        CountryPhoneRule(
            countryCode = "KR",
            countryName = "South Korea",
            countryNameAr = "كوريا الجنوبية",
            dialCode = "+82",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("1"), // Mobile: 010
            exampleNumber = "+821012345678",
            flagEmoji = "🇰🇷"
        ),
        CountryPhoneRule(
            countryCode = "ID",
            countryName = "Indonesia",
            countryNameAr = "إندونيسيا",
            dialCode = "+62",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("8"), // Mobile: 08xx
            exampleNumber = "+628123456789",
            flagEmoji = "🇮🇩"
        ),
        CountryPhoneRule(
            countryCode = "TH",
            countryName = "Thailand",
            countryNameAr = "تايلاند",
            dialCode = "+66",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("6", "8", "9"), // Mobile: 06x, 08x, 09x
            exampleNumber = "+66812345678",
            flagEmoji = "🇹🇭"
        ),
        CountryPhoneRule(
            countryCode = "VN",
            countryName = "Vietnam",
            countryNameAr = "فيتنام",
            dialCode = "+84",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("3", "5", "7", "8", "9"), // Mobile: 03x-09x
            exampleNumber = "+84912345678",
            flagEmoji = "🇻🇳"
        ),
        CountryPhoneRule(
            countryCode = "MY",
            countryName = "Malaysia",
            countryNameAr = "ماليزيا",
            dialCode = "+60",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("1"), // Mobile: 01x
            exampleNumber = "+60123456789",
            flagEmoji = "🇲🇾"
        ),
        CountryPhoneRule(
            countryCode = "RU",
            countryName = "Russia",
            countryNameAr = "روسيا",
            dialCode = "+7",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("9"), // Mobile: 9xx
            exampleNumber = "+79123456789",
            flagEmoji = "🇷🇺"
        ),
        
        // Oceania
        CountryPhoneRule(
            countryCode = "AU",
            countryName = "Australia",
            countryNameAr = "أستراليا",
            dialCode = "+61",
            phoneLength = 9,
            startsWithZero = true,
            validPrefixes = listOf("4", "5"), // Mobile: 04xx, 05xx
            exampleNumber = "+61412345678",
            flagEmoji = "🇦🇺"
        ),
        CountryPhoneRule(
            countryCode = "NZ",
            countryName = "New Zealand",
            countryNameAr = "نيوزيلندا",
            dialCode = "+64",
            phoneLength = 8,
            startsWithZero = false,
            validPrefixes = listOf("2"), // Mobile: 2xx
            exampleNumber = "+6421123456",
            flagEmoji = "🇳🇿"
        ),
        
        // Latin America
        CountryPhoneRule(
            countryCode = "BR",
            countryName = "Brazil",
            countryNameAr = "البرازيل",
            dialCode = "+55",
            phoneLength = 11,
            startsWithZero = false,
            validPrefixes = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), // Mobile: 9xxxx
            exampleNumber = "+5511912345678",
            flagEmoji = "🇧🇷"
        ),
        CountryPhoneRule(
            countryCode = "MX",
            countryName = "Mexico",
            countryNameAr = "المكسيك",
            dialCode = "+52",
            phoneLength = 10,
            startsWithZero = false,
            validPrefixes = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            exampleNumber = "+5215512345678",
            flagEmoji = "🇲🇽"
        ),
        CountryPhoneRule(
            countryCode = "AR",
            countryName = "Argentina",
            countryNameAr = "الأرجنتين",
            dialCode = "+54",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("1", "2", "3"), // Mobile: 011x, 02xx, 03xx
            exampleNumber = "+5491112345678",
            flagEmoji = "🇦🇷"
        ),
        
        // Africa
        CountryPhoneRule(
            countryCode = "NG",
            countryName = "Nigeria",
            countryNameAr = "نيجيريا",
            dialCode = "+234",
            phoneLength = 10,
            startsWithZero = true,
            validPrefixes = listOf("7", "8", "9"), // Mobile: 070x, 080x, 090x
            exampleNumber = "+2348012345678",
            flagEmoji = "🇳🇬"
        ),
        CountryPhoneRule(
            countryCode = "ZA",
            countryName = "South Africa",
            countryNameAr = "جنوب أفريقيا",
            dialCode = "+27",
            phoneLength = 9,
            startsWithZero = false,
            validPrefixes = listOf("6", "7", "8"), // Mobile: 6xx, 7xx, 8xx
            exampleNumber = "+27812345678",
            flagEmoji = "🇿🇦"
        )
    )
    
    /**
     * Map of dial codes to country rules for quick lookup
     */
    private val dialCodeMap: Map<String, CountryPhoneRule> by lazy {
        allCountries.associateBy { it.dialCode }
    }
    
    /**
     * Map of country codes to country rules for quick lookup
     */
    private val countryCodeMap: Map<String, CountryPhoneRule> by lazy {
        allCountries.associateBy { it.countryCode }
    }
    
    /**
     * Get country rule by dial code (e.g., "+966")
     */
    fun getByDialCode(dialCode: String): CountryPhoneRule? {
        return dialCodeMap[dialCode]
    }
    
    /**
     * Get country rule by country code (e.g., "SA")
     */
    fun getByCountryCode(countryCode: String): CountryPhoneRule? {
        return countryCodeMap[countryCode.uppercase()]
    }
    
    /**
     * Detect country from a phone number
     * Returns the country rule if detected, null otherwise
     */
    fun detectCountry(phoneNumber: String): CountryPhoneRule? {
        val digitsOnly = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // Try to match dial code
        for (country in allCountries) {
            val dialCode = country.getNumericDialCode()
            if (digitsOnly.startsWith("+$dialCode") || 
                digitsOnly.startsWith("00$dialCode") ||
                digitsOnly.startsWith(dialCode)) {
                return country
            }
        }
        
        return null
    }
    
    /**
     * Get default country — returns a generic neutral rule (no country bias).
     * Used when auto-detection fails and user hasn't set a preference.
     */
    fun getDefault(): CountryPhoneRule {
        return GENERIC_RULE
    }

    /**
     * Generic neutral country rule — no country bias.
     * Used as fallback when no country can be detected.
     * Accepts any number format without country-specific validation.
     */
    val GENERIC_RULE = CountryPhoneRule(
        countryCode = "ZZ",
        countryName = "Unknown",
        countryNameAr = "غير معروف",
        dialCode = "",
        phoneLength = 0,
        startsWithZero = false,
        validPrefixes = emptyList(),
        exampleNumber = "",
        flagEmoji = "\uD83C\uDF10" // 🌐
    )
}
