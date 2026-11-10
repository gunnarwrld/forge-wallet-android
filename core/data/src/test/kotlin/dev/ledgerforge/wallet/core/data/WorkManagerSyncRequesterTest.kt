package dev.ledgerforge.wallet.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WorkManagerSyncRequesterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private lateinit var workManager: WorkManager
    private lateinit var requester: WorkManagerSyncRequester

    /** Stands in for SyncWorker: stays running until the test releases it. */
    private inner class BlockingWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : Worker(appContext, params) {
        override fun doWork(): Result {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            return Result.success()
        }
    }

    @Before
    fun setUp() {
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = BlockingWorker(appContext, workerParameters)
            }
        val configuration =
            Configuration
                .Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .setWorkerFactory(factory)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        requester = WorkManagerSyncRequester(workManager)
    }

    private fun infos(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerSyncRequester.SYNC_WORK).get()

    @Test
    fun `a request waits for a network`() =
        runBlocking {
            requester.requestSync()

            val info = infos().single()
            assertEquals(WorkInfo.State.ENQUEUED, info.state)
            assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        }

    @Test
    fun `a request while another run is waiting adds nothing`() =
        runBlocking {
            requester.requestSync()
            requester.requestSync()
            requester.requestSync()

            assertEquals(1, infos().size)
        }

    @Test
    fun `a request during a run queues one more run behind it`() =
        runBlocking {
            requester.requestSync()
            val first = infos().single().id
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first)
            assertTrue(started.await(10, TimeUnit.SECONDS), "the first run never started")

            requester.requestSync()
            requester.requestSync()

            assertEquals(
                setOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED),
                infos().map { it.state }.toSet(),
            )
            assertEquals(2, infos().size, "one run in progress and one behind it, however many requests")
            release.countDown()
        }

    @Test
    fun `sync now replaces a run that is waiting`() =
        runBlocking {
            requester.requestSync()
            val waiting = infos().single().id

            requester.syncNow()

            val current = infos().single()
            assertTrue(current.id != waiting, "the waiting run, and any backoff it has, should be gone")
            assertEquals(WorkInfo.State.ENQUEUED, current.state)
        }
}
