package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(row: OutboxEntity): Long

    /** Null when the account isn't known. */
    @Query(
        """
        SELECT a.balance_minor - COALESCE(
                   (SELECT SUM(o.amount_minor) FROM outbox o
                     WHERE o.source_account_id = a.id AND o.status IN ('PENDING', 'SETTLED')),
                   0
               )
          FROM accounts a
         WHERE a.id = :accountId
        """,
    )
    suspend fun availableMinor(accountId: String): Long?

    /**
     * Queues [row] only if the available balance covers it. The check and the insert are one
     * transaction, so two sends at once can't both spend the same money.
     */
    @Transaction
    suspend fun enqueueIfFunded(row: OutboxEntity): Boolean {
        val available = availableMinor(row.sourceAccountId) ?: return false
        if (available < row.amountMinor) return false
        insert(row)
        return true
    }

    /** The oldest pending row, skipping sources whose head row is waiting on a retry. */
    @Query(
        """
        SELECT * FROM outbox
         WHERE status = 'PENDING' AND source_account_id NOT IN (:blockedSources)
         ORDER BY seq
         LIMIT 1
        """,
    )
    suspend fun nextPending(blockedSources: Collection<String> = emptyList()): OutboxEntity?

    @Query("UPDATE outbox SET status = 'SETTLED', transfer_id = :transferId WHERE seq = :seq AND status = 'PENDING'")
    suspend fun markSettled(
        seq: Long,
        transferId: String,
    )

    @Query(
        """
        UPDATE outbox
           SET status = 'FAILED', failure_http_status = :httpStatus, failure_detail = :detail
         WHERE seq = :seq AND status = 'PENDING'
        """,
    )
    suspend fun markFailed(
        seq: Long,
        httpStatus: Int,
        detail: String?,
    )

    @Query("SELECT seq FROM outbox WHERE source_account_id = :accountId AND status = 'SETTLED'")
    suspend fun settledSeqs(accountId: String): List<Long>

    @Query("SELECT * FROM outbox WHERE seq = :seq")
    suspend fun get(seq: Long): OutboxEntity?

    suspend fun getByKey(key: String): OutboxEntity? = TODO()

    /**
     * Queues a failed row's request again as a new row under [newKey], and dismisses the failed
     * row, in one transaction. Only a failed row can be retried, so a double tap retries once.
     */
    suspend fun retryFailed(
        failedKey: String,
        newKey: String,
        createdAtEpochMs: Long,
    ): RetryOutcome = TODO()

    /** Dismisses [key] if it failed. Any other row is left alone. */
    suspend fun dismissFailed(key: String): Unit = TODO()
}
