#!/usr/bin/env python3
"""PreToolUse hook: the agent merges, approves and changes rulesets only through maintainer.py.

The agent may use the maintainer's GitHub account, so GitHub can't tell its approvals from the
maintainer's. Rather than try to recognise every safe spelling of a merge, the hook refuses any
Bash command that looks like a merge, an approval or a ruleset change, unless the whole command
is a plain call to .claude/hooks/maintainer.py. That script checks docs/review-policy.md itself
(see decide()) before it acts.

This guards against mistakes, not against an agent set on getting round it: a script written to
disk and then run isn't visible here. Anything the hook can't classify is refused.

The policy logic (CODEOWNERS matching, decide(), fetch_pr()) lives here so maintainer.py and the
tests share it.
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

# The only commands allowed to merge, approve or apply a ruleset: one call to the wrapper with
# nothing chained on, a plain PR number, and a quoted reason with no substitutions.
_WRAPPER = re.compile(
    r"""^\s*(?:python3\s+)?(?:\S*/)?\.claude/hooks/maintainer\.py\s+"""
    r"""(?:merge\s+\d+|approve\s+\d+\s+--reason\s+(?:"[^"`$\\]*"|'[^']*')|apply-ruleset)\s*$"""
)

_RISKY = [
    (r"\bpr\s+merge\b", "a PR merge"),
    (r"pulls/\d+/merge\b", "a PR merge through the API"),
    (r"mergePullRequest|enablePullRequestAutoMerge|addPullRequestReview|submitPullRequestReview"
     r"|approvePullRequest|(?:create|update|delete)RepositoryRuleset", "a GraphQL merge, review or ruleset change"),
    (r"\bgh\b.*\balias\b", "a gh alias"),
    (r"\bgit\s+push\b.*(?:\s|:)(?:refs/heads/)?main\b", "a push to main"),
]


def classify(command):
    """"wrapper" for an allowed call to maintainer.py, a reason string for a refused command,
    or None for anything that can't merge, approve or change a ruleset."""
    if _WRAPPER.match(command):
        return "wrapper"
    if re.search(r"maintainer\.py\b.*\b(?:merge|approve|apply-ruleset)\b", command):
        return "maintainer.py has to be called on its own, with a plain PR number and a quoted --reason"
    for pattern, what in _RISKY:
        if re.search(pattern, command, re.I):
            return f"this looks like {what}"
    if re.search(r"\bpr\s+review\b", command) and re.search(r"(?:^|\s)(?:--approve|-a)(?:\s|$|['\"])", command):
        return "this looks like a PR approval"
    if re.search(r"pulls/\d+/reviews\b", command) and re.search(r"APPROVE", command, re.I):
        return "this looks like a PR approval through the API"
    if re.search(r"\brulesets\b", command) and re.search(
        r"-X\s*(?!GET\b)\w+|--method[=\s]+(?!GET\b)\w+|--input\b|(?:^|\s)-[fF]\s|--(?:raw-)?field\b", command, re.I
    ):
        return "this looks like a ruleset change"
    return None


# Decisions -----------------------------------------------------------------------------------


def maintainer_approved_head(pr):
    """The maintainer's latest review on the current commit is a real (non-agent) approval."""
    state = None
    for r in pr.reviews:
        if r["user"] == MAINTAINER and r["commit"] == pr.head and r["state"] in ("APPROVED", "CHANGES_REQUESTED", "DISMISSED"):
            agent = (r.get("body") or "").startswith(AGENT_APPROVAL_PREFIX)
            state = "AGENT" if (r["state"] == "APPROVED" and agent) else r["state"]
    return state == "APPROVED"


def is_low(pr):
    """Labelled risk:low and no higher tier: the highest label wins."""
    return "risk:low" in pr.labels and not ({"risk:medium", "risk:high"} & pr.labels)


def decide(action, pr, patterns):
    protected = [f for f in pr.files if is_protected(f, patterns)]
    where = f"PR #{pr.number}"
    why_not_low = (f"touches protected paths ({', '.join(protected[:5])})" if protected
                   else "isn't labelled risk:low alone")
    if action == "merge":
        if maintainer_approved_head(pr):
            return Decision(True, f"{where}: the maintainer approved the current commit.")
        if is_low(pr) and not protected:
            return Decision(True, f"{where}: risk:low and no protected paths.")
        return Decision(False, f"{where} {why_not_low} and the maintainer hasn't approved its current commit. "
                               "Wait for their approval on GitHub.")
    if action == "approve":
        if is_low(pr) and not protected:
            return Decision(True, f"{where}: risk:low and no protected paths.")
        return Decision(False, f"{where} {why_not_low}. Only the maintainer can approve it.")
    return Decision(False, f"Unknown action {action}.")


# GitHub and git ------------------------------------------------------------------------------


def gh(args, cwd, as_maintainer=False, stdin=None):
    env = dict(os.environ)
    if as_maintainer:
        env.pop("GH_CONFIG_DIR", None)
    else:
        env["GH_CONFIG_DIR"] = BOT_GH_CONFIG
    out = subprocess.run(["gh", *args], cwd=cwd, env=env, input=stdin, capture_output=True, text=True,
                         timeout=60, check=True)
    return out.stdout


def git(args, cwd):
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, timeout=60, check=True).stdout


def repo_name(cwd):
    return gh(["repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"], cwd).strip()


def fetch_pr(number, cwd):
    repo = repo_name(cwd)
    info = json.loads(gh(["api", f"repos/{repo}/pulls/{number}"], cwd))
    files = json.loads(gh(["api", "--paginate", "--slurp", f"repos/{repo}/pulls/{number}/files"], cwd))
    reviews = json.loads(gh(["api", "--paginate", "--slurp", f"repos/{repo}/pulls/{number}/reviews"], cwd))
    return Pr(
        number=number,
        head=info["head"]["sha"],
        labels={label["name"] for label in info["labels"]},
        files=[f["filename"] for page in files for f in page]
        + [f["previous_filename"] for page in files for f in page if f.get("previous_filename")],
        reviews=[{"user": (r.get("user") or {}).get("login"), "state": r["state"], "commit": r["commit_id"],
                  "body": r.get("body")} for page in reviews for r in page],
    )


def codeowners_on_main(cwd):
    try:
        git(["fetch", "-q", "origin", "main"], cwd)
        return parse_codeowners(git(["show", "origin/main:.github/CODEOWNERS"], cwd))
    except (subprocess.SubprocessError, OSError):
        return None


# Hook ----------------------------------------------------------------------------------------


def deny(reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": "Merge gate: " + reason,
    }}))


def main():
    try:
        event = json.load(sys.stdin)
        if event.get("tool_name") != "Bash":
            return 0
        verdict = classify(event.get("tool_input", {}).get("command", ""))
        if verdict not in (None, "wrapper"):
            deny(f"{verdict}. Merge, approve or apply the ruleset with "
                 ".claude/hooks/maintainer.py, which checks docs/review-policy.md first. "
                 "To write text that mentions these commands, use the Write tool.")
    except Exception as e:  # fail closed
        deny(f"couldn't check this command ({e}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
