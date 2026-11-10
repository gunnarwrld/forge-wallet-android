package dev.ledgerforge.wallet.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): ForgeDatabase = Room.databaseBuilder(context, ForgeDatabase::class.java, "forge-wallet.db").build()

    @Provides
    fun accountDao(database: ForgeDatabase): AccountDao = database.accountDao()

    @Provides
    fun outboxDao(database: ForgeDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun syncDao(database: ForgeDatabase): SyncDao = database.syncDao()

    @Provides
    fun activityDao(database: ForgeDatabase): ActivityDao = database.activityDao()
}
