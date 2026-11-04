package dev.ledgerforge.wallet.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeLedger.Fault
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The plan's headline metric as a test: many sends with random faults and process deaths, then
 * count what reached the ledger. The target is 0 duplicates and 0 lost. A seed that fails here can
 * be replayed exactly. The on-device version of this script comes with the metrics work.
 */
@RunWith(AndroidJUnit4::class)
class ExactlyOnceChaosTest {
    private val harness = SyncHarness()
    private val sek = CurrencyCode.of("SEK")

    @After
    fun tearDown() = harness.close()

    @Test
    fun `200 sends under random faults and restarts post exactly once and in order`() =
        runBlocking {
            val sends = 200
            val opening = 100_000L
            val wallets = listOf("alice", "bob", "carol").map { harness.addWallet(it, opening) }
            val random = Random(SEED)
            val faults = Random(SEED + 1)
            harness.ledger.transferFaults = { if (faults.nextInt(100) < FAULT_PERCENT) Fault.entries.random(faults) else null }

            val ledger = harness.ledger
            val queuedBySource = wallets.associateWith { mutableListOf<Long>() }
            var synchronizer = harness.newSynchronizer()
            repeat(sends) {
                val (source, destination) = wallets.shuffled(random).take(2)
                val amount = random.nextLong(1, 500)
                assertEquals(QueueResult.Queued, harness.repository.queueTransfer(source, destination, Money(amount, sek)))
                queuedBySource.getValue(source) += amount

                val action = random.nextInt(6)
                if (action < 2) synchronizer.sync()
                wallets.forEach { wallet ->
                    val available = harness.wallet(wallet).availableBalance.minor
                    assertTrue(available <= ledger.balanceOf(wallet.value), "${wallet.value} could spend money it doesn't have")
                }
                if (action == 2) {
                    harness.restartProcess()
                    synchronizer = harness.newSynchronizer()
                }
            }

            var runs = 0
            while (synchronizer.sync() != SyncResult.Done) {
                assertTrue(++runs < 2_000, "the outbox never drained")
            }

            assertEquals(sends, ledger.transfers.size, "lost or duplicated transfers")
            assertEquals(
                sends,
                ledger.transfers
                    .map { it.idempotencyKey }
                    .toSet()
                    .size,
                "a key posted twice",
            )
            assertEquals(
                sends,
                ledger.attempts
                    .map { it.idempotencyKey }
                    .toSet()
                    .size,
                "a retry used a new key",
            )
            assertTrue(ledger.attempts.size > sends, "the faults never fired, so this proved nothing")

            wallets.forEach { wallet: AccountId ->
                assertEquals(
                    queuedBySource.getValue(wallet),
                    ledger.transfers.filter { it.source == wallet.value }.map { it.amountMinor },
                    "order for ${wallet.value}",
                )
                val local = harness.wallet(wallet)
                assertEquals(ledger.balanceOf(wallet.value), local.postedBalance.minor)
                assertEquals(ledger.balanceOf(wallet.value), local.availableBalance.minor)
            }
            assertEquals(3 * opening, wallets.sumOf { ledger.balanceOf(it.value) }, "money was created or destroyed")
        }

    private companion object {
        const val SEED = 20261104
        const val FAULT_PERCENT = 40
    }
}
