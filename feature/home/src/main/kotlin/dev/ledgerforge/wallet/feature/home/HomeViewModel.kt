package dev.ledgerforge.wallet.feature.home

import androidx.lifecycle.ViewModel
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import kotlinx.coroutines.flow.StateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val wallets: List<Wallet> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val demoWallets: DemoWalletsState = DemoWalletsState.Idle,
)

enum class DemoWalletsState {
    Idle,
    Creating,
    Failed,
}

sealed interface HomeIntent {
    data object Refresh : HomeIntent

    data object CreateDemoWallets : HomeIntent
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val sync: SyncRequester,
    ) : ViewModel() {
        val state: StateFlow<HomeUiState> get() = TODO()

        fun onIntent(intent: HomeIntent): Unit = TODO()
    }
