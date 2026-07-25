package com.rasmi.purevon.domain.service

import androidx.annotation.StringRes

/**
 * Abstraction for resolving string resources without depending on Android Context.
 * Implementations live in the data layer (e.g., AndroidStringProvider).
 */
interface StringProvider {
    
    /**
     * Resolve a string resource by its ID.
     */
    fun getString(@StringRes resId: Int): String
    
    /**
     * Resolve a string resource with format arguments.
     */
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String
}
