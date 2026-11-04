package dev.ledgerforge.wallet.core.domain.usecase

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
        ): SendResult = TODO()
    }
