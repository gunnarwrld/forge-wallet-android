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
- Keys never expire.

## Decision

The client sends at least once and the server applies at most once, so each transfer
takes effect exactly once.

1. **Write locally first.** A send writes a pending statement line and an outbox row in a
   single Room transaction. The outbox row holds the exact request body and a UUID
   idempotency key generated once, at insert, and never regenerated.
2. **Room is the source of truth for the UI.** The pending line shows at once, and the
   available balance is the server balance minus pending debits.
3. **One unique WorkManager chain syncs: push, then pull.** It drains the outbox FIFO per
   source account, with a network constraint and exponential backoff, then pulls the
   statement. Push goes first because pending lines are matched to posted lines by
   `transferId`, which only the POST response reveals.
4. **Classify responses strictly.**
   - 201, including a replay: settled. Store `transferId`.
   - 400, 404 or 422 with a Ledger Forge `application/problem+json` body: failed. Reverse
     the pending line.
   - 409 with a problem body: failed, and logged as an invariant violation (the body
     never changes, so this means a client bug).
   - Anything else (5xx, timeouts, IO errors, non-problem 4xx such as captive portals or
     proxies): retry with the same key.
5. **A user retry after a terminal failure is a new intent with a new key.** The failed row
   stays as history.

## Consequences

- If the process dies after the server commits, the next run re-POSTs the same key, gets
  the replayed receipt and settles. Nothing moves twice.
- If a push fails transiently while a pull succeeds, the available balance can briefly
  under-report. It never over-reports, which is the safe direction for a wallet.
- Correctness relies on Ledger Forge keeping keys forever. Against a provider that expires
  keys (Stripe expires them after 24 hours), the outbox would need a maximum age and
  manual reconciliation for older rows.
- Force-stopping the app cancels its scheduled work until the next launch. Settlement is
  delayed but still happens exactly once.

## Alternatives rejected

- **POST directly from the ViewModel and retry in memory.** Process death loses the
  intent, or loses the key and double-posts.
- **Generate a new key per attempt.** That turns every retry into a new payment.
- **Pull before push.** After a crash, the posted debit can't be matched to its pending
  row and counts twice.
