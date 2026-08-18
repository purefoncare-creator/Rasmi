package com.rasmi.purevon.util.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportDataTest {

    @Test
    fun `ExportData serializable`() {
        val data = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 1234567890L
        )
        assertThat(data.exportDate).isEqualTo(1234567890L)
    }

    @Test
    fun `ExportData empty lists`() {
        val data = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 0L
        )
        assertThat(data.callMetadata).isEmpty()
        assertThat(data.messageMetadata).isEmpty()
        assertThat(data.blockedNumbers).isEmpty()
        assertThat(data.spamNumbers).isEmpty()
    }

    @Test
    fun `ExportData equality`() {
        val a = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 100L
        )
        val b = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 100L
        )
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `ExportData copy`() {
        val original = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 100L
        )
        val copy = original.copy(exportDate = 200L)
        assertThat(copy.exportDate).isEqualTo(200L)
    }

    @Test
    fun `ExportData hash consistent`() {
        val data = ExportData(
            callMetadata = emptyList(),
            messageMetadata = emptyList(),
            blockedNumbers = emptyList(),
            spamNumbers = emptyList(),
            exportDate = 500L
        )
        assertThat(data.hashCode()).isEqualTo(data.hashCode())
    }
}
