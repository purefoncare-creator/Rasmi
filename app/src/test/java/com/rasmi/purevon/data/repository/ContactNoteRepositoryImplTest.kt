package com.rasmi.purevon.data.repository

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ContactNoteDao
import com.rasmi.purevon.data.local.entity.ContactNoteEntity
import com.rasmi.purevon.domain.model.ContactNote
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ContactNoteRepositoryImplTest {

    private lateinit var dao: ContactNoteDao
    private lateinit var repo: ContactNoteRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = ContactNoteRepositoryImpl(dao)
    }

    private fun entity(
        id: Long = 1L,
        phoneNumber: String = "+966501234567",
        note: String = "Follow up",
        callDuration: Long = 120,
        isIncoming: Boolean = false,
        createdAt: Long = 1000L
    ) = ContactNoteEntity(id, phoneNumber, note, callDuration, isIncoming, createdAt)

    @Test
    fun `getAllNotes maps entities to domain models`() = runTest {
        every { dao.getAllNotes() } returns flowOf(listOf(
            entity(id = 1L, phoneNumber = "+966500000001"),
            entity(id = 2L, phoneNumber = "+966500000002", isIncoming = true)
        ))

        val notes = repo.getAllNotes().toList().flatten()

        assertThat(notes).hasSize(2)
        assertThat(notes[0].id).isEqualTo(1L)
        assertThat(notes[0].phoneNumber).isEqualTo("+966500000001")
        assertThat(notes[0].note).isEqualTo("Follow up")
        assertThat(notes[0].callDuration).isEqualTo(120)
        assertThat(notes[0].isIncoming).isFalse()
        assertThat(notes[1].isIncoming).isTrue()
    }

    @Test
    fun `getAllNotes returns empty list when dao returns empty`() = runTest {
        every { dao.getAllNotes() } returns flowOf(emptyList())

        val notes = repo.getAllNotes().toList().flatten()

        assertThat(notes).isEmpty()
    }

    @Test
    fun `maps all ContactNote fields`() = runTest {
        every { dao.getAllNotes() } returns flowOf(listOf(
            entity(id = 9L, phoneNumber = "+123", note = "hello", callDuration = 42, isIncoming = true, createdAt = 777L)
        ))

        val note: ContactNote = repo.getAllNotes().toList().flatten().single()

        assertThat(note.id).isEqualTo(9L)
        assertThat(note.phoneNumber).isEqualTo("+123")
        assertThat(note.note).isEqualTo("hello")
        assertThat(note.callDuration).isEqualTo(42)
        assertThat(note.isIncoming).isTrue()
        assertThat(note.createdAt).isEqualTo(777L)
    }

    @Test
    fun `getPhoneNumbersWithNotes delegates to dao`() = runTest {
        every { dao.getPhoneNumbersWithNotes() } returns flowOf(listOf("+966500000001", "+966500000002"))

        val phones = repo.getPhoneNumbersWithNotes().toList().flatten()

        assertThat(phones).containsExactly("+966500000001", "+966500000002")
    }
}
