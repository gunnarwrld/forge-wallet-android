package dev.ledgerforge.wallet.feature.activity

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.domain.usecase.RetryTransfer
import kotlinx.coroutines.flow.StateFlow
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
        val state: StateFlow<ActivityUiState> get() = TODO()

        fun onIntent(intent: ActivityIntent): Unit = TODO()
    }
