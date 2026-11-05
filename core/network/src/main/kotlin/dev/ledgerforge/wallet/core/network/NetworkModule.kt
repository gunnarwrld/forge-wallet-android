package dev.ledgerforge.wallet.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Where Ledger Forge lives. The app module provides it for each build type. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LedgerBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun ledgerApi(
        @LedgerBaseUrl baseUrl: String,
        client: OkHttpClient,
    ): LedgerApi = LedgerApiFactory.create(baseUrl, client)
}
