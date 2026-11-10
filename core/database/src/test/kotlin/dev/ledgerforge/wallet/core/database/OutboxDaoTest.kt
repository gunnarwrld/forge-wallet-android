package dev.ledgerforge.wallet.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {
    private lateinit var database: ForgeDatabase
    private val outbox get() = database.outboxDao()

    @Before
    fun setUp() =
        runTest {
            database = inMemoryDatabase()
            database.accountDao().upsert(account("alice", balanceMinor = 1_000))
            database.accountDao().upsert(account("carol", balanceMinor = 1_000))
        }

    @After
    fun tearDown() = database.close()

    @Test
    fun `queues a transfer the available balance covers`() =
        runTest {
            assertTrue(outbox.enqueueIfFunded(transfer("k1", amountMinor = 400)))

            assertEquals(600, outbox.availableMinor("alice"))
        }

    @Test
    fun `refuses a transfer that pending debits leave uncovered`() =
        runTest {
            assertTrue(outbox.enqueueIfFunded(transfer("k1", amountMinor = 700)))

            assertFalse(outbox.enqueueIfFunded(transfer("k2", amountMinor = 400)))

            assertEquals(300, outbox.availableMinor("alice"))
            assertEquals("k1", outbox.nextPending()?.idempotencyKey)
        }

    @Test
    fun `allows spending exactly the available amount`() =
        runTest {
            assertTrue(outbox.enqueueIfFunded(transfer("k1", amountMinor = 1_000)))

            assertEquals(0, outbox.availableMinor("alice"))
        }

    @Test
    fun `refuses a transfer from an unknown account`() =
        runTest {
            assertFalse(outbox.enqueueIfFunded(transfer("k1", source = "ghost")))
            assertNull(outbox.availableMinor("ghost"))
        }

    @Test
    fun `settled rows keep reducing the available balance until reconciled`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))
            val seq = outbox.nextPending()!!.seq

            outbox.markSettled(seq, "transfer-1")
            assertEquals(600, outbox.availableMinor("alice"))

            // The next pull stores a balance that includes the debit.
            database.syncDao().applySnapshot("alice", balanceMinor = 600, lines = emptyList(), settledSeqs = listOf(seq))
            assertEquals(600, outbox.availableMinor("alice"))
        }

    @Test
    fun `failed rows give the money back`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))

            outbox.markFailed(outbox.nextPending()!!.seq, httpStatus = 422, detail = "insufficient funds")

            assertEquals(1_000, outbox.availableMinor("alice"))
            assertNull(outbox.nextPending())
        }

    @Test
    fun `next pending is the oldest row`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", createdAt = 5_000))
            outbox.enqueueIfFunded(transfer("k2", createdAt = 1_000))

            assertEquals("k1", outbox.nextPending()?.idempotencyKey)

            outbox.markSettled(outbox.nextPending()!!.seq, "transfer-1")
            assertEquals("k2", outbox.nextPending()?.idempotencyKey)
        }

    @Test
    fun `next pending skips blocked sources`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", source = "alice"))
            outbox.enqueueIfFunded(transfer("k2", source = "carol"))

            assertEquals("k2", outbox.nextPending(blockedSources = listOf("alice"))?.idempotencyKey)
            assertNull(outbox.nextPending(blockedSources = listOf("alice", "carol")))
        }

    @Test
    fun `only pending rows can settle or fail`() =
        runTest {
            outbox.enqueueIfFunded(transfer("settled"))
            val settled = outbox.nextPending()!!.seq
            outbox.markSettled(settled, "transfer-1")
            outbox.enqueueIfFunded(transfer("failed"))
            val failed = outbox.nextPending()!!.seq
            outbox.markFailed(failed, httpStatus = 422, detail = null)

            outbox.markFailed(settled, httpStatus = 500, detail = "late failure")
            outbox.markSettled(failed, "transfer-2")

            assertEquals(OutboxStatus.SETTLED, outbox.get(settled)?.status)
            assertEquals("transfer-1", outbox.get(settled)?.transferId)
            assertEquals(OutboxStatus.FAILED, outbox.get(failed)?.status)
            assertNull(outbox.get(failed)?.transferId)
        }

    @Test
    fun `settling keeps the key and the request exactly as queued`() =
        runTest {
            val queued = transfer("k1", amountMinor = 250, createdAt = 42)
            outbox.enqueueIfFunded(queued)
            val seq = outbox.nextPending()!!.seq

            outbox.markSettled(seq, "transfer-1")

            val expected = queued.copy(seq = seq, status = OutboxStatus.SETTLED, transferId = "transfer-1")
            assertEquals(expected, outbox.get(seq))
        }

    @Test
    fun `settled seqs are per account and exclude other statuses`() =
        runTest {
            outbox.enqueueIfFunded(transfer("a1", source = "alice"))
            val aliceSettled = outbox.nextPending()!!.seq
            outbox.markSettled(aliceSettled, "t1")
            outbox.enqueueIfFunded(transfer("a2", source = "alice"))
            outbox.enqueueIfFunded(transfer("c1", source = "carol"))
            val pendingAlice = outbox.nextPending()!!.seq
            val carol = outbox.nextPending(blockedSources = listOf("alice"))!!.seq
            outbox.markSettled(carol, "t2")

            assertEquals(listOf(aliceSettled), outbox.settledSeqs("alice"))
            assertEquals(listOf(carol), outbox.settledSeqs("carol"))
            assertEquals(OutboxStatus.PENDING, outbox.get(pendingAlice)?.status)
        }

    @Test
    fun `an idempotency key can only be queued once`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1"))

            assertFailsWith<SQLiteConstraintException> { outbox.enqueueIfFunded(transfer("k1")) }
        }
}
