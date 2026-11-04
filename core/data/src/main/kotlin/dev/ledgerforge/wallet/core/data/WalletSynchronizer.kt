package dev.ledgerforge.wallet.core.data

import dev.ledgerforge.wallet.core.database.AccountDao
import dev.ledgerforge.wallet.core.database.OutboxDao
import dev.ledgerforge.wallet.core.database.SyncDao
import dev.ledgerforge.wallet.core.network.LedgerClient
import javax.inject.Inject

enum class SyncResult {
    Done,

    /** Something couldn't be confirmed. Run again later: the outbox makes that safe. */
    RetryLater,
}

/** One sync: push the outbox, then pull every wallet. See ADR 0001. */
class WalletSynchronizer
    @Inject
    constructor(
        private val ledger: LedgerClient,
        private val accounts: AccountDao,
        private val outbox: OutboxDao,
        private val syncDao: SyncDao,
    ) {
        suspend fun sync(): SyncResult = TODO()
    }
