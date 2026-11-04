# Forge Wallet

An offline-first Android wallet built on [Ledger Forge](https://github.com/gunnarwrld/ledger-api).
Turn on airplane mode, send money, kill the app and go back online: the transfer settles
exactly once, and Ledger Forge's statement proves it.

> **Status:** week 1. The build, CI, `core:money` and the design-system base are in place.
> Next is the payment path: Room outbox → WorkManager → `POST /transfers`.
> See the [plan review](docs/plan-review.md) for scope and order.

## How "exactly once" works

A send writes a pending statement line and an outbox row, holding the request and a UUID
idempotency key, in one Room transaction. WorkManager posts the row with the same key until
Ledger Forge answers. Ledger Forge stores the key and the transfer in one database
transaction and replays the original receipt for a repeated key. Delivery is at least once,
the effect at most once, so each transfer moves money exactly once.
[ADR 0001](docs/adr/0001-exactly-once-payments.md) covers the response rules and the
failure cases.

## Modules

| Module | Contents |
|---|---|
| `app` | Hilt root and activity; navigation arrives with the features |
| `core:money` | `Money` in `Long` minor units, ISO 4217 exponents, exact parsing, locale formatting. Pure Kotlin. |
| `core:designsystem` | Theme, spacing tokens, `AmountText` with tabular figures |
| `build-logic` | Convention plugins for AGP 9.5 (built-in Kotlin) |

Planned: `core:domain`, `core:data` (repositories, outbox, sync worker), `core:database`
(Room), `core:network` (Retrofit, OkHttp), `core:testing`, and `feature:home`, `feature:send`,
`feature:activity`.

## Build and test

You need JDK 21 or newer; Android Studio's bundled JDK works.

```bash
./gradlew assembleDebug                   # debug APK
./gradlew unitTest                        # host unit tests in every module (JVM and Robolectric)
./gradlew ktlintCheck detekt lintDebug    # static analysis, as CI runs it
```

**Stack:** Kotlin 2.4, AGP 9.5, Jetpack Compose with Material 3, Hilt, Room, WorkManager,
Retrofit and OkHttp. Tests use JUnit 4, kotest-property, Robolectric and Turbine. ktlint,
detekt and Kover match Ledger Forge.
