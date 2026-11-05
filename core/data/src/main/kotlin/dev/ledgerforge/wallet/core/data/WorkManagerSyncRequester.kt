package dev.ledgerforge.wallet.core.data

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerSyncRequester
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : SyncRequester {
        /**
         * Guarantees a run that starts after this call, so a row queued just before it can't be
         * stranded. A run that hasn't started will see it, so nothing more is needed. A run in
         * progress may already have finished reading the outbox, so one more goes behind it.
         */
        override suspend fun requestSync() {
            val infos = workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK).first()
            val aRunHasNotStarted = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            if (!aRunHasNotStarted) {
                workManager.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, syncRequest())
            }
        }

        /** Cancelling a run mid-request is safe: the row stays pending and the new run replays its key. */
        override suspend fun syncNow() {
            workManager.enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.REPLACE, syncRequest())
        }

        private fun syncRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

        companion object {
            const val SYNC_WORK = "wallet-sync"
            private const val BACKOFF_SECONDS = 10L
        }
    }
