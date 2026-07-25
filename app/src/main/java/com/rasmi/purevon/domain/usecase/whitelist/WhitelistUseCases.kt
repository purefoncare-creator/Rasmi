package com.rasmi.purevon.domain.usecase.whitelist

import com.rasmi.purevon.domain.model.WhitelistNumber
import com.rasmi.purevon.domain.repository.WhitelistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWhitelistNumbersUseCase @Inject constructor(
    private val repository: WhitelistRepository
) {
    operator fun invoke(): Flow<List<WhitelistNumber>> {
        return repository.getAllWhitelistNumbers()
    }
}

class AddToWhitelistUseCase @Inject constructor(
    private val repository: WhitelistRepository
) {
    suspend operator fun invoke(phoneNumber: String, contactName: String?) {
        repository.addToWhitelist(phoneNumber, contactName)
    }
}

class RemoveFromWhitelistUseCase @Inject constructor(
    private val repository: WhitelistRepository
) {
    suspend operator fun invoke(phoneNumber: String) {
        repository.removeFromWhitelist(phoneNumber)
    }
}
