package com.rasmi.purevon.util.message

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.regex.Pattern
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart OTP detection & auto-copy manager.
 *
 * Detection strategy (ordered by confidence):
 * 1. **Explicit patterns** — keyword immediately followed by a digit group
 *    (e.g. "Your OTP is 482916", "رمز التحقق: 5831").
 * 2. **Contextual fallback** — if the message contains strong OTP keywords
 *    but no adjacent digit, scan for an isolated 4-8 digit number and
 *    validate it is NOT a phone number, amount, date, or order ID.
 *
 * False-positive mitigations:
 * • Numbers preceded/followed by `.` `,` or currency symbols are rejected (amounts).
 * • Numbers ≥ 9 digits are rejected (phone numbers).
 * • Numbers inside `+xxx...` patterns are rejected (phone numbers).
 * • Dates like 2024, 2025, 2026 etc. are rejected.
 * • Pure zeros (0000, 000000) are rejected.
 */
@Singleton
class OtpManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: com.rasmi.purevon.data.preferences.SettingsDataStore
) {

    @Volatile
    private var cachedMinLength: Int = 4

    // ✅ FIX #4: Removed runBlocking init block — relies on default value 4
    // until refreshMinLength() is called from a coroutine context

    suspend fun refreshMinLength() {
        cachedMinLength = settingsDataStore.otpMinLength.first()
    }

    companion object {
        private const val TAG = "OtpManager"

        // ──────────────────────────────────────────────────────────────
        // TIER 1: Explicit patterns — keyword + adjacent digits (high confidence)
        // These capture the code in group(1).
        // ──────────────────────────────────────────────────────────────

        private val EXPLICIT_PATTERNS: List<Pattern> = listOf(
            // === Universal / English ===
            // "OTP is 123456", "your OTP: 1234", "OTP code 12345678"
            Pattern.compile("\\bOTP\\b[^\\d]{0,20}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "verification code is 1234", "verification code: 123456"
            Pattern.compile("verification\\s+code[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "security code: 1234"
            Pattern.compile("security\\s+code[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "one-time code 123456", "one time password 1234"
            Pattern.compile("one[- ]?time[^\\d]{0,20}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "passcode is 1234", "passcode: 123456"
            Pattern.compile("\\bpasscode[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "code is 1234" / "code: 1234" — only if near start or with "your"
            Pattern.compile("(?:your|the|enter)\\s+code[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "pin is 1234" / "pin: 1234"
            Pattern.compile("\\bpin[^\\d]{0,10}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // Digits followed by keyword: "123456 is your verification code"
            Pattern.compile("\\b(\\d{4,8})\\s+(?:is your|est votre|ist Ihr|es tu|è il tuo)\\b", Pattern.CASE_INSENSITIVE),

            // === Arabic ===
            Pattern.compile("(?:رمز التحقق|رمز التأكيد|كود التحقق|رمز الدخول|الرمز|كود|رمز)[^\\d]{0,15}(\\d{4,8})\\b"),
            // Digits first: "123456 هو رمز"
            Pattern.compile("\\b(\\d{4,8})\\s*(?:هو رمز|رمز التحقق|هو كود)"),

            // === Chinese ===
            Pattern.compile("(?:验证码|確認碼|驗證碼)[^\\d]{0,10}(\\d{4,8})\\b"),

            // === French ===
            Pattern.compile("(?:code de vérification|code de sécurité|votre code)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === German ===
            Pattern.compile("(?:bestätigungscode|sicherheitscode|verifizierungscode|Ihr Code)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Spanish ===
            Pattern.compile("(?:código de verificación|código de seguridad|tu código)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Italian ===
            Pattern.compile("(?:codice di verifica|codice di sicurezza|il tuo codice)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Russian ===
            Pattern.compile("(?:код подтверждения|код безопасности|ваш код|код верификации)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Japanese ===
            Pattern.compile("(?:認証コード|確認コード|セキュリティコード|パスコード)[^\\d]{0,10}(\\d{4,8})\\b"),

            // === Portuguese ===
            Pattern.compile("(?:código de verificação|código de segurança|seu código)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Korean ===
            Pattern.compile("(?:인증번호|인증 코드|보안 코드|확인 코드)[^\\d]{0,10}(\\d{4,8})\\b"),

            // === Turkish ===
            Pattern.compile("(?:doğrulama kodu|güvenlik kodu|onay kodu|şifreniz)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Hindi/Urdu ===
            Pattern.compile("(?:सत्यापन कोड|कोड|پاس ورڈ|کوڈ)[^\\d]{0,15}(\\d{4,8})\\b"),

            // === Indonesian / Malay ===
            Pattern.compile("(?:kode verifikasi|kode keamanan|kod pengesahan|kod keselamatan)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Vietnamese ===
            Pattern.compile("(?:mã xác thực|mã xác minh|mã xác nhận)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Thai ===
            Pattern.compile("(?:รหัสยืนยัน|รหัส OTP|รหัสความปลอดภัย)[^\\d]{0,10}(\\d{4,8})\\b"),

            // === Persian ===
            Pattern.compile("(?:کد تأیید|کد امنیتی|رمز عبور|کد احراز)[^\\d]{0,15}(\\d{4,8})\\b"),

            // === Hebrew ===
            Pattern.compile("(?:קוד אימות|קוד אבטחה|הקוד שלך)[^\\d]{0,15}(\\d{4,8})\\b"),

            // === Ukrainian ===
            Pattern.compile("(?:код підтвердження|код безпеки|ваш код)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Polish / Czech / Hungarian / Romanian / Swedish / Dutch / Greek ===
            Pattern.compile("(?:kod weryfikacyjny|ověřovací kód|biztonsági kód|cod de verificare|verifieringskod|verificatiecode|κωδικός επαλήθευσης)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Bengali / Swahili ===
            Pattern.compile("(?:যাচাইকরণ কোড|uthibitishaji msimbo)[^\\d]{0,15}(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Generic: colon/equals separator — "code: 1234" / "code = 1234" ===
            Pattern.compile("(?:code|código|codice|код|코드|コード|kode?|cod|kód)\\s*[:=]\\s*(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE)
        )

        // ──────────────────────────────────────────────────────────────
        // TIER 2: Strong OTP keywords — if ANY of these appear we allow
        //         the contextual fallback (isolated digit scan).
        //         These are precise phrases; "code" alone is NOT here.
        // ──────────────────────────────────────────────────────────────

        private val STRONG_OTP_KEYWORDS = listOf(
            // English
            "otp", "one-time", "one time", "verification code", "security code",
            "passcode", "2fa", "two-factor", "login code", "sign-in code",
            "access code", "confirm your", "verify your",

            // Arabic
            "رمز التحقق", "رمز التأكيد", "كود التحقق", "رمز الدخول",
            "رمز المصادقة", "رمز مؤقت",

            // Chinese
            "验证码", "驗證碼", "确认码", "確認碼",

            // French
            "code de vérification", "code de sécurité", "code à usage unique",

            // German
            "bestätigungscode", "sicherheitscode", "einmalpasswort",

            // Spanish
            "código de verificación", "código de seguridad",

            // Italian
            "codice di verifica", "codice di sicurezza",

            // Russian
            "код подтверждения", "код безопасности", "одноразовый код",

            // Japanese
            "認証コード", "確認コード", "ワンタイム",

            // Korean
            "인증번호", "인증 코드", "보안 코드",

            // Portuguese
            "código de verificação", "código de segurança",

            // Turkish
            "doğrulama kodu", "güvenlik kodu", "onay kodu",

            // Hindi
            "सत्यापन कोड",

            // Indonesian
            "kode verifikasi", "kode keamanan",

            // Vietnamese
            "mã xác thực", "mã xác minh",

            // Thai
            "รหัสยืนยัน", "รหัส OTP",

            // Persian
            "کد تأیید", "کد امنیتی",

            // Hebrew
            "קוד אימות", "קוד אבטחה",

            // Ukrainian
            "код підтвердження", "код безпеки",

            // Others
            "kod weryfikacyjny", "ověřovací kód", "biztonsági kód",
            "verificatiecode", "verifieringskod", "κωδικός επαλήθευσης"
        )

        // ──────────────────────────────────────────────────────────────
        // EXCLUSION: patterns that look like OTPs but are NOT
        // ──────────────────────────────────────────────────────────────

        /** Characters that surround amounts/prices — the digit is NOT an OTP */
        private val AMOUNT_CONTEXT = Pattern.compile(
            "(?:" +
                "[\\$€£¥₹₽₺฿₫₩₪]\\s*\\d" +   // currency symbol before digit
                "|\\d[.,]\\d{2,}\\b" +             // "1234.50" or "1,234"
                "|\\d\\s*(?:ر\\.?س|ريال|جنيه|دينار|درهم|SAR|USD|EUR|GBP|EGP)" +  // amount + currency name
                ")",
            Pattern.CASE_INSENSITIVE
        )

        /** Looks like a phone number:  +966…, 05…, 09…  (≥ 9 digits or +prefix) */
        private val PHONE_PATTERN = Pattern.compile(
            "(?:\\+\\d{1,4}[\\s-]?)?\\d{9,}"
        )

        /** Year-like numbers: 2020-2030 */
        private val YEAR_PATTERN = Pattern.compile("\\b20[2-3]\\d\\b")
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Detect OTP code in [messageBody].
     *
     * @return the OTP string (4-8 digits) or `null` if nothing found.
     */
    fun detectOtp(messageBody: String): String? {
        if (messageBody.isBlank()) return null

        // ── TIER 1: Explicit keyword→digit patterns (high confidence) ──
        for (pattern in EXPLICIT_PATTERNS) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find()) {
                val candidate = matcher.group(1) ?: continue
                if (isValidOtp(candidate, messageBody)) {
                    Log.d(TAG, "✅ OTP detected (explicit pattern)")
                    return candidate
                }
            }
        }

        // ── TIER 2: Contextual fallback — strong keyword + isolated digits ──
        if (containsStrongKeyword(messageBody)) {
            val candidate = findIsolatedDigitGroup(messageBody)
            if (candidate != null) {
                Log.d(TAG, "✅ OTP detected (contextual pattern)")
                return candidate
            }
        }

        Log.d(TAG, "No OTP found in message")
        return null
    }

    // ──────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────

    /** Does the message contain at least one strong OTP keyword? */
    private fun containsStrongKeyword(text: String): Boolean {
        val lower = text.lowercase()
        return STRONG_OTP_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Scan the message for ALL isolated 4-8 digit groups and pick the best
     * candidate (highest confidence).  Rejects amounts, phones, dates, etc.
     */
    private fun findIsolatedDigitGroup(text: String): String? {
        // Match digit groups that are NOT glued to letters/symbols
        val digitMatcher = Pattern.compile("(?<![\\d+.,])\\b(\\d{4,8})\\b(?![.,]\\d)").matcher(text)
        val candidates = mutableListOf<String>()

        while (digitMatcher.find()) {
            val num = digitMatcher.group(1) ?: continue
            if (isValidOtp(num, text)) {
                candidates.add(num)
            }
        }

        if (candidates.isEmpty()) return null

        // Prefer 6 digits (most common OTP length), then 4, 5, 8
        return candidates.sortedByDescending { otpLengthScore(it.length) }.first()
    }

    /** Heuristic score: 6-digit = most common OTP length */
    private fun otpLengthScore(len: Int): Int = when (len) {
        6 -> 10
        4 -> 8
        5 -> 7
        8 -> 6
        7 -> 3
        else -> 1
    }

    /** Validate that a digit string is a plausible OTP (not a phone, amount, date, etc.) */
    private fun isValidOtp(candidate: String, fullMessage: String): Boolean {
        // Length check — respect user's otpMinLength setting
        if (candidate.length !in cachedMinLength..8) return false

        // All zeros → not an OTP
        if (candidate.all { it == '0' }) return false

        // Looks like a year (2020–2030)
        if (candidate.length == 4 && YEAR_PATTERN.matcher(candidate).matches()) return false

        // Check if this number appears inside a phone-number-like pattern in the message
        if (looksLikePhoneInContext(candidate, fullMessage)) return false

        // Check if this number appears inside a monetary amount pattern
        if (looksLikeAmountInContext(candidate, fullMessage)) return false

        return true
    }

    /** Returns true if [digits] belongs to a phone number in [text]. */
    private fun looksLikePhoneInContext(digits: String, text: String): Boolean {
        val phoneMatcher = PHONE_PATTERN.matcher(text)
        while (phoneMatcher.find()) {
            val phone = phoneMatcher.group()
            if (phone.contains(digits) && phone.replace(Regex("[^\\d]"), "").length >= 9) {
                return true
            }
        }
        return false
    }

    /** Returns true if [digits] sits inside a currency/amount context. */
    private fun looksLikeAmountInContext(digits: String, text: String): Boolean {
        val idx = text.indexOf(digits)
        if (idx < 0) return false
        // Check a window around the digit occurrence
        val start = (idx - 5).coerceAtLeast(0)
        val end = (idx + digits.length + 10).coerceAtMost(text.length)
        val window = text.substring(start, end)
        return AMOUNT_CONTEXT.matcher(window).find()
    }
    
    /**
     * Auto-copy OTP to clipboard
     */
    fun autoCopyOtp(messageBody: String, showToast: Boolean = true): Boolean {
        val otp = detectOtp(messageBody)
        
        return if (otp != null) {
            val copyAction = Runnable {
                try {
                    val clip = ClipData.newPlainText("OTP", otp)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboardManager.setPrimaryClip(clip)
                    Log.d(TAG, "OTP copied to clipboard successfully")
                    
                    if (showToast) {
                        val masked = otp.take(2) + "*".repeat((otp.length - 2).coerceAtLeast(0))
                        Toast.makeText(context, "OTP copied: $masked", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying OTP to clipboard", e)
                }
            }
            // Execute synchronously if already on main thread, otherwise post
            if (Looper.myLooper() == Looper.getMainLooper()) {
                copyAction.run()
            } else {
                Handler(Looper.getMainLooper()).post(copyAction)
            }
            true
        } else {
            false
        }
    }
    
    /**
     * Copy text to clipboard (must be called from main thread)
     */
    fun copyToClipboard(text: String) {
        fun createSensitiveClip(label: String, content: String): ClipData {
            val clip = ClipData.newPlainText(label, content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            return clip
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            clipboardManager.setPrimaryClip(createSensitiveClip("OTP", text))
        } else {
            Handler(Looper.getMainLooper()).post {
                clipboardManager.setPrimaryClip(createSensitiveClip("OTP", text))
            }
        }
    }
    
    /**
     * Get copied text from clipboard
     */
    fun getClipboardText(): String? {
        return try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting clipboard text", e)
            null
        }
    }
    
    /**
     * Check if OTP auto-copy is enabled
     */
    suspend fun isOtpAutoCopyEnabled(): Boolean {
        return settingsDataStore.otpAutoCopyEnabled.first()
    }
}
