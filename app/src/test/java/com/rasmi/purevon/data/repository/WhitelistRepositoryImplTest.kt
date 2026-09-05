package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.BlockedNumberDao
import com.rasmi.purevon.data.local.dao.WhitelistDao
import com.rasmi.purevon.data.local.entity.WhitelistEntity
import com.rasmi.purevon.domain.model.WhitelistNumber
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class WhitelistRepositoryImplTest {

    private lateinit var whitelistDao: WhitelistDao
    private lateinit var blockedNumberDao: BlockedNumberDao
    private lateinit var repo: WhitelistRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        whitelistDao = mockk(relaxed = true)
        blockedNumberDao = mockk(relaxed = true)
        repo = WhitelistRepositoryImpl(whitelistDao, blockedNumberDao)
    }

    private fun entity(
        id: Long = 1L,
        phoneNumber: String = "+966501234567",
        contactName: String? = "Alice",
        reason: String? = null,
        createdAt: Long = 1000L
    ) = WhitelistEntity(id, phoneNumber, contactName, reason, createdAt)

    @Test
    fun `getAllWhitelistNumbers maps entities to domain models`() = runTest {
        every { whitelistDao.getAllWhitelistNumbers() } returns flowOf(listOf(
            entity(id = 1L, phoneNumber = "+966500000001", contactName = "Alice"),
            entity(id = 2L, phoneNumber = "+966500000002", contactName = null)
        ))

        val numbers = repo.getAllWhitelistNumbers().toList().flatten()

        assertThat(numbers).hasSize(2)
        val first: WhitelistNumber = numbers[0]
        assertThat(first.id).isEqualTo(1L)
        assertThat(first.phoneNumber).isEqualTo("+966500000001")
        assertThat(first.contactName).isEqualTo("Alice")
        assertThat(first.reason).isNull()
        assertThat(first.createdAt).isEqualTo(1000L)
        assertThat(numbers[1].contactName).isNull()
    }

    @Test
    fun `getAllWhitelistNumbers returns empty when no entries`() = runTest {
        every { whitelistDao.getAllWhitelistNumbers() } returns flowOf(emptyList())

        val numbers = repo.getAllWhitelistNumbers().toList().flatten()

        assertThat(numbers).isEmpty()
    }

    @Test
    fun `addToWhitelist removes from blocklist first then inserts`() = runTest {
        coEvery { blockedNumberDao.unblockNumber("+966501234567") } returns Unit

        repo.addToWhitelist("+966501234567", "Alice")

        coVerify(exactly = 1) { blockedNumberDao.unblockNumber("+966501234567") }
        coVerify(exactly = 1) {
            whitelistDao.insertWhitelistNumber(withArg { entity ->
                assertThat(entity.phoneNumber).isEqualTo("+966501234567")
                assertThat(entity.contactName).isEqualTo("Alice")
            })
        }
    }

    @Test
    fun `addToWhitelist still inserts when blocklist removal fails`() = runTest {
        coEvery { blockedNumberDao.unblockNumber(any()) } throws RuntimeException("db error")

        repo.addToWhitelist("+966509999999", null)

        coVerify(exactly = 1) { whitelistDao.insertWhitelistNumber(any()) }
    }

    @Test
    fun `removeFromWhitelist delegates to dao`() = runTest {
        repo.removeFromWhitelist("+966501234567")

        coVerify(exactly = 1) { whitelistDao.removeFromWhitelist("+966501234567") }
    }
}
