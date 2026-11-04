package dev.ledgerforge.wallet.core.network

import javax.inject.Inject

/** What a transfer attempt told us. Anything but these two answers means "ask again". */
sealed interface TransferOutcome {
    /** Ledger Forge applied the transfer, now or on an earlier attempt with this key. */
    data class Settled(
        val transferId: String,
    ) : TransferOutcome

    /** Ledger Forge refused the transfer and no money moved. Retrying with this key can't help. */
    data class Rejected(
        val httpStatus: Int,
        val detail: String?,
    ) : TransferOutcome

    /** The money may or may not have moved. The same key will find out safely. */
    data object RetryLater : TransferOutcome
}

sealed interface SnapshotOutcome {
    data class Fetched(
        val account: AccountDto,
        val statement: List<StatementLineDto>,
    ) : SnapshotOutcome

    /** Ledger Forge says this account doesn't exist. Retrying won't change that. */
    data object AccountGone : SnapshotOutcome

    data object RetryLater : SnapshotOutcome
}

/**
 * Turns Ledger Forge's HTTP responses into the answers the outbox can act on.
 * The rules, and why they're this strict, are in ADR 0001.
 */
class LedgerClient
    @Inject
    constructor(
        private val api: LedgerApi,
    ) {
        suspend fun submitTransfer(
            idempotencyKey: String,
            request: TransferRequestDto,
        ): TransferOutcome = TODO()

        suspend fun fetchSnapshot(accountId: String): SnapshotOutcome = TODO()

        /** Null if the account couldn't be created. */
        suspend fun createAccount(
            name: String,
            currencyCode: String,
            openingBalanceMinor: Long,
        ): AccountDto? = TODO()
    }
