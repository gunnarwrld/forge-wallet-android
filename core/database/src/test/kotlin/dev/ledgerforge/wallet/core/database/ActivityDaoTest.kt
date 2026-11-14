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
class ActivityDaoTest {
    private lateinit var database: ForgeDatabase
    private val activity get() = database.activityDao()
    private val outbox get() = database.outboxDao()

    @Before
    fun setUp() =
        runTest {
            database = inMemoryDatabase()
            database.accountDao().upsert(account("alice", balanceMinor = 1_000, name = "Alice"))
            database.accountDao().upsert(account("bob", name = "Bob"))
        }

    @After
    fun tearDown() = database.close()

    @Test
    fun `merges local rows and statement lines, newest first`() =
        runTest {
            database.syncDao().applySnapshot(
                accountId = "alice",
                balanceMinor = 1_000,
                lines = listOf(line("old", signedAmountMinor = 1_000, postedAt = 1_000)),
                settledSeqs = emptyList(),
            )
            outbox.enqueueIfFunded(transfer("pending", amountMinor = 250, createdAt = 2_000))

            val rows = activity.observe().first()

            assertEquals(listOf(2_000L, 1_000L), rows.map { it.atEpochMs })
            assertEquals(listOf("PENDING", ActivityRow.POSTED), rows.map { it.status })
            assertEquals(listOf(-250L, 1_000L), rows.map { it.signedAmountMinor })
            assertEquals(listOf("Alice", "Alice"), rows.map { it.accountName })
        }

    @Test
    fun `failed rows stay visible and carry their status code`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1"))
            outbox.markFailed(outbox.nextPending()!!.seq, httpStatus = 422, detail = "insufficient funds")

            val row = activity.observe().first().single()

            assertEquals("FAILED", row.status)
            assertEquals(422, row.failureHttpStatus)
        }

    @Test
    fun `the two sides of one journal entry are separate lines`() =
        runTest {
            database.syncDao().applySnapshot("alice", 900, listOf(line("j1", account = "alice", signedAmountMinor = -100)), emptyList())
            database.syncDao().applySnapshot("bob", 100, listOf(line("j1", account = "bob", signedAmountMinor = 100)), emptyList())

            val rows = activity.observe().first()

            assertEquals(2, rows.map { it.id }.toSet().size)
            assertEquals(setOf("Alice", "Bob"), rows.map { it.accountName }.toSet())
        }

    @Test
    fun `reconciling swaps the pending row for the posted line in one emission`() =
        runTest {
            activity.observe().test {
                assertEquals(emptyList(), awaitItem())

                outbox.enqueueIfFunded(transfer("k1", amountMinor = 100))
                assertEquals(listOf("PENDING"), awaitItem().map { it.status })

                val seq = outbox.nextPending()!!.seq
                outbox.markSettled(seq, "transfer-1")
                assertEquals(listOf("SETTLED"), awaitItem().map { it.status })

                database.syncDao().applySnapshot("alice", 900, listOf(line("j1", signedAmountMinor = -100)), listOf(seq))
                assertEquals(listOf(ActivityRow.POSTED), awaitItem().map { it.status })

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissed rows leave the activity list`() =
        runTest {
            outbox.enqueueIfFunded(transfer("k1"))
            outbox.markFailed(outbox.nextPending()!!.seq, httpStatus = 422, detail = null)

            outbox.dismissFailed("k1")

            assertEquals(emptyList(), activity.observe().first())
        }
}
