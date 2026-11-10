#!/usr/bin/env python3
"""Drives the emulator through the accessibility tree, for scripts/verify_exactly_once.sh.

    ui.py texts               everything on screen
    ui.py tap LABEL           tap the node with exactly this text
    ui.py field               tap the first text field
    ui.py wait TEXT [SECS]    wait until TEXT is on screen
    ui.py gone TEXT [SECS]    wait until TEXT is off screen
    ui.py debits AMOUNT       count rows showing a debit of AMOUNT, for example 7.00
"""
import html
import os
import re
import subprocess
import sys
import time

ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")), "platform-tools", "adb")


def nodes():
    out = subprocess.run([ADB, "exec-out", "uiautomator", "dump", "/dev/tty"], capture_output=True, text=True).stdout
    found = []
    for match in re.finditer(r"<node [^>]*>", out):
        node = match.group(0)
        text = html.unescape(re.search(r' text="([^"]*)"', node).group(1)).replace(" ", " ")
        cls = re.search(r' class="([^"]*)"', node).group(1)
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        found.append((text, cls, (x1 + x2) // 2, (y1 + y2) // 2))
    return found


def on_screen():
    return " | ".join(text for text, _, _, _ in nodes() if text)


def tap(x, y):
    subprocess.run([ADB, "shell", "input", "tap", str(x), str(y)], check=True)


def wait_until(condition, seconds, failure):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return
        time.sleep(1)
    sys.exit(f"{failure}; on screen: {on_screen()}")


def main():
    command = sys.argv[1]
    if command == "texts":
        print(on_screen())
    elif command == "tap":
        matches = [n for n in nodes() if n[0] == sys.argv[2]]
        if not matches:
            sys.exit(f"nothing to tap with text {sys.argv[2]!r}; on screen: {on_screen()}")
        tap(matches[-1][2], matches[-1][3])
    elif command == "field":
        matches = [n for n in nodes() if n[1] == "android.widget.EditText"]
        if not matches:
            sys.exit(f"no text field; on screen: {on_screen()}")
        tap(matches[0][2], matches[0][3])
    elif command == "wait":
        seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 20
        wait_until(lambda: any(sys.argv[2] in n[0] for n in nodes()), seconds, f"never saw {sys.argv[2]!r}")
    elif command == "gone":
        seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 20
        wait_until(lambda: not any(sys.argv[2] in n[0] for n in nodes()), seconds, f"{sys.argv[2]!r} never went away")
    elif command == "debits":
        amount = re.escape(sys.argv[2]).replace(r"\.", "[.,]")
        print(sum(1 for n in nodes() if re.search(rf"^[-−].*\b{amount}\b", n[0])))
    else:
        sys.exit(__doc__)


main()
