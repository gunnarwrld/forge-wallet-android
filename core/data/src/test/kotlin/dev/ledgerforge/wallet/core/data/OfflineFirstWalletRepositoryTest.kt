package dev.ledgerforge.wallet.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ledgerforge.wallet.core.database.ActivityRow
import dev.ledgerforge.wallet.core.database.OutboxStatus
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class OfflineFirstWalletRepositoryTest {
    private val harness = SyncHarness()
    private val sek = CurrencyCode.of("SEK")

    @After
    fun tearDown() = harness.close()

    @Test
    fun `queueing only writes to the device`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)

            assertEquals(QueueResult.Queued, harness.repository.queueTransfer(alice, bob, Money(400, sek)))

            assertEquals(emptyList(), harness.ledger.requestLog)
            val wallet = harness.wallet(alice)
            assertEquals(Money(1_000, sek), wallet.postedBalance)
            assertEquals(Money(600, sek), wallet.availableBalance)
        }

    @Test
    fun `queueing refuses more than the available balance`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)
            harness.repository.queueTransfer(alice, bob, Money(700, sek))

            assertEquals(QueueResult.InsufficientFunds, harness.repository.queueTransfer(alice, bob, Money(400, sek)))
        }

    @Test
    fun `every transfer gets its own UUID key`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)
            harness.repository.queueTransfer(alice, bob, Money(100, sek))
            harness.repository.queueTransfer(alice, bob, Money(100, sek))

            harness.newSynchronizer().sync()

            val keys = harness.ledger.transfers.map { it.idempotencyKey }
            assertEquals(2, keys.toSet().size)
            keys.forEach { UUID.fromString(it) }
        }

    @Test
    fun `a pending row is a signed debit stamped with the clock`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)

            harness.repository.queueTransfer(alice, bob, Money(400, sek))

            val item = harness.repository.observeActivity().first().single()
            assertEquals(Money(-400, sek), item.amount)
            assertEquals(ActivityStatus.Pending, item.status)
            assertEquals("alice", item.accountName)
            assertEquals(Instant.ofEpochSecond(2_000_000_000), item.at)
        }

    @Test
    fun `activity statuses map to the domain`() {
        assertEquals(ActivityStatus.Pending, row(OutboxStatus.PENDING.name).toActivityItem().status)
        assertEquals(ActivityStatus.Settled, row(OutboxStatus.SETTLED.name).toActivityItem().status)
        assertEquals(ActivityStatus.Posted, row(ActivityRow.POSTED).toActivityItem().status)
    }

    @Test
    fun `failure statuses map to reasons`() {
        fun failure(httpStatus: Int?) = row(OutboxStatus.FAILED.name, httpStatus).toActivityItem().status

        assertEquals(ActivityStatus.Failed(TransferFailure.InsufficientFunds), failure(422))
        assertEquals(ActivityStatus.Failed(TransferFailure.AccountNotFound), failure(404))
        assertEquals(ActivityStatus.Failed(TransferFailure.Conflict), failure(409))
        assertEquals(ActivityStatus.Failed(TransferFailure.Rejected), failure(400))
        assertEquals(ActivityStatus.Failed(TransferFailure.Rejected), failure(null))
    }

    @Test
    fun `demo wallets are created on Ledger Forge and stored on the device`() =
        runBlocking {
            assertTrue(harness.repository.createDemoWallets())

            val wallets = harness.repository.observeWallets().first()
            assertEquals(listOf("Alice", "Bob"), wallets.map { it.name })
            wallets.forEach { wallet: dev.ledgerforge.wallet.core.domain.model.Wallet ->
                assertEquals(harness.ledger.balanceOf(wallet.id.value), wallet.postedBalance.minor)
            }
            assertTrue(wallets.all { it.postedBalance.isPositive })
        }

    @Test
    fun `no demo wallets are stored if Ledger Forge cannot be reached`() =
        runBlocking {
            harness.closeServer()

            assertFalse(harness.repository.createDemoWallets())

            assertEquals(emptyList(), harness.repository.observeWallets().first())
        }

    @Test
    fun `wallets map to the domain`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)

            assertEquals(AccountId("alice"), harness.wallet(alice).id)
            assertEquals("alice", harness.wallet(alice).name)
        }

    private fun row(
        status: String,
        failureHttpStatus: Int? = null,
    ) = ActivityRow("id", "Alice", -100, "SEK", 1_000, status, failureHttpStatus)
}
