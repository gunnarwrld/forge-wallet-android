package dev.ledgerforge.wallet.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import java.time.Instant
import java.util.UUID

/**
 * Ledger Forge in memory, served through MockWebServer. It follows ledger-api's rules: a repeated
 * key replays the original receipt, the same key with a different body is a 409, a 422 leaves
 * nothing recorded under the key, and refusals are `application/problem+json`.
 *
 * Unlike the real thing, it can also fail the way networks fail. See [Fault].
 */
class FakeLedger : Dispatcher() {
    enum class Fault {
        /** The connection dies before the ledger does anything. */
        DropRequest,

        /** The ledger commits, then the response is lost. This is the case that double-posts. */
        DropResponse,

        /** A 503 before anything is committed. */
        ServerError,

        /** A captive portal answers instead of the ledger. */
        CaptivePortal,
    }

    data class Attempt(
        val idempotencyKey: String,
        val body: String,
    )

    data class Transfer(
        val idempotencyKey: String,
        val transferId: String,
        val source: String,
        val destination: String,
        val amountMinor: Long,
    )

    private class Account(
        val id: String,
        val name: String,
        val currency: String,
        var balanceMinor: Long,
    )

    private class Receipt(
        val requestHash: String,
        val transferId: String,
    )

    private val accounts = linkedMapOf<String, Account>()
    private val statements = mutableMapOf<String, MutableList<JsonObject>>()
    private val receipts = mutableMapOf<String, Receipt>()
    private val postedTransfers = mutableListOf<Transfer>()
    private val transferAttempts = mutableListOf<Attempt>()
    private val requests = mutableListOf<String>()
    private var clockSeconds = 1_800_000_000L

    /** Decides, for each transfer attempt, whether it fails. Takes the source account id. */
    var transferFaults: (String) -> Fault? = { null }

    /** Makes every GET answer 503. */
    var readsFail = false

    /** Runs while a transfer attempt is being handled, to simulate something happening meanwhile. */
    var onTransferAttempt: () -> Unit = {}

    /** Transfers the ledger applied, in commit order. */
    val transfers: List<Transfer> get() = synchronized(this) { postedTransfers.toList() }

    /** Every transfer request that reached the server, including replays and faulted ones. */
    val attempts: List<Attempt> get() = synchronized(this) { transferAttempts.toList() }

    /** Every request, as "METHOD /path", in the order the server received them. */
    val requestLog: List<String> get() = synchronized(this) { requests.toList() }

    fun addAccount(
        id: String,
        balanceMinor: Long,
        currency: String = "SEK",
        name: String = id,
    ): Unit =
        synchronized(this) {
            accounts[id] = Account(id, name, currency, balanceMinor)
            statements[id] = mutableListOf()
            if (balanceMinor > 0) {
                postLine(id, UUID.randomUUID().toString(), "OPENING_BALANCE", null, balanceMinor, currency, nextInstant())
            }
        }

    fun balanceOf(id: String): Long = synchronized(this) { accounts.getValue(id).balanceMinor }

    fun setBalance(
        id: String,
        balanceMinor: Long,
    ): Unit = synchronized(this) { accounts.getValue(id).balanceMinor = balanceMinor }

    /** Records [key] as used by some other request, so sending it again is a 409. */
    fun useKeyElsewhere(key: String): Unit = synchronized(this) { receipts[key] = Receipt("another request", "another transfer") }

    override fun dispatch(request: RecordedRequest): MockResponse =
        synchronized(this) {
            val path = request.url.encodedPath
            requests += "${request.method} $path"
            when {
                request.method == "POST" && path == "/api/v1/transfers" -> handleTransfer(request)
                request.method == "POST" && path == "/api/v1/accounts" -> handleCreateAccount(request)
                readsFail -> problem(503, "Service Unavailable")
                request.method == "GET" && path.endsWith("/statement") -> handleStatement(path.split("/")[4])
                request.method == "GET" -> handleAccount(path.split("/")[4])
                else -> problem(405, "Method Not Allowed")
            }
        }

    private fun handleTransfer(request: RecordedRequest): MockResponse {
        val key = request.headers["Idempotency-Key"] ?: return problem(400, "Missing Idempotency-Key")
        val bodyText = request.body!!.utf8()
        val body = Json.parseToJsonElement(bodyText).jsonObject
        transferAttempts += Attempt(key, bodyText)
        onTransferAttempt()

        return when (transferFaults(body.text("sourceAccountId"))) {
            Fault.DropRequest -> {
                droppedConnection()
            }

            Fault.ServerError -> {
                problem(503, "Service Unavailable")
            }

            Fault.CaptivePortal -> {
                captivePortal()
            }

            Fault.DropResponse -> {
                applyTransfer(key, body)
                droppedConnection()
            }

            null -> {
                applyTransfer(key, body)
            }
        }
    }

