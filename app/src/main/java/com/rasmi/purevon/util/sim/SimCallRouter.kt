package com.rasmi.purevon.util.sim

import android.os.Build
import com.rasmi.purevon.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared SIM call action result used across screens.
 */
sealed class SimCallAction {
    data class MakeCall(val phoneNumber: String, val subscriptionId: Int?) : SimCallAction()
    data class ShowSimPicker(val phoneNumber: String, val availableSims: List<SimInfo>) : SimCallAction()
}

/**
 * Resolves which SIM to use for a call based on user preference.
 */
@Singleton
class SimCallRouter @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val simManager: SimManager
) {
    sealed class CallRoute {
        /** Call directly with this subscriptionId (null = system default) */
        data class Direct(val subscriptionId: Int?) : CallRoute()
        /** Show a SIM picker dialog before calling */
        data class AskSim(val availableSims: List<SimInfo>) : CallRoute()
    }

    suspend fun resolveRoute(): CallRoute {
        val isAsk = settingsDataStore.isSimAskMode.first()
        return if (isAsk) {
            val sims = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                simManager.getAvailableSims()
            } else {
                emptyList()
            }
            CallRoute.AskSim(sims)
        } else {
            var subId = settingsDataStore.defaultSimSubscriptionId.first()
            // Auto-initialize: if no SIM preference saved yet, detect SIM 1 and persist it
            if (subId <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sims = simManager.getAvailableSims()
                val firstSim = sims.firstOrNull()
                if (firstSim != null) {
                    subId = firstSim.subscriptionId
                    settingsDataStore.setDefaultSimSubscriptionId(subId)
                    settingsDataStore.setDefaultSimSlot(firstSim.slotIndex)
                }
            }
            CallRoute.Direct(if (subId > 0) subId else null)
        }
    }

    /**
     * Resolve subscriptionId only (for use in notifications / PendingIntents).
     * Returns the saved preference or auto-detects SIM 1.
     */
    suspend fun resolveSubscriptionId(): Int? {
        var subId = settingsDataStore.defaultSimSubscriptionId.first()
        if (subId <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val sims = simManager.getAvailableSims()
            val firstSim = sims.firstOrNull()
            if (firstSim != null) {
                subId = firstSim.subscriptionId
                settingsDataStore.setDefaultSimSubscriptionId(subId)
                settingsDataStore.setDefaultSimSlot(firstSim.slotIndex)
            }
        }
        return if (subId > 0) subId else null
    }
}
