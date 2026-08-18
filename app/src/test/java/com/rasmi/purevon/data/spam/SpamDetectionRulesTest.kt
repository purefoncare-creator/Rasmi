package com.rasmi.purevon.data.spam

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class SpamDetectionRulesTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    // ════════════════════════════════════════════════════════════
    // analyzePhoneNumberCharacteristics
    // ════════════════════════════════════════════════════════════

    @Test
    fun `normal phone number returns low score`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("+966559876543")
        assertThat(score).isAtMost(0.3f)
    }

    @Test
    fun `alphanumeric sender returns zero`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("AMAZON")
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `arabic sender returns zero`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("البنك")
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `very long number gets penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("12345678901234567890")
        assertThat(score).isAtLeast(0.4f)
    }

    @Test
    fun `very short number gets penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("1234")
        assertThat(score).isAtLeast(0.3f)
    }

    @Test
    fun `repeating 4 digits gets penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("55551234567")
        assertThat(score).isAtLeast(0.3f)
    }

    @Test
    fun `repeating 6 digits gets higher penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("8888881234")
        assertThat(score).isAtLeast(0.8f)
    }

    @Test
    fun `all same digit gets high penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("7777777777")
        assertThat(score).isAtLeast(0.7f)
    }

    @Test
    fun `sequential digits gets penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("12345678901")
        assertThat(score).isAtLeast(0.3f)
    }

    @Test
    fun `palindrome 6+ digits gets penalty`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("123321")
        assertThat(score).isAtLeast(0.2f)
    }

    @Test
    fun `empty number returns zero`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("")
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `phone score never exceeds 1`() {
        val score = SpamDetectionRules.analyzePhoneNumberCharacteristics("99999999999999999999")
        assertThat(score).isAtMost(1f)
    }

    // ════════════════════════════════════════════════════════════
    // analyzeMessageContent
    // ════════════════════════════════════════════════════════════

    @Test
    fun `OTP message returns low score`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent("Your verification code is 4829")
        assertThat(score).isEqualTo(0.1f)
        assertThat(patterns).contains("OTP/Verification")
    }

    @Test
    fun `scam message detected`() {
        val (score, _) = SpamDetectionRules.analyzeMessageContent(
            "You have won a prize! Claim your prize now!"
        )
        assertThat(score).isAtLeast(0.3f)
    }

    @Test
    fun `promotional message detected`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent(
            "Dear customer, congratulations! You have been selected"
        )
        assertThat(patterns).isNotEmpty()
    }

    @Test
    fun `URL in message adds penalty`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent(
            "Visit http://bit.ly/spam for free money"
        )
        assertThat(patterns).contains("Contains URL")
    }

    @Test
    fun `excessive caps adds penalty`() {
        val msg = "CONGRATULATIONS YOU HAVE WON TEN MILLION DOLLARS ACT NOW"
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent(msg)
        assertThat(patterns).contains("Excessive Capitals")
    }

    @Test
    fun `excessive exclamation marks adds penalty`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent("Hello!!! Welcome!!! Free!!! Prize!!!")
        assertThat(patterns).contains("Excessive Punctuation")
    }

    @Test
    fun `empty message returns zero`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent("")
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `clean message returns low score`() {
        val (score, _) = SpamDetectionRules.analyzeMessageContent("Hey, are you coming to dinner tonight?")
        assertThat(score).isAtMost(0.2f)
    }

    @Test
    fun `high keyword density detected`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent(
            "win prize free money lottery jackpot claim reward urgent immediately"
        )
        assertThat(patterns).contains("High Keyword Density")
        assertThat(score).isAtLeast(0.6f)
    }

    @Test
    fun `medium keyword density detected`() {
        val (score, patterns) = SpamDetectionRules.analyzeMessageContent(
            "win prize free money"
        )
        assertThat(patterns).contains("Medium Keyword Density")
    }

    @Test
    fun `tinyurl detected in message`() {
        val (_, patterns) = SpamDetectionRules.analyzeMessageContent(
            "Check tinyurl.com/abc for details"
        )
        assertThat(patterns).contains("Contains URL")
    }

    @Test
    fun `content score never exceeds 1`() {
        val (score, _) = SpamDetectionRules.analyzeMessageContent(
            "YOU HAVE WON!!! CLICK http://bit.ly/scam ACT NOW!!! " +
            "PRIZE FREE MONEY LOTTERY JACKPOT WINNER CLAIM REWARD"
        )
        assertThat(score).isAtMost(1f)
    }

    // ════════════════════════════════════════════════════════════
    // calculateCombinedSpamScore
    // ════════════════════════════════════════════════════════════

    @Test
    fun `clean number and clean message returns low score`() {
        val (score, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567",
            messageContent = "Hello friend"
        )
        assertThat(score).isAtMost(0.3f)
    }

    @Test
    fun `premium prefix adds score`() {
        val (score, reasons) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "1900123456",
            messageContent = null
        )
        assertThat(reasons).contains("Known Spam Prefix")
        assertThat(score).isGreaterThan(0f)
    }

    @Test
    fun `user blocked flag adds high score`() {
        val (score, reasons) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567",
            userBlocked = true
        )
        assertThat(reasons).contains("User Blocked")
        assertThat(score).isAtLeast(0.8f)
    }

    @Test
    fun `report count contributes to score`() {
        val (score1, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567", reportCount = 0
        )
        val (score5, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567", reportCount = 5
        )
        assertThat(score5).isGreaterThan(score1)
    }

    @Test
    fun `spam message content contributes to score`() {
        val (scoreClean, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567", messageContent = "Hello"
        )
        val (scoreSpam, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567",
            messageContent = "YOU HAVE WON! CLICK http://bit.ly/scam ACT NOW!!!"
        )
        assertThat(scoreSpam).isGreaterThan(scoreClean)
    }

    @Test
    fun `combined score never exceeds 1`() {
        val (score, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "1900999999",
            messageContent = "YOU HAVE WON CLICK http://bit.ly scam prize free money",
            reportCount = 10,
            userBlocked = true
        )
        assertThat(score).isAtMost(1f)
    }

    @Test
    fun `null message content handled gracefully`() {
        val (score, _) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567",
            messageContent = null
        )
        assertThat(score).isAtLeast(0f)
    }

    @Test
    fun `no indicators returns default text`() {
        val (_, reasons) = SpamDetectionRules.calculateCombinedSpamScore(
            phoneNumber = "+966501234567",
            messageContent = "Hi"
        )
        assertThat(reasons).isNotEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // Pattern collections
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SPAM_KEYWORDS is not empty`() {
        assertThat(SpamDetectionRules.SPAM_KEYWORDS).isNotEmpty()
    }

    @Test
    fun `OTP_PATTERNS is not empty`() {
        assertThat(SpamDetectionRules.OTP_PATTERNS).isNotEmpty()
    }

    @Test
    fun `PROMOTIONAL_PATTERNS is not empty`() {
        assertThat(SpamDetectionRules.PROMOTIONAL_PATTERNS).isNotEmpty()
    }

    @Test
    fun `SCAM_PATTERNS is not empty`() {
        assertThat(SpamDetectionRules.SCAM_PATTERNS).isNotEmpty()
    }

    @Test
    fun `SPAM_PREFIXES is not empty`() {
        assertThat(SpamDetectionRules.SPAM_PREFIXES).isNotEmpty()
    }
}
