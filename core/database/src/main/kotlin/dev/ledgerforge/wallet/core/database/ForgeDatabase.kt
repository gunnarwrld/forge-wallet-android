package dev.ledgerforge.wallet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, OutboxEntity::class, StatementLineEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncDao(): SyncDao

    abstract fun activityDao(): ActivityDao
}
