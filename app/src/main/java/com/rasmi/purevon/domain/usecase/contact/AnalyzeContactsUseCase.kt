package com.rasmi.purevon.domain.usecase.contact

import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.domain.model.ContactAnalysisResult
import com.rasmi.purevon.domain.model.ContactPhoneUpdate
import com.rasmi.purevon.domain.model.CountryPhoneRule
import com.rasmi.purevon.domain.model.PhoneNumberAnalysis
import com.rasmi.purevon.domain.model.PhoneNumberIssue
import com.rasmi.purevon.domain.repository.ContactRepository
import com.rasmi.purevon.domain.service.CountryDetectorService
import com.rasmi.purevon.util.PhoneNumberNormalizer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case to analyze all contacts and identify phone numbers needing cleanup
 */
class AnalyzeContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val countryDetectorService: CountryDetectorService
) {
    /**
     * Analyze all contacts for phone number issues
     * 
     * @param countryRule Country rule to use (null = auto-detect)
     * @return ContactAnalysisResult with statistics and samples
     */
    suspend operator fun invoke(
        countryRule: CountryPhoneRule? = null
    ): ContactAnalysisResult {
        // Get country to use
        val country = countryRule ?: countryDetectorService.detectCountry()
        
        // Get all contacts
        val allContacts = contactRepository.getAllContacts().first()
        
        // Analyze each contact's phone number
        val analyses = mutableListOf<ContactPhoneUpdate>()
        val issuesCounts = mutableMapOf<PhoneNumberIssue, Int>()
        
        allContacts.forEach { contact ->
            val analysis = PhoneNumberNormalizer.normalizePhoneNumber(
                contact.phoneNumber,
                country
            )
            
            if (analysis.needsUpdate) {
                analyses.add(
                    ContactPhoneUpdate(
                        contactId = contact.id,
                        displayName = contact.displayName,
                        photoUri = contact.photoUri,
                        analysis = analysis,
                        isSelected = true
                    )
                )
                
                // Count issues
                analysis.issues.forEach { issue ->
                    issuesCounts[issue] = (issuesCounts[issue] ?: 0) + 1
                }
            }
        }
        
        // Sort by confidence (uncertain ones first for review)
        val sortedAnalyses = analyses.sortedBy { it.analysis.confidence }
        
        // Take samples for preview (mix of confident and uncertain)
        val samples = sortedAnalyses.take(5).map { it.analysis }
        
        // Estimate time (assume 0.5 seconds per contact)
        val estimatedSeconds = (analyses.size * 0.5).toInt().coerceAtLeast(1)
        
        return ContactAnalysisResult(
            totalContacts = allContacts.size,
            needsCleanup = analyses.size,
            byIssueType = issuesCounts,
            samples = samples,
            allNeedingUpdate = sortedAnalyses,
            estimatedSeconds = estimatedSeconds,
            countryUsed = country
        )
    }
}
