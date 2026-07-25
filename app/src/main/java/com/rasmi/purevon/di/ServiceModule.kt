package com.rasmi.purevon.di

import com.rasmi.purevon.data.service.AndroidAppFileProvider
import com.rasmi.purevon.data.service.AndroidCountryDetectorService
import com.rasmi.purevon.data.service.AndroidStringProvider
import com.rasmi.purevon.data.service.AndroidSystemCallLogWriter
import com.rasmi.purevon.data.service.AndroidSystemContactWriter
import com.rasmi.purevon.domain.service.AppFileProvider
import com.rasmi.purevon.domain.service.CountryDetectorService
import com.rasmi.purevon.domain.service.StringProvider
import com.rasmi.purevon.domain.service.SystemCallLogWriter
import com.rasmi.purevon.domain.service.SystemContactWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding domain service interfaces to their Android implementations.
 * Keeps the domain layer free of Android framework dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    
    @Binds
    @Singleton
    abstract fun bindAppFileProvider(impl: AndroidAppFileProvider): AppFileProvider
    
    @Binds
    @Singleton
    abstract fun bindCountryDetectorService(impl: AndroidCountryDetectorService): CountryDetectorService
    
    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
    
    @Binds
    @Singleton
    abstract fun bindSystemCallLogWriter(impl: AndroidSystemCallLogWriter): SystemCallLogWriter
    
    @Binds
    @Singleton
    abstract fun bindSystemContactWriter(impl: AndroidSystemContactWriter): SystemContactWriter
}
