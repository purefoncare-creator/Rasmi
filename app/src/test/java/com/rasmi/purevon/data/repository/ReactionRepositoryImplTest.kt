package com.rasmi.purevon.data.repository

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.local.dao.MessageReactionDao
import com.rasmi.purevon.data.local.entity.MessageReactionEntity
import com.rasmi.purevon.domain.model.MessageReaction
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ReactionRepositoryImplTest {

    private lateinit var dao: MessageReactionDao
    private lateinit var repo: ReactionRepositoryImpl

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        dao = mockk(relaxed = true)
        repo = ReactionRepositoryImpl(dao)
    }

    private fun entity(
        id: Long = 1L,
        messageId: Long = 10L,
        emoji: String = "❤️",
        userId: String = "me",
        timestamp: Long = 1000L
    ) = MessageReactionEntity(id, messageId, emoji, timestamp, userId, synced = false)

    @Test
    fun `addReaction inserts reaction and returns success`() = runTest {
        val result = repo.addReaction(10L, "👍")

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) {
            dao.insertReaction(withArg { entity ->
                assertThat(entity.messageId).isEqualTo(10L)
                assertThat(entity.emoji).isEqualTo("👍")
                assertThat(entity.userId).isEqualTo("me")
            })
        }
    }

    @Test
    fun `addReaction returns failure when dao throws`() = runTest {
        coEvery { dao.insertReaction(any()) } throws RuntimeException("db error")

        val result = repo.addReaction(10L, "👍")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `removeReaction removes user reaction`() = runTest {
        val result = repo.removeReaction(10L)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { dao.removeUserReaction(10L, "me") }
    }

    @Test
    fun `toggleReaction delegates to dao`() = runTest {
        val result = repo.toggleReaction(10L, "🔥")

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { dao.toggleReaction(10L, "🔥", "me") }
    }

    @Test
    fun `getReactions maps entities to domain`() = runTest {
        coEvery { dao.getReactionsForMessage(10L) } returns listOf(
            entity(id = 1L, emoji = "❤️", userId = "me"),
            entity(id = 2L, emoji = "👍", userId = "other")
        )

        val reactions = repo.getReactions(10L)

        assertThat(reactions).hasSize(2)
        val first: MessageReaction = reactions[0]
        assertThat(first.id).isEqualTo(1L)
        assertThat(first.messageId).isEqualTo(10L)
        assertThat(first.emoji).isEqualTo("❤️")
        assertThat(first.userId).isEqualTo("me")
        assertThat(first.timestamp).isEqualTo(1000L)
    }

    @Test
    fun `getReactionsFlow emits mapped domain reactions`() = runTest {
        every { dao.getReactionsForMessageFlow(10L) } returns flowOf(listOf(
            entity(id = 1L, emoji = "❤️")
        ))

        val all = repo.getReactionsFlow(10L).toList()

        assertThat(all.flatten()[0].emoji).isEqualTo("❤️")
    }

    @Test
    fun `getReactionSummary groups by emoji and finds my reaction`() = runTest {
        coEvery { dao.getReactionsForMessage(10L) } returns listOf(
            entity(id = 1L, emoji = "❤️", userId = "me"),
            entity(id = 2L, emoji = "👍", userId = "other"),
            entity(id = 3L, emoji = "👍", userId = "third")
        )

        val summary = repo.getReactionSummary(10L)

        assertThat(summary.messageId).isEqualTo(10L)
        assertThat(summary.reactions).containsEntry("❤️", 1)
        assertThat(summary.reactions).containsEntry("👍", 2)
        assertThat(summary.myReaction).isEqualTo("❤️")
        assertThat(summary.totalCount).isEqualTo(3)
    }

    @Test
    fun `getReactionSummary with no reactions has empty map and null myReaction`() = runTest {
        coEvery { dao.getReactionsForMessage(10L) } returns emptyList()

        val summary = repo.getReactionSummary(10L)

        assertThat(summary.reactions).isEmpty()
        assertThat(summary.myReaction).isNull()
        assertThat(summary.totalCount).isEqualTo(0)
    }

    @Test
    fun `getUserReaction returns emoji when present`() = runTest {
        coEvery { dao.getUserReaction(10L, "me") } returns entity(emoji = "😮")

        val emoji = repo.getUserReaction(10L)

        assertThat(emoji).isEqualTo("😮")
    }

    @Test
    fun `getUserReaction returns null when no reaction`() = runTest {
        coEvery { dao.getUserReaction(10L, "me") } returns null

        val emoji = repo.getUserReaction(10L)

        assertThat(emoji).isNull()
    }

    @Test
    fun `getMessagesWithReaction returns ids`() = runTest {
        coEvery { dao.getMessagesWithReaction("🔥", 10) } returns listOf(1L, 2L)

        val ids = repo.getMessagesWithReaction("🔥", 10)

        assertThat(ids).containsExactly(1L, 2L)
    }

    @Test
    fun `getMostUsedReactions sorts by descending count`() = runTest {
        coEvery { dao.getUserReactionStats("me") } returns mapOf("👍" to 5, "❤️" to 9, "🔥" to 2)

        val ranked = repo.getMostUsedReactions()

        assertThat(ranked).containsExactly("❤️" to 9, "👍" to 5, "🔥" to 2).inOrder()
    }
}
