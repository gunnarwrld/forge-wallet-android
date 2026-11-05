# Verification

Measured on 4 Nov 2026 at commit `21fce27`, on the Pixel 10 Pro emulator (Android 17, API 37)
against `ledger-api` master at `7f4fc5f`, running in Docker. The checks read Ledger Forge's Postgres
database, not the app. This is an emulator, not a physical device.

## End to end, against the real server

To re-run all of this:

```bash
scripts/verify_exactly_once.sh /path/to/ledger-api
```

| Scenario | How | Result |
|---|---|---|
| Offline send, then process death | Airplane mode on, send 12.50, home, `am kill`, airplane mode off. The app isn't reopened. | Nothing reached the ledger while offline. Android restarted the process and the transfer settled 5 s after reconnecting. 1 transfer, correct balances. |
| Process death with a request in flight | Freeze the container with `docker compose pause app`, send 5.00, `kill -9` the app, unfreeze. | 1 transfer, correct balances. |
| Commit, then lost response, then process death | [`scripts/lossy_proxy.py`](../scripts/lossy_proxy.py) drops the first response after the ledger commits. Send 7.00, then `kill -9` the app while it waits to retry. | 1 transfer, correct balances, and the debit shows once throughout. See the log below. |

The proxy's log for that last transfer:

```
attempt 1  key=9c88acb7…  201  transferId=4538a014…  response dropped
attempt 2  key=9c88acb7…  201  transferId=4538a014…  delivered
```

Same key and same body on both attempts, and the second returned the first one's `transferId`.

## Found by running it

The unit tests had the first of these down as expected behaviour, and nothing checked the second:

- **A debit counted twice during the retry window.** The screen showed the payment twice and the
  balance short by that amount until the replay landed. Fixed by not pulling a wallet with an
  unanswered transfer. See [ADR 0001](adr/0001-exactly-once-payments.md).
- **Currency codes ran into the digits** (`SEK968.50`), on the JDK and on Android.

## Automated

`ExactlyOnceChaosTest` runs 200 sends with a 40% chance of a fault on each attempt and random
process restarts. It checks that every transfer posts exactly once and in order per wallet, that
a retry never uses a new key, that no wallet can spend more than it has, and that no money is
created or destroyed. The seed is fixed, so a failure can be replayed.

To check that the tests can fail, [`scripts/mutation_check.py`](../scripts/mutation_check.py)
introduces these bugs one at a time. At commit `be55afb`, every one failed tests:

| Bug introduced | Tests that failed |
|---|---|
| A new idempotency key on every attempt | 5 |
| Giving up on a transient failure | 9 |
| Never reconciling settled rows | 8 |
| Pulling before pushing | 9 |
| Pulling a wallet whose transfer has no answer | 2 |
| `KEEP` instead of `APPEND_OR_REPLACE` during a run | 1 |
| Treating any 4xx as a refusal, proxies included | 1 |
| Removing the funds check when queueing | 2 |
| Removing the double-tap guard | 2 |

## Not verified yet

- A physical device. Doze and app standby can delay background work there.
- The on-device version of the chaos run: 200 sends with airplane toggles and kills.
- A minified release build at runtime. It builds, but nothing has exercised it.
- The Android Studio IDE. Everything so far ran through Gradle on the command line.
