package dev.ledgerforge.wallet.core.domain.model

import dev.ledgerforge.wallet.core.money.Money

/** A Ledger Forge account id. */
@JvmInline
value class AccountId(
    val value: String,
)

data class Wallet(
    val id: AccountId,
    val name: String,
    /** The balance Ledger Forge last confirmed. */
    val postedBalance: Money,
    /** The posted balance minus debits it doesn't include yet. This is what the user can spend. */
    val availableBalance: Money,
)
