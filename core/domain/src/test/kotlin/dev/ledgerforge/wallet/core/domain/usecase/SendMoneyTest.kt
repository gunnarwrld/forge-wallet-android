package dev.ledgerforge.wallet.core.domain.usecase

import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.testing.FakeSyncRequester
import dev.ledgerforge.wallet.core.testing.FakeWalletRepository
import dev.ledgerforge.wallet.core.testing.QueuedTransfer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SendMoneyTest {
    private val sek = CurrencyCode.of("SEK")
    private val eur = CurrencyCode.of("EUR")
    private val alice = wallet("alice", sek)
    private val bob = wallet("bob", sek)

    private val repository = FakeWalletRepository()
    private val sync = FakeSyncRequester()
    private val sendMoney = SendMoney(repository, sync)

    @Test
    fun `queues the transfer and asks for a sync`() =
        runTest {
            val result = sendMoney(alice, bob, Money(1_250, sek))

            assertEquals(SendResult.Queued, result)
            assertEquals(listOf(QueuedTransfer(alice.id, bob.id, Money(1_250, sek))), repository.queued)
            assertEquals(1, sync.requests)
        }

    @Test
    fun `refuses zero and negative amounts without queueing`() =
        runTest {
            assertEquals(SendResult.AmountNotPositive, sendMoney(alice, bob, Money(0, sek)))
            assertEquals(SendResult.AmountNotPositive, sendMoney(alice, bob, Money(-1, sek)))
            assertEquals(emptyList(), repository.queued)
            assertEquals(0, sync.requests)
        }

    @Test
    fun `refuses sending to the same wallet`() =
        runTest {
            assertEquals(SendResult.SameWallet, sendMoney(alice, alice, Money(100, sek)))
            assertEquals(emptyList(), repository.queued)
        }

    @Test
    fun `refuses a currency that does not match both wallets`() =
        runTest {
            val euroWallet = wallet("euro", eur)

            assertEquals(SendResult.CurrencyMismatch, sendMoney(alice, bob, Money(100, eur)))
            assertEquals(SendResult.CurrencyMismatch, sendMoney(alice, euroWallet, Money(100, sek)))
            assertEquals(emptyList(), repository.queued)
        }

    @Test
    fun `reports insufficient funds from the queue and does not sync`() =
        runTest {
            repository.nextQueueResult = QueueResult.InsufficientFunds

            assertEquals(SendResult.InsufficientFunds, sendMoney(alice, bob, Money(100, sek)))
            assertEquals(0, sync.requests)
        }

    private fun wallet(
        id: String,
        currency: CurrencyCode,
    ) = Wallet(AccountId(id), id, Money(10_000, currency), Money(10_000, currency))
}
