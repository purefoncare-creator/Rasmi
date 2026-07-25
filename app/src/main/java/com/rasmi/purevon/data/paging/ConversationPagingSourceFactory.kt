package com.rasmi.purevon.data.paging

import android.content.Context
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ConversationPagingSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationPreferencesDao: com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
) {
    fun create(query: String = ""): ConversationPagingSource {
        return ConversationPagingSource(context, conversationPreferencesDao, query)
    }
}
