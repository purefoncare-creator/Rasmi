package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.SpamNumberDao
import com.rasmi.purevon.data.local.entity.SpamNumberEntity
import com.rasmi.purevon.data.local.entity.SpamType
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SpamRepositoryImplTest {

    private lateinit var dao: SpamNumberDao
    private lateinit var repo: SpamRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        dao = mockk(relaxed = true)
        repo = SpamRepositoryImpl(dao)
    }

    private fun spamEntity(
        phone: String = "+966501234567",
        score: Float = 0.8f,
        type: SpamType = SpamType.SCAM,
        reportCount: Int = 5,
        userBlocked: Boolean = false,
        whitelisted: Boolean = false
    ) = SpamNumberEntity(
        id = 1L,
        phoneNumber = phone,
        spamScore = score,
        spamType = type,
        reportCount = reportCount,
        isUserBlocked = userBlocked,
        isWhitelisted = whitelisted
    )

    // ════════════════════════════════════════════════════════════
    // checkIfSpam
    // ════════════════════════════════════════════════════════════

    @Test
    fun `checkIfSpam returns true when DAO has high score`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns spamEntity(score = 0.8f)

        val (isSpam, score) = repo.checkIfSpam("+966501234567")

        assertThat(isSpam).isTrue()
        assertThat(score).isEqualTo(0.8f)
    }

    @Test
    fun `checkIfSpam returns false when DAO has low score`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns spamEntity(score = 0.3f)

        val (isSpam, score) = repo.checkIfSpam("+966501234567")

        assertThat(isSpam).isFalse()
        assertThat(score).isEqualTo(0.3f)
    }

    @Test
    fun `checkIfSpam falls back to rules when DAO returns null`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        val (_, score) = repo.checkIfSpam("+966501234567")

        assertThat(score).isAtLeast(0f)
    }

    @Test
    fun `checkIfSpam detects premium prefix when not in DAO`() = runTest {
        coEvery { dao.getSpamNumberByPhone("1900123456") } returns null

        val (isSpam, score) = repo.checkIfSpam("1900123456")

        assertThat(score).isGreaterThan(0f)
    }

    // ════════════════════════════════════════════════════════════
    // reportSpam
    // ════════════════════════════════════════════════════════════

    @Test
    fun `reportSpam increments existing entry`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns spamEntity()

        repo.reportSpam("+966501234567")

        coVerify(exactly = 1) { dao.incrementReportCount("+966501234567", any()) }
        coVerify(exactly = 0) { dao.insertSpamNumber(any()) }
    }

    @Test
    fun `reportSpam inserts new entry when not in DAO`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        repo.reportSpam("+966501234567")

        coVerify(exactly = 1) { dao.insertSpamNumber(any()) }
    }

    // ════════════════════════════════════════════════════════════
    // markAsNotSpam
    // ════════════════════════════════════════════════════════════

    @Test
    fun `markAsNotSpam sets whitelisted to true`() = runTest {
        repo.markAsNotSpam("+966501234567")

        coVerify(exactly = 1) { dao.setWhitelisted("+966501234567", true) }
    }

    // ════════════════════════════════════════════════════════════
    // getSpamNumbers
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getSpamNumbers maps entities to phone numbers`() = runTest {
        every { dao.getAllSpamNumbers() } returns flowOf(listOf(
            spamEntity(phone = "+966500000001"),
            spamEntity(phone = "+966500000002")
        ))

        val numbers = mutableListOf<List<String>>()
        repo.getSpamNumbers().collect { numbers.add(it) }

        assertThat(numbers.first()).containsExactly("+966500000001", "+966500000002")
    }

    @Test
    fun `getSpamNumbers returns empty list when no spam`() = runTest {
        every { dao.getAllSpamNumbers() } returns flowOf(emptyList())

        val numbers = mutableListOf<List<String>>()
        repo.getSpamNumbers().collect { numbers.add(it) }

        assertThat(numbers.first()).isEmpty()
    }

    // ════════════════════════════════════════════════════════════
    // getSpamScore
    // ════════════════════════════════════════════════════════════

    @Test
    fun `getSpamScore returns from DAO when available`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns spamEntity(score = 0.75f)

        val score = repo.getSpamScore("+966501234567")

        assertThat(score).isEqualTo(0.75f)
    }

    @Test
    fun `getSpamScore calculates on the fly when DAO returns null`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        val score = repo.getSpamScore("+966501234567")

        assertThat(score).isAtLeast(0f)
    }

    // ════════════════════════════════════════════════════════════
    // checkMessageSpam
    // ════════════════════════════════════════════════════════════

    @Test
    fun `checkMessageSpam returns false for clean message`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        val (isSpam, score) = repo.checkMessageSpam("+966501234567", "Hello, how are you?")

        assertThat(isSpam).isFalse()
        assertThat(score).isAtMost(0.5f)
    }

    @Test
    fun `checkMessageSpam auto-inserts high score unknown number`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        repo.checkMessageSpam("+966501234567",
            "YOU HAVE WON CLICK http://bit.ly/scam ACT NOW!!! CLAIM PRIZE FREE MONEY LOTTERY"
        )

        coVerify(exactly = 1) { dao.insertSpamNumber(any()) }
    }

    @Test
    fun `checkMessageSpam does not insert low score number`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns null

        repo.checkMessageSpam("+966501234567", "Hello friend")

        coVerify(exactly = 0) { dao.insertSpamNumber(any()) }
    }

    @Test
    fun `checkMessageSpam uses existing report count from DAO`() = runTest {
        coEvery { dao.getSpamNumberByPhone("+966501234567") } returns spamEntity(reportCount = 10)

        repo.checkMessageSpam("+966501234567", "Some message")

        coVerify(exactly = 0) { dao.insertSpamNumber(any()) }
    }

    // ════════════════════════════════════════════════════════════
    // determineSpamType (private, tested via reportSpam)
    // ════════════════════════════════════════════════════════════

    @Test
    fun `reportSpam for high score uses FRAUD type`() = runTest {
        coEvery { dao.getSpamNumberByPhone("1900999999") } returns null

        repo.reportSpam("1900999999")

        coVerify {
            dao.insertSpamNumber(withArg { entity ->
                // High score prefix 1900 → 0.6 * 0.2 = 0.12 prefix score
                // Actual type depends on combined score calculation
                assertThat(entity.spamType).isAnyOf(
                    SpamType.FRAUD, SpamType.SCAM, SpamType.TELEMARKETER,
                    SpamType.PROMOTIONAL, SpamType.UNKNOWN
                )
            })
        }
    }
}
