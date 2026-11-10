package dev.ledgerforge.wallet.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.ledgerforge.wallet.core.database.AccountEntity
import dev.ledgerforge.wallet.core.database.ForgeDatabase
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.network.LedgerApiFactory
import dev.ledgerforge.wallet.core.network.LedgerClient
import dev.ledgerforge.wallet.core.testing.FakeLedger
import kotlinx.coroutines.flow.first
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** A real Room database and real HTTP against a [FakeLedger]. Only the server is fake. */
internal class SyncHarness : Closeable {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    val ledger = FakeLedger()
    var database: ForgeDatabase = openDatabase()
        private set

    // OkHttp's own silent retry would hide some attempts; turn it off so tests can count them.
    private val ledgerClient: LedgerClient by lazy {
        val http =
            OkHttpClient
                .Builder()
                .retryOnConnectionFailure(false)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        LedgerClient(LedgerApiFactory.create(server.url("/").toString(), http))
    }

    init {
        server.dispatcher = ledger
        server.start()
    }

    val repository: OfflineFirstWalletRepository
        get() =
            OfflineFirstWalletRepository(
                database.accountDao(),
                database.outboxDao(),
                database.activityDao(),
                ledgerClient,
                Clock.fixed(Instant.ofEpochSecond(2_000_000_000), ZoneOffset.UTC),
            )

    fun newSynchronizer() =
        WalletSynchronizer(
            ledgerClient,
            database.accountDao(),
            database.outboxDao(),
            database.syncDao(),
        )

    /** A wallet both Ledger Forge and this device know about. */
    suspend fun addWallet(
        id: String,
        balanceMinor: Long,
    ): AccountId {
        ledger.addAccount(id, balanceMinor)
        database.accountDao().upsert(AccountEntity(id, id, "SEK", balanceMinor))
        return AccountId(id)
    }

    suspend fun wallet(id: AccountId): Wallet = repository.observeWallets().first().single { it.id == id }

    /** What process death leaves behind: the file on disk, and nothing in memory. */
    fun restartProcess() {
        database.close()
        database = openDatabase()
    }

    fun closeServer() = server.close()

    override fun close() {
        server.close()
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun openDatabase() = Room.databaseBuilder(context, ForgeDatabase::class.java, DATABASE_NAME).build()

    private companion object {
        const val DATABASE_NAME = "sync-test.db"
    }
}
