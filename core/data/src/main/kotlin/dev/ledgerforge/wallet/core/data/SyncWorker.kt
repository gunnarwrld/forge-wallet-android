package dev.ledgerforge.wallet.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val synchronizer: WalletSynchronizer,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (synchronizer.sync()) {
                SyncResult.Done -> Result.success()
                SyncResult.RetryLater -> Result.retry()
            }
    }
