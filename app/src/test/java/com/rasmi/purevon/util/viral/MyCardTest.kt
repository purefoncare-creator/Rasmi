package com.rasmi.purevon.util.viral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ✅ VIRAL #4 — اختبارات بطاقتي
 */
class MyCardTest {

    @Test
    fun `builds valid vcard with all fields`() {
        val vcf = MyCard.buildVCard(
            MyCardData("أحمد", "العلي", "+966501234567", "ahmed@example.com", "شركة النور")
        )
        assertTrue(vcf.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(vcf.contains("FN:أحمد العلي\r\n"))
        assertTrue(vcf.contains("N:العلي;أحمد;;;\r\n"))
        assertTrue(vcf.contains("TEL;TYPE=CELL:+966501234567\r\n"))
        assertTrue(vcf.contains("EMAIL;TYPE=INTERNET:ahmed@example.com\r\n"))
        assertTrue(vcf.contains("ORG:شركة النور\r\n"))
        assertTrue(vcf.endsWith("END:VCARD"))
    }

    @Test
    fun `omits empty optional fields`() {
        val vcf = MyCard.buildVCard(MyCardData(firstName = "Sara", phone = "0501234567"))
        assertFalse(vcf.contains("EMAIL"))
        assertFalse(vcf.contains("ORG"))
    }

    @Test
    fun `empty card returns empty string`() {
        assertEquals("", MyCard.buildVCard(MyCardData()))
        // هاتف بلا اسم غير كافٍ
        assertEquals("", MyCard.buildVCard(MyCardData(phone = "0501234567")))
    }

    @Test
    fun `escapes special vcard characters`() {
        assertEquals("Nour\\; Co\\, Ltd", MyCard.escape("Nour; Co, Ltd"))
        assertEquals("line1\\nline2", MyCard.escape("line1\nline2"))
    }

    @Test
    fun `displayName combines parts safely`() {
        assertEquals("أحمد العلي", MyCardData(" أحمد ", " العلي ").displayName)
        assertEquals("أحمد", MyCardData("أحمد", "").displayName)
    }
}
