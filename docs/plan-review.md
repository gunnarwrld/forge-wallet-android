# Plan review

Reviewed on 4 Nov 2026: *Forge Wallet: Revolut Android project plan* (the 4-week plan).
I checked its backend claims against `gunnarwrld/ledger-api` master (last push 4 Nov 2026)
and its stack against the library versions published today.

## Verdict

Keep the core. An offline-first wallet whose payments settle exactly once is the right
project: it tells a correctness story rather than a screens story, it reuses Ledger Forge,
and the backend behaviour it depends on is real (table below).

Change seven things:

1. Apply this week, not after week 2.
2. Build the payment path before the screens.
3. Take the backend PRs off the critical path.
4. Tighten the sync rules (six gaps listed below).
5. Fix week 3's overload and two inconsistencies.
6. Merge `core:sync` into `core:data`.
7. Update the stack for November 2026 (AGP 9 built-in Kotlin, Navigation 3).

## What ledger-api actually does

| Plan claim | What the code does | What it means for the client |
|---|---|---|
| Same key and body replays the receipt | `TransferTransaction.execute` writes `idempotency_records` and the journal entry in one READ COMMITTED transaction. A replay returns the stored `transferId`. | Outbox retries are safe. |
| Different body returns 409 | The request hash is SHA-256 of `source\|destination\|amount\|currency`. A mismatch throws `IdempotencyConflictException`, which maps to 409. | The outbox never changes a body, so a 409 means a client bug. Fail the row and flag it. |
| Concurrent duplicates (not in the plan) | The second insert blocks on the primary key. Once the first commits, it gets a unique violation and replays: 201. | There is no "request in progress" 409. A timeout followed by a retry is safe. |
| Insufficient funds returns 422 | The exception rolls back the whole transaction, idempotency row included. | A 422 leaves nothing under the key. It's terminal for that attempt. |
| Key lifetime (not in the plan) | No TTL, no cleanup job. | A phone offline for weeks still can't double-post. Real PSPs expire keys (Stripe after 24 hours), so the README should state this assumption. |
| Statement | Newest 500 postings, `referenceId` = `transferId`, no cursor, no idempotency key. | The client only learns `transferId` from the POST response. See "push before pull" below. |
| Local run | docker compose exposes port 8080. | The emulator reaches it at `10.0.2.2:8080`, through a debug-only cleartext config and, on API 37, the `ACCESS_LOCAL_NETWORK` permission (see "What building it changed"). |

## Changes

### 1. Apply this week

The intake is rolling, LinkedIn showed 182 applicants on 31 Oct, and recruitment runs until
December. At best the week-2 cut lands on 18 Nov. An application needs a credible repo
link, not a finished app, and whoever opens the link later sees a later state. Apply once
the vertical slice runs (target 11 Nov), or now with the repo link.

### 2. Payment path first

Week 1 as written is all horizontal: build-logic, CI, design system, Room, backend PRs and
home. The part that sets the project apart (outbox, worker, idempotency) only starts in
week 2, so any week-1 slip lands on the demo. The plan's own risk list budgets 2 to 3 days
for Gradle alone.

New order:

- **Days 1–2:** skeleton, CI, `core:money` test-first. *(Done in this commit.)*
- **Days 3–5:** the payment slice with minimal UI. Room (accounts, statement lines,
  outbox), send use case, outbox worker, `POST /transfers`, settle. Worker tests cover
  timeout then success, a dropped response, 409, 422, and a replay after process death.
  *(Done, and [verified against the real server](verification.md).)*
- **Week 2:** home, send and activity screens on the design system, the biometric gate and
  the GIF. The cut line holds, with less risk behind it.

### 3. Backend PRs off the critical path

The slice needs no ledger-api change. Seed wallets with the existing `POST /accounts` and
read the existing statement. Move the account list and cursor paging to week 2, and add
auth once the slice works.

### 4. Sync rules to add

- **Push before pull.** One unique WorkManager job drains the outbox first, then pulls the
  statement. If a pull ran first after a crash, the posted debit would arrive while its row was
  still pending. A wallet with an unanswered row isn't pulled at all, for the same reason.
- **Terminal only when Ledger Forge says so.** 400, 404, 409 and 422 are terminal only
  when the body is `application/problem+json` from Ledger Forge. Anything else (captive
  portal HTML, a proxy 4xx, an empty body) retries with the same key.
