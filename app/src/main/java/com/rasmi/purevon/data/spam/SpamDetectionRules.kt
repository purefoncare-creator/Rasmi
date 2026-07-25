package com.rasmi.purevon.data.spam

/**
 * Enhanced spam detection patterns and rules
 */
object SpamDetectionRules {
    
    /**
     * Common spam number prefixes — toll-free and premium rate numbers only.
     *
     * Country-code prefixes were removed to avoid geographic bias.
     * Users can manage blocked numbers/contacts via the spam management screen.
     */
    val SPAM_PREFIXES = mapOf(
        // International toll-free and premium
        "1800" to 0.3f,
        "1888" to 0.3f,
        "1900" to 0.6f, // Premium rate
        "0800" to 0.3f,
        "0900" to 0.6f,
    )
    
    /**
     * Spam keywords in SMS messages
     */
    val SPAM_KEYWORDS = listOf(
        // Financial scams
        "win", "won", "winner", "prize", "reward", "claim", "free money",
        "lottery", "jackpot", "million", "inheritance", "beneficiary",
        "bank account", "verify account", "suspended account",
        "credit card", "debit card", "cvv", "pin code",
        
        // Urgent action required
        "urgent", "immediately", "act now", "limited time", "expires",
        "confirm now", "verify now", "click here", "call now",
        
        // Too good to be true offers
        "100% free", "risk free", "no cost", "free trial", "special offer",
        "exclusive deal", "discount", "sale", "clearance",
        
        // Suspicious links
        // ✅ FIX #57: Removed "http://", "https://" — already penalized by URL detection below
        "bit.ly", "tinyurl", "short.link",
        
        // Cryptocurrency/Investment scams
        "bitcoin", "crypto", "investment opportunity", "trading",
        "guaranteed return", "passive income",
        
        // Phishing
        "suspicious activity", "unusual activity", "security alert",
        "reset password", "verify identity", "confirm details"
    )
    
    /**
     * OTP/Verification patterns (usually legitimate)
     */
    val OTP_PATTERNS = listOf(
        Regex("\\b\\d{4,8}\\b.*(?:code|otp|verification|verify|password)"),
        Regex("(?:code|otp|verification|verify|password).*\\b\\d{4,8}\\b"),
        Regex("your.*(?:code|otp|pin).*is.*\\d{4,8}"),
        Regex("\\d{4,8}.*is your.*(?:code|otp|verification)")
    )
    
    /**
     * Promotional patterns (medium spam risk)
     */
    val PROMOTIONAL_PATTERNS = listOf(
        Regex("(?i)dear customer"),
        Regex("(?i)congratulations"),
        Regex("(?i)you have been selected"),
        Regex("(?i)limited time offer"),
        Regex("(?i)act fast"),
        Regex("(?i)reply stop to unsubscribe"),
        Regex("(?i)offer valid until")
    )
    
    /**
     * Scam patterns (high spam risk)
     */
    val SCAM_PATTERNS = listOf(
        Regex("(?i)you have won"),
        Regex("(?i)claim your prize"),
        Regex("(?i)verify your account"),
        Regex("(?i)suspend(?:ed)?\\s+account"),
        Regex("(?i)unusual activity detected"),
        Regex("(?i)click.*link"),
        Regex("(?i)call.*number.*immediately"),
        Regex("(?i)tax refund"),
        Regex("(?i)inheritance.*million")
    )
    
    /**
     * Phone number characteristics that indicate spam
     */
    fun analyzePhoneNumberCharacteristics(phoneNumber: String): Float {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        // ✅ FIX 2.14: Alphanumeric senders (AMAZON, STC, البنك) produce empty cleanNumber.
        // Without this guard, cleanNumber[0] crashes and score gets inflated to 1.0.
        if (cleanNumber.isEmpty()) return 0f
        var score = 0f
        
        // Very long numbers
        if (cleanNumber.length > 15) score += 0.4f
        
        // Very short numbers (short codes can be spam)
        if (cleanNumber.length < 5) score += 0.3f
        
        // Repeating digits (e.g., 8888888888)
        if (hasRepeatingDigits(cleanNumber, 4)) score += 0.3f
        if (hasRepeatingDigits(cleanNumber, 6)) score += 0.5f
        
        // Sequential digits (e.g., 123456789)
        if (hasSequentialDigits(cleanNumber, 5)) score += 0.3f
        if (hasSequentialDigits(cleanNumber, 7)) score += 0.5f
        
        // All same digit
        if (cleanNumber.all { it == cleanNumber[0] }) score += 0.7f
        
        // Palindrome numbers (suspicious pattern)
        if (isPalindrome(cleanNumber) && cleanNumber.length >= 6) score += 0.2f
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Analyze message content for spam indicators
     */
    fun analyzeMessageContent(message: String): Pair<Float, List<String>> {
        val lowerMessage = message.lowercase()
        var score = 0f
        val detectedPatterns = mutableListOf<String>()
        
        // Check if it's OTP (reduce spam score)
        val isOTP = OTP_PATTERNS.any { it.containsMatchIn(lowerMessage) }
        if (isOTP) {
            return Pair(0.1f, listOf("OTP/Verification"))
        }
        
        // Check for scam patterns (high risk)
        SCAM_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(lowerMessage)) {
                score += 0.3f
                detectedPatterns.add("Scam Pattern")
            }
        }
        
