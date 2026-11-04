package dev.ledgerforge.wallet.core.data

import dev.ledgerforge.wallet.core.database.AccountDao
import dev.ledgerforge.wallet.core.database.ActivityDao
import dev.ledgerforge.wallet.core.database.ActivityRow
import dev.ledgerforge.wallet.core.database.OutboxDao
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.network.LedgerClient
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject

/** Reads and writes go to Room. Only demo-wallet creation talks to the network directly. */
class OfflineFirstWalletRepository
    @Inject
    constructor(
        private val accounts: AccountDao,
        private val outbox: OutboxDao,
        private val activity: ActivityDao,
        private val ledger: LedgerClient,
        private val clock: Clock,
    ) : WalletRepository {
        override fun observeWallets(): Flow<List<Wallet>> = TODO()

        override fun observeActivity(): Flow<List<ActivityItem>> = TODO()

        override suspend fun queueTransfer(
            source: AccountId,
            destination: AccountId,
            amount: Money,
        ): QueueResult = TODO()

        override suspend fun createDemoWallets(): Boolean = TODO()
    }

internal fun ActivityRow.toActivityItem(): ActivityItem = TODO()
