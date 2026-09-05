package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.local.entity.BlockedNumberEntity
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BlockRepositoryImplTest {

    private lateinit var blockedNumberDao: BlockedNumberDao
    private lateinit var whitelistDao: WhitelistDao
    private lateinit var repo: BlockRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        blockedNumberDao = mockk(relaxed = true)
        whitelistDao = mockk(relaxed = true)
        repo = BlockRepositoryImpl(blockedNumberDao, whitelistDao)
    }

    private fun entity(
        id: Long = 1L,
        phoneNumber: String = "+966501234567",
        pattern: String? = null,
        isWildcard: Boolean = false,
        blockCalls: Boolean = true,
        blockMessages: Boolean = true,
        reason: String? = null
    ) = BlockedNumberEntity(
        id = id,
        phoneNumber = phoneNumber,
        pattern = pattern,
        isWildcard = isWildcard,
        blockCalls = blockCalls,
        blockMessages = blockMessages,
        reason = reason
    )

    @Test
    fun `getAllBlockedNumbers returns phone numbers`() = runTest {
        every { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(listOf(
            entity(phoneNumber = "+966500000001"),
            entity(phoneNumber = "+966500000002")
        ))

        val numbers = repo.getAllBlockedNumbers().first()

        assertThat(numbers).containsExactly("+966500000001", "+966500000002")
    }

    @Test
    fun `getAllBlockedNumbers returns empty when none`() = runTest {
        every { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())

        assertThat(repo.getAllBlockedNumbers().first()).isEmpty()
    }

    @Test
    fun `blockNumber removes from whitelist then inserts`() = runTest {
        repo.blockNumber("+966501234567", "Spam")

        coVerify(exactly = 1) { whitelistDao.removeFromWhitelist("+966501234567") }
        coVerify(exactly = 1) {
            blockedNumberDao.insertBlockedNumber(withArg { e ->
                assertThat(e.phoneNumber).isEqualTo("+966501234567")
                assertThat(e.reason).isEqualTo("Spam")
                assertThat(e.blockCalls).isTrue()
                assertThat(e.blockMessages).isTrue()
                assertThat(e.isWildcard).isFalse()
            })
        }
    }

    @Test
    fun `blockNumber still blocks when whitelist removal throws`() = runTest {
        coEvery { whitelistDao.removeFromWhitelist(any()) } throws RuntimeException("db error")

        repo.blockNumber("+966501234567", null)

        coVerify(exactly = 1) { blockedNumberDao.insertBlockedNumber(any()) }
    }

    @Test
    fun `unblockNumber delegates to dao`() = runTest {
        repo.unblockNumber("+966501234567")

        coVerify(exactly = 1) { blockedNumberDao.unblockNumber("+966501234567") }
    }

    @Test
    fun `isNumberBlocked returns true on exact match`() = runTest {
        coEvery { blockedNumberDao.isNumberBlocked("+966501234567") } returns true

        assertThat(repo.isNumberBlocked("+966501234567")).isTrue()
    }

    @Test
    fun `isNumberBlocked returns false when not blocked`() = runTest {
        coEvery { blockedNumberDao.isNumberBlocked("+966501234567") } returns false
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())

        assertThat(repo.isNumberBlocked("+966501234567")).isFalse()
    }

    @Test
    fun `blockWildcard inserts wildcard entity`() = runTest {
        repo.blockWildcard("05555*", "Scam prefix")

        coVerify(exactly = 1) {
            blockedNumberDao.insertBlockedNumber(withArg { e ->
                assertThat(e.pattern).isEqualTo("05555*")
                assertThat(e.isWildcard).isTrue()
            })
        }
    }

    @Test
    fun `shouldBlockCall true when exact blocked`() = runTest {
        coEvery { blockedNumberDao.isNumberBlocked("+966501234567") } returns true

        assertThat(repo.shouldBlockCall("+966501234567")).isTrue()
    }

    @Test
    fun `shouldBlockCall true when wildcard pattern matches`() = runTest {
        coEvery { blockedNumberDao.isNumberBlocked("+96650000000") } returns false
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())
        coEvery { blockedNumberDao.getWildcardBlocks() } returns flowOf(listOf(entity(pattern = "+9665*", isWildcard = true)))

        assertThat(repo.shouldBlockCall("+96650000000")).isTrue()
    }

    @Test
    fun `shouldBlockCall false when nothing matches`() = runTest {
        coEvery { blockedNumberDao.isNumberBlocked("+96677777777") } returns false
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())
        coEvery { blockedNumberDao.getWildcardBlocks() } returns flowOf(emptyList())

        assertThat(repo.shouldBlockCall("+96677777777")).isFalse()
    }

    @Test
    fun `shouldBlockMessage true when exact blocked and messages blocked`() = runTest {
        coEvery { blockedNumberDao.getBlockedNumber("+966501234567") } returns entity(blockMessages = true)

        assertThat(repo.shouldBlockMessage("+966501234567")).isTrue()
    }

    @Test
    fun `shouldBlockMessage false when exact blocked but messages not blocked`() = runTest {
        coEvery { blockedNumberDao.getBlockedNumber("+966501234567") } returns entity(blockMessages = false)
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())
        coEvery { blockedNumberDao.getWildcardBlocks() } returns flowOf(emptyList())

        assertThat(repo.shouldBlockMessage("+966501234567")).isFalse()
    }

    @Test
    fun `shouldBlockMessage true when wildcard pattern matches`() = runTest {
        coEvery { blockedNumberDao.getBlockedNumber("+96655555555") } returns null
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())
        coEvery { blockedNumberDao.getWildcardBlocks() } returns flowOf(listOf(entity(pattern = "+9665*", isWildcard = true)))

        assertThat(repo.shouldBlockMessage("+96655555555")).isTrue()
    }

    @Test
    fun `shouldBlockMessage respects blockMessages false for wildcard`() = runTest {
        coEvery { blockedNumberDao.getBlockedNumber("+96655555555") } returns null
        coEvery { blockedNumberDao.getAllBlockedNumbers() } returns flowOf(emptyList())
        coEvery { blockedNumberDao.getWildcardBlocks() } returns flowOf(listOf(entity(pattern = "+9665*", isWildcard = true, blockMessages = false)))

        assertThat(repo.shouldBlockMessage("+96655555555")).isFalse()
    }
}
