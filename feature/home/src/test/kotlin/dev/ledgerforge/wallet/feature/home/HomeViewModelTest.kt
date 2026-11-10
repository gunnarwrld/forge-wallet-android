package dev.ledgerforge.wallet.feature.home

import app.cash.turbine.test
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.Wallet
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
import kotlin.test.assertTrue

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sek = CurrencyCode.of("SEK")
    private val repository = FakeWalletRepository()
    private val sync = FakeSyncRequester()
    private val viewModel by lazy { HomeViewModel(repository, sync) }

    private val alice = Wallet(AccountId("alice"), "Alice", Money(1_000, sek), Money(600, sek))
    private val pending = ActivityItem("key", "Alice", Money(-400, sek), Instant.EPOCH, ActivityStatus.Pending)

    @Test
    fun `shows wallets and activity from the repository`() =
        runTest {
            repository.wallets.value = listOf(alice)
            repository.activity.value = listOf(pending)

            viewModel.state.test {
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertEquals(listOf(alice), state.wallets)
                assertEquals(listOf(pending), state.activity)
            }
        }

    @Test
    fun `follows changes in the database`() =
        runTest {
            viewModel.state.test {
                assertEquals(emptyList(), awaitItem().wallets)

                repository.wallets.value = listOf(alice)

                assertEquals(listOf(alice), awaitItem().wallets)
            }
        }

    @Test
    fun `refresh syncs now, cutting any backoff short`() =
        runTest {
            viewModel.onIntent(HomeIntent.Refresh)

            assertEquals(1, sync.immediateRequests)
        }

    @Test
    fun `creating demo wallets shows progress, then pulls their opening balances`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.demoWalletsGate = gate

            viewModel.state.test {
                assertEquals(DemoWalletsState.Idle, awaitItem().demoWallets)

                viewModel.onIntent(HomeIntent.CreateDemoWallets)
                assertEquals(DemoWalletsState.Creating, awaitItem().demoWallets)
                assertEquals(0, sync.requests)

                gate.complete(Unit)
                assertEquals(DemoWalletsState.Idle, awaitItem().demoWallets)
                assertEquals(1, sync.requests)
            }
        }

    @Test
    fun `a failure to create demo wallets is shown and syncs nothing`() =
        runTest {
            repository.demoWalletsResult = false

            viewModel.state.test {
                awaitItem()
                viewModel.onIntent(HomeIntent.CreateDemoWallets)

                assertEquals(DemoWalletsState.Failed, expectMostRecentItem().demoWallets)
                assertEquals(0, sync.requests)
            }
        }

    @Test
    fun `a double tap creates one set of demo wallets`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.demoWalletsGate = gate

            viewModel.onIntent(HomeIntent.CreateDemoWallets)
            viewModel.onIntent(HomeIntent.CreateDemoWallets)
            gate.complete(Unit)

            assertEquals(1, repository.demoWalletCalls)
            assertTrue(sync.requests <= 1)
        }
}
