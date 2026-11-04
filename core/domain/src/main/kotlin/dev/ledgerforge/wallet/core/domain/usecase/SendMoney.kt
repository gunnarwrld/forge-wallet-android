package dev.ledgerforge.wallet.core.domain.usecase

import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.money.Money
import javax.inject.Inject

enum class SendResult {
    Queued,
    InsufficientFunds,
    AmountNotPositive,
    SameWallet,
    CurrencyMismatch,
}

/** Validates a send, queues it on the device and asks for a sync. It never touches the network. */
class SendMoney
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val sync: SyncRequester,
    ) {
        suspend operator fun invoke(
            source: Wallet,
            destination: Wallet,
            amount: Money,
        ): SendResult {
            val rejection =
                when {
                    !amount.isPositive -> SendResult.AmountNotPositive
                    source.id == destination.id -> SendResult.SameWallet
                    amount.currency != source.postedBalance.currency -> SendResult.CurrencyMismatch
                    amount.currency != destination.postedBalance.currency -> SendResult.CurrencyMismatch
                    else -> null
                }
            if (rejection != null) return rejection

            return when (repository.queueTransfer(source.id, destination.id, amount)) {
                QueueResult.Queued -> {
                    sync.requestSync()
                    SendResult.Queued
                }

                QueueResult.InsufficientFunds -> {
                    SendResult.InsufficientFunds
                }
            }
        }
    }
