package com.rasmi.purevon.domain.model

data class SearchResult(
    val rowid: Long = 0,
    val address: String = "",
    val contactName: String = "",
    val body: String = "",
    val snippetText: String = "",
    val threadId: Long = 0,
    val date: Long = 0,
    val type: Int = 0,
    val rankScore: Float = 0f
)

data class SearchFilters(
    val query: String = "",
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val phoneNumber: String? = null,
    val messageType: Int? = null,
    val threadId: Long? = null,
    val limit: Int = 50,
    val offset: Int = 0
)
