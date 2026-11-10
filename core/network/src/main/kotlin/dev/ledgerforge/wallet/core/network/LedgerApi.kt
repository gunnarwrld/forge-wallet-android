package dev.ledgerforge.wallet.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Ledger Forge's REST API. Everything returns a Response so [LedgerClient] can read the status. */
interface LedgerApi {
    @POST("api/v1/transfers")
    suspend fun postTransfer(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: TransferRequestDto,
    ): Response<TransferReceiptDto>

    @POST("api/v1/accounts")
    suspend fun createAccount(
        @Body request: CreateAccountRequestDto,
    ): Response<AccountDto>

    @GET("api/v1/accounts/{id}")
    suspend fun getAccount(
        @Path("id") id: String,
    ): Response<AccountDto>

    @GET("api/v1/accounts/{id}/statement")
    suspend fun getStatement(
        @Path("id") id: String,
    ): Response<List<StatementLineDto>>
}

internal val ledgerJson = Json { ignoreUnknownKeys = true }

object LedgerApiFactory {
    /** @param baseUrl must end with a slash, for example `http://10.0.2.2:8080/`. */
    fun create(
        baseUrl: String,
        client: OkHttpClient,
    ): LedgerApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ledgerJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LedgerApi::class.java)
}
