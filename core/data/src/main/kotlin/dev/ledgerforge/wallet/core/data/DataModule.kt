package dev.ledgerforge.wallet.core.data

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun walletRepository(repository: OfflineFirstWalletRepository): WalletRepository

    @Binds
    abstract fun syncRequester(requester: WorkManagerSyncRequester): SyncRequester
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun workManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)
}
