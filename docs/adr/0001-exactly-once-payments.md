# ADR 0001: Exactly-once payments with a transactional outbox

- Status: accepted
- Date: 2026-11-04

## Context

The app must let a user send money while offline and survive airplane mode, process death
and retries without moving the money twice. The network can lose a request, lose a
response, or deliver a request more than once. A phone can't make a remote call exactly
once; it can only make it at least once.

Ledger Forge (`ledger-api`) accepts `POST /api/v1/transfers` with a required
`Idempotency-Key` header. Verified on master, 4 Nov 2026:

- It stores the idempotency record and the journal entry in one database transaction.
- A repeat with the same key and the same body returns the original receipt (201).
- The same key with a different body returns 409.
- A concurrent duplicate waits on the key's primary key and then replays the receipt.
- A 422 (insufficient funds) rolls back the whole transaction, so nothing stays recorded
  under the key.
- Every refusal is `application/problem+json`.
- Keys never expire.

## Decision

The client sends at least once and the server applies at most once, so each transfer
takes effect exactly once.

1. **Write locally first.** A send is one Room transaction: check that the available balance
   covers it, then insert an outbox row holding the exact request and a UUID idempotency key.
   The key is generated once and never regenerated. The outbox row is also the pending line
   in the activity list, because a second table for pending lines could disagree with it.
2. **Room is the source of truth for the UI.** The available balance is the posted balance
   minus every row that is `PENDING` or `SETTLED`.
3. **One unique WorkManager job syncs: push, then pull.** The same worker does both, in that
   order, with a network constraint and exponential backoff.
   - Push drains the outbox FIFO per source wallet. An unanswered row holds back that wallet's
     later rows, so its transfers post in the order they were sent.
   - A wallet with an unanswered row isn't pulled. Its debit may already be on the ledger, and
     that balance minus the pending row would count the debit twice.
4. **A row goes `PENDING` → `SETTLED` → `RECONCILED`.** A pull reads which rows are settled,
   fetches the statement and then the balance, and stores all of it in one transaction, marking
   those rows reconciled. Each of them got its 201 before the balance was read, so the balance
   includes its debit. That reasoning doesn't depend on matching statement lines, so the
   statement's 500-line cap can't strand a row.
5. **Classify responses strictly.**
   - 201, including a replay: settled. Store `transferId`.
   - 400, 404 or 422 with a `application/problem+json` body: failed. The money is released.
   - 409 with a problem body: failed, and logged as an invariant violation (the body never
     changes, so this means a client bug).
   - Anything else (5xx, timeouts, IO errors, unreadable 2xx, 4xx without a problem body such as
     captive portals or proxies): retry with the same key.
6. **A user retry after a terminal failure is a new intent with a new key.** The failed row
   stays as history.

## Consequences

- If the process dies after the server commits, the next run re-POSTs the same key, gets the
  replayed receipt and settles. Nothing moves twice. [Verification](../verification.md) shows
  this against the real server.
- While a transfer has no answer, its wallet's posted balance is stale but its available
  balance is right, and the debit shows once.
- If `POST` keeps failing while `GET` works, that wallet doesn't refresh until it gets an
  answer, so incoming money shows up late. The pending transfer is visible, and other wallets
  still refresh.
- Correctness relies on Ledger Forge keeping keys forever. Against a provider that expires keys
  (Stripe expires them after 24 hours), the outbox would need a maximum age and manual
  reconciliation for older rows.
- Force-stopping the app cancels its scheduled work until the next launch. Settlement is delayed
  but still happens exactly once.

## Alternatives rejected

- **POST directly from the ViewModel and retry in memory.** Process death loses the intent, or
  loses the key and double-posts.
- **Generate a new key per attempt.** That turns every retry into a new payment.
- **Pull before push.** After a crash, the posted debit arrives while its row is still pending.
- **Pull every wallet whatever the push did.** This was the first design, on the reasoning that
  under-reporting a balance is safe. Running it against the real server showed the payment twice
  in the activity list until the retry landed, which is exactly what this app promises not to do.
- **A WorkManager chain of a push worker and a pull worker.** While a push retries, the chain
  stops pulling every wallet. One worker keeps the order in plain sequential code.
- **A pending statement line next to the outbox row.** That's a dual write that can diverge, for
  no benefit: the row already has everything the line needs.
