package dev.ledgerforge.wallet.core.domain.usecase

import dev.ledgerforge.wallet.core.domain.model.RetryResult
import dev.ledgerforge.wallet.core.testing.FakeSyncRequester
import dev.ledgerforge.wallet.core.testing.FakeWalletRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class RetryTransferTest {
    private val repository = FakeWalletRepository()
    private val sync = FakeSyncRequester()
    private val retryTransfer = RetryTransfer(repository, sync)

    @Test
    fun `a queued retry asks for a sync`() =
        runTest {
            repository.retryResult = RetryResult.Queued

            assertEquals(RetryResult.Queued, retryTransfer("failed-1"))

            assertEquals(listOf("failed-1"), repository.retried)
            assertEquals(1, sync.requests)
        }

    @Test
    fun `a retry that was not queued does not sync`() =
        runTest {
            listOf(RetryResult.InsufficientFunds, RetryResult.NothingToRetry).forEach { result ->
                repository.retryResult = result
                assertEquals(result, retryTransfer("failed-1"))
            }

            assertEquals(0, sync.requests)
        }
}
