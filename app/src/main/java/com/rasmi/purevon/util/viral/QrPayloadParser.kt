package com.rasmi.purevon.util.viral

/**
 * ✅ الماسح الداخلي — محلل حمولات QR جهات الاتصال (خالص قابل للاختبار)
 *
 * يدعم: vCard 3.0/4.0، MECARD، tel:
 * يعيد [MyCardData] أو null إن لم يوجد اسم ولا رقم صالح.
 */
object QrPayloadParser {

    fun parse(raw: String?): MyCardData? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text.contains("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text)
            text.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(text)
            else -> parseTel(text)
        }
    }

    private fun parseVCard(text: String): MyCardData? {
        var fn = ""
        var family = ""
        var given = ""
        var tel = ""
        var email = ""
        var org = ""
        for (line in text.split("\r\n", "\n", "\r")) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val property = line.substring(0, idx).trim().uppercase()
            val value = line.substring(idx + 1).trim()
            when {
                property == "FN" && fn.isEmpty() -> fn = value
                property == "N" && family.isEmpty() && given.isEmpty() -> {
                    val parts = value.split(';')
                    family = parts.getOrNull(0)?.trim().orEmpty()
                    given = parts.getOrNull(1)?.trim().orEmpty()
                }
                property.startsWith("TEL") && tel.isEmpty() -> {
                    val clean = cleanPhone(value)
                    if (clean.isNotEmpty()) tel = clean
                }
                property.startsWith("EMAIL") && email.isEmpty() -> email = value
                property == "ORG" && org.isEmpty() ->
                    org = value.substringBefore(';').trim()
            }
        }
        val name = unescape(fn.ifBlank { "$given $family".trim() })
        if (name.isBlank() && tel.isBlank()) return null
        return MyCardData(
            firstName = name,
            lastName = "",
            phone = tel,
            email = unescape(email),
            company = unescape(org)
        )
    }

    private fun parseMeCard(text: String): MyCardData? {
        val body = if (text.length > 7) text.substring(7) else ""
        var name = ""
        var tel = ""
        var email = ""
        var org = ""
        for (token in body.split(';')) {
            val idx = token.indexOf(':')
            if (idx <= 0) continue
            val key = token.substring(0, idx).trim().uppercase()
            val value = token.substring(idx + 1).trim()
            when (key) {
                "N" -> {
                    // ترتيب MECARD: الأخير،الأول
                    val parts = value.split(',')
                    name = if (parts.size >= 2) {
                        "${parts[1].trim()} ${parts[0].trim()}".trim()
                    } else value.trim()
                }
                "TEL" -> { val c = cleanPhone(value); if (tel.isEmpty() && c.isNotEmpty()) tel = c }
                "EMAIL" -> if (email.isEmpty()) email = value
                "ORG" -> if (org.isEmpty()) org = value
            }
        }
        if (name.isBlank() && tel.isBlank()) return null
        return MyCardData(name, "", tel, email, org)
    }

    private fun parseTel(text: String): MyCardData? {
        if (!text.startsWith("tel:", ignoreCase = true)) return null
        val phone = cleanPhone(text.substring(4))
        if (phone.isEmpty()) return null
        return MyCardData(phone = phone)
    }

    private fun cleanPhone(value: String): String =
        value.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) }

    private fun unescape(value: String): String = value
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
