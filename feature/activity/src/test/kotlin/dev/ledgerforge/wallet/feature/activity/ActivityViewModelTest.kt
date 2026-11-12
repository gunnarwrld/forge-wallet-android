package dev.ledgerforge.wallet.feature.activity

import app.cash.turbine.test
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.RetryResult
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.domain.usecase.RetryTransfer
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeSyncRequester
import dev.ledgerforge.wallet.core.testing.FakeWalletRepository
import dev.ledgerforge.wallet.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ActivityViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sek = CurrencyCode.of("SEK")
    private val repository = FakeWalletRepository()
    private val sync = FakeSyncRequester()
    private val viewModel by lazy { ActivityViewModel(repository, RetryTransfer(repository, sync)) }

    private val failed =
        ActivityItem("failed-1", "Alice", Money(-400, sek), Instant.EPOCH, ActivityStatus.Failed(TransferFailure.AccountNotFound))
    private val posted = ActivityItem("posted-1", "Alice", Money(1_000, sek), Instant.EPOCH, ActivityStatus.Posted)

    @Test
    fun `shows the activity from the repository`() =
        runTest {
            repository.activity.value = listOf(failed, posted)

            viewModel.state.test {
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertEquals(listOf(failed, posted), state.items)
            }
        }

    @Test
    fun `retry queues the transfer again and says so`() =
        runTest {
            viewModel.state.test {
                awaitItem()

                viewModel.onIntent(ActivityIntent.Retry("failed-1"))

                assertEquals(ActivityMessage.RetryQueued, expectMostRecentItem().message)
                assertEquals(listOf("failed-1"), repository.retried)
                assertEquals(1, sync.requests)
            }
        }

    @Test
    fun `a retry the balance cannot cover says so`() =
        runTest {
            repository.retryResult = RetryResult.InsufficientFunds

            viewModel.state.test {
                awaitItem()

                viewModel.onIntent(ActivityIntent.Retry("failed-1"))

                assertEquals(ActivityMessage.RetryNeedsFunds, expectMostRecentItem().message)
            }
        }

    @Test
    fun `a double tap retries once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.retryGate = gate

            viewModel.onIntent(ActivityIntent.Retry("failed-1"))
            viewModel.onIntent(ActivityIntent.Retry("failed-1"))
            gate.complete(Unit)

            assertEquals(listOf("failed-1"), repository.retried)
        }

    @Test
    fun `nothing left to retry is not news`() =
        runTest {
            repository.retryResult = RetryResult.NothingToRetry

            viewModel.state.test {
                assertNull(awaitItem().message)

                viewModel.onIntent(ActivityIntent.Retry("failed-1"))

                expectNoEvents()
            }
            assertEquals(listOf("failed-1"), repository.retried)
        }

    @Test
    fun `dismiss hides the transfer`() =
        runTest {
            viewModel.onIntent(ActivityIntent.Dismiss("failed-1"))

            assertEquals(listOf("failed-1"), repository.dismissed)
        }

    @Test
    fun `a shown message is cleared`() =
        runTest {
            viewModel.state.test {
                awaitItem()
                viewModel.onIntent(ActivityIntent.Retry("failed-1"))
                assertEquals(ActivityMessage.RetryQueued, expectMostRecentItem().message)

                viewModel.onIntent(ActivityIntent.MessageShown)

                assertNull(expectMostRecentItem().message)
            }
        }
}
