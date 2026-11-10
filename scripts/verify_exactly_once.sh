#!/usr/bin/env bash
# Runs the exactly-once scenarios on a running emulator against a real Ledger Forge, and checks the
# results in Ledger Forge's own database. Exits non-zero if any check fails.
#
#   scripts/verify_exactly_once.sh /path/to/ledger-api
#
# It starts Ledger Forge with docker compose and leaves it running. Stop it, and delete its test
# data, with `docker compose down -v`. It replaces the installed app and its data.
set -euo pipefail

LEDGER_DIR="$(cd "${1:?usage: $0 /path/to/ledger-api}" && pwd)"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
UI="$REPO/scripts/ui.py"
APP=dev.ledgerforge.wallet
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
ATTEMPTS_LOG="$(mktemp)"
PROXY_PID=""
FAILED=0

compose() { (cd "$LEDGER_DIR" && docker compose "$@"); }

cleanup() {
    [ -n "$PROXY_PID" ] && kill "$PROXY_PID" 2>/dev/null || true
    compose unpause app >/dev/null 2>&1 || true
    "$ADB" shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
}
trap cleanup EXIT

sql() { compose exec -T postgres psql -U ledger -d ledger -tA -c "$1"; }
transfers() { sql "select count(*) from journal_entries where entry_type = 'TRANSFER'"; }
balance() { sql "select balance_minor from accounts where id = '$1'"; }
latest_wallet() { sql "select id from accounts where name = '$1' order by created_at desc limit 1"; }

expect() {
    if [ "$2" = "$3" ]; then
        echo "  PASS  $1"
    else
        echo "  FAIL  $1: expected $2, got $3"
        FAILED=1
    fi
}

# Waits up to $1 seconds for the ledger to hold more than $2 transfers. Prints the seconds it took.
wait_for_transfer_after() {
    local start=$SECONDS
    while [ "$(transfers)" = "$2" ] && [ $((SECONDS - start)) -lt "$1" ]; do sleep 1; done
    echo $((SECONDS - start))
}

open_app() {
    "$ADB" shell am start -W -n "$APP/.MainActivity" >/dev/null
    "$UI" wait "Activity" 30
}

send() {
    "$UI" tap "Send money"
    "$UI" wait "Amount" 10
    "$UI" field
    "$ADB" shell input text "$1"
    sleep 1
    "$UI" tap "Send"
    "$UI" wait "Pending" 10
}

kill_nine() { "$ADB" shell "run-as $APP kill -9 $("$ADB" shell pidof "$APP")"; }

echo "== Setup"
"$ADB" wait-for-device
"$ADB" shell 'until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 1; done'
"$ADB" shell cmd connectivity airplane-mode disable
compose up -d --build --wait >/dev/null 2>&1
"$ADB" uninstall "$APP" >/dev/null 2>&1 || true
(cd "$REPO" && ./gradlew assembleDebug --console=plain -q)
"$ADB" install -g "$APK" >/dev/null
"$ADB" shell am start -W -n "$APP/.MainActivity" >/dev/null
"$UI" wait "Create demo wallets" 30
"$UI" tap "Create demo wallets"
"$UI" wait "Activity" 30
sleep 3
ALICE="$(latest_wallet Alice)"
BOB="$(latest_wallet Bob)"
expect "demo wallets exist on the ledger" "100000 25000" "$(balance "$ALICE") $(balance "$BOB")"

echo "== 1. Offline send, process death, app not reopened"
before="$(transfers)"
"$ADB" shell cmd connectivity airplane-mode enable
sleep 4
send 12.50
expect "nothing reaches the ledger while offline" "$before" "$(transfers)"
"$ADB" shell input keyevent HOME
sleep 2
"$ADB" shell am kill "$APP"
sleep 1
expect "the process is gone" "" "$("$ADB" shell pidof "$APP" || true)"
"$ADB" shell cmd connectivity airplane-mode disable
took="$(wait_for_transfer_after 180 "$before")"
echo "  settled ${took}s after going back online, with the app not reopened"
expect "exactly one transfer" "$((before + 1))" "$(transfers)"
expect "balances moved once" "98750 26250" "$(balance "$ALICE") $(balance "$BOB")"
open_app

echo "== 2. Process death with the request in flight"
before="$(transfers)"
compose pause app >/dev/null 2>&1
send 5
sleep 4
kill_nine
sleep 1
compose unpause app >/dev/null 2>&1
open_app
"$UI" gone "Pending" 120
expect "exactly one transfer" "$((before + 1))" "$(transfers)"
expect "balances moved once" "98250 26750" "$(balance "$ALICE") $(balance "$BOB")"

echo "== 3. Committed, response lost, then process death"
(cd "$REPO" && ./gradlew assembleDebug -PledgerBaseUrl=http://10.0.2.2:8081/ --console=plain -q)
python3 "$REPO/scripts/lossy_proxy.py" "$ATTEMPTS_LOG" &
PROXY_PID=$!
sleep 1
"$ADB" install -r "$APK" >/dev/null
open_app
sleep 3
before="$(transfers)"
send 7
wait_for_transfer_after 30 "$before" >/dev/null
sleep 2
expect "the ledger committed" "$((before + 1))" "$(transfers)"
expect "the app has no receipt yet, and shows the debit once" "1" "$("$UI" debits 7.00)"
"$UI" wait "Pending" 5
kill_nine
sleep 2
open_app
"$UI" gone "Pending" 120
expect "still exactly one transfer" "$((before + 1))" "$(transfers)"
expect "balances moved once" "97550 27450" "$(balance "$ALICE") $(balance "$BOB")"
expect "the debit shows once after the replay" "1" "$("$UI" debits 7.00)"
python3 - "$ATTEMPTS_LOG" <<'PY'
import json
import sys

rows = [json.loads(line) for line in open(sys.argv[1])]
for row in rows:
    print(f"  attempt {row['attempt']}  key={row['key'][:8]}…  {row['status']}  transferId={row['transferId'][:8]}…  "
          f"{'response dropped' if row['responseDropped'] else 'delivered'}")
checks = {
    "more than one attempt": len(rows) > 1,
    "the same key on every attempt": len({r["key"] for r in rows}) == 1,
    "the same body on every attempt": len({r["body"] for r in rows}) == 1,
    "the same transfer id on every attempt": len({r["transferId"] for r in rows}) == 1,
}
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
sys.exit(0 if all(checks.values()) else 1)
PY
[ $? -eq 0 ] || FAILED=1

CRASHES="$("$ADB" logcat -d -b crash | grep -c FATAL || true)"
expect "no crashes" "0" "$CRASHES"

kill "$PROXY_PID" 2>/dev/null || true
PROXY_PID=""
(cd "$REPO" && ./gradlew assembleDebug --console=plain -q)

if [ "$FAILED" -eq 0 ]; then echo "== ALL CHECKS PASSED"; else echo "== SOME CHECKS FAILED"; fi
exit "$FAILED"
