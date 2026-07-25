package com.rasmi.purevon.data.service

import android.content.Context
import androidx.annotation.StringRes
import com.rasmi.purevon.domain.service.StringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [StringProvider].
 * Resolves string resources from the application context.
 */
@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    
    override fun getString(@StringRes resId: Int): String {
        return context.getString(resId)
    }
    
    override fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }
}
