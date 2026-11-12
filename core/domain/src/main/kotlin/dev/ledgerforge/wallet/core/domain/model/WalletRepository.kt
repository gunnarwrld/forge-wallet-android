package dev.ledgerforge.wallet.core.domain.model

import dev.ledgerforge.wallet.core.money.Money
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun observeWallets(): Flow<List<Wallet>>

    fun observeActivity(): Flow<List<ActivityItem>>

    /** Queues a transfer on the device. When this returns [QueueResult.Queued], the intent is durable. */
    suspend fun queueTransfer(
        source: AccountId,
        destination: AccountId,
        amount: Money,
    ): QueueResult

    /**
     * Queues a refused transfer again, as a new intent under a new key, and dismisses the refused
     * one. Ledger Forge refused the old key for good: after a 409 it would refuse it forever.
     */
    suspend fun retryTransfer(failedId: String): RetryResult

    /** Hides a refused transfer from the activity list. No money moved, so nothing else changes. */
    suspend fun dismissTransfer(failedId: String)

    /** Creates demo wallets on Ledger Forge. Returns false if they couldn't be created. */
    suspend fun createDemoWallets(): Boolean
}

enum class QueueResult {
    Queued,
    InsufficientFunds,
}

enum class RetryResult {
    Queued,
    InsufficientFunds,

    /** It was already retried or dismissed, or it never failed. A double tap lands here. */
    NothingToRetry,
}

interface SyncRequester {
    /** Asks for a sync once the device is online, after any run already in progress. */
    suspend fun requestSync()

    /** Starts over now, cutting any retry backoff short. For when the user asks for fresh data. */
    suspend fun syncNow()
}
