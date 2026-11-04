package dev.ledgerforge.wallet.core.data

import android.util.Log
import dev.ledgerforge.wallet.core.database.AccountDao
import dev.ledgerforge.wallet.core.database.OutboxDao
import dev.ledgerforge.wallet.core.database.OutboxEntity
import dev.ledgerforge.wallet.core.database.StatementLineEntity
import dev.ledgerforge.wallet.core.database.SyncDao
import dev.ledgerforge.wallet.core.network.LedgerClient
import dev.ledgerforge.wallet.core.network.SnapshotOutcome
import dev.ledgerforge.wallet.core.network.StatementLineDto
import dev.ledgerforge.wallet.core.network.TransferOutcome
import dev.ledgerforge.wallet.core.network.TransferRequestDto
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.time.Instant
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
        /**
         * Push goes first so a row whose response was lost gets its receipt before its debit
         * shows up in a pulled balance. A failed push doesn't stop the pull: fresh balances are
         * still useful, and at worst a debit counts twice until the replay lands.
         */
        suspend fun sync(): SyncResult {
            val pushed = push()
            val pulled = pull()
            return if (pushed && pulled) SyncResult.Done else SyncResult.RetryLater
        }

        /** Returns false if any row is still waiting on a retry. */
        private suspend fun push(): Boolean {
            val blockedSources = mutableSetOf<String>()
            while (true) {
                // One row at a time, so a transfer queued during this loop goes out in this run.
                val row = outbox.nextPending(blockedSources) ?: break
                when (val outcome = ledger.submitTransfer(row.idempotencyKey, row.toRequest())) {
                    is TransferOutcome.Settled -> {
                        outbox.markSettled(row.seq, outcome.transferId)
                    }

                    is TransferOutcome.Rejected -> {
                        reject(row, outcome)
                    }

                    TransferOutcome.RetryLater -> {
                        // Later rows from this wallet wait, so its transfers post in the order they were sent.
                        blockedSources += row.sourceAccountId
                    }
                }
            }
            return blockedSources.isEmpty()
        }

        private suspend fun reject(
            row: OutboxEntity,
            rejection: TransferOutcome.Rejected,
        ) {
            if (rejection.httpStatus == HTTP_CONFLICT) {
                // A row's body never changes, so the same key with a different body is a bug here.
                Log.e(TAG, "Idempotency conflict on outbox row ${row.seq}: the outbox invariant is broken")
            }
            outbox.markFailed(row.seq, rejection.httpStatus, rejection.detail)
        }

        /** Returns false if any wallet couldn't be refreshed. A failure doesn't stop the others. */
        private suspend fun pull(): Boolean = accounts.ids().map { pull(it) }.all { it }

        private suspend fun pull(accountId: String): Boolean {
            // Read these before fetching: each got its 201 before the balance below was read, so
            // that balance includes its debit, however the statement looks.
            val settled = outbox.settledSeqs(accountId)
            return when (val snapshot = ledger.fetchSnapshot(accountId)) {
                is SnapshotOutcome.Fetched -> {
                    syncDao.applySnapshot(
                        accountId = accountId,
                        balanceMinor = snapshot.account.balanceMinor,
                        lines = snapshot.statement.map { it.toEntity(accountId) },
                        settledSeqs = settled,
                    )
                    true
                }

                SnapshotOutcome.AccountGone -> {
                    Log.w(TAG, "Ledger Forge no longer knows a local wallet")
                    true
                }

                SnapshotOutcome.RetryLater -> {
                    false
                }
            }
        }

        private fun OutboxEntity.toRequest() = TransferRequestDto(sourceAccountId, destinationAccountId, amountMinor, currencyCode)

        private fun StatementLineDto.toEntity(accountId: String) =
            StatementLineEntity(
                accountId = accountId,
                journalEntryId = journalEntryId,
                entryType = entryType,
                referenceId = referenceId,
                signedAmountMinor = signedAmountMinor,
                currencyCode = currencyCode,
                postedAtEpochMs = Instant.parse(postedAt).toEpochMilli(),
            )

        private companion object {
            const val TAG = "WalletSynchronizer"
        }
    }
