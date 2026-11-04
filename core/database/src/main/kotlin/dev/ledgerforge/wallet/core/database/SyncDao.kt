package dev.ledgerforge.wallet.core.database

import androidx.room.Dao

@Dao
interface SyncDao {
    /**
     * Stores what a pull fetched, in one transaction: the statement lines, the balance, and
     * [settledSeqs] as reconciled. The caller reads [settledSeqs] before it fetches the balance,
     * so every one of those debits is in [balanceMinor]. See ADR 0001.
     */
    suspend fun applySnapshot(
        accountId: String,
        balanceMinor: Long,
        lines: List<StatementLineEntity>,
        settledSeqs: List<Long>,
    ): Unit = TODO()
}
