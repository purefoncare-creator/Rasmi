package com.rasmi.purevon.presentation.screen.dialer

import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.util.sim.SimInfo

/**
 * UI State for Dialer Screen
 */
data class DialerUiState(
    val dialedNumber: String = "",
    val searchResults: List<Contact> = emptyList(),
    val recentContacts: List<Contact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCallButton: Boolean = false,
    val isContactSelected: Boolean = false,
    val selectedContactName: String? = null,
    val currentSimLabel: String = "SIM1",
    val availableSims: List<SimInfo> = emptyList()
)

/**
 * UI Events for Dialer Screen
 */
sealed class DialerUiEvent {
    data class NumberChanged(val number: String) : DialerUiEvent()
    data class DigitPressed(val digit: String) : DialerUiEvent()
    data object BackspacePressed : DialerUiEvent()
    data object BackspaceLongPressed : DialerUiEvent()
    data class ContactSelected(val contact: Contact) : DialerUiEvent()
    data class InitiateCall(val hasPermission: Boolean) : DialerUiEvent()
    data object SimSwitchPressed : DialerUiEvent() // ✅ تبديل الشريحة (SIM1 → SIM2 → ASK → SIM1)
    data class SimSelectedForCall(val subscriptionId: Int, val phoneNumber: String) : DialerUiEvent() // ✅ تأكيد الشريحة من حوار ASK
    data object RequestCallPermission : DialerUiEvent()
    data object DismissError : DialerUiEvent()
    data object ClearInput : DialerUiEvent() // ✅ مسح الـ input عند الانتقال من InCall
}

/**
 * UI Actions that require Context/Activity (one-time events)
 */
sealed class DialerUiAction {
    data class MakePhoneCall(
        val phoneNumber: String, 
        val subscriptionId: Int? = null
    ) : DialerUiAction()
    data class ShowSimPickerForCall(val phoneNumber: String) : DialerUiAction() // ✅ إظهار حوار اختيار الشريحة
    data object RequestPermission : DialerUiAction()
}
