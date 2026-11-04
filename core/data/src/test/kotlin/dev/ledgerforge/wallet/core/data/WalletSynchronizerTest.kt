package dev.ledgerforge.wallet.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ledgerforge.wallet.core.database.OutboxEntity
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeLedger
import dev.ledgerforge.wallet.core.testing.FakeLedger.Fault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WalletSynchronizerTest {
    private val sek = CurrencyCode.of("SEK")
    private val harness = SyncHarness()
    private val ledger get() = harness.ledger
    private val alice = AccountId("alice")
    private val bob = AccountId("bob")
    private val carol = AccountId("carol")

    @Before
    fun setUp() =
        runBlocking<Unit> {
            harness.addWallet("alice", 10_000)
            harness.addWallet("bob", 0)
            harness.addWallet("carol", 10_000)
        }

    @After
    fun tearDown() = harness.close()

    private suspend fun send(
        minor: Long,
        from: AccountId = alice,
        to: AccountId = bob,
    ) = assertEquals(QueueResult.Queued, harness.repository.queueTransfer(from, to, Money(minor, sek)))

    private fun faults(vararg sequence: Fault?) {
        val remaining = ArrayDeque(sequence.toList())
        ledger.transferFaults = { remaining.removeFirstOrNull() }
    }

    private suspend fun assertConverged(account: AccountId) {
        val wallet = harness.wallet(account)
        val onServer = ledger.balanceOf(account.value)
        assertEquals(onServer, wallet.postedBalance.minor, "posted balance of ${account.value}")
        assertEquals(onServer, wallet.availableBalance.minor, "available balance of ${account.value}")
    }

    private suspend fun statuses() =
        harness.repository
            .observeActivity()
            .first()
            .map { it.status }

    @Test
    fun `a queued transfer settles once and the balances converge`() =
        runBlocking {
            send(1_250)

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(1, ledger.transfers.size)
            assertEquals(8_750, ledger.balanceOf("alice"))
            assertEquals(1_250, ledger.balanceOf("bob"))
            assertConverged(alice)
            assertConverged(bob)
        }

    @Test
    fun `the pending row becomes one posted line`() =
        runBlocking {
            send(1_250)
            assertEquals(ActivityStatus.Pending, statuses().first())

            harness.newSynchronizer().sync()

            val items = harness.repository.observeActivity().first()
            assertTrue(items.all { it.status == ActivityStatus.Posted })
            assertEquals(1, items.count { it.amount.minor == -1_250L })
            assertEquals(1, items.count { it.amount.minor == 1_250L })
        }

    @Test
    fun `a lost response does not move the money twice`() =
        runBlocking {
            send(1_250)
            faults(Fault.DropResponse)

            assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())
            assertEquals(1, ledger.transfers.size, "the ledger committed")
            assertEquals(ActivityStatus.Pending, statuses().first { it != ActivityStatus.Posted })

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(1, ledger.transfers.size, "the retry replayed instead of posting again")
            assertEquals(2, ledger.attempts.size)
            assertEquals(8_750, ledger.balanceOf("alice"))
            assertConverged(alice)
        }

    @Test
    fun `every attempt sends the same key and the same body`() =
        runBlocking {
            send(1_250)
            faults(Fault.DropRequest, Fault.ServerError, Fault.CaptivePortal, Fault.DropResponse)

            repeat(4) { assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync()) }
            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(5, ledger.attempts.size)
            assertEquals(1, ledger.attempts.distinct().size)
            assertEquals(1, ledger.transfers.size)
        }

    @Test
    fun `process death after the commit replays the key and settles once`() =
        runBlocking {
            send(1_250)
            faults(Fault.DropResponse)
            harness.newSynchronizer().sync()

            harness.restartProcess()

            assertEquals(ActivityStatus.Pending, statuses().first { it != ActivityStatus.Posted })
            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertEquals(1, ledger.transfers.size)
            assertConverged(alice)
            assertConverged(bob)
        }

    @Test
    fun `a transfer queued before process death still goes out`() =
        runBlocking {
            send(1_250)

            harness.restartProcess()

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertEquals(1, ledger.transfers.size)
        }

    @Test
    fun `insufficient funds fails the row and gives the money back`() =
        runBlocking {
            send(1_250)
            ledger.setBalance("alice", 100)

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(ActivityStatus.Failed(TransferFailure.InsufficientFunds), statuses().first { it != ActivityStatus.Posted })
            assertEquals(0, ledger.transfers.size)
            assertConverged(alice)
        }

    @Test
    fun `an unknown account fails the row`() =
        runBlocking {
            send(1_250, to = AccountId("ghost"))

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(ActivityStatus.Failed(TransferFailure.AccountNotFound), statuses().first { it != ActivityStatus.Posted })
        }

    @Test
    fun `a key conflict fails the row as a conflict and moves no money`() =
        runBlocking {
            harness.database.outboxDao().enqueueIfFunded(
                OutboxEntity(
                    idempotencyKey = "used-elsewhere",
                    sourceAccountId = "alice",
                    destinationAccountId = "bob",
                    amountMinor = 1_250,
                    currencyCode = "SEK",
                    createdAtEpochMs = 1,
                ),
            )
            ledger.useKeyElsewhere("used-elsewhere")

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(ActivityStatus.Failed(TransferFailure.Conflict), statuses().first { it != ActivityStatus.Posted })
            assertEquals(0, ledger.transfers.size)
            assertConverged(alice)
        }

    @Test
    fun `a transient failure holds back the rows behind it, in order`() =
        runBlocking {
            send(100)
            send(200)
            faults(Fault.ServerError)

            assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())
            assertEquals(1, ledger.attempts.size, "the second row waits for the first")

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertEquals(listOf(100L, 200L), ledger.transfers.map { it.amountMinor })
        }

    @Test
    fun `a terminal failure does not hold back the rows behind it`() =
        runBlocking {
            send(1_250, to = AccountId("ghost"))
            send(200)

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(listOf(200L), ledger.transfers.map { it.amountMinor })
        }

    @Test
    fun `one wallet waiting on a retry does not hold up another`() =
        runBlocking {
            send(100, from = alice)
            send(200, from = carol)
            ledger.transferFaults = { source -> if (source == "alice") Fault.ServerError else null }

            assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())

            assertEquals(listOf("carol"), ledger.transfers.map { it.source })
        }

    @Test
    fun `a transfer queued during a sync goes out in the same run`() =
        runBlocking {
            send(100)
            var queuedMeanwhile = false
            ledger.onTransferAttempt = {
                if (!queuedMeanwhile) {
                    queuedMeanwhile = true
                    runBlocking { send(200) }
                }
            }

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(listOf(100L, 200L), ledger.transfers.map { it.amountMinor })
        }

    @Test
    fun `it pushes before it pulls`() =
        runBlocking {
            send(100)

            harness.newSynchronizer().sync()

            assertEquals("POST /api/v1/transfers", ledger.requestLog.first())
            assertTrue(ledger.requestLog.drop(1).all { it.startsWith("GET") })
        }

    @Test
    fun `settled debits stay out of the available balance until a pull lands`() =
        runBlocking {
            send(1_250)
            ledger.readsFail = true

            assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())

            assertEquals(ActivityStatus.Settled, statuses().first { it != ActivityStatus.Posted })
            val stale = harness.wallet(alice)
            assertEquals(10_000, stale.postedBalance.minor)
            assertEquals(8_750, stale.availableBalance.minor)

            ledger.readsFail = false
            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertConverged(alice)
        }

    @Test
    fun `while a transfer has no answer its debit counts once, in the balance and the activity list`() =
        runBlocking {
            send(1_250)
            // The ledger commits and the response is lost, so the debit is already on the server.
            faults(Fault.DropResponse)

            assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())

            assertEquals(8_750, ledger.balanceOf("alice"))
            val wallet = harness.wallet(alice)
            assertEquals(10_000, wallet.postedBalance.minor, "not refreshed until the transfer has an answer")
            assertEquals(8_750, wallet.availableBalance.minor, "the debit counts once")
            val debits =
                harness.repository
                    .observeActivity()
                    .first()
                    .filter { it.amount.minor == -1_250L }
            assertEquals(listOf<ActivityStatus>(ActivityStatus.Pending), debits.map { it.status })

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertConverged(alice)
        }

    @Test
    fun `the debit counts once however many retries fail`() =
        runBlocking {
            send(1_250)
            faults(Fault.DropResponse, Fault.ServerError, Fault.DropRequest, Fault.CaptivePortal)

            repeat(4) {
                assertEquals(SyncResult.RetryLater, harness.newSynchronizer().sync())
                assertEquals(8_750, harness.wallet(alice).availableBalance.minor)
            }

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
            assertConverged(alice)
        }

    @Test
    fun `other wallets still refresh while one waits on an answer`() =
        runBlocking {
            send(1_250)
            faults(Fault.DropResponse)

            harness.newSynchronizer().sync()

            assertConverged(bob)
            assertEquals(1_250, harness.wallet(bob).postedBalance.minor)
        }

    @Test
    fun `a wallet Ledger Forge no longer knows does not cause retries`() =
        runBlocking {
            harness.database.accountDao().upsert(
                dev.ledgerforge.wallet.core.database
                    .AccountEntity("ghost", "ghost", "SEK", 0),
            )

            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())
        }

    @Test
    fun `nothing queued means no transfer requests`() =
        runBlocking {
            assertEquals(SyncResult.Done, harness.newSynchronizer().sync())

            assertEquals(emptyList<FakeLedger.Attempt>(), ledger.attempts)
        }
}
