package dev.ledgerforge.wallet.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.domain.usecase.SendMoney
import dev.ledgerforge.wallet.core.domain.usecase.SendResult
import dev.ledgerforge.wallet.core.money.MoneyParseError
import dev.ledgerforge.wallet.core.money.MoneyParseResult
import dev.ledgerforge.wallet.core.money.MoneyParser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

private data class SendForm(
    val sourceId: AccountId? = null,
    val destinationId: AccountId? = null,
    val amountText: String = "",
    val error: SendError? = null,
    val isSubmitting: Boolean = false,
)

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
        private val form = MutableStateFlow(SendForm())
        private val effectChannel = Channel<SendEffect>(Channel.BUFFERED)

        val effects: Flow<SendEffect> = effectChannel.receiveAsFlow()

        val state: StateFlow<SendUiState> =
            combine(repository.observeWallets(), form, ::toUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SendUiState())

        fun onIntent(intent: SendIntent) {
            when (intent) {
                is SendIntent.SelectSource -> form.update { it.copy(sourceId = intent.id, error = null) }
                is SendIntent.SelectDestination -> form.update { it.copy(destinationId = intent.id, error = null) }
                is SendIntent.EditAmount -> form.update { it.copy(amountText = intent.text, error = null) }
                SendIntent.Submit -> submit()
            }
        }

        private fun submit() {
            // Claimed before anything suspends: a second tap would be a second key, and a real
            // second payment. It stays claimed after success, because the screen is leaving.
            if (form.value.isSubmitting) return
            form.update { it.copy(isSubmitting = true, error = null) }
            viewModelScope.launch {
                val current = toUiState(repository.observeWallets().first(), form.value)
                val source = current.source
                val destination = current.destination
                if (source == null || destination == null) {
                    form.update { it.copy(isSubmitting = false) }
                    return@launch
                }
                val error = queue(source, destination, current.amountText)
                if (error == null) {
                    effectChannel.send(SendEffect.Queued)
                } else {
                    form.update { it.copy(isSubmitting = false, error = error) }
                }
            }
        }

        /** Returns null once the transfer is safely queued. */
        private suspend fun queue(
            source: Wallet,
            destination: Wallet,
            amountText: String,
        ): SendError? =
            when (val parsed = MoneyParser.parse(amountText, source.availableBalance.currency)) {
                is MoneyParseResult.Failure -> parsed.error.toSendError()
                is MoneyParseResult.Success -> sendMoney(source, destination, parsed.money).toSendError()
            }

        private fun toUiState(
            wallets: List<Wallet>,
            form: SendForm,
        ): SendUiState {
            val source = wallets.firstOrNull { it.id == form.sourceId } ?: wallets.firstOrNull()
            val destination = wallets.firstOrNull { it.id == form.destinationId } ?: wallets.firstOrNull { it != source }
            return SendUiState(wallets, source, destination, form.amountText, form.error, form.isSubmitting)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun MoneyParseError.toSendError(): SendError =
    when (this) {
        MoneyParseError.Empty -> SendError.AmountEmpty
        MoneyParseError.InvalidCharacter, MoneyParseError.TooManyDecimalSeparators -> SendError.AmountInvalid
        MoneyParseError.TooManyFractionDigits -> SendError.TooManyDecimals
        MoneyParseError.Overflow -> SendError.AmountTooLarge
    }

private fun SendResult.toSendError(): SendError? =
    when (this) {
        SendResult.Queued -> null
        SendResult.InsufficientFunds -> SendError.InsufficientFunds
        SendResult.AmountNotPositive -> SendError.AmountNotPositive
        SendResult.SameWallet -> SendError.SameWallet
        SendResult.CurrencyMismatch -> SendError.CurrencyMismatch
    }