        // Check for promotional patterns
        PROMOTIONAL_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(lowerMessage)) {
                score += 0.15f
                detectedPatterns.add("Promotional")
            }
        }
        
        // Count spam keywords
        var keywordCount = 0
        SPAM_KEYWORDS.forEach { keyword ->
            if (lowerMessage.contains(keyword.lowercase())) {
                keywordCount++
            }
        }
        
        // Add score based on keyword density
        when {
            keywordCount >= 5 -> {
                score += 0.6f
                detectedPatterns.add("High Keyword Density")
            }
            keywordCount >= 3 -> {
                score += 0.4f
                detectedPatterns.add("Medium Keyword Density")
            }
            keywordCount >= 1 -> {
                score += 0.2f
                detectedPatterns.add("Low Keyword Density")
            }
        }
        
        // Check for URLs (can be phishing)
        if (lowerMessage.contains("http://") || lowerMessage.contains("https://") ||
            lowerMessage.contains("bit.ly") || lowerMessage.contains("tinyurl")) {
            score += 0.3f
            detectedPatterns.add("Contains URL")
        }
        
        // Check for ALL CAPS (common in spam)
        // ✅ FIX #56: Guard against division by zero on empty messages
        val capsRatio = if (message.isEmpty()) 0f else message.count { it.isUpperCase() }.toFloat() / message.length
        if (capsRatio > 0.6f && message.length > 20) {
            score += 0.2f
            detectedPatterns.add("Excessive Capitals")
        }
        
        // Check for excessive punctuation (!!! ???)
        val exclamationCount = message.count { it == '!' }
        if (exclamationCount >= 3) {
            score += 0.15f
            detectedPatterns.add("Excessive Punctuation")
        }
        
        return Pair(score.coerceIn(0f, 1f), detectedPatterns)
    }
    
    /**
     * Combined spam score calculation
     */
    fun calculateCombinedSpamScore(
        phoneNumber: String,
        messageContent: String? = null,
        reportCount: Int = 0,
        userBlocked: Boolean = false
    ): Pair<Float, String> {
        var totalScore = 0f
        val reasons = mutableListOf<String>()
        
        // Phone number analysis
        val phoneScore = analyzePhoneNumberCharacteristics(phoneNumber)
        if (phoneScore > 0.3f) {
            totalScore += phoneScore * 0.3f // 30% weight
            reasons.add("Suspicious Number Pattern")
        }
        
        // Check prefixes
        SPAM_PREFIXES.forEach { (prefix, score) ->
            if (phoneNumber.startsWith(prefix)) {
                totalScore += score * 0.2f // 20% weight
                reasons.add("Known Spam Prefix")
            }
        }
        
        // Message content analysis
        messageContent?.let { message ->
            val (contentScore, patterns) = analyzeMessageContent(message)
            if (contentScore > 0.2f) {
                totalScore += contentScore * 0.4f // 40% weight
                reasons.addAll(patterns)
            }
        }
        
        // Community reporting weight
        if (reportCount > 0) {
            val reportScore = (reportCount * 0.1f).coerceAtMost(0.5f)
            totalScore += reportScore * 0.3f // 30% weight
            reasons.add("$reportCount Reports")
        }
        
        // User blocked weight
        if (userBlocked) {
            totalScore += 0.8f
            reasons.add("User Blocked")
        }
        
        val finalScore = totalScore.coerceIn(0f, 1f)
        val reasonsText = if (reasons.isEmpty()) "No indicators" else reasons.joinToString(", ")
        
        return Pair(finalScore, reasonsText)
    }
    
    private fun hasRepeatingDigits(number: String, minLength: Int): Boolean {
        return number.windowed(minLength).any { window ->
            window.all { it == window[0] }
        }
    }
    
    private fun hasSequentialDigits(number: String, minLength: Int): Boolean {
        return number.windowed(minLength).any { window ->
            window.zipWithNext().all { (a, b) -> b.digitToInt() == a.digitToInt() + 1 }
        }
    }
    
    private fun isPalindrome(number: String): Boolean {
        return number == number.reversed()
    }
}
