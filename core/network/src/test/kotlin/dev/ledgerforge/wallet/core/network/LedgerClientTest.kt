package dev.ledgerforge.wallet.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LedgerClientTest {
    private val server = MockWebServer()
    private lateinit var client: LedgerClient

    private val request = TransferRequestDto("alice", "bob", 1_250, "SEK")

    @Before
    fun setUp() {
        server.start()
        val http =
            OkHttpClient
                .Builder()
                .readTimeout(300, TimeUnit.MILLISECONDS)
                .build()
        client = LedgerClient(LedgerApiFactory.create(server.url("/").toString(), http))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `sends the key and the exact body`() =
        runTest {
            server.enqueue(receipt())

            client.submitTransfer("key-1", request)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/transfers", recorded.url.encodedPath)
            assertEquals("key-1", recorded.headers["Idempotency-Key"])
            val body = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
            assertEquals("alice", body["sourceAccountId"]?.jsonPrimitive?.content)
            assertEquals("bob", body["destinationAccountId"]?.jsonPrimitive?.content)
            assertEquals("1250", body["amountMinor"]?.jsonPrimitive?.content)
            assertEquals("SEK", body["currencyCode"]?.jsonPrimitive?.content)
        }

    @Test
    fun `a 201 settles with the receipt's transfer id`() =
        runTest {
            server.enqueue(receipt(transferId = "transfer-9"))

            assertEquals(TransferOutcome.Settled("transfer-9"), client.submitTransfer("key-1", request))
        }

    @Test
    fun `Ledger Forge's refusals are terminal and keep their detail`() =
        runTest {
            listOf(400, 404, 409, 422).forEach { status ->
                server.enqueue(problem(status, "refused with $status"))

                assertEquals(
                    TransferOutcome.Rejected(status, "refused with $status"),
                    client.submitTransfer("key-1", request),
                )
            }
        }

    @Test
    fun `a refusal with an unreadable problem body is still terminal`() =
        runTest {
            server.enqueue(problem(422, detail = null, rawBody = "not json"))

            assertEquals(TransferOutcome.Rejected(422, null), client.submitTransfer("key-1", request))
        }

    @Test
    fun `server errors retry, even in Ledger Forge's own format`() =
        runTest {
            listOf(500, 502, 503, 504).forEach { status ->
                server.enqueue(problem(status, "try later"))
                assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
            }
        }

    @Test
    fun `other statuses in Ledger Forge's format retry`() =
        runTest {
            listOf(401, 403, 408, 429).forEach { status ->
                server.enqueue(problem(status, "not final"))
                assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
            }
        }

    @Test
    fun `4xx that did not come from Ledger Forge retry`() =
        runTest {
            // A captive portal, a proxy, or a gateway. None of them can say what the ledger did.
            server.enqueue(html(403))
            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
            server.enqueue(html(404))
            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(422)
                    .addHeader("Content-Type", "application/json")
                    .body("{}")
                    .build(),
            )
            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
        }

    @Test
    fun `a 200 from anything else retries`() =
        runTest {
            server.enqueue(html(200))

            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
        }

    @Test
    fun `a 201 with an unreadable body retries`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(201)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"transferId\":")
                    .build(),
            )

            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
        }

    @Test
    fun `a connection dropped before the response retries`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.ShutdownConnection)
                    .build(),
            )

            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
        }

    @Test
    fun `a timeout retries`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .onRequestStart(SocketEffect.Stall)
                    .build(),
            )

            assertEquals(TransferOutcome.RetryLater, client.submitTransfer("key-1", request))
        }

    @Test
    fun `cancellation is not mistaken for a retry`() =
        runBlocking<Unit> {
            server.enqueue(
                MockResponse
                    .Builder()
                    .onRequestStart(SocketEffect.Stall)
                    .build(),
            )
            val started = CompletableDeferred<Unit>()

            val call =
                async(Dispatchers.Default) {
                    started.complete(Unit)
                    client.submitTransfer("key-1", request)
                }
            started.await()
            call.cancel()

            assertFailsWith<CancellationException> { call.await() }
        }

    @Test
    fun `a snapshot reads the statement before the balance`() =
        runTest {
            server.enqueue(json("""[$OPENING_LINE]"""))
            server.enqueue(json(ALICE))

            val outcome = client.fetchSnapshot("alice")

            assertEquals("/api/v1/accounts/alice/statement", server.takeRequest().url.encodedPath)
            assertEquals("/api/v1/accounts/alice", server.takeRequest().url.encodedPath)
            val fetched = outcome as SnapshotOutcome.Fetched
            assertEquals(AccountDto("alice", "Alice", "SEK", 900), fetched.account)
            assertEquals(
                listOf(StatementLineDto("journal-1", "OPENING_BALANCE", null, "2026-11-04T21:11:13.477475Z", 1_000, "SEK")),
                fetched.statement,
            )
        }

    @Test
    fun `a snapshot of an account Ledger Forge does not know is gone`() =
        runTest {
            server.enqueue(problem(404, "not found"))

            assertEquals(SnapshotOutcome.AccountGone, client.fetchSnapshot("ghost"))
        }

    @Test
    fun `a snapshot retries on server errors, proxies and bad bodies`() =
        runTest {
            server.enqueue(problem(503, "try later"))
            assertEquals(SnapshotOutcome.RetryLater, client.fetchSnapshot("alice"))

            server.enqueue(html(404))
            assertEquals(SnapshotOutcome.RetryLater, client.fetchSnapshot("alice"))

            server.enqueue(json("""[$OPENING_LINE]"""))
            server.enqueue(problem(500, "balance lookup failed"))
            assertEquals(SnapshotOutcome.RetryLater, client.fetchSnapshot("alice"))

            server.enqueue(json("not json"))
            assertEquals(SnapshotOutcome.RetryLater, client.fetchSnapshot("alice"))
        }

    @Test
    fun `creating an account returns it, or null on failure`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(201)
                    .addHeader("Content-Type", "application/json")
                    .body(ALICE)
                    .build(),
            )
            assertEquals(AccountDto("alice", "Alice", "SEK", 900), client.createAccount("Alice", "SEK", 900))

            server.enqueue(problem(400, "bad request"))
            assertNull(client.createAccount("Alice", "SEK", -1))

            server.enqueue(
                MockResponse
                    .Builder()
                    .onResponseStart(SocketEffect.ShutdownConnection)
                    .build(),
            )
            assertNull(client.createAccount("Alice", "SEK", 900))
        }

    private fun receipt(transferId: String = "transfer-1") =
        MockResponse
            .Builder()
            .code(201)
            .addHeader("Content-Type", "application/json")
            .body("""{"transferId":"$transferId","journalEntryId":"journal-1","amountMinor":1250}""")
            .build()

    private fun problem(
        status: Int,
        detail: String?,
        rawBody: String = """{"type":"about:blank","title":"Refused","status":$status,"detail":"$detail"}""",
    ) = MockResponse
        .Builder()
        .code(status)
        .addHeader("Content-Type", "application/problem+json")
        .body(rawBody)
        .build()

    private fun html(status: Int) =
        MockResponse
            .Builder()
            .code(status)
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html>Sign in to the Wi-Fi</html>")
            .build()

    private fun json(body: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private companion object {
        const val ALICE =
            """{"id":"alice","name":"Alice","currencyCode":"SEK","kind":"CUSTOMER_WALLET",""" +
                """"balanceMinor":900,"createdAt":"2026-11-04T21:11:13.249224Z"}"""
        const val OPENING_LINE =
            """{"journalEntryId":"journal-1","entryType":"OPENING_BALANCE","referenceId":null,"postedAt":"2026-11-04T21:11:13.477475Z",""" +
                """"side":"CREDIT","amountMinor":1000,"signedAmountMinor":1000,"currencyCode":"SEK"}"""
    }
}
