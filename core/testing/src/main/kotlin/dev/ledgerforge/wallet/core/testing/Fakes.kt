package dev.ledgerforge.wallet.core.testing

import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.money.Money
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class QueuedTransfer(
    val source: AccountId,
    val destination: AccountId,
    val amount: Money,
)

class FakeWalletRepository : WalletRepository {
    val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val queued = mutableListOf<QueuedTransfer>()
    var nextQueueResult = QueueResult.Queued

    /** Holds [queueTransfer] open until completed, to test what happens in the meantime. */
    var queueGate: CompletableDeferred<Unit>? = null

    var demoWalletsResult = true
    var demoWalletCalls = 0

    /** Holds [createDemoWallets] open until completed, to test what happens in the meantime. */
    var demoWalletsGate: CompletableDeferred<Unit>? = null

    override fun observeWallets(): Flow<List<Wallet>> = wallets

    override fun observeActivity(): Flow<List<ActivityItem>> = activity

    override suspend fun queueTransfer(
        source: AccountId,
        destination: AccountId,
        amount: Money,
    ): QueueResult {
        queueGate?.await()
        if (nextQueueResult == QueueResult.Queued) queued += QueuedTransfer(source, destination, amount)
        return nextQueueResult
    }

    override suspend fun createDemoWallets(): Boolean {
        demoWalletCalls++
        demoWalletsGate?.await()
        return demoWalletsResult
    }
}

class FakeSyncRequester : SyncRequester {
    var requests = 0
    var immediateRequests = 0

    override suspend fun requestSync() {
        requests++
    }

    override suspend fun syncNow() {
        immediateRequests++
    }
}
