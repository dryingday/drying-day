#!/usr/bin/env python3
"""PreToolUse hook: holds the agent to docs/review-policy.md when it merges or approves.

The agent may use the maintainer's GitHub account, so GitHub can't tell its approvals from
the maintainer's. This hook checks every `gh` merge or approval before it runs:

- merge:   allowed if the maintainer approved the PR's current commit, or the PR is labelled
           risk:low and touches no protected path (CODEOWNERS on origin/main).
- approve: allowed only for risk:low PRs that touch no protected path, with an approval body
           starting with AGENT_APPROVAL_PREFIX, so it can be told apart from the maintainer's.
- ruleset: applying a ruleset is allowed only from .github/rulesets/main.json exactly as merged.

Approvals starting with AGENT_APPROVAL_PREFIX never count as the maintainer's. Anything the hook
can't check (no PR found, GitHub unreachable) is refused.
"""

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field

MAINTAINER = "frangipanes"
BOT_GH_CONFIG = os.path.expanduser("~/.config/gh-drying-bot")
AGENT_APPROVAL_PREFIX = "Approved by the agent: risk:low under docs/review-policy.md."
RULESET_FILE = ".github/rulesets/main.json"


@dataclass
class Pr:
    number: int
    head: str
    labels: set = field(default_factory=set)
    files: list = field(default_factory=list)
    reviews: list = field(default_factory=list)  # dicts: user, state, commit, body (in order)


@dataclass
class Decision:
    allow: bool
    reason: str


# CODEOWNERS ---------------------------------------------------------------------------------


def _pattern_to_regex(pattern):
    anchored = pattern.startswith("/") or "/" in pattern.strip("/")
    directory = pattern.endswith("/")
    core = pattern.strip("/")
    out, i = "", 0
    while i < len(core):
        if core.startswith("**/", i):
            out, i = out + "(?:.*/)?", i + 3
        elif core.startswith("**", i):
            out, i = out + ".*", i + 2
        elif core[i] == "*":
            out, i = out + "[^/]*", i + 1
        elif core[i] == "?":
            out, i = out + "[^/]", i + 1
        else:
            out, i = out + re.escape(core[i]), i + 1
    prefix = "^" if anchored else "^(?:.*/)?"
    suffix = "/.*$" if directory else "(?:/.*)?$"
    return re.compile(prefix + out + suffix)


def parse_codeowners(text):
    patterns = []
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            patterns.append(_pattern_to_regex(line.split()[0]))
    return patterns


def is_protected(path, patterns):
    """Without a CODEOWNERS file every path counts as protected."""
    if patterns is None:
        return True
    return any(p.match(path) for p in patterns)


# Commands ------------------------------------------------------------------------------------

_MERGE = re.compile(r"\bgh\s+pr\s+merge\b(?:\s+(\d+))?")
_REVIEW = re.compile(r"\bgh\s+pr\s+review\b(?:\s+(\d+))?([^;&|]*)")
_API = re.compile(r"\bgh\s+api\b([^;&|]*)")


def actions_in(command):
    """The merges, approvals and ruleset changes a shell command would make, with PR numbers."""
    found = []
    for m in _MERGE.finditer(command):
        found.append(("merge", int(m.group(1)) if m.group(1) else None))
    for m in _REVIEW.finditer(command):
        if re.search(r"(^|\s)(--approve|-a)(\s|$)", m.group(2)):
            found.append(("approve", int(m.group(1)) if m.group(1) else None))
    for m in _API.finditer(command):
        args = m.group(1)
        if n := re.search(r"pulls/(\d+)/merge\b", args):
            found.append(("merge", int(n.group(1))))
        elif (n := re.search(r"pulls/(\d+)/reviews\b", args)) and re.search(r"APPROVE\b", args):
            found.append(("approve", int(n.group(1))))
        elif re.search(r"\brulesets\b", args) and re.search(r"-X\s*(PUT|POST|PATCH|DELETE)|--method\s+(PUT|POST|PATCH|DELETE)", args):
            found.append(("ruleset", None))
    return found


# Decisions -----------------------------------------------------------------------------------


def maintainer_approved_head(pr):
    """The maintainer's latest review on the current commit is a real (non-agent) approval."""
    state = None
    for r in pr.reviews:
        if r["user"] == MAINTAINER and r["commit"] == pr.head and r["state"] in ("APPROVED", "CHANGES_REQUESTED", "DISMISSED"):
            agent = (r.get("body") or "").startswith(AGENT_APPROVAL_PREFIX)
            state = "AGENT" if (r["state"] == "APPROVED" and agent) else r["state"]
    return state == "APPROVED"


