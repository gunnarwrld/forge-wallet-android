package dev.ledgerforge.wallet.core.data

import dev.ledgerforge.wallet.core.database.AccountDao
import dev.ledgerforge.wallet.core.database.AccountEntity
import dev.ledgerforge.wallet.core.database.ActivityDao
import dev.ledgerforge.wallet.core.database.ActivityRow
import dev.ledgerforge.wallet.core.database.OutboxDao
import dev.ledgerforge.wallet.core.database.OutboxEntity
import dev.ledgerforge.wallet.core.database.OutboxStatus
import dev.ledgerforge.wallet.core.database.WalletRow
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.network.AccountDto
import dev.ledgerforge.wallet.core.network.LedgerClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.time.Clock
import java.time.Instant
import java.util.UUID
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
        override fun observeWallets(): Flow<List<Wallet>> = accounts.observeWallets().map { rows -> rows.map(WalletRow::toWallet) }

        override fun observeActivity(): Flow<List<ActivityItem>> = activity.observe().map { rows -> rows.map(ActivityRow::toActivityItem) }

        override suspend fun queueTransfer(
            source: AccountId,
            destination: AccountId,
            amount: Money,
        ): QueueResult {
            val row =
                OutboxEntity(
                    // Generated here, once. Every retry of this transfer sends this same key.
                    idempotencyKey = UUID.randomUUID().toString(),
                    sourceAccountId = source.value,
                    destinationAccountId = destination.value,
                    amountMinor = amount.minor,
                    currencyCode = amount.currency.code,
                    createdAtEpochMs = clock.millis(),
                )
            return if (outbox.enqueueIfFunded(row)) QueueResult.Queued else QueueResult.InsufficientFunds
        }

        override suspend fun createDemoWallets(): Boolean {
            val alice = ledger.createAccount("Alice", DEMO_CURRENCY, ALICE_OPENING_MINOR) ?: return false
            val bob = ledger.createAccount("Bob", DEMO_CURRENCY, BOB_OPENING_MINOR) ?: return false
            accounts.upsert(alice.toEntity())
            accounts.upsert(bob.toEntity())
            return true
        }

        private companion object {
            const val DEMO_CURRENCY = "SEK"
            const val ALICE_OPENING_MINOR = 100_000L
            const val BOB_OPENING_MINOR = 25_000L
        }
    }

private fun AccountDto.toEntity() = AccountEntity(id, name, currencyCode, balanceMinor)

private fun WalletRow.toWallet(): Wallet {
    val currency = CurrencyCode.of(currencyCode)
    return Wallet(AccountId(id), name, Money(balanceMinor, currency), Money(availableMinor, currency))
}

internal fun ActivityRow.toActivityItem(): ActivityItem =
    ActivityItem(
        id = id,
        accountName = accountName,
        amount = Money(signedAmountMinor, CurrencyCode.of(currencyCode)),
        at = Instant.ofEpochMilli(atEpochMs),
        status =
            when (status) {
                ActivityRow.POSTED -> ActivityStatus.Posted
                OutboxStatus.PENDING.name -> ActivityStatus.Pending
                OutboxStatus.SETTLED.name -> ActivityStatus.Settled
                else -> ActivityStatus.Failed(failureHttpStatus.toTransferFailure())
            },
    )

private fun Int?.toTransferFailure(): TransferFailure =
    when (this) {
        HTTP_UNPROCESSABLE_ENTITY -> TransferFailure.InsufficientFunds
        HTTP_NOT_FOUND -> TransferFailure.AccountNotFound
        HTTP_CONFLICT -> TransferFailure.Conflict
        else -> TransferFailure.Rejected
    }

private const val HTTP_UNPROCESSABLE_ENTITY = 422
