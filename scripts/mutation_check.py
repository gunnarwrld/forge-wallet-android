#!/usr/bin/env python3
"""Checks that the tests can fail: introduces one bug at a time, runs the relevant tests, counts what
fails, and restores the file. Run it with a clean working tree. It exits with an error if a bug no
longer applies to the code, so the list can't quietly go stale.

    python3 scripts/mutation_check.py
"""
import glob, html, os, re, subprocess, sys

SYNC = "core/data/src/main/kotlin/dev/ledgerforge/wallet/core/data/WalletSynchronizer.kt"
REQ = "core/data/src/main/kotlin/dev/ledgerforge/wallet/core/data/WorkManagerSyncRequester.kt"
SEND = "feature/send/src/main/kotlin/dev/ledgerforge/wallet/feature/send/SendViewModel.kt"
OUTBOX = "core/database/src/main/kotlin/dev/ledgerforge/wallet/core/database/OutboxDao.kt"
CLIENT = "core/network/src/main/kotlin/dev/ledgerforge/wallet/core/network/LedgerClient.kt"
REPO = "core/data/src/main/kotlin/dev/ledgerforge/wallet/core/data/OfflineFirstWalletRepository.kt"
DB = ":core:database:testDebugUnitTest"
DATA = ":core:data:testDebugUnitTest"

MUTATIONS = [
    ("A new idempotency key on every attempt", SYNC,
     "ledger.submitTransfer(row.idempotencyKey, row.toRequest())",
     "ledger.submitTransfer(java.util.UUID.randomUUID().toString(), row.toRequest())", [DATA]),
    ("Giving up on a transient failure", SYNC,
     "unanswered += row.sourceAccountId",
     'outbox.markFailed(row.seq, 0, "gave up")', [DATA]),
    ("Never reconciling settled rows", SYNC,
     "val settled = outbox.settledSeqs(accountId)",
     "val settled = emptyList<Long>()", [DATA]),
    ("Pulling before pushing", SYNC,
     "val unanswered = push()\n            val pulled = pull(accounts.ids() - unanswered)",
     "val pulled = pull(accounts.ids())\n            val unanswered = push()", [DATA]),
    ("Pulling a wallet whose transfer has no answer", SYNC,
     "pull(accounts.ids() - unanswered)",
     "pull(accounts.ids())", [DATA]),
    ("`KEEP` instead of `APPEND_OR_REPLACE` during a run", REQ,
     "ExistingWorkPolicy.APPEND_OR_REPLACE",
     "ExistingWorkPolicy.KEEP", [DATA]),
    ("Treating any 4xx as a refusal, proxies included", CLIENT,
     "response.code() in REFUSALS && response.isLedgerProblem()",
     "response.code() in REFUSALS", [":core:network:test"]),
    ("Removing the funds check when queueing", OUTBOX,
     "if (available < row.amountMinor) return false\n",
     "\n", [DB, DATA]),
    ("Retrying a row that didn't fail", OUTBOX,
     "if (failed == null || failed.status != OutboxStatus.FAILED) return RetryOutcome.NOTHING_TO_RETRY",
     "if (failed == null) return RetryOutcome.NOTHING_TO_RETRY", [DB, DATA]),
    ("Retrying without checking funds again", OUTBOX,
     "if (!enqueueIfFunded(retry)) return RetryOutcome.INSUFFICIENT_FUNDS",
     "insert(retry)", [DB, DATA]),
    ("Retrying under the refused key", REPO,
     "newKey = UUID.randomUUID().toString()",
     "newKey = failedId", [DATA]),
    ("Removing the double-tap guard", SEND,
     "if (form.value.isSubmitting) return\n",
     "\n", [":feature:send:testDebugUnitTest"]),
]

RESULT_DIRS = {
    DATA: "core/data/build/test-results/testDebugUnitTest",
    ":core:network:test": "core/network/build/test-results/test",
    DB: "core/database/build/test-results/testDebugUnitTest",
    ":feature:send:testDebugUnitTest": "feature/send/build/test-results/testDebugUnitTest",
}

def failures(tasks):
    found = []
    for task in tasks:
        for path in glob.glob(RESULT_DIRS[task] + "/*.xml"):
            text = html.unescape(open(path, encoding="utf-8").read())
            for m in re.finditer(r'<testcase name="([^"]*)" classname="[^"]*\.([A-Za-z]+)"[^>]*>\s*<failure', text):
                found.append(f"{m.group(2)}: {m.group(1)}")
    return found

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
rows, survived = [], []
for name, path, old, new, tasks in MUTATIONS:
    source = open(path, encoding="utf-8").read()
    if old not in source:
        sys.exit(f"mutation no longer applies: {name}")
    open(path, "w", encoding="utf-8").write(source.replace(old, new))
    try:
        run = subprocess.run(["./gradlew", *tasks, "--console=plain", "--continue"], capture_output=True, text=True)
        compile_errors = [l for l in run.stdout.splitlines() + run.stderr.splitlines() if l.startswith("e: ")]
        if compile_errors:
            sys.exit(f"{name} did not compile: {compile_errors[:2]}")
        failed = failures(tasks)
    finally:
        subprocess.run(["git", "checkout", "--", path], check=True)
    rows.append((name, len(failed)))
    if not failed:
        survived.append(name)
    print(f"{len(failed):>3} failed  {name}", flush=True)
    for test in failed[:4]:
        print(f"       - {test[:105]}", flush=True)

print("\n| Bug introduced | Tests that failed |\n|---|---|")
for name, count in rows:
    print(f"| {name} | {count} |")
print("\nSURVIVED:" if survived else "\nEvery bug was caught.", *survived)
sys.exit(1 if survived else 0)
