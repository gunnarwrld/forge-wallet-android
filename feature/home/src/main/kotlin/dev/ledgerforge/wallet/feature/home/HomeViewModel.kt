package dev.ledgerforge.wallet.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        private val demoWallets = MutableStateFlow(DemoWalletsState.Idle)

        val state: StateFlow<HomeUiState> =
            combine(repository.observeWallets(), repository.observeActivity(), demoWallets) { wallets, activity, demo ->
                HomeUiState(isLoading = false, wallets = wallets, activity = activity, demoWallets = demo)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

        fun onIntent(intent: HomeIntent) {
            when (intent) {
                HomeIntent.Refresh -> viewModelScope.launch { sync.syncNow() }
                HomeIntent.CreateDemoWallets -> createDemoWallets()
            }
        }

        private fun createDemoWallets() {
            if (demoWallets.value == DemoWalletsState.Creating) return
            demoWallets.value = DemoWalletsState.Creating
            viewModelScope.launch {
                if (repository.createDemoWallets()) {
                    demoWallets.value = DemoWalletsState.Idle
                    sync.requestSync()
                } else {
                    demoWallets.value = DemoWalletsState.Failed
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
