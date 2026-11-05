package dev.ledgerforge.wallet.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeLedger.Fault
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    private val harness = SyncHarness()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = harness.close()

    private fun worker() =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = SyncWorker(appContext, workerParameters, harness.newSynchronizer())
                },
            ).build()

    @Test
    fun `succeeds when everything is confirmed`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)
            harness.repository.queueTransfer(alice, bob, Money(100, CurrencyCode.of("SEK")))

            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            assertEquals(1, harness.ledger.transfers.size)
        }

    @Test
    fun `asks for a retry when a transfer could not be confirmed`() =
        runBlocking {
            val alice = harness.addWallet("alice", 1_000)
            val bob = harness.addWallet("bob", 0)
            harness.repository.queueTransfer(alice, bob, Money(100, CurrencyCode.of("SEK")))
            harness.ledger.transferFaults = { Fault.DropResponse }

            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        }
}
