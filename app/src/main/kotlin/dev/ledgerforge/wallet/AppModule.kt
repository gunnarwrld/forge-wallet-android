package dev.ledgerforge.wallet

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ledgerforge.wallet.core.network.LedgerBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @LedgerBaseUrl
    fun ledgerBaseUrl(): String = BuildConfig.LEDGER_BASE_URL
}
