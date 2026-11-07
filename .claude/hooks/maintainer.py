#!/usr/bin/env python3
"""The agent's only way to act through the maintainer's GitHub account.

    maintainer.py merge N                  merge PR N, if docs/review-policy.md allows it
    maintainer.py approve N --reason TEXT  approve a risk:low PR touching no protected path
    maintainer.py apply-ruleset            apply .github/rulesets/main.json as merged on main

Each command checks the policy itself (merge_gate.decide) on the PR as GitHub has it now:
  - merges are pinned to the commit that was checked (--match-head-commit);
  - approvals always start with AGENT_APPROVAL_PREFIX, so they never count as the maintainer's;
  - the ruleset is taken from origin/main, never from the working tree.
The merge gate hook refuses any other way of doing these things.
"""

import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from merge_gate import (  # noqa: E402
    AGENT_APPROVAL_PREFIX,
    MAINTAINER,
    RULESET_FILE,
    codeowners_on_main,
    decide,
    fetch_pr,
    gh,
    git,
    repo_name,
)


def fail(message):
    print(f"maintainer.py: {message}", file=sys.stderr)
    sys.exit(1)


def check_maintainer_account(cwd):
    login = gh(["api", "user", "--jq", ".login"], cwd, as_maintainer=True).strip()
    if login != MAINTAINER:
        fail(f"the default gh login is {login!r}, not {MAINTAINER!r}.")


def merge(number, cwd):
    pr = fetch_pr(number, cwd)
    d = decide("merge", pr, codeowners_on_main(cwd))
    if not d.allow:
        fail(d.reason)
    print(d.reason)
    print(gh(["pr", "merge", str(number), "--squash", "--delete-branch", "--match-head-commit", pr.head],
             cwd, as_maintainer=True), end="")


def approve(number, reason, cwd):
    if not reason.strip():
        fail("give the tier reviewer's reason with --reason.")
    pr = fetch_pr(number, cwd)
    d = decide("approve", pr, codeowners_on_main(cwd))
    if not d.allow:
        fail(d.reason)
    print(d.reason)
    body = f"{AGENT_APPROVAL_PREFIX} {reason.strip()}"
    print(gh(["pr", "review", str(number), "--approve", "--body", body], cwd, as_maintainer=True), end="")


def _normalise(ruleset):
    return {
        "enforcement": ruleset["enforcement"],
        "conditions": ruleset["conditions"],
        "bypass_actors": ruleset.get("bypass_actors") or [],
        "rules": {r["type"]: r.get("parameters") for r in ruleset["rules"]},
    }


def apply_ruleset(cwd):
    git(["fetch", "-q", "origin", "main"], cwd)
    wanted = git(["show", f"origin/main:{RULESET_FILE}"], cwd)
    name = json.loads(wanted)["name"]
    repo = repo_name(cwd)
    ids = [r["id"] for r in json.loads(gh(["api", f"repos/{repo}/rulesets"], cwd)) if r["name"] == name]
    if len(ids) != 1:
        fail(f"expected one ruleset named {name!r} on {repo}, found {len(ids)}.")
    gh(["api", "-X", "PUT", f"repos/{repo}/rulesets/{ids[0]}", "--input", "-"], cwd, as_maintainer=True, stdin=wanted)
    live = json.loads(gh(["api", f"repos/{repo}/rulesets/{ids[0]}"], cwd))
    if _normalise(live) != _normalise(json.loads(wanted)):
        fail("applied, but the live ruleset doesn't match the file. Check it on GitHub.")
    print(f"Ruleset {name!r} ({ids[0]}) on {repo} now matches {RULESET_FILE} on origin/main.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("merge").add_argument("number", type=int)
    approve_parser = sub.add_parser("approve")
    approve_parser.add_argument("number", type=int)
    approve_parser.add_argument("--reason", required=True)
    sub.add_parser("apply-ruleset")
    args = parser.parse_args()

    cwd = git(["rev-parse", "--show-toplevel"], os.getcwd()).strip()
    try:
        check_maintainer_account(cwd)
        if args.command == "merge":
            merge(args.number, cwd)
        elif args.command == "approve":
            approve(args.number, args.reason, cwd)
        else:
            apply_ruleset(cwd)
    except subprocess.CalledProcessError as e:
        fail(f"{' '.join(e.cmd[:3])} failed: {(e.stderr or '').strip()}")


if __name__ == "__main__":
    main()
