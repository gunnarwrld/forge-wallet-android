package dev.ledgerforge.wallet.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {
    private lateinit var database: ForgeDatabase
    private val accounts get() = database.accountDao()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `wallets show their available balance, sorted by name`() =
        runTest {
            accounts.upsert(account("b", balanceMinor = 500, name = "Bob"))
            accounts.upsert(account("alice", balanceMinor = 1_000, name = "Alice"))
            database.outboxDao().enqueueIfFunded(transfer("k1", source = "alice", amountMinor = 300))

            val wallets = accounts.observeWallets().first()

            assertEquals(listOf("Alice", "Bob"), wallets.map { it.name })
            assertEquals(listOf(1_000L, 500L), wallets.map { it.balanceMinor })
            assertEquals(listOf(700L, 500L), wallets.map { it.availableMinor })
        }

    @Test
    fun `the available balance updates as the outbox changes`() =
        runTest {
            accounts.upsert(account("alice", balanceMinor = 1_000))

            accounts.observeWallets().test {
                assertEquals(1_000, awaitItem().single().availableMinor)

                database.outboxDao().enqueueIfFunded(transfer("k1", amountMinor = 300))
                assertEquals(700, awaitItem().single().availableMinor)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ids lists every account`() =
        runTest {
            accounts.upsert(account("alice"))
            accounts.upsert(account("bob"))

            assertEquals(setOf("alice", "bob"), accounts.ids().toSet())
        }
}
