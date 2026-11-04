package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    /**
     * Outbox rows not yet reconciled plus statement lines, newest first. One query, so reconciling
     * swaps a pending row for its posted line in a single emission and the list never shows both.
     */
    @Query(
        """
        SELECT o.idempotency_key AS id, a.name AS account_name, -o.amount_minor AS signed_amount_minor,
               o.currency_code AS currency_code, o.created_at_epoch_ms AS at_epoch_ms,
               o.status AS status, o.failure_http_status AS failure_http_status
          FROM outbox o JOIN accounts a ON a.id = o.source_account_id
         WHERE o.status != 'RECONCILED'
        UNION ALL
        SELECT l.account_id || ':' || l.journal_entry_id, a.name, l.signed_amount_minor,
               l.currency_code, l.posted_at_epoch_ms, 'POSTED', NULL
          FROM statement_lines l JOIN accounts a ON a.id = l.account_id
         ORDER BY at_epoch_ms DESC, id
         LIMIT :limit
        """,
    )
    fun observe(limit: Int = 100): Flow<List<ActivityRow>>
}
