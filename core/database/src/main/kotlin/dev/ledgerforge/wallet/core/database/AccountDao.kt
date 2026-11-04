package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT id FROM accounts")
    suspend fun ids(): List<String>

    @Query(
        """
        SELECT a.id, a.name, a.currency_code, a.balance_minor,
               a.balance_minor - COALESCE(
                   (SELECT SUM(o.amount_minor) FROM outbox o
                     WHERE o.source_account_id = a.id AND o.status IN ('PENDING', 'SETTLED')),
                   0
               ) AS available_minor
          FROM accounts a
         ORDER BY a.name
        """,
    )
    fun observeWallets(): Flow<List<WalletRow>>
}
