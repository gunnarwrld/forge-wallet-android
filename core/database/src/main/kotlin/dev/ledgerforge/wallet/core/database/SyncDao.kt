package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface SyncDao {
    @Upsert
    suspend fun upsertLines(lines: List<StatementLineEntity>)

    @Query("UPDATE accounts SET balance_minor = :balanceMinor WHERE id = :accountId")
    suspend fun updateBalance(
        accountId: String,
        balanceMinor: Long,
    )

    @Query("UPDATE outbox SET status = 'RECONCILED' WHERE seq IN (:seqs) AND status = 'SETTLED'")
    suspend fun markReconciled(seqs: List<Long>)

    /**
     * Stores what a pull fetched, in one transaction: the statement lines, the balance, and
     * [settledSeqs] as reconciled. The caller reads [settledSeqs] before it fetches the balance,
     * so every one of those debits is in [balanceMinor]. See ADR 0001.
     */
    @Transaction
    suspend fun applySnapshot(
        accountId: String,
        balanceMinor: Long,
        lines: List<StatementLineEntity>,
        settledSeqs: List<Long>,
    ) {
        updateBalance(accountId, balanceMinor)
        upsertLines(lines)
        markReconciled(settledSeqs)
    }
}
