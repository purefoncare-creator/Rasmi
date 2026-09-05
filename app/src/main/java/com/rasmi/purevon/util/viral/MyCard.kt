package com.rasmi.purevon.util.viral

/**
 * ✅ VIRAL #4 — بطاقتي (My Card)
 *
 * بطاقة المستخدم الشخصية القابلة للمشاركة كملف vCard مع توقيع الانتشار.
 * المنطق خالص (RFC 6350 مبسط) وقابل للاختبار دون جهاز.
 */
data class MyCardData(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = ""
) {
    /** الاسم الكامل للعرض */
    val displayName: String
        get() = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** هل البطاقة صالحة للمشاركة على الأقل (اسم + هاتف)؟ */
    fun isShareable(): Boolean =
        firstName.isNotBlank() && phone.isNotBlank()
}

object MyCard {

    private const val CR = "\r\n" // RFC 6350 يتطلب CRLF

    /**
     * بناء محتوى ملف vCard 3.0 للبطاقة.
     * الحقول الفارغة تُتخطى؛ الترميز يتبع قواعد الإلحاق في RFC 6350.
     */
    fun buildVCard(data: MyCardData): String {
        if (!data.isShareable()) return ""
        return buildString {
            append("BEGIN:VCARD").append(CR)
            append("VERSION:3.0").append(CR)
            append("FN:").append(escape(data.displayName)).append(CR)

            // N: Family;Given;Middle;Prefix;Suffix
            val family = data.lastName.trim()
            val given = data.firstName.trim()
            append("N:").append(escape(family)).append(";")
                .append(escape(given)).append(";;;").append(CR)

            append("TEL;TYPE=CELL:").append(data.phone.trim()).append(CR)
            data.email.trim().takeIf { it.isNotEmpty() }?.let {
                append("EMAIL;TYPE=INTERNET:").append(escape(it)).append(CR)
            }
            data.company.trim().takeIf { it.isNotEmpty() }?.let {
                append("ORG:").append(escape(it)).append(CR)
            }
            append("END:VCARD")
        }
    }

    /** Escape per RFC 6350 — نفس قواعد VCardBuilder الموجود */
    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
        .replace("\r", "")
}
