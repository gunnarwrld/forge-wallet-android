package dev.ledgerforge.wallet.core.data

import androidx.work.WorkManager
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import javax.inject.Inject

class WorkManagerSyncRequester
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : SyncRequester {
        override suspend fun requestSync(): Unit = TODO()

        override suspend fun syncNow(): Unit = TODO()

        companion object {
            const val SYNC_WORK = "wallet-sync"
        }
    }
