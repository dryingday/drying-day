#!/usr/bin/env python3
"""Stop hook: don't let the agent finish while ./gradlew check fails (Spotless always rerun).

It skips the build when the working tree is exactly as it was at the last green check
(HEAD, tracked changes and untracked files), so chat-only turns stay fast. The state of the
last green check is kept in .gradle/, which git ignores. Claude Code stops re-running a
blocking Stop hook after 8 consecutive continuations, so a check that can't be fixed in the
session doesn't trap it.
"""

import hashlib
import json
import os
import subprocess
import sys

STAMP = os.path.join(".gradle", "claude-last-green-check")
TAIL_LINES = 60


def _git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, check=True).stdout


def tree_key(cwd):
    h = hashlib.sha256()
    h.update(_git(cwd, "rev-parse", "HEAD"))
    h.update(_git(cwd, "diff", "HEAD", "--binary"))
    for name in sorted(_git(cwd, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0")):
        if name:
            h.update(name)
            path = os.path.join(cwd, name.decode())
            if os.path.isfile(path):
                with open(path, "rb") as f:
                    h.update(hashlib.sha256(f.read()).digest())
    return h.hexdigest()


def main():
    event = json.load(sys.stdin)
    cwd = event.get("cwd") or os.getcwd()
    try:
        root = _git(cwd, "rev-parse", "--show-toplevel").decode().strip()
    except (subprocess.SubprocessError, OSError):
        return 0  # not in a git checkout
    if not os.path.exists(os.path.join(root, "gradlew")):
        return 0
    key = tree_key(root)
    stamp = os.path.join(root, STAMP)
    if os.path.exists(stamp) and open(stamp).read().strip() == key:
        return 0
    result = subprocess.run(["./gradlew", "check", "--console=plain", "-q"], cwd=root,
                            capture_output=True, text=True, timeout=900)
    if result.returncode == 0:
        # Spotless has reported UP-TO-DATE after a change before, so it always runs fresh.
        result = subprocess.run(["./gradlew", "spotlessCheck", "--rerun-tasks", "--console=plain", "-q"],
                                cwd=root, capture_output=True, text=True, timeout=300)
    if result.returncode == 0:
        os.makedirs(os.path.dirname(stamp), exist_ok=True)
        with open(stamp, "w") as f:
            f.write(key)
        return 0
    tail = "\n".join((result.stdout + result.stderr).splitlines()[-TAIL_LINES:])
    print(json.dumps({
        "decision": "block",
        "reason": "./gradlew check fails, so the work isn't finished. Fix it, then stop.\n\n" + tail,
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
