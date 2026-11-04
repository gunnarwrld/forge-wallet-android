package dev.ledgerforge.wallet.core.database

import androidx.room.Dao
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)

    suspend fun ids(): List<String> = TODO()

    fun observeWallets(): Flow<List<WalletRow>> = TODO()
}
