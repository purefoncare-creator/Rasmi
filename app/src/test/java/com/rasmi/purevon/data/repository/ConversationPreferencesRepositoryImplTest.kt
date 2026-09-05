package com.rasmi.purevon.data.repository

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import com.rasmi.purevon.domain.repository.MessageRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConversationPreferencesRepositoryImplTest {

    private lateinit var dao: ConversationPreferencesDao
    private lateinit var messageRepository: MessageRepository
    private lateinit var repo: ConversationPreferencesRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        repo = ConversationPreferencesRepositoryImpl(dao, messageRepository)
    }

    private fun prefs(
        threadId: Long = 10L,
        isPinned: Boolean = false,
        isMuted: Boolean = false,
        isArchived: Boolean = false
    ) = ConversationPreferencesEntity(systemThreadId = threadId, isPinned = isPinned, isMuted = isMuted, isArchived = isArchived)

    @Test
    fun `togglePin pins when not previously pinned`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs(isPinned = false)

        val result = repo.togglePin(10L)

        assertThat(result).isTrue()
        coVerify(exactly = 1) { dao.updatePinned(10L, true, any()) }
        coVerify(exactly = 0) { dao.insertPreferences(any()) }
        coVerify(exactly = 1) { messageRepository.notifyConversationsChanged() }
    }

    @Test
    fun `togglePin unpins when previously pinned`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs(isPinned = true)

        val result = repo.togglePin(10L)

        assertThat(result).isFalse()
        coVerify(exactly = 1) { dao.updatePinned(10L, false, any()) }
    }

    @Test
    fun `togglePin inserts new row when no settings exist`() = runTest {
        coEvery { dao.getPreferences(10L) } returns null

        val result = repo.togglePin(10L)

        assertThat(result).isTrue()
        coVerify(exactly = 1) {
            dao.insertPreferences(withArg { entity ->
                assertThat(entity.systemThreadId).isEqualTo(10L)
                assertThat(entity.isPinned).isTrue()
            })
        }
        coVerify(exactly = 0) { dao.updatePinned(any(), any(), any()) }
    }

    @Test
    fun `setMuted inserts when no settings exist`() = runTest {
        coEvery { dao.getPreferences(10L) } returns null

        repo.setMuted(10L, true, 999L)

        coVerify(exactly = 1) {
            dao.insertPreferences(withArg { entity ->
                assertThat(entity.systemThreadId).isEqualTo(10L)
                assertThat(entity.isMuted).isTrue()
            })
        }
        coVerify(exactly = 0) { dao.updateMuted(any(), any(), any()) }
        coVerify(exactly = 1) { messageRepository.notifyConversationsChanged() }
    }

    @Test
    fun `setMuted updates when settings exist`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs()

        repo.setMuted(10L, true, 999L)

        coVerify(exactly = 1) { dao.updateMuted(10L, true, any()) }
        coVerify(exactly = 0) { dao.insertPreferences(any()) }
    }

    @Test
    fun `isMuted returns true when muted`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs(isMuted = true)

        val muted = repo.isMuted(10L)

        assertThat(muted).isTrue()
    }

    @Test
    fun `isMuted returns false when no settings`() = runTest {
        coEvery { dao.getPreferences(10L) } returns null

        val muted = repo.isMuted(10L)

        assertThat(muted).isFalse()
    }

    @Test
    fun `toggleArchive archives when not archived`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs(isArchived = false)

        val result = repo.toggleArchive(10L)

        assertThat(result).isTrue()
        coVerify(exactly = 1) { dao.updateArchived(10L, true, any()) }
        coVerify(exactly = 1) { messageRepository.notifyConversationsChanged() }
    }

    @Test
    fun `toggleArchive unarchives when previously archived`() = runTest {
        coEvery { dao.getPreferences(10L) } returns prefs(isArchived = true)

        val result = repo.toggleArchive(10L)

        assertThat(result).isFalse()
        coVerify(exactly = 1) { dao.updateArchived(10L, false, any()) }
    }

    @Test
    fun `toggleArchive inserts when no settings exist`() = runTest {
        coEvery { dao.getPreferences(10L) } returns null

        val result = repo.toggleArchive(10L)

        assertThat(result).isTrue()
        coVerify(exactly = 1) { dao.insertPreferences(any()) }
        coVerify(exactly = 0) { dao.updateArchived(any(), any(), any()) }
    }
}
