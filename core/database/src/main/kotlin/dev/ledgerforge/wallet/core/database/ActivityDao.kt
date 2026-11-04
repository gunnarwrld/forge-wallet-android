package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    /**
     * Outbox rows not yet reconciled plus statement lines, newest first. One query, so reconciling
     * swaps a pending row for its posted line in a single emission and the list never shows both.
     */
    fun observe(limit: Int = 100): Flow<List<ActivityRow>> = TODO()
}
