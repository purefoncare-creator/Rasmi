package com.rasmi.purevon.domain.usecase.contact

import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.repository.ContactRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Tests for Contact use cases:
 *   GetAllContacts, GetFavoriteContacts, SearchContacts,
 *   GetContactByNumber, ToggleFavorite, DeleteContact
 */
class ContactUseCasesTest {

    private lateinit var repo: ContactRepository

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
    }

    // ════════════════════════════════════════════════════════════
    // GetAllContactsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetAllContacts returns flow from repository`() = runTest {
        val contacts = listOf(mockk<Contact>(), mockk<Contact>())
        every { repo.getAllContacts() } returns flowOf(contacts)

        val useCase = GetAllContactsUseCase(repo)
        val result = useCase().first()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `GetAllContacts returns empty flow when no contacts`() = runTest {
        every { repo.getAllContacts() } returns flowOf(emptyList())

        val useCase = GetAllContactsUseCase(repo)
        val result = useCase().first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // GetFavoriteContactsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetFavoriteContacts returns favorites only`() = runTest {
        val favorites = listOf(mockk<Contact>())
        every { repo.getFavoriteContacts() } returns flowOf(favorites)

        val useCase = GetFavoriteContactsUseCase(repo)
        val result = useCase().first()

        assertThat(result).hasSize(1)
    }

    // ════════════════════════════════════════════════════════════
    // SearchContactsUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `SearchContacts delegates query to repository`() = runTest {
        val contacts = listOf(mockk<Contact>())
        every { repo.searchContacts("Ahmed") } returns flowOf(contacts)

        val useCase = SearchContactsUseCase(repo)
        val result = useCase("Ahmed").first()

        assertThat(result).hasSize(1)
        verify(exactly = 1) { repo.searchContacts("Ahmed") }
    }

    @Test
    fun `SearchContacts empty query returns empty`() = runTest {
        every { repo.searchContacts("") } returns flowOf(emptyList())

        val useCase = SearchContactsUseCase(repo)
        val result = useCase("").first()

        assertThat(result).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // GetContactByNumberUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `GetContactByNumber returns contact when found`() = runTest {
        val contact = mockk<Contact>()
        coEvery { repo.getContactByNumber("+201234567890") } returns contact

        val useCase = GetContactByNumberUseCase(repo)
        val result = useCase("+201234567890")

        assertThat(result).isNotNull()
    }

    @Test
    fun `GetContactByNumber returns null when not found`() = runTest {
        coEvery { repo.getContactByNumber("+201111111111") } returns null

        val useCase = GetContactByNumberUseCase(repo)
        val result = useCase("+201111111111")

        assertThat(result).isNull()
    }

    // ════════════════════════════════════════════════════════════
    // ToggleFavoriteUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `ToggleFavorite sets favorite true`() = runTest {
        val useCase = ToggleFavoriteUseCase(repo)
        useCase(1L, true)

        coVerify(exactly = 1) { repo.setFavorite(1L, true) }
    }

    @Test
    fun `ToggleFavorite sets favorite false`() = runTest {
        val useCase = ToggleFavoriteUseCase(repo)
        useCase(1L, false)

        coVerify(exactly = 1) { repo.setFavorite(1L, false) }
    }

    // ════════════════════════════════════════════════════════════

    // DeleteContactUseCase
    // ════════════════════════════════════════════════════════════

    @Test
    fun `DeleteContact delegates contactId to repository`() = runTest {
        val useCase = DeleteContactUseCase(repo)
        useCase(42L)

        coVerify(exactly = 1) { repo.deleteContact(42L) }
    }
}
