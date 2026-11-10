package dev.ledgerforge.wallet.core.network

import kotlinx.serialization.Serializable

@Serializable
data class TransferRequestDto(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amountMinor: Long,
    val currencyCode: String,
)

@Serializable
data class TransferReceiptDto(
    val transferId: String,
)

@Serializable
data class CreateAccountRequestDto(
    val name: String,
    val currencyCode: String,
    val openingBalanceMinor: Long,
)

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val currencyCode: String,
    val balanceMinor: Long,
)

@Serializable
data class StatementLineDto(
    val journalEntryId: String,
    val entryType: String,
    val referenceId: String? = null,
    val postedAt: String,
    val signedAmountMinor: Long,
    val currencyCode: String,
)

/** An RFC 9457 problem body, as Ledger Forge sends for every refusal. */
@Serializable
internal data class ProblemDto(
    val detail: String? = null,
)