    private fun applyTransfer(
        key: String,
        body: JsonObject,
    ): MockResponse {
        val source = body.text("sourceAccountId")
        val destination = body.text("destinationAccountId")
        val amount = body.getValue("amountMinor").jsonPrimitive.long
        val currency = body.text("currencyCode")
        val hash = "$source|$destination|$amount|$currency"

        receipts[key]?.let { existing ->
            return if (existing.requestHash == hash) {
                receiptResponse(existing.transferId)
            } else {
                problem(409, "This idempotency key was already used for a different request")
            }
        }

        val from = accounts[source] ?: return problem(404, "Account $source was not found")
        val to = accounts[destination] ?: return problem(404, "Account $destination was not found")
        if (from.currency != currency || to.currency != currency) {
            return problem(400, "Transfer currency must match both account currencies")
        }
        if (from.balanceMinor < amount) return problem(422, "The source account has insufficient funds")

        val transferId = UUID.randomUUID().toString()
        val journalEntryId = UUID.randomUUID().toString()
        val at = nextInstant()
        from.balanceMinor -= amount
        to.balanceMinor += amount
        postLine(source, journalEntryId, "TRANSFER", transferId, -amount, currency, at)
        postLine(destination, journalEntryId, "TRANSFER", transferId, amount, currency, at)
        receipts[key] = Receipt(hash, transferId)
        postedTransfers += Transfer(key, transferId, source, destination, amount)
        return receiptResponse(transferId)
    }

    private fun handleCreateAccount(request: RecordedRequest): MockResponse {
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        val id = UUID.randomUUID().toString()
        addAccount(id, body.getValue("openingBalanceMinor").jsonPrimitive.long, body.text("currencyCode"), body.text("name"))
        return json(201, accountJson(accounts.getValue(id)).toString())
    }

    private fun handleAccount(id: String): MockResponse {
        val account = accounts[id] ?: return problem(404, "Account $id was not found")
        return json(200, accountJson(account).toString())
    }

    private fun handleStatement(id: String): MockResponse {
        val lines = statements[id] ?: return problem(404, "Account $id was not found")
        return json(200, buildJsonArray { lines.forEach { add(it) } }.toString())
    }

    private fun postLine(
        accountId: String,
        journalEntryId: String,
        entryType: String,
        referenceId: String?,
        signedAmountMinor: Long,
        currency: String,
        at: Instant,
    ) {
        statements.getValue(accountId) +=
            buildJsonObject {
                put("journalEntryId", journalEntryId)
                put("entryType", entryType)
                put("referenceId", referenceId)
                put("postedAt", at.toString())
                put("side", if (signedAmountMinor < 0) "DEBIT" else "CREDIT")
                put("amountMinor", kotlin.math.abs(signedAmountMinor))
                put("signedAmountMinor", signedAmountMinor)
                put("currencyCode", currency)
            }
    }

    private fun accountJson(account: Account) =
        buildJsonObject {
            put("id", account.id)
            put("name", account.name)
            put("currencyCode", account.currency)
            put("kind", "CUSTOMER_WALLET")
            put("balanceMinor", account.balanceMinor)
            put("createdAt", Instant.ofEpochSecond(clockSeconds).toString())
        }

    private fun receiptResponse(transferId: String) =
        json(
            201,
            buildJsonObject {
                put("transferId", transferId)
                put("journalEntryId", UUID.randomUUID().toString())
            }.toString(),
        )

    private fun nextInstant(): Instant = Instant.ofEpochSecond(++clockSeconds)

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

    private fun json(
        status: Int,
        body: String,
    ) = MockResponse
        .Builder()
        .code(status)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun problem(
        status: Int,
        detail: String,
    ) = MockResponse
        .Builder()
        .code(status)
        .addHeader("Content-Type", "application/problem+json")
        .body(
            buildJsonObject {
                put("type", "about:blank")
                put("status", status)
                put("detail", detail)
            }.toString(),
        ).build()

    private fun captivePortal() =
        MockResponse
            .Builder()
            .code(403)
            .addHeader("Content-Type", "text/html; charset=utf-8")
            .body("<html>Sign in to the Wi-Fi</html>")
            .build()

    private fun droppedConnection() =
        MockResponse
            .Builder()
            .onResponseStart(SocketEffect.ShutdownConnection)
            .build()
}
