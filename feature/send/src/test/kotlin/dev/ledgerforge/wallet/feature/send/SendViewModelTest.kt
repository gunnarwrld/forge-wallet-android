package dev.ledgerforge.wallet.feature.send

import app.cash.turbine.test
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.usecase.SendMoney
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeSyncRequester
import dev.ledgerforge.wallet.core.testing.FakeWalletRepository
import dev.ledgerforge.wallet.core.testing.MainDispatcherRule
import dev.ledgerforge.wallet.core.testing.QueuedTransfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SendViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sek = CurrencyCode.of("SEK")
    private val alice = wallet("alice", "Alice")
    private val bob = wallet("bob", "Bob")
    private val carol = wallet("carol", "Carol")

    private val repository = FakeWalletRepository()
    private val sync = FakeSyncRequester()
    private val viewModel by lazy { SendViewModel(repository, SendMoney(repository, sync)) }

    @Before
    fun setUp() {
        repository.wallets.value = listOf(alice, bob, carol)
    }

    private suspend fun currentState() = viewModel.state.first { it.wallets.isNotEmpty() }

    @Test
    fun `starts with two different wallets selected`() =
        runTest {
            val state = currentState()

            assertEquals(alice, state.source)
            assertEquals(bob, state.destination)
            assertEquals("", state.amountText)
            assertNull(state.error)
        }

    @Test
    fun `selecting wallets and typing updates the form`() =
        runTest {
            viewModel.state.test {
                awaitItem()
                viewModel.onIntent(SendIntent.SelectSource(carol.id))
                viewModel.onIntent(SendIntent.SelectDestination(alice.id))
                viewModel.onIntent(SendIntent.EditAmount("12.50"))

                val state = expectMostRecentItem()
                assertEquals(carol, state.source)
                assertEquals(alice, state.destination)
                assertEquals("12.50", state.amountText)
            }
        }

    @Test
    fun `submitting parses the amount exactly and queues it`() =
        runTest {
            viewModel.effects.test {
                viewModel.onIntent(SendIntent.EditAmount("12,50"))
                viewModel.onIntent(SendIntent.Submit)

                assertEquals(SendEffect.Queued, awaitItem())
                assertEquals(listOf(QueuedTransfer(alice.id, bob.id, Money(1_250, sek))), repository.queued)
                assertEquals(1, sync.requests)
            }
        }

    @Test
    fun `a double tap queues one transfer`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.queueGate = gate
            viewModel.onIntent(SendIntent.EditAmount("10"))

            viewModel.onIntent(SendIntent.Submit)
            viewModel.onIntent(SendIntent.Submit)
            gate.complete(Unit)

            assertEquals(1, repository.queued.size)
        }

    @Test
    fun `tapping again after the transfer is queued does not queue another`() =
        runTest {
            viewModel.onIntent(SendIntent.EditAmount("10"))

            viewModel.onIntent(SendIntent.Submit)
            viewModel.onIntent(SendIntent.Submit)

            assertEquals(1, repository.queued.size)
        }

    @Test
    fun `input it cannot read exactly is an error and queues nothing`() =
        runTest {
            val cases =
                mapOf(
                    "" to SendError.AmountEmpty,
                    "12a" to SendError.AmountInvalid,
                    "1,000.50" to SendError.AmountInvalid,
                    "12.505" to SendError.TooManyDecimals,
                    "99999999999999999999" to SendError.AmountTooLarge,
                )
            viewModel.state.test {
                awaitItem()
                cases.forEach { (input, expected) ->
                    viewModel.onIntent(SendIntent.EditAmount(input))
                    viewModel.onIntent(SendIntent.Submit)

                    assertEquals(expected, expectMostRecentItem().error, "for '$input'")
                }
            }
            assertEquals(emptyList(), repository.queued)
        }

    @Test
    fun `refusals from the use case are errors the user can fix`() =
        runTest {
            viewModel.state.test {
                awaitItem()

                viewModel.onIntent(SendIntent.EditAmount("0"))
                viewModel.onIntent(SendIntent.Submit)
                assertEquals(SendError.AmountNotPositive, expectMostRecentItem().error)

                viewModel.onIntent(SendIntent.EditAmount("10"))
                viewModel.onIntent(SendIntent.SelectDestination(alice.id))
                viewModel.onIntent(SendIntent.Submit)
                assertEquals(SendError.SameWallet, expectMostRecentItem().error)

                viewModel.onIntent(SendIntent.SelectDestination(bob.id))
                repository.nextQueueResult = QueueResult.InsufficientFunds
                viewModel.onIntent(SendIntent.Submit)
                val state = expectMostRecentItem()
                assertEquals(SendError.InsufficientFunds, state.error)
                assertEquals(false, state.isSubmitting)
            }
        }

    @Test
    fun `editing the amount clears the error`() =
        runTest {
            viewModel.state.test {
                awaitItem()
                viewModel.onIntent(SendIntent.Submit)
                assertEquals(SendError.AmountEmpty, expectMostRecentItem().error)

                viewModel.onIntent(SendIntent.EditAmount("1"))
                assertNull(expectMostRecentItem().error)
            }
        }

    @Test
    fun `no effect fires until the transfer is safely queued`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            repository.queueGate = gate
            viewModel.onIntent(SendIntent.EditAmount("10"))

            viewModel.effects.test {
                viewModel.onIntent(SendIntent.Submit)
                expectNoEvents()
                assertTrue(repository.queued.isEmpty())

                gate.complete(Unit)
                assertEquals(SendEffect.Queued, awaitItem())
            }
        }

    private fun wallet(
        id: String,
        name: String,
    ) = Wallet(AccountId(id), name, Money(100_000, sek), Money(100_000, sek))
}
