package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContactTest {

    private fun createContact(
        id: Long = 1L,
        displayName: String = "Alice",
        phoneNumber: String = "+1234567890",
        phoneType: String? = "Mobile",
        photoUri: String? = null,
        email: String? = null,
        company: String? = null,
        isFavorite: Boolean = false,
        isBlocked: Boolean = false,
        lastContactedTime: Long? = null,
        timesContacted: Int = 0,
        preferredSimSlot: Int? = null
    ) = Contact(
        id = id, displayName = displayName, phoneNumber = phoneNumber,
        phoneType = phoneType, photoUri = photoUri, email = email,
        company = company, isFavorite = isFavorite, isBlocked = isBlocked,
        lastContactedTime = lastContactedTime, timesContacted = timesContacted,
        preferredSimSlot = preferredSimSlot
    )

    @Test
    fun `name alias returns displayName`() {
        val contact = createContact(displayName = "Bob")
        assertThat(contact.name).isEqualTo("Bob")
    }

    @Test
    fun `lastContactTime alias returns lastContactedTime`() {
        val contact = createContact(lastContactedTime = 5000L)
        assertThat(contact.lastContactTime).isEqualTo(5000L)
    }

    @Test
    fun `equality works`() {
        val a = createContact(id = 1L)
        val b = createContact(id = 1L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `inequality on different id`() {
        val a = createContact(id = 1L)
        val b = createContact(id = 2L)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `copy modifies only specified fields`() {
        val original = createContact(isFavorite = false)
        val copy = original.copy(isFavorite = true)
        assertThat(copy.isFavorite).isTrue()
        assertThat(copy.id).isEqualTo(original.id)
    }

    @Test
    fun `null fields are valid`() {
        val contact = createContact(
            phoneType = null, photoUri = null, email = null,
            company = null, lastContactedTime = null, preferredSimSlot = null
        )
        assertThat(contact.phoneType).isNull()
        assertThat(contact.photoUri).isNull()
        assertThat(contact.email).isNull()
        assertThat(contact.company).isNull()
        assertThat(contact.lastContactedTime).isNull()
        assertThat(contact.preferredSimSlot).isNull()
    }

    @Test
    fun `isFavorite defaults false`() {
        val contact = createContact()
        assertThat(contact.isFavorite).isFalse()
    }

    @Test
    fun `isBlocked defaults false`() {
        val contact = createContact()
        assertThat(contact.isBlocked).isFalse()
    }

    @Test
    fun `timesContacted defaults 0`() {
        val contact = createContact()
        assertThat(contact.timesContacted).isEqualTo(0)
    }

    @Test
    fun `blocked contact`() {
        val contact = createContact(isBlocked = true)
        assertThat(contact.isBlocked).isTrue()
    }

    @Test
    fun `hash is consistent`() {
        val contact = createContact()
        assertThat(contact.hashCode()).isEqualTo(contact.hashCode())
    }

    @Test
    fun `toString contains displayName`() {
        val contact = createContact(displayName = "Charlie")
        assertThat(contact.toString()).contains("Charlie")
    }
}
