package dev.ledgerforge.wallet.core.domain.usecase

import dev.ledgerforge.wallet.core.domain.model.RetryResult
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import javax.inject.Inject

/** Queues a refused transfer again under a new key, then asks for a sync. */
class RetryTransfer
    @Inject
    constructor(
        private val repository: WalletRepository,
        private val sync: SyncRequester,
    ) {
        suspend operator fun invoke(failedId: String): RetryResult =
            repository.retryTransfer(failedId).also { result ->
                if (result == RetryResult.Queued) sync.requestSync()
            }
    }
