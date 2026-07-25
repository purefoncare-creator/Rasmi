package com.rasmi.purevon.presentation.screen.statistics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.domain.repository.CallLogRepository
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.util.CallGrouper
import com.rasmi.purevon.util.CallStatistics
import com.rasmi.purevon.util.ContactFrequency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Statistics Screen
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val callRepository: CallLogRepository,
    private val messageRepository: MessageRepository,
    private val callGrouper: CallGrouper
) : ViewModel() {
    
    companion object {
        private const val TAG = "StatisticsViewModel"
    }

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    
    init {
        loadStatistics()
    }
    
    private fun loadStatistics() {
        viewModelScope.launch {
            callRepository.getAllCallLogs()
                .catch { e ->
                    Log.e(TAG, "Error loading call logs for statistics", e)
                    emit(emptyList())
                }
                .collect { calls ->
                    val stats = callGrouper.getCallStatistics(calls)
                    val frequent = callGrouper.getMostFrequentContacts(calls, 5)
                    
                    _uiState.update {
                        it.copy(
                            callStats = stats,
                            frequentContacts = frequent
                        )
                    }
                }
        }
        
        viewModelScope.launch {
            combine(
                messageRepository.getTotalMessageCount()
                    .catch { e ->
                        Log.e(TAG, "Error loading total message count", e)
                        emit(0)
                    },
                messageRepository.getMessageCountByType(com.rasmi.purevon.data.local.entity.MessageType.RECEIVED.value)
                    .catch { e ->
                        Log.e(TAG, "Error loading received message count", e)
                        emit(0)
                    },
                messageRepository.getMessageCountByType(com.rasmi.purevon.data.local.entity.MessageType.SENT.value)
                    .catch { e ->
                        Log.e(TAG, "Error loading sent message count", e)
                        emit(0)
                    }
            ) { total, received, sent ->
                Triple(total, received, sent)
            }.collect { (total, received, sent) ->
                _uiState.update {
                    it.copy(
                        totalMessages = total,
                        receivedMessages = received,
                        sentMessages = sent
                    )
                }
            }
        }
    }
}

/**
 * UI State for Statistics Screen
 */
data class StatisticsUiState(
    val callStats: CallStatistics? = null,
    val frequentContacts: List<ContactFrequency> = emptyList(),
    val totalMessages: Int = 0,
    val receivedMessages: Int = 0,
    val sentMessages: Int = 0
)
