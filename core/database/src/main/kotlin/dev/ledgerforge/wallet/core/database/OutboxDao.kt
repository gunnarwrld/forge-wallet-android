package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(row: OutboxEntity): Long

    /** Null when the account isn't known. */
    suspend fun availableMinor(accountId: String): Long? = TODO()

    /**
     * Queues [row] only if the available balance covers it. The check and the insert are one
     * transaction, so two sends at once can't both spend the same money.
     */
    suspend fun enqueueIfFunded(row: OutboxEntity): Boolean = TODO()

    /** The oldest pending row, skipping sources whose head row is waiting on a retry. */
    suspend fun nextPending(blockedSources: Collection<String> = emptyList()): OutboxEntity? = TODO()

    suspend fun markSettled(
        seq: Long,
        transferId: String,
    ): Unit = TODO()

    suspend fun markFailed(
        seq: Long,
        httpStatus: Int,
        detail: String?,
    ): Unit = TODO()

    suspend fun settledSeqs(accountId: String): List<Long> = TODO()

    suspend fun get(seq: Long): OutboxEntity? = TODO()
}
