package com.rasmi.purevon.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchModelsTest {

    @Test
    fun `SearchResult defaults are sensible`() {
        val result = SearchResult()
        assertThat(result.rowid).isEqualTo(0)
        assertThat(result.address).isEmpty()
        assertThat(result.contactName).isEmpty()
        assertThat(result.body).isEmpty()
        assertThat(result.snippetText).isEmpty()
        assertThat(result.threadId).isEqualTo(0)
        assertThat(result.date).isEqualTo(0)
        assertThat(result.type).isEqualTo(0)
        assertThat(result.rankScore).isEqualTo(0f)
    }

    @Test
    fun `SearchFilters defaults are sensible`() {
        val filters = SearchFilters()
        assertThat(filters.query).isEmpty()
        assertThat(filters.fromDate).isNull()
        assertThat(filters.toDate).isNull()
        assertThat(filters.phoneNumber).isNull()
        assertThat(filters.messageType).isNull()
        assertThat(filters.threadId).isNull()
        assertThat(filters.limit).isEqualTo(50)
        assertThat(filters.offset).isEqualTo(0)
    }

    @Test
    fun `SearchFilters copy with new values`() {
        val filters = SearchFilters(query = "hello", limit = 10)
        val updated = filters.copy(limit = 20, offset = 5)
        assertThat(updated.query).isEqualTo("hello")
        assertThat(updated.limit).isEqualTo(20)
        assertThat(updated.offset).isEqualTo(5)
    }
}