- **409 is an invariant violation**, not a user error: log it and surface it in debug
  builds.
- **A user retry after a terminal failure is a new intent with a new key.** Automatic
  retries always reuse the key.
- **FIFO per source account.** Terminal failures don't block the queue; transient ones
  do. Validate sends against the available balance (server balance minus pending debits)
  so the queue doesn't fill with future 422s.
- **"Kill the app" in the demo means swipe-away or process death.** Force stop cancels the
  app's scheduled jobs until the next launch. Settlement then waits for the user to reopen
  the app: still exactly once, just later. Say so in the README.

### 5. Week 3

- Week 3 holds three backend features (SSE, FX rates, device keys) on top of UI tests,
  screenshot tests and accessibility. Device-bound signing also appears as stretch #1.
  Make it the single week-4 stretch item, which the plan already says to pick first.
- The role-mapping table promises "two variants behind a feature flag", but `core:flags`
  is stretch. Add a local `FeatureFlags` interface in core (about an hour) and keep the
  remote `/config` endpoint as stretch.

### 6. Module graph

- Merge `core:sync` into `core:data`. The worker needs the DAOs, the API client and the
  outbox rules, which is all data-layer code. This removes the
  `data → core:sync → core:network` chain from the diagram.
- Rename `data` to `core:data` and `domain` to `core:domain`.

Result: `app`, `feature:{home,send,activity}`,
`core:{domain,money,data,database,network,designsystem,testing}` and `build-logic`, with
`benchmark` and `baselineprofile` added in week 4. Modules are created when they get real
code, not as empty shells.

### 7. Stack as of November 2026

- **AGP 9.5 has built-in Kotlin.** You no longer apply the `kotlin-android` plugin,
  `CommonExtension` has lost its generic parameters, and compiler options live in
  `kotlin { compilerOptions }`. Now in Android's AGP 8 convention plugins don't copy
  across; the ones in this repo are written for 9.5.
- **Navigation 3** (stable, 1.2.1) instead of Navigation Compose type-safe routes. The back
  stack is a list the app owns, which fits MVI and is unit-testable.
- **Biometric:** the stable `androidx.biometric` release is still 1.1.0 (1.4.0 is in beta).
  Use 1.1.0.
- **minSdk 26** gives `java.time` without desugaring. Keystore per-use auth needs an
  API 30 branch.
- **Pinned versions** are in `gradle/libs.versions.toml`: Kotlin 2.4.21, AGP 9.5.0,
  Gradle 9.8.2, Compose BOM 2026.09.00, Room 2.8.5, Hilt 2.60.1, WorkManager 2.12.0,
  OkHttp 5.5.1, Retrofit 3.0.0, Robolectric 4.17, Roborazzi 1.77.0, and the same
  ktlint-gradle (14.2.0), detekt (2.0 alpha) and Kover (0.9.11) as Ledger Forge.

## What building it changed

Four things changed once the slice ran against the real server. The reasoning is in
[ADR 0001](adr/0001-exactly-once-payments.md).

- **The outbox row is the pending line.** The plan writes a pending statement line and an outbox
  row together, but a second table could disagree with the first, so there is only the row.
- **One worker pushes then pulls,** instead of a WorkManager chain, which would stop pulling every
  wallet while one push retries.
- **A wallet with an unanswered transfer isn't pulled.** The first version pulled anyway, on the
  reasoning that under-reporting is safe. On screen, the payment showed up twice.
- **API 37 needs a permission to reach `10.0.2.2`.** The plan's "point the emulator at
  `10.0.2.2:8080`" hangs on a target SDK of 37: sockets time out with no error unless the app
  holds `ACCESS_LOCAL_NETWORK`. Debug builds now ask for it. A public server doesn't need it.

## Unchanged

The pitch, Room as the source of truth, the MVI ADR, SSE over WebSockets, FX as
display-only, Roborazzi, the metrics table with measured numbers only, the week-2 cut line,
the recruiter README and interview prep all stay.

## On Claude Code writing the code

The plan's own warning stands: Revolut will ask why. To keep that answer yours:

- Tests land in their own commit, failing, before the code that makes them pass.
- Every non-obvious choice gets an ADR in `docs/adr`.
- Commits stay small enough to review in one sitting.

Write at least one slice yourself (the send reducer is a good candidate), and practise
explaining the outbox on a blank page. If anyone asks, say you built it with an AI pair
and that you own the decisions.
