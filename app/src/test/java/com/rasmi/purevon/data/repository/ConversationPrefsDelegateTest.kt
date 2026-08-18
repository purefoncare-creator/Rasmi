package com.rasmi.purevon.data.repository

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.CachedConversationDao
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConversationPrefsDelegateTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var prefsDao: ConversationPreferencesDao
    private lateinit var convDao: CachedConversationDao
    private lateinit var syncDelegate: MessageSyncDelegate
    private lateinit var delegate: ConversationPrefsDelegate

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        prefsDao = mockk(relaxed = true)
        convDao = mockk(relaxed = true)
        syncDelegate = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver

        delegate = ConversationPrefsDelegate(context, prefsDao, convDao, syncDelegate)
    }

    // ════════════════════════════════════════════════════════════
    // setConversationPinned
    // ════════════════════════════════════════════════════════════

    @Test
    fun `setConversationPinned inserts when no existing prefs`() = runTest {
        coEvery { prefsDao.getPreferences(10L) } returns null

        delegate.setConversationPinned(10L, true)

        coVerify(exactly = 1) {
            prefsDao.insertPreferences(withArg { assertThat(it.isPinned).isTrue() })
        }
        coVerify(exactly = 0) { prefsDao.updatePinned(any(), any(), any()) }
    }

    @Test
    fun `setConversationPinned updates when prefs exist`() = runTest {
        coEvery { prefsDao.getPreferences(10L) } returns ConversationPreferencesEntity(
            systemThreadId = 10L, isPinned = false
        )

        delegate.setConversationPinned(10L, true)

        coVerify(exactly = 0) { prefsDao.insertPreferences(any()) }
        coVerify(exactly = 1) { prefsDao.updatePinned(10L, true, any()) }
    }

    @Test
    fun `setConversationPinned invalidates caches`() = runTest {
        coEvery { prefsDao.getPreferences(10L) } returns null

        delegate.setConversationPinned(10L, true)

        coVerify(exactly = 1) { syncDelegate.invalidateAllCaches() }
        coVerify(exactly = 1) { syncDelegate.syncConversations() }
    }

    // ════════════════════════════════════════════════════════════
    // setConversationMuted
    // ════════════════════════════════════════════════════════════

    @Test
    fun `setConversationMuted inserts when no existing prefs`() = runTest {
        coEvery { prefsDao.getPreferences(20L) } returns null

        delegate.setConversationMuted(20L, true)

        coVerify(exactly = 1) {
            prefsDao.insertPreferences(withArg { assertThat(it.isMuted).isTrue() })
        }
    }

    @Test
    fun `setConversationMuted updates when prefs exist`() = runTest {
        coEvery { prefsDao.getPreferences(20L) } returns ConversationPreferencesEntity(
            systemThreadId = 20L, isMuted = false
        )

        delegate.setConversationMuted(20L, true)

        coVerify(exactly = 1) { prefsDao.updateMuted(20L, true, any()) }
    }

    @Test
    fun `setConversationMuted invalidates caches`() = runTest {
        coEvery { prefsDao.getPreferences(20L) } returns null

        delegate.setConversationMuted(20L, true)

        coVerify(exactly = 1) { syncDelegate.invalidateAllCaches() }
    }
}
