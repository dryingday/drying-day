#!/usr/bin/env python3
"""Stop hook: don't let the agent finish while ./gradlew check fails (Spotless always rerun).

The result is remembered against the state of the working tree (HEAD, tracked changes and
untracked files), kept in .gradle/, which git ignores:
- unchanged since the last green check: stop straight away, so chat-only turns stay fast;
- unchanged since the last failed check, while retrying in the same turn (stop_hook_active):
  block again with the saved output, without rebuilding.
A timeout or an error in this hook counts as a failure. Claude Code's hooks reference says it
ends a turn after 8 consecutive Stop-hook continuations, so a failure that can't be fixed in
the session doesn't trap it.
"""

import hashlib
import json
import os
import subprocess
import sys

GREEN = os.path.join(".gradle", "claude-last-green-check")
FAILED = os.path.join(".gradle", "claude-last-failed-check.json")
TAIL_LINES = 60
CHECK_TIMEOUT = 900
SPOTLESS_TIMEOUT = 300


def _git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, check=True).stdout


def tree_key(cwd):
    h = hashlib.sha256()
    h.update(_git(cwd, "rev-parse", "HEAD"))
    h.update(_git(cwd, "diff", "HEAD", "--binary"))
    for name in sorted(_git(cwd, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0")):
        if name and not name.startswith(b".gradle/"):  # where this hook keeps its own state
            h.update(name)
            path = os.path.join(cwd, name.decode())
            if os.path.isfile(path):
                with open(path, "rb") as f:
                    h.update(hashlib.sha256(f.read()).digest())
    return h.hexdigest()


def run_checks(root):
    """None when green, otherwise the tail of the failing output."""
    steps = [
        (["./gradlew", "check", "--console=plain", "-q"], CHECK_TIMEOUT),
        # Spotless has reported UP-TO-DATE after a change before, so it always runs fresh.
        (["./gradlew", "spotlessCheck", "--rerun-tasks", "--console=plain", "-q"], SPOTLESS_TIMEOUT),
    ]
    for command, timeout in steps:
        try:
            result = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return f"`{' '.join(command[:2])}` didn't finish within {timeout // 60} minutes."
        if result.returncode != 0:
            return "\n".join((result.stdout + result.stderr).splitlines()[-TAIL_LINES:])
    return None


def block(reason):
    print(json.dumps({"decision": "block", "reason": reason}))


def main():
    try:
        return check(json.load(sys.stdin))
    except Exception as e:  # fail closed: an error mustn't let the agent stop unchecked
        block(f"The check-before-stop hook failed ({e}), so ./gradlew check wasn't confirmed. Run it, then stop.")
        return 0


def check(event):
    cwd = event.get("cwd") or os.getcwd()
    try:
        root = _git(cwd, "rev-parse", "--show-toplevel").decode().strip()
    except (subprocess.SubprocessError, OSError):
        return 0  # not in a git checkout
    if not os.path.exists(os.path.join(root, "gradlew")):
        return 0
    key = tree_key(root)
    green, failed = os.path.join(root, GREEN), os.path.join(root, FAILED)
    if os.path.exists(green) and open(green).read().strip() == key:
        return 0
    # Within one turn's retries, an unchanged failing tree isn't rebuilt. A new turn always
    # reruns, so a flaky failure gets another go.
    if event.get("stop_hook_active") and os.path.exists(failed):
        saved = json.load(open(failed))
        if saved.get("key") == key:
            block("./gradlew check still fails and nothing has changed since it last ran. "
                  "Fix it, then stop.\n\n" + saved.get("output", ""))
            return 0
    output = run_checks(root)
    os.makedirs(os.path.join(root, ".gradle"), exist_ok=True)
    if output is None:
        with open(green, "w") as f:
            f.write(key)
        return 0
    with open(failed, "w") as f:
        json.dump({"key": key, "output": output}, f)
    block("./gradlew check fails, so the work isn't finished. Fix it, then stop.\n\n" + output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