def decide(action, pr, patterns, command):
    protected = [f for f in pr.files if is_protected(f, patterns)]
    low = "risk:low" in pr.labels
    where = f"PR #{pr.number}"
    if action == "merge":
        if maintainer_approved_head(pr):
            return Decision(True, f"{where}: the maintainer approved the current commit.")
        if low and not protected:
            return Decision(True, f"{where}: risk:low and no protected paths.")
        why = f"touches protected paths ({', '.join(protected[:5])})" if protected else "isn't labelled risk:low"
        return Decision(False, f"{where} {why} and the maintainer hasn't approved its current commit. Wait for their approval on GitHub.")
    if action == "approve":
        if not low or protected:
            why = f"touches protected paths ({', '.join(protected[:5])})" if protected else "isn't labelled risk:low"
            return Decision(False, f"{where} {why}. Only the maintainer can approve it.")
        if AGENT_APPROVAL_PREFIX not in command:
            return Decision(False, f'Agent approvals must start with "{AGENT_APPROVAL_PREFIX}" and give the tier reviewer\'s reason.')
        return Decision(True, f"{where}: risk:low, no protected paths, marked as the agent's.")
    return Decision(False, f"Unknown action {action}.")


# GitHub and git ------------------------------------------------------------------------------


def _gh(args, cwd):
    env = dict(os.environ, GH_CONFIG_DIR=BOT_GH_CONFIG)
    out = subprocess.run(["gh", *args], cwd=cwd, env=env, capture_output=True, text=True, timeout=60, check=True)
    return out.stdout


def _git(args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, timeout=30, check=True).stdout


def fetch_pr(number, cwd):
    if number is None:
        number = int(_gh(["pr", "view", "--json", "number", "--jq", ".number"], cwd).strip())
    repo = _gh(["repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"], cwd).strip()
    info = json.loads(_gh(["api", f"repos/{repo}/pulls/{number}"], cwd))
    files = json.loads(_gh(["api", "--paginate", "--slurp", f"repos/{repo}/pulls/{number}/files"], cwd))
    reviews = json.loads(_gh(["api", "--paginate", "--slurp", f"repos/{repo}/pulls/{number}/reviews"], cwd))
    return Pr(
        number=number,
        head=info["head"]["sha"],
        labels={label["name"] for label in info["labels"]},
        files=[f["filename"] for page in files for f in page]
        + [f["previous_filename"] for page in files for f in page if f.get("previous_filename")],
        reviews=[{"user": r["user"]["login"], "state": r["state"], "commit": r["commit_id"], "body": r.get("body")}
                 for page in reviews for r in page],
    )


def codeowners_on_main(cwd):
    try:
        _git(["fetch", "-q", "origin", "main"], cwd)
        return parse_codeowners(_git(["show", "origin/main:.github/CODEOWNERS"], cwd))
    except (subprocess.SubprocessError, OSError):
        return None


def ruleset_decision(command, cwd):
    if f"--input {RULESET_FILE}" not in command:
        return Decision(False, f"Rulesets may only be applied with --input {RULESET_FILE}.")
    try:
        _git(["fetch", "-q", "origin", "main"], cwd)
        merged = _git(["show", f"origin/main:{RULESET_FILE}"], cwd)
        local = open(os.path.join(cwd, RULESET_FILE)).read()
    except (subprocess.SubprocessError, OSError) as e:
        return Decision(False, f"Couldn't compare {RULESET_FILE} with origin/main: {e}")
    if merged != local:
        return Decision(False, f"{RULESET_FILE} differs from origin/main. Apply only the version the maintainer approved.")
    return Decision(True, f"{RULESET_FILE} matches origin/main.")


def main():
    event = json.load(sys.stdin)
    if event.get("tool_name") != "Bash":
        return 0
    command = event.get("tool_input", {}).get("command", "")
    actions = actions_in(command)
    if not actions:
        return 0
    cwd = event.get("cwd") or os.getcwd()
    for action, number in actions:
        try:
            if action == "ruleset":
                d = ruleset_decision(command, cwd)
            else:
                d = decide(action, fetch_pr(number, cwd), codeowners_on_main(cwd), command)
        except (subprocess.SubprocessError, OSError, ValueError, KeyError) as e:
            d = Decision(False, f"Merge gate couldn't check {action} of PR {number}: {e}")
        if not d.allow:
            print(json.dumps({"hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": "Merge gate: " + d.reason,
            }}))
            return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
