package com.rasmi.purevon.util.viral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ✅ VIRAL #4b — اختبارات منطق بطاقة QR
 */
class QrContactCardTest {

    @Test
    fun `payload wraps number in tel uri`() {
        assertEquals("tel:0557197822", QrContactCard.buildQrPayload("0557197822"))
    }

    @Test
    fun `payload strips spaces dashes and inner plus`() {
        assertEquals(
            "tel:+966557197822",
            QrContactCard.buildQrPayload("+966 55-71+978 22")
        )
    }

    @Test
    fun `null or unknown numbers rejected`() {
        assertNull(QrContactCard.buildQrPayload(null))
        assertNull(QrContactCard.buildQrPayload(""))
        assertNull(QrContactCard.buildQrPayload("   "))
        assertNull(QrContactCard.buildQrPayload("Unknown"))
        assertNull(QrContactCard.buildQrPayload("+"))
    }

    @Test
    fun `mask shows last four digits only`() {
        assertEquals("•••• 7822", QrContactCard.maskPhone("+966557197822"))
    }

    @Test
    fun `short numbers shown fully`() {
        assertEquals("911", QrContactCard.maskPhone("911"))
        assertEquals("7822", QrContactCard.maskPhone("7822"))
        assertEquals("", QrContactCard.maskPhone(null))
    }

    @Test
    fun `arabic indic digits masked too`() {
        val m = QrContactCard.maskPhone("٠٥٥٧١٩٧٨٢٢")
        assert(m.startsWith("•••• "))
        assertEquals(4, m.removePrefix("•••• ").length)
    }

    @Test
    fun `file name is sanitized and stable`() {
        assertEquals("rasmi_card_6557197822.png", QrContactCard.cardFileName("+966 55-7197822"))
        assertEquals("rasmi_card_contact.png", QrContactCard.cardFileName(null))
    }

    // ✅ vCard 3.0 — المسح يعرض «إضافة لجهات الاتصال»

    @Test
    fun `contact payload is full vcard with all provided fields`() {
        val payload = QrContactCard.buildContactPayload(
            com.rasmi.purevon.util.viral.MyCardData(
                firstName = "Ahmed",
                lastName = "Ali",
                phone = "+966557197822",
                email = "a@b.com",
                company = "ACME"
            )
        )!!
        assert(payload.startsWith("BEGIN:VCARD"))
        assert(payload.contains("VERSION:3.0"))
        assert(payload.contains("FN:Ahmed Ali"))
        assert(payload.contains("TEL;TYPE=CELL:+966557197822"))
        assert(payload.contains("EMAIL;TYPE=INTERNET:a@b.com"))
        assert(payload.contains("ORG:ACME"))
        assert(payload.endsWith("END:VCARD"))
    }

    @Test
    fun `contact payload skips empty email and company`() {
        val payload = QrContactCard.buildContactPayload(
            com.rasmi.purevon.util.viral.MyCardData(firstName = "Sara", phone = "0551234567")
        )!!
        assert(payload.startsWith("BEGIN:VCARD"))
        assert(!payload.contains("EMAIL"))
        assert(!payload.contains("ORG"))
    }

    @Test
    fun `contact payload falls back to tel when name missing`() {
        val payload = QrContactCard.buildContactPayload(
            com.rasmi.purevon.util.viral.MyCardData(phone = "0557197822")
        )!!
        assertEquals("tel:0557197822", payload)
    }
}
