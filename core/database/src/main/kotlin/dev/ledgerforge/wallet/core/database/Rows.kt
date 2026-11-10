package dev.ledgerforge.wallet.core.database

import androidx.room.ColumnInfo

/** A wallet with its spendable balance: posted balance minus debits it doesn't include yet. */
data class WalletRow(
    val id: String,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "available_minor") val availableMinor: Long,
)

/** One line of the activity list: an outbox row not yet reconciled, or a statement line. */
data class ActivityRow(
    val id: String,
    @ColumnInfo(name = "account_name") val accountName: String,
    @ColumnInfo(name = "signed_amount_minor") val signedAmountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    /** An [OutboxStatus] name for outbox rows, or [POSTED] for statement lines. */
    val status: String,
    @ColumnInfo(name = "failure_http_status") val failureHttpStatus: Int?,
) {
    companion object {
        const val POSTED = "POSTED"
    }
}
