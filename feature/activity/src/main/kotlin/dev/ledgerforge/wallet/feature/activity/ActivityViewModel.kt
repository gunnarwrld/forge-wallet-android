package dev.ledgerforge.wallet.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.RetryResult
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.domain.usecase.RetryTransfer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val isLoading: Boolean = true,
    val items: List<ActivityItem> = emptyList(),
    val message: ActivityMessage? = null,
)

enum class ActivityMessage {
    RetryQueued,
    RetryNeedsFunds,
}

sealed interface ActivityIntent {
    data class Retry(
        val id: String,
    ) : ActivityIntent

    data class Dismiss(
        val id: String,
    ) : ActivityIntent

    data object MessageShown : ActivityIntent
}

@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val retryTransfer: RetryTransfer,
    ) : ViewModel() {
        private val message = MutableStateFlow<ActivityMessage?>(null)
        private val retrying = mutableSetOf<String>()

        val state: StateFlow<ActivityUiState> =
            combine(repository.observeActivity(), message) { items, message ->
                ActivityUiState(isLoading = false, items = items, message = message)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActivityUiState())

        fun onIntent(intent: ActivityIntent) {
            when (intent) {
                is ActivityIntent.Retry -> retry(intent.id)
                is ActivityIntent.Dismiss -> viewModelScope.launch { repository.dismissTransfer(intent.id) }
                ActivityIntent.MessageShown -> message.value = null
            }
        }

        private fun retry(id: String) {
            // Claimed before suspending. The database would refuse a second retry anyway, but the
            // button shouldn't look as if it did something twice.
            if (!retrying.add(id)) return
            viewModelScope.launch {
                try {
                    when (retryTransfer(id)) {
                        RetryResult.Queued -> message.value = ActivityMessage.RetryQueued
                        RetryResult.InsufficientFunds -> message.value = ActivityMessage.RetryNeedsFunds
                        RetryResult.NothingToRetry -> Unit
                    }
                } finally {
                    retrying.remove(id)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
