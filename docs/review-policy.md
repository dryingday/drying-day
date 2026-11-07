# Review policy

How a PR gets from opened to merged. What reviewers look for is in [REVIEW.md](../REVIEW.md).

Adapted from [how Anthropic triages code review](https://claude.dev/blog/solving-code-review-at-anthropic/). Three ideas carry over unchanged:

- **The change sets the tier, not the author.**
- **An agent's approval never counts as a person's.** Automated review can clear a low-risk change, but can't fill a slot that needs a human. On low-risk PRs the agent approves through the maintainer's account (see Enforcement), and its approval says so.
- **Whoever directs the agent is accountable** for what it ships, and counts as the required reviewer on its PRs.

## Tiers

The article has three tiers: low (no human), medium (one person) and high (two people). This project has one maintainer, so high and medium both mean "the maintainer reviews", and high-risk PRs are marked so they get a slower read.

| Tier | What | Who merges |
|---|---|---|
| **Low** | Docs and copy, tests and fixtures only, refactors with no behaviour change, store listing text, additive changes behind a flag that is off by default | The agent approves and merges, once CI is green, every review round is clean, and the branch is up to date with `main`. Never for a PR that touches a protected path. Listed in the bolt summary for a skim. |
| **Medium** | Ordinary product changes: verdict wording, UI, forecast handling, non-threshold rule changes | The maintainer approves; the agent then merges. |
| **High** | Protected paths and protected changes (below), and changes to drying thresholds | The maintainer approves after a slower read; the agent then merges. The PR says why it's high. |

## Protected paths

Owned by the maintainer in `.github/CODEOWNERS`, so GitHub requires their approval whatever the tier says.

- `.github/**`: CI workflows, PR templates, CODEOWNERS, the `main` ruleset
- `settings.gradle.kts`, `**/build.gradle.kts`, `gradle.properties`, `gradle/**`, `scripts/**`: dependencies, plugins, the wrapper and dependency verification (supply chain and F-Droid compliance)
- `config/**`, `.editorconfig`: lint and formatting rules
- `**/AndroidManifest.xml`: permissions
- `.claude/**`, `CLAUDE.md`, `REVIEW.md`, `docs/review-policy.md`: the guardrails themselves
- `LICENSE`, signing and release configuration, `fastlane/**` changelogs, version bumps

## Protected changes

High wherever they are in the code:

- New network calls, or any new data sent off the device
- Location handling
- Deleting or weakening tests, or loosening a lint rule or check
- New dependencies
- Drying thresholds (humidity, wind, temperature, rain chance, bring-in timing)

## Who sets the tier

1. **Path rules**, deterministic: CODEOWNERS for protected paths.
2. **A tier reviewer**: a fresh subagent that sees only the diff, this policy and REVIEW.md, not the conversation that wrote the code. It picks a tier with a one-line reason.
3. **An escalation check**, as in the article: the same reviewer separately asks whether this particular change needs a person even if its tier says it doesn't (e.g. a "refactor" that subtly changes rounding).

The highest result wins. The author can't lower it. The tier is applied as a `risk:low`, `risk:medium` or `risk:high` label, with the reason in the PR description.

## Review rounds

Every PR, before the tier is set:

1. `/code-review` (bugs). It reads CLAUDE.md, which points it at REVIEW.md.
2. A spec reviewer subagent: the diff against the bolt spec, skipping style.
3. `/security-review` when the PR touches network, location, permissions, dependencies or release.

Fix confirmed findings only. Record every finding and what happened to it in the PR description. **After three rounds** of findings on the same PR, stop and rethink the approach rather than patching again.

## Approvals cover one commit

An approval covers the commit it was given on. If the agent pushes after the maintainer approves, the approval is dismissed (branch rule) and the PR is flagged again. Low-tier PRs re-run the review rounds after any new push.

## Enforcement

- **Agent identity:** the agent opens PRs, pushes and comments as its own GitHub machine account, `frangipanes-bot`, so the maintainer can approve them (GitHub doesn't let authors approve their own PRs). It uses the maintainer's account for two things only:
  - merging a PR the maintainer has approved on its current commit, with CI green, at any tier;
  - approving and merging a low-risk PR. The approval reads "Approved by the agent: risk:low under docs/review-policy.md" with the tier reviewer's reason, so it can be told apart from the maintainer's.
- **Ruleset on `main`:** kept in `.github/rulesets/main.json` and applied by the maintainer's account. Changes by PR only, squash merges; the CI check "Check and assemble" required; branch must be up to date; code owner approval required for protected paths; approvals dismissed on new pushes; no force pushes or deletion.
- **Claude Code hook:** a `PreToolUse` hook blocks `gh pr merge` unless the PR is labelled `risk:low` or the maintainer has approved its current commit.
- **Bolt summary:** at the end of each bolt the agent lists what it merged on its own, and alerts the maintainer only about PRs waiting for them.
