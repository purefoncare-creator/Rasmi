package com.rasmi.purevon.util.viral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ✅ الماسح الداخلي — اختبارات محلل حمولات QR
 */
class QrPayloadParserTest {

    @Test
    fun `vcard full fields parsed`() {
        val data = QrPayloadParser.parse(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ahmed Ali\r\n" +
                "N:Ali;Ahmed;;;\r\nTEL;TYPE=CELL:+966557197822\r\n" +
                "EMAIL;TYPE=INTERNET:a@b.com\r\nORG:ACME\r\nEND:VCARD"
        )
        assertNotNull(data)
        assertEquals("Ahmed Ali", data!!.firstName)
        assertEquals("+966557197822", data.phone)
        assertEquals("a@b.com", data.email)
        assertEquals("ACME", data.company)
    }

    @Test
    fun `vcard arabic name survives`() {
        val data = QrPayloadParser.parse(
            "BEGIN:VCARD\nFN:أحمد علي\nTEL:+966501234567\nEND:VCARD"
        )
        assertEquals("أحمد علي", data!!.firstName)
    }

    @Test
    fun `vcard falls back to N when FN missing`() {
        val data = QrPayloadParser.parse(
            "BEGIN:VCARD\nN:Ali;Ahmed;;;\nTEL:0551234567\nEND:VCARD"
        )
        assertEquals("Ahmed Ali", data!!.firstName)
    }

    @Test
    fun `vcard escaped values unescaped`() {
        val data = QrPayloadParser.parse(
            "BEGIN:VCARD\nFN:Ltd\\; Co\\, LLC\nTEL:0500000000\nEND:VCARD"
        )
        assertEquals("Ltd; Co, LLC", data!!.firstName)
    }

    @Test
    fun `mecard parsed with last first order`() {
        val data = QrPayloadParser.parse(
            "MECARD:N:Ali,Ahmed;TEL:+966557197822;EMAIL:a@b.com;ORG:ACME;;"
        )
        assertEquals("Ahmed Ali", data!!.firstName)
        assertEquals("+966557197822", data.phone)
        assertEquals("a@b.com", data.email)
        assertEquals("ACME", data.company)
    }

    @Test
    fun `tel uri parsed`() {
        val data = QrPayloadParser.parse("tel:+96655-7197822")
        assertEquals("+966557197822", data!!.phone)
        assertEquals("", data.firstName)
    }

    @Test
    fun `random text rejected`() {
        assertNull(QrPayloadParser.parse(null))
        assertNull(QrPayloadParser.parse(""))
        assertNull(QrPayloadParser.parse("hello world"))
        assertNull(QrPayloadParser.parse("tel:abc"))
        assertNull(QrPayloadParser.parse("BEGIN:VCARD\nEND:VCARD"))
    }
}
