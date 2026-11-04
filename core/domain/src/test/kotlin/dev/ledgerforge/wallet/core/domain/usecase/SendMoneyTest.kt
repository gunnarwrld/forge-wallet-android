package dev.ledgerforge.wallet.core.domain.usecase

import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.QueueResult
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.domain.model.WalletRepository
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SendMoneyTest {
    private val sek = CurrencyCode.of("SEK")
    private val eur = CurrencyCode.of("EUR")
    private val alice = wallet("alice", sek)
    private val bob = wallet("bob", sek)

    private val repository = FakeRepository()
    private val sync = FakeSync()
    private val sendMoney = SendMoney(repository, sync)

    @Test
    fun `queues the transfer and asks for a sync`() =
        runTest {
            val result = sendMoney(alice, bob, Money(1_250, sek))

            assertEquals(SendResult.Queued, result)
            assertEquals(listOf(Queued(alice.id, bob.id, Money(1_250, sek))), repository.queued)
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
            repository.nextResult = QueueResult.InsufficientFunds

            assertEquals(SendResult.InsufficientFunds, sendMoney(alice, bob, Money(100, sek)))
            assertEquals(0, sync.requests)
        }

    private fun wallet(
        id: String,
        currency: CurrencyCode,
    ) = Wallet(AccountId(id), id, Money(10_000, currency), Money(10_000, currency))

    private data class Queued(
        val source: AccountId,
        val destination: AccountId,
        val amount: Money,
    )

    private class FakeRepository : WalletRepository {
        val queued = mutableListOf<Queued>()
        var nextResult = QueueResult.Queued

        override fun observeWallets(): Flow<List<Wallet>> = flowOf(emptyList())

        override fun observeActivity(): Flow<List<ActivityItem>> = flowOf(emptyList())

        override suspend fun queueTransfer(
            source: AccountId,
            destination: AccountId,
            amount: Money,
        ): QueueResult {
            if (nextResult == QueueResult.Queued) queued += Queued(source, destination, amount)
            return nextResult
        }

        override suspend fun createDemoWallets() = true
    }

    private class FakeSync : SyncRequester {
        var requests = 0

        override suspend fun requestSync() {
            requests++
        }

        override suspend fun syncNow() = requestSync()
    }
}
