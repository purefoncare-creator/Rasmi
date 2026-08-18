package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.domain.repository.SyncRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for [SyncRepositoryImpl] — sync progress tracking, cancellation, and state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: android.content.Context
    private lateinit var messageRepository: MessageRepository
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var prefsEditor: android.content.SharedPreferences.Editor
    private lateinit var repo: SyncRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        prefsEditor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putBoolean(any(), any()) } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor

        repo = SyncRepositoryImpl(context, messageRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ════════════════════════════════════════════════════════════
    // hasCompletedInitialSync
    // ════════════════════════════════════════════════════════════

    @Test
    fun `hasCompletedInitialSync returns false when flag not set`() = testScope.runTest {
        every { prefs.getBoolean("initial_sync_completed", false) } returns false

        val result = repo.hasCompletedInitialSync()

        assertThat(result).isFalse()
    }

    @Test
    fun `hasCompletedInitialSync returns false when flag set but no cache`() = testScope.runTest {
        every { prefs.getBoolean("initial_sync_completed", false) } returns true
        coEvery { messageRepository.hasCachedConversations() } returns false

        val result = repo.hasCompletedInitialSync()

        assertThat(result).isFalse()
    }

    @Test
    fun `hasCompletedInitialSync returns true when flag set and cache exists`() = testScope.runTest {
        every { prefs.getBoolean("initial_sync_completed", false) } returns true
        coEvery { messageRepository.hasCachedConversations() } returns true

        val result = repo.hasCompletedInitialSync()

        assertThat(result).isTrue()
    }

    // ════════════════════════════════════════════════════════════
    // SyncProgress states
    // ════════════════════════════════════════════════════════════

    @Test
    fun `syncProgress starts as Idle`() = testScope.runTest {
        assertThat(repo.syncProgress.value).isEqualTo(SyncRepository.SyncProgress.Idle)
    }

    @Test
    fun `cancelSync sets progress to Idle`() = testScope.runTest {
        repo.cancelSync()

        assertThat(repo.syncProgress.value).isEqualTo(SyncRepository.SyncProgress.Idle)
    }

    // ════════════════════════════════════════════════════════════
    // syncConversation
    // ════════════════════════════════════════════════════════════

    @Test
    fun `syncConversation calls repository with correct threadId`() = testScope.runTest {
        coEvery { messageRepository.syncMessages(42L) } just Runs

        repo.syncConversation(42L)

        coVerify(exactly = 1) { messageRepository.syncMessages(42L) }
    }

    @Test
    fun `syncConversation sets Idle on completion`() = testScope.runTest {
        coEvery { messageRepository.syncMessages(any()) } just Runs

        repo.syncConversation(1L)

        assertThat(repo.syncProgress.value).isEqualTo(SyncRepository.SyncProgress.Idle)
    }

    @Test
    fun `syncConversation sets Idle on error`() = testScope.runTest {
        coEvery { messageRepository.syncMessages(any()) } throws RuntimeException("DB error")

        try { repo.syncConversation(1L) } catch (_: Exception) {}

        assertThat(repo.syncProgress.value).isEqualTo(SyncRepository.SyncProgress.Idle)
    }

    // ════════════════════════════════════════════════════════════
    // syncConversationsOnly
    // ════════════════════════════════════════════════════════════

    @Test
    fun `syncConversationsOnly calls syncAllConversations`() = testScope.runTest {
        coEvery { messageRepository.syncAllConversations() } just Runs

        repo.syncConversationsOnly()

        coVerify(exactly = 1) { messageRepository.syncAllConversations() }
    }

    // ════════════════════════════════════════════════════════════
    // resetSyncState
    // ════════════════════════════════════════════════════════════

    @Test
    fun `resetSyncState clears flag and timestamp`() {
        repo.resetSyncState()

        verify {
            prefsEditor.putBoolean("initial_sync_completed", false)
            prefsEditor.putLong("last_sync_timestamp", 0)
            prefsEditor.apply()
        }
    }
}
