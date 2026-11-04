package dev.ledgerforge.wallet.feature.send

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.domain.usecase.SendMoney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SendUiState(
    val wallets: List<Wallet> = emptyList(),
    val source: Wallet? = null,
    val destination: Wallet? = null,
    val amountText: String = "",
    val error: SendError? = null,
    val isSubmitting: Boolean = false,
)

enum class SendError {
    AmountEmpty,
    AmountInvalid,
    TooManyDecimals,
    AmountTooLarge,
    AmountNotPositive,
    InsufficientFunds,
    SameWallet,
    CurrencyMismatch,
}

sealed interface SendIntent {
    data class SelectSource(
        val id: AccountId,
    ) : SendIntent

    data class SelectDestination(
        val id: AccountId,
    ) : SendIntent

    data class EditAmount(
        val text: String,
    ) : SendIntent

    data object Submit : SendIntent
}

sealed interface SendEffect {
    /** The transfer is safely on the device and will go out when it can. */
    data object Queued : SendEffect
}

/**
 * The slice's minimal send flow: edit, then queue. The review step, biometric step-up and the
 * full state machine come with the send feature in week 2.
 */
@HiltViewModel
class SendViewModel
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val sendMoney: SendMoney,
    ) : ViewModel() {
        val state: StateFlow<SendUiState> get() = TODO()

        val effects: Flow<SendEffect> get() = TODO()

        fun onIntent(intent: SendIntent): Unit = TODO()
    }
