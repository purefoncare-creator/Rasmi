package com.rasmi.purevon.di

import android.content.Context
import com.rasmi.purevon.domain.call.InCallServiceBridge
import com.rasmi.purevon.service.InCallServiceBridgeImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context
    
    @Provides
    @Singleton
    fun provideImageCompressor(
        @ApplicationContext context: Context
    ): com.rasmi.purevon.util.ImageCompressor {
        return com.rasmi.purevon.util.ImageCompressor(context)
    }
    
    // ✅ FIXED Issue #14: Injectable InCallServiceBridge instead of static PurevonInCallService.Companion
    @Provides
    @Singleton
    fun provideInCallServiceBridge(
        impl: InCallServiceBridgeImpl
    ): InCallServiceBridge = impl
}
