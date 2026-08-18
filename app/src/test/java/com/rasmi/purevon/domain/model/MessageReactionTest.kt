package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageReactionTest {

    @Test
    fun `MessageReaction default values`() {
        val reaction = MessageReaction(messageId = 1L, emoji = "👍")
        assertThat(reaction.id).isEqualTo(0)
        assertThat(reaction.messageId).isEqualTo(1L)
        assertThat(reaction.emoji).isEqualTo("👍")
        assertThat(reaction.userId).isEqualTo("self")
        assertThat(reaction.synced).isFalse()
    }

    @Test
    fun `MessageReaction copy with changed fields`() {
        val reaction = MessageReaction(messageId = 1L, emoji = "❤️")
        val updated = reaction.copy(synced = true)
        assertThat(updated.synced).isTrue()
        assertThat(updated.emoji).isEqualTo("❤️")
    }

    @Test
    fun `ReactionSummary default values`() {
        val summary = ReactionSummary(messageId = 1L)
        assertThat(summary.messageId).isEqualTo(1L)
        assertThat(summary.reactions).isEmpty()
        assertThat(summary.myReaction).isNull()
        assertThat(summary.totalCount).isEqualTo(0)
    }

    @Test
    fun `ReactionSummary with reactions`() {
        val reactions = mapOf("👍" to 3, "❤️" to 1)
        val summary = ReactionSummary(
            messageId = 1L,
            reactions = reactions,
            myReaction = "👍",
            totalCount = 4
        )
        assertThat(summary.reactions).hasSize(2)
        assertThat(summary.reactions["👍"]).isEqualTo(3)
        assertThat(summary.totalCount).isEqualTo(4)
    }
}
