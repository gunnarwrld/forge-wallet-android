package dev.ledgerforge.wallet.core.network

import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_NOT_FOUND
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
        ): TransferOutcome =
            try {
                val response = api.postTransfer(idempotencyKey, request)
                val receipt = response.body()
                when {
                    response.code() == HTTP_CREATED && receipt != null -> {
                        TransferOutcome.Settled(receipt.transferId)
                    }

                    response.code() in REFUSALS && response.isLedgerProblem() -> {
                        TransferOutcome.Rejected(
                            response.code(),
                            response.problemDetail(),
                        )
                    }

                    else -> {
                        TransferOutcome.RetryLater
                    }
                }
            } catch (_: IOException) {
                TransferOutcome.RetryLater
            } catch (_: SerializationException) {
                TransferOutcome.RetryLater
            }

        /**
         * Fetches the statement first and the balance second. A transfer landing between the two
         * ends up in the balance but not in the lines, and the next pull adds the line. The other
         * order could store a line whose debit isn't in the balance yet.
         */
        suspend fun fetchSnapshot(accountId: String): SnapshotOutcome =
            try {
                val statement = api.getStatement(accountId)
                val lines = statement.body()
                if (lines == null) {
                    statement.toFailedSnapshot()
                } else {
                    val account = api.getAccount(accountId)
                    account.body()?.let { SnapshotOutcome.Fetched(it, lines) } ?: account.toFailedSnapshot()
                }
            } catch (_: IOException) {
                SnapshotOutcome.RetryLater
            } catch (_: SerializationException) {
                SnapshotOutcome.RetryLater
            }

        /** Null if the account couldn't be created. */
        suspend fun createAccount(
            name: String,
            currencyCode: String,
            openingBalanceMinor: Long,
        ): AccountDto? =
            try {
                api.createAccount(CreateAccountRequestDto(name, currencyCode, openingBalanceMinor)).body()
            } catch (_: IOException) {
                null
            } catch (_: SerializationException) {
                null
            }

        private fun Response<*>.toFailedSnapshot(): SnapshotOutcome =
            if (code() == HTTP_NOT_FOUND && isLedgerProblem()) SnapshotOutcome.AccountGone else SnapshotOutcome.RetryLater

        /** A proxy or captive portal can send any status, but it won't speak Ledger Forge's format. */
        private fun Response<*>.isLedgerProblem(): Boolean {
            val contentType = errorBody()?.contentType() ?: return false
            return contentType.type == "application" && contentType.subtype == "problem+json"
        }

        private fun Response<*>.problemDetail(): String? =
            try {
                errorBody()?.string()?.let { ledgerJson.decodeFromString<ProblemDto>(it).detail }
            } catch (_: SerializationException) {
                null
            }

        private companion object {
            /** The statuses Ledger Forge uses when it refuses a transfer and rolls everything back. */
            val REFUSALS = setOf(HTTP_BAD_REQUEST, HTTP_NOT_FOUND, HTTP_CONFLICT, HTTP_UNPROCESSABLE_ENTITY)
            const val HTTP_UNPROCESSABLE_ENTITY = 422
        }
    }
