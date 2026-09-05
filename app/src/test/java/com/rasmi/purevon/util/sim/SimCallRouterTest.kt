package com.rasmi.purevon.util.sim

import com.google.common.truth.Truth.assertThat
import com.rasmi.purevon.data.preferences.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SimCallRouterTest {

    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val simManager = mockk<SimManager>(relaxed = true)
    private val router = SimCallRouter(settingsDataStore, simManager)

    private fun sim(subId: Int, slot: Int, name: String = "SIM") =
        SimInfo(slotIndex = slot, subscriptionId = subId, displayName = name, carrierName = "Carrier", phoneNumber = "123")

    @Test
    fun `ask mode returns AskSim with available sims`() = runTest {
        val sims = listOf(sim(1, 0), sim(2, 1))
        every { settingsDataStore.isSimAskMode } returns flowOf(true)
        every { simManager.getAvailableSims() } returns sims

        val route = router.resolveRoute()

        assertThat(route).isInstanceOf(SimCallRouter.CallRoute.AskSim::class.java)
        val ask = route as SimCallRouter.CallRoute.AskSim
        assertThat(ask.availableSims).containsExactlyElementsIn(sims)
        coVerify(exactly = 0) { settingsDataStore.setDefaultSimSubscriptionId(any()) }
    }

    @Test
    fun `direct mode uses saved subscription id`() = runTest {
        every { settingsDataStore.isSimAskMode } returns flowOf(false)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(7)

        val route = router.resolveRoute()

        assertThat(route).isEqualTo(SimCallRouter.CallRoute.Direct(7))
        coVerify(exactly = 0) { simManager.getAvailableSims() }
    }

    @Test
    fun `direct mode auto-initializes from first sim when no preference`() = runTest {
        every { settingsDataStore.isSimAskMode } returns flowOf(false)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { simManager.getAvailableSims() } returns listOf(sim(11, 2))

        val route = router.resolveRoute()

        assertThat(route).isEqualTo(SimCallRouter.CallRoute.Direct(11))
        coVerify { settingsDataStore.setDefaultSimSubscriptionId(11) }
        coVerify { settingsDataStore.setDefaultSimSlot(2) }
    }

    @Test
    fun `direct mode with zero subscription id auto-initializes`() = runTest {
        every { settingsDataStore.isSimAskMode } returns flowOf(false)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(0)
        every { simManager.getAvailableSims() } returns listOf(sim(5, 0))

        val route = router.resolveRoute()

        assertThat(route).isEqualTo(SimCallRouter.CallRoute.Direct(5))
    }

    @Test
    fun `direct mode returns null subscription when no sims available`() = runTest {
        every { settingsDataStore.isSimAskMode } returns flowOf(false)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { simManager.getAvailableSims() } returns emptyList()

        val route = router.resolveRoute()

        assertThat(route).isEqualTo(SimCallRouter.CallRoute.Direct(null))
    }

    @Test
    fun `direct mode with invalid preference and no sims returns null`() = runTest {
        every { settingsDataStore.isSimAskMode } returns flowOf(false)
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { simManager.getAvailableSims() } returns emptyList()

        val route = router.resolveRoute()

        assertThat(route).isEqualTo(SimCallRouter.CallRoute.Direct(null))
    }

    @Test
    fun `resolveSubscriptionId returns saved preference`() = runTest {
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(3)

        assertThat(router.resolveSubscriptionId()).isEqualTo(3)
    }

    @Test
    fun `resolveSubscriptionId auto-detects first sim`() = runTest {
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { simManager.getAvailableSims() } returns listOf(sim(9, 0))

        assertThat(router.resolveSubscriptionId()).isEqualTo(9)
        coVerify { settingsDataStore.setDefaultSimSubscriptionId(9) }
    }

    @Test
    fun `resolveSubscriptionId returns null when no sims`() = runTest {
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(-1)
        every { simManager.getAvailableSims() } returns emptyList()

        assertThat(router.resolveSubscriptionId()).isNull()
    }

    @Test
    fun `resolveSubscriptionId treats zero as unset and auto-detects`() = runTest {
        every { settingsDataStore.defaultSimSubscriptionId } returns flowOf(0)
        every { simManager.getAvailableSims() } returns listOf(sim(4, 0))

        assertThat(router.resolveSubscriptionId()).isEqualTo(4)
    }

    @Test
    fun `SimCallAction MakeCall holds phone and subscription`() {
        val action = SimCallAction.MakeCall("+966501234567", 2)
        assertThat(action.phoneNumber).isEqualTo("+966501234567")
        assertThat(action.subscriptionId).isEqualTo(2)
    }

    @Test
    fun `SimCallAction ShowSimPicker holds number and sims`() {
        val sims = listOf(sim(1, 0), sim(2, 1))
        val action = SimCallAction.ShowSimPicker("123", sims)
        assertThat(action.phoneNumber).isEqualTo("123")
        assertThat(action.availableSims).containsExactlyElementsIn(sims)
    }
}
