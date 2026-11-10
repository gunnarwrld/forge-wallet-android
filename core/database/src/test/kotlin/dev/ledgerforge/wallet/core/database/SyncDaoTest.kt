package dev.ledgerforge.wallet.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class SyncDaoTest {
    private lateinit var database: ForgeDatabase
    private val sync get() = database.syncDao()
    private val outbox get() = database.outboxDao()

    @Before
    fun setUp() =
        runTest {
            database = inMemoryDatabase()
            database.accountDao().upsert(account("alice", balanceMinor = 1_000))
        }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a snapshot stores the balance and reconciles the rows it covers`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))
            val seq = outbox.nextPending()!!.seq
            outbox.markSettled(seq, "transfer-1")

            sync.applySnapshot("alice", balanceMinor = 600, lines = listOf(line("j1", signedAmountMinor = -400)), settledSeqs = listOf(seq))

            assertEquals(OutboxStatus.RECONCILED, outbox.get(seq)?.status)
            assertEquals(600, outbox.availableMinor("alice"))
        }

    @Test
    fun `a row settled after the snapshot was read stays settled`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))
            val seq = outbox.nextPending()!!.seq
            outbox.markSettled(seq, "transfer-1")

            sync.applySnapshot("alice", balanceMinor = 1_000, lines = emptyList(), settledSeqs = emptyList())

            assertEquals(OutboxStatus.SETTLED, outbox.get(seq)?.status)
            assertEquals(600, outbox.availableMinor("alice"))
        }

    @Test
    fun `only settled rows can be reconciled`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))
            val seq = outbox.nextPending()!!.seq

            sync.applySnapshot("alice", balanceMinor = 1_000, lines = emptyList(), settledSeqs = listOf(seq))

            assertEquals(OutboxStatus.PENDING, outbox.get(seq)?.status)
        }

    @Test
    fun `applying the same lines twice keeps one line per posting`() =
        runTest {
            val lines = listOf(line("j1"), line("j2"))

            sync.applySnapshot("alice", balanceMinor = 800, lines = lines, settledSeqs = emptyList())
            sync.applySnapshot("alice", balanceMinor = 800, lines = lines, settledSeqs = emptyList())

            val rows = database.activityDao().observeOnce()
            assertEquals(2, rows.size)
        }

    @Test
    fun `a failed snapshot changes nothing`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1", amountMinor = 400))
            val seq = outbox.nextPending()!!.seq
            outbox.markSettled(seq, "transfer-1")

            // The line breaks the foreign key, so the whole transaction has to roll back.
            assertFailsWith<SQLiteConstraintException> {
                sync.applySnapshot("alice", balanceMinor = 600, lines = listOf(line("j1", account = "ghost")), settledSeqs = listOf(seq))
            }

            assertEquals(OutboxStatus.SETTLED, outbox.get(seq)?.status)
            assertEquals(600, outbox.availableMinor("alice"))
        }

    private suspend fun ActivityDao.observeOnce() = observe().first()
}
