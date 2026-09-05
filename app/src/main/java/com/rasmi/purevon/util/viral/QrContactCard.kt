package com.rasmi.purevon.util.viral

/**
 * ✅ VIRAL #4b — بطاقة QR جهة الاتصال (نمط سناب شات)
 *
 * المنطق الخالص القابل للاختبار:
 * - حمولة الـQR تحمل الرقم الكامل (tel:) ليعمل المسح بكاميرا أي هاتف
 * - البطاقة المرئية تعرض آخر 4 أرقام فقط (خصوصية من يرى الشاشة/لقطة الشاشة)
 */
object QrContactCard {

    private const val TEL_PREFIX = "tel:"

    /**
     * بناء حمولة الـQR لبطاقة اتصال كاملة:
     * vCard 3.0 (اسم + هاتف + بريد + شركة) ليظهر عند المسح «إضافة لجهات الاتصال»،
     * مع سقوط آمن إلى tel: إذا غاب الاسم.
     */
    fun buildContactPayload(data: com.rasmi.purevon.util.viral.MyCardData): String? {
        val vcard = com.rasmi.purevon.util.viral.MyCard.buildVCard(data)
        if (vcard.isNotEmpty()) return vcard
        return buildQrPayload(data.phone)
    }

    /**
     * بناء حمولة الـQR: tel:<رقم نظيف>
     * يعيد null إذا كان الرقم فارغًا أو «Unknown»
     */
    fun buildQrPayload(phoneNumber: String?): String? {
        val digits = phoneNumber?.trim().orEmpty()
        if (digits.isEmpty() || digits.equals("Unknown", ignoreCase = true)) return null
        val cleaned = digits.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty() || cleaned == "+") return null
        // + في غير بداية الرقم يفسد tel: — نحتفظ بأول + فقط
        val normalized = if (cleaned.startsWith("+")) {
            "+" + cleaned.substring(1).replace("+", "")
        } else {
            cleaned.replace("+", "")
        }
        return TEL_PREFIX + normalized
    }

    /**
     * إخفاء الرقم للعرض المرئي: آخر 4 أرقام فقط.
     * أقل من 5 أرقام → يظهر كاملًا. يدعم الأرقام العربية-الفارسية.
     */
    fun maskPhone(phoneNumber: String?): String {
        val digitsOnly = phoneNumber?.filter { it.isDigit() }.orEmpty()
        if (digitsOnly.length <= 4) return digitsOnly
        return "•••• " + digitsOnly.takeLast(4)
    }

    /** اسم ملف آمن للبطاقة داخل sharedCacheDir */
    fun cardFileName(phoneNumber: String?): String {
        val safe = phoneNumber?.filter { it.isLetterOrDigit() }?.takeLast(10)
        return "rasmi_card_${if (safe.isNullOrEmpty()) "contact" else safe}.png"
    }
}
