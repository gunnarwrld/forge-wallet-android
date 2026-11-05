package dev.ledgerforge.wallet.core.domain.model

import dev.ledgerforge.wallet.core.money.Money
import java.time.Instant

/** One line of account activity. The amount is signed: debits are negative. */
data class ActivityItem(
    val id: String,
    val accountName: String,
    val amount: Money,
    val at: Instant,
    val status: ActivityStatus,
)

sealed interface ActivityStatus {
    /** Queued on this device. Ledger Forge hasn't confirmed it yet. */
    data object Pending : ActivityStatus

    /** Ledger Forge confirmed it. The statement line hasn't been pulled yet. */
    data object Settled : ActivityStatus

    /** A line on the Ledger Forge statement. */
    data object Posted : ActivityStatus

    /** Ledger Forge refused it, so no money moved. */
    data class Failed(
        val reason: TransferFailure,
    ) : ActivityStatus
}

enum class TransferFailure {
    InsufficientFunds,
    AccountNotFound,
    Rejected,

    /** The key was already used with a different body. The outbox never does that, so it's a client bug. */
    Conflict,
}
