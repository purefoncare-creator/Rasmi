package com.rasmi.purevon.domain.usecase.contact

import android.util.Log
import com.rasmi.purevon.domain.model.ContactPhoneUpdate
import com.rasmi.purevon.domain.model.PhoneNumberAnalysis
import com.rasmi.purevon.domain.service.SystemContactWriter
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for CleanupContactsUseCase, in particular the empty-list guard
 * (division by zero) and progress/result aggregation across mixed outcomes.
 */
class CleanupContactsUseCaseTest {

    private lateinit var writer: SystemContactWriter
    private lateinit var useCase: CleanupContactsUseCase

    @Before
    fun setUp() {
        writer = mockk()
        useCase = CleanupContactsUseCase(writer)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun update(id: Long, original: String = "0501234567") = ContactPhoneUpdate(
        contactId = id,
        displayName = "Contact $id",
        photoUri = null,
        analysis = PhoneNumberAnalysis(
            original = original,
            normalized = "+9665$id",
            countryCode = "SA",
            countryName = "Saudi Arabia",
            needsUpdate = true,
            confidence = 1.0,
            issues = emptyList(),
            flagEmoji = "🇸🇦"
        )
    )

    @Test
    fun `empty list emits Complete with zero counts without dividing by zero`() = runTest {
        val emissions = useCase(emptyList()).toList()

        assertThat(emissions).hasSize(1)
        val complete = emissions.single() as CleanupProgress.Complete
        assertThat(complete.result.totalProcessed).isEqualTo(0)
        assertThat(complete.result.successCount).isEqualTo(0)
        assertThat(complete.result.failedCount).isEqualTo(0)
        assertThat(complete.result.failures).isEmpty()
    }

    @Test
    fun `all contacts succeed`() = runTest {
        val contacts = listOf(update(1), update(2), update(3))
        coEvery { writer.updateContactPhoneNumber(any(), any(), any()) } returns Unit

        val emissions = useCase(contacts).toList()

        val complete = emissions.last() as CleanupProgress.Complete
        assertThat(complete.result.totalProcessed).isEqualTo(3)
        assertThat(complete.result.successCount).isEqualTo(3)
        assertThat(complete.result.failedCount).isEqualTo(0)
        assertThat(complete.result.isFullySuccessful()).isTrue()
    }

    @Test
    fun `mixed success and failure aggregates correctly`() = runTest {
        val contacts = listOf(update(1), update(2), update(3))
        coEvery { writer.updateContactPhoneNumber(1, any(), any()) } returns Unit
        coEvery { writer.updateContactPhoneNumber(2, any(), any()) } throws RuntimeException("boom")
        coEvery { writer.updateContactPhoneNumber(3, any(), any()) } returns Unit

        val emissions = useCase(contacts).toList()

        val complete = emissions.last() as CleanupProgress.Complete
        assertThat(complete.result.totalProcessed).isEqualTo(3)
        assertThat(complete.result.successCount).isEqualTo(2)
        assertThat(complete.result.failedCount).isEqualTo(1)
        assertThat(complete.result.failures).hasSize(1)
        assertThat(complete.result.failures.single().contactId).isEqualTo(2)
        assertThat(complete.result.failures.single().errorMessage).isEqualTo("boom")
        assertThat(complete.result.isFullySuccessful()).isFalse()
    }

    @Test
    fun `emits one Processing event per contact with increasing progress`() = runTest {
        val contacts = listOf(update(1), update(2))
        coEvery { writer.updateContactPhoneNumber(any(), any(), any()) } returns Unit

        val emissions = useCase(contacts).toList()

        val processingEvents = emissions.filterIsInstance<CleanupProgress.Processing>()
        assertThat(processingEvents).hasSize(2)
        assertThat(processingEvents[0].progress).isEqualTo(0.5f)
        assertThat(processingEvents[1].progress).isEqualTo(1.0f)
        assertThat(emissions.last()).isInstanceOf(CleanupProgress.Complete::class.java)
    }
}
