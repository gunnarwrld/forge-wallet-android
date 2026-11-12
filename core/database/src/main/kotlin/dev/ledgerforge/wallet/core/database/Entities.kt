package dev.ledgerforge.wallet.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Ledger Forge wallet. [balanceMinor] is the balance Ledger Forge last confirmed. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
)

/** Where an outbox row is in its life. See ADR 0001. */
enum class OutboxStatus {
    /** Queued. The money may or may not have moved: only a response from Ledger Forge can say. */
    PENDING,

    /** Ledger Forge answered 201. The local balance doesn't include the debit yet. */
    SETTLED,

    /** A balance fetched after the 201 has been stored, so the debit is in the local balance. */
    RECONCILED,

    /** Ledger Forge refused it, so no money moved. */
    FAILED,

    /** A failed row the user dismissed or retried as a new row. Kept as history, never shown or sent. */
    DISMISSED,
}

enum class RetryOutcome {
    QUEUED,
    INSUFFICIENT_FUNDS,
    NOTHING_TO_RETRY,
}

/**
 * One transfer the user asked for, and its pending line in the activity list.
 *
 * The idempotency key and the request fields are written once, at insert, and no DAO method
 * updates them: every retry has to send exactly what Ledger Forge hashed the first time.
 */
@Entity(
    tableName = "outbox",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_account_id"],
        ),
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["source_account_id", "status"]),
        Index(value = ["status", "seq"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "source_account_id") val sourceAccountId: String,
    @ColumnInfo(name = "destination_account_id") val destinationAccountId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    val status: OutboxStatus = OutboxStatus.PENDING,
    @ColumnInfo(name = "transfer_id") val transferId: String? = null,
    @ColumnInfo(name = "failure_http_status") val failureHttpStatus: Int? = null,
    @ColumnInfo(name = "failure_detail") val failureDetail: String? = null,
)

/**
 * A line of the Ledger Forge statement. Ledger Forge gives lines no id of their own, but a
 * journal entry posts to an account at most once, so the two ids together identify a line.
 */
@Entity(
    tableName = "statement_lines",
    primaryKeys = ["account_id", "journal_entry_id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
        ),
    ],
    indices = [Index(value = ["posted_at_epoch_ms"])],
)
data class StatementLineEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "journal_entry_id") val journalEntryId: String,
    @ColumnInfo(name = "entry_type") val entryType: String,
    @ColumnInfo(name = "reference_id") val referenceId: String?,
    @ColumnInfo(name = "signed_amount_minor") val signedAmountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "posted_at_epoch_ms") val postedAtEpochMs: Long,
)
