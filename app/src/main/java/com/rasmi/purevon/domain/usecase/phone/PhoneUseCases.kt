package com.rasmi.purevon.domain.usecase.phone

import javax.inject.Inject

/**
 * Aggregator for all phone-related use cases
 * Makes it easier to inject all use cases together
 */
data class PhoneUseCases @Inject constructor(
    val formatPhoneNumber: FormatPhoneNumberUseCase,
    val validatePhoneNumber: ValidatePhoneNumberUseCase
)


