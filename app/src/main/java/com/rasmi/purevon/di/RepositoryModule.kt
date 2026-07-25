package com.rasmi.purevon.di

import android.content.Context
import com.rasmi.purevon.data.local.dao.*
import com.rasmi.purevon.data.repository.*
import com.rasmi.purevon.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Repository dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideContactRepository(
        @ApplicationContext context: Context,
        blockedNumberDao: BlockedNumberDao
    ): ContactRepository {
        return ContactRepositoryImpl(context, blockedNumberDao)
    }
    
    @Provides
    @Singleton
    fun provideCallLogRepository(
        callMetadataDao: CallMetadataDao,
        blockedNumberDao: BlockedNumberDao,
        @ApplicationContext context: Context,
        simManager: com.rasmi.purevon.util.sim.SimManager
    ): CallLogRepository {
        return CallLogRepositoryImpl(context, callMetadataDao, blockedNumberDao, simManager)
    }
    
    @Provides
    @Singleton
    fun provideMessageRepository(
        messageMetadataDao: MessageMetadataDao,
        conversationPreferencesDao: ConversationPreferencesDao,
        cachedConversationDao: com.rasmi.purevon.data.local.dao.CachedConversationDao,
        cachedMessageDao: com.rasmi.purevon.data.local.dao.CachedMessageDao,
        scheduledMessageDao: com.rasmi.purevon.data.local.dao.ScheduledMessageDao,
        messageTemplateDao: com.rasmi.purevon.data.local.dao.MessageTemplateDao,
        messagePagingSourceFactory: com.rasmi.purevon.data.paging.MessagePagingSource.Factory,
        conversationPagingSourceFactory: com.rasmi.purevon.data.paging.ConversationPagingSourceFactory,
        imageCompressor: com.rasmi.purevon.util.ImageCompressor,
        @ApplicationContext context: Context,
        simManager: com.rasmi.purevon.util.sim.SimManager,
        apnManager: com.rasmi.purevon.util.mms.ApnManager
    ): MessageRepository {
        return MessageRepositoryImpl(
            context, 
            messageMetadataDao, 
            conversationPreferencesDao, 
            cachedConversationDao,
            cachedMessageDao,
            scheduledMessageDao,
            messageTemplateDao,
            messagePagingSourceFactory,
            conversationPagingSourceFactory,
            imageCompressor,
            simManager,
            apnManager
        )
    }
    
    @Provides
    @Singleton
    fun provideSpamRepository(
        spamNumberDao: SpamNumberDao
    ): SpamRepository {
        return SpamRepositoryImpl(spamNumberDao)
    }
    
    @Provides
    @Singleton
    fun provideBlockRepository(
        blockedNumberDao: BlockedNumberDao,
        whitelistDao: com.rasmi.purevon.data.local.dao.WhitelistDao
    ): BlockRepository {
        return BlockRepositoryImpl(blockedNumberDao, whitelistDao)
    }
    
    // ConversationRepository(Impl) deleted — replaced by MessageRepository

    @Provides
    @Singleton
    fun provideSearchRepository(
        searchDao: SearchDao,
        contactRepository: ContactRepository,
        @ApplicationContext context: Context
    ): SearchRepository {
        return SearchRepositoryImpl(searchDao, context.contentResolver, contactRepository)
    }
    
    @Provides
    @Singleton
    fun provideReactionRepository(
        reactionDao: MessageReactionDao
    ): ReactionRepository {
        return ReactionRepositoryImpl(reactionDao)
    }
    
    @Provides
    @Singleton
    fun provideWhitelistRepository(
        whitelistDao: WhitelistDao,
        blockedNumberDao: BlockedNumberDao
    ): WhitelistRepository {
        return WhitelistRepositoryImpl(whitelistDao, blockedNumberDao)
    }
    
    @Provides
    @Singleton
    fun provideConversationPreferencesRepository(
        conversationPreferencesDao: ConversationPreferencesDao,
        messageRepository: MessageRepository
    ): ConversationPreferencesRepository {
        return ConversationPreferencesRepositoryImpl(conversationPreferencesDao, messageRepository)
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context,
        messageRepository: MessageRepository
    ): SyncRepository {
        return SyncRepositoryImpl(context, messageRepository)
    }
    
    @Provides
    @Singleton
    fun provideContactNoteRepository(
        contactNoteDao: ContactNoteDao
    ): ContactNoteRepository {
        return ContactNoteRepositoryImpl(contactNoteDao)
    }
    
}
