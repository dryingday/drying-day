# Review policy

How a PR gets from opened to merged. What reviewers look for is in [REVIEW.md](../REVIEW.md).

Adapted from [how Anthropic triages code review](https://claude.dev/blog/solving-code-review-at-anthropic/). Three ideas carry over unchanged:

- **The change sets the tier, not the author.**
- **An agent's approval never counts as a person's.** Automated review can clear a low or medium change, but can't fill a slot that needs a human. On those PRs the agent approves through the maintainer's account (see Enforcement), and its approval says so.
- **Whoever directs the agent is accountable** for what it ships, and counts as the required reviewer on its PRs.

One idea is this project's own: **the maintainer makes decisions; agents check facts.** The maintainer decides what the app does: its thresholds, what leaves the device, its permissions, its dependencies, and the guardrails. They decide once, in a bolt spec. Whether a diff does what the spec says is a fact, and the review rounds check it. A PR goes to the maintainer only when it holds a decision they haven't made yet.

## Tiers

The article has three tiers: low (no human), medium (one person) and high (two people). This project has one maintainer, who reads specs and not every diff, so here low and medium are both merged by the agent and high is the maintainer's.

| Tier | What | Who merges |
|---|---|---|
| **Low** | Docs and copy, tests and fixtures only, refactors with no behaviour change, store listing text, additive changes behind a flag that is off by default. Never a protected change, covered or not | The agent approves and merges, once CI is green, every review round is clean, and the branch is up to date with `main`. Never for a PR that touches a protected path. Listed in the bolt summary. |
| **Medium** | Product changes: verdict wording, UI, forecast handling, non-threshold rule changes, and protected changes that an approved spec covers (below) | As low, and the spec reviewer has confirmed that the PR holds no protected change beyond its spec. Listed in the bolt summary with a line on what it changed. |
| **High** | Protected paths, protected changes that no approved spec covers, and anything the escalation check finds needs a decision | The maintainer approves; the agent then merges. The PR opens with the decisions it asks for. |

## Protected paths

Owned by the maintainer in `.github/CODEOWNERS`, so GitHub requires the maintainer account's approval whatever the tier says. The agent can also use that account (see Enforcement), so for the agent this rule is enforced by `maintainer.py` and the merge gate hook, not by GitHub.

- `.github/**`: CI workflows, PR templates, CODEOWNERS, the `main` ruleset
- `.claude/**`, `CLAUDE.md`, `REVIEW.md`, `docs/review-policy.md`: the guardrails themselves
- `docs/bolts/**`: the specs. A spec is where the maintainer's decisions are written down, so changing one is changing a decision
- Gradle scripts in either language (`*.gradle.kts`, `*.gradle`), `buildSrc/**`, `build-logic/**`, `gradle.properties` files, `gradle/wrapper/**`, `gradlew`, `gradlew.bat`, `scripts/**`: plugins, repositories, the wrapper, and the scripts that generate dependency verification
- `config/**`, `.editorconfig` files, `lint.xml`, lint and detekt baselines: lint and formatting rules
- `LICENSE`, signing and release configuration (keystores, `keystore.properties`), `fastlane/**` changelogs, version bumps

**Keep protected paths out of feature PRs.** One protected file makes a whole PR high. When a feature needs a build script line or a CI change, that goes in its own small PR, so the maintainer reads a few lines and not the feature.

## Protected changes

These need the maintainer's decision wherever they are in the code:

- New network calls, or any new data sent off the device
- Location handling
- New dependencies and version changes: anything in `gradle/libs.versions.toml` or `gradle/verification-metadata.xml`
- Any change to an `AndroidManifest.xml`: permissions, exported components, backup rules, the rest
- Drying thresholds (humidity, wind, temperature, rain chance, bring-in timing)
- Deleting or weakening tests, or loosening a lint rule or check

## What an approved spec covers

A bolt spec on `main` got there with the maintainer's approval, because `docs/bolts/**` is a protected path. That approval is the decision. A PR that carries out a protected change the spec names is medium, not high, when the spec names **that specific thing**:

- the host called and the data sent to it;
- the permission, or the manifest attribute and its value;
- the dependency, by its coordinates (the version is checked against Maven metadata in the PR's evidence);
- the threshold and its value;
- what location is read, how precisely, and where it's kept.

A spec that says only "handles location" or "adds a settings library" covers nothing: the PR is high, or the spec is tightened first. Anything the diff does beyond what the spec names is not covered. Read the spec as it is on `origin/main`, never as the PR's branch has it.

Two protected changes are never covered by a spec: deleting or weakening tests, and loosening a lint rule or check. They're always high. That includes switching off or widening dependency verification in `gradle/verification-metadata.xml` (`verify-metadata`, trusted artifacts or keys), and changing the version of a lint tool in the catalogue.

**What this buys for dependencies is narrow.** A new dependency needs a line in a module's build script, which is a protected path, so adding one is always a high PR: its own small one, with the catalogue entry and the verification metadata. What a spec covers without the maintainer is a version change to a dependency it names.

The catalogue, the verification metadata and the manifests are not protected paths, so GitHub doesn't ask for the maintainer on them. The tier reviewer and the spec reviewer are what hold this rule, each reading the diff against the spec on its own. `maintainer.py` adds one fixed check: a PR touching any of those files is never low.

## Who sets the tier

1. **Path rules**, deterministic: a PR touching a protected path (CODEOWNERS) is high. `maintainer.py` checks this for the agent.
2. **A tier reviewer**: a fresh subagent that sees only the diff, this policy, REVIEW.md and the specs on `origin/main`, not the conversation that wrote the code. It picks a tier with a one-line reason, and for each protected change it finds, cites the spec line that covers it or says none does.
3. **An escalation check**, as in the article: the same reviewer separately asks whether this particular change holds a decision only the maintainer can make, even if its tier says it doesn't.

The highest result wins. The author can't lower it. The tier is applied as one `risk:low`, `risk:medium` or `risk:high` label, with the reason in the PR description.

### Escalations name a decision, not a doubt

An escalation makes the PR high, so it has to say what the maintainer is being asked to decide: a product judgement (what a verdict should mean), a privacy trade-off, a change to what a spec promised, a guardrail. It's written as a question the maintainer can answer without reading the diff.

"A person should confirm the rounding is unchanged" is not a decision. It's a fact, and facts are checked: the tier reviewer returns it as a **check**, the author settles it with a test or a comparison recorded under Evidence, and the review rounds run again. A check doesn't raise the tier. If a check can't be settled by evidence, say why; only then is it a question for the maintainer.

## Review rounds

Every PR, before the tier is set:

1. `/code-review` (bugs). It reads CLAUDE.md, which points it at REVIEW.md.
2. A spec reviewer subagent: the diff against the bolt spec, skipping style. It also lists every protected change in the diff against the spec line that covers it.
3. `/security-review` when the PR touches network, location, permissions, dependencies or release.

Fix confirmed findings only. Record every finding and what happened to it in the PR description. **After three rounds** of findings on the same PR, stop and rethink the approach rather than patching again.

## What the maintainer is asked

A high PR's description opens with **Decisions for the maintainer**: one to three questions, each answerable without reading the diff, with the agent's recommendation. Approving the PR answers yes to them. Everything else in the description is evidence that the checking was done, there if the maintainer wants it.

If a high PR has no question to ask (a CI fix, a build script line), it says what the change is in one sentence and why it's on a protected path.

Low and medium PRs don't ask for the maintainer's review. If the maintainer requests changes on one anyway, the agent stops and doesn't approve or merge it until they approve or dismiss their request.

## Approvals cover one commit

An approval covers the commit it was given on. If the agent pushes after the maintainer approves, the approval is dismissed (branch rule) and the PR is flagged again. Low and medium PRs re-run the review rounds and the tier reviewer after any new push.

## Enforcement

- **Agent identity:** the agent opens PRs, pushes and comments as its own GitHub machine account, `frangipanes-bot`, so the maintainer can approve them (GitHub doesn't let authors approve their own PRs). It uses the maintainer's account for three things only:
  - merging a PR the maintainer has approved on its current commit, with CI green, at any tier;
  - approving and merging a low or medium PR. The approval reads "Approved by the agent: risk:medium under docs/review-policy.md" (or `risk:low`) with the tier reviewer's reason, so it can be told apart from the maintainer's;
  - applying `.github/rulesets/main.json` to GitHub, only as merged with the maintainer's approval.

  GitHub can't tell these actions from the maintainer's own, so the agent takes all three through one script, `.claude/hooks/maintainer.py`. It merges only if the maintainer approved the PR's current commit, or the PR carries exactly one risk label, `risk:low` or `risk:medium`, touches no protected path, and has no changes requested by the maintainer; a PR touching the version catalogue, the verification metadata or a manifest has to be `risk:medium`. It pins the merge to that commit. It approves only that second kind of PR, with the marked text. It applies the ruleset only as merged on `main`. Approvals starting "Approved by the agent" never count as the maintainer's.

  The script checks labels, paths and reviews. It can't check that the label is right, that the review rounds ran, or that a spec covers a protected change: those rest on the reviewers and on the record in the PR description.
- **Ruleset on `main`:** kept in `.github/rulesets/main.json` and applied by the maintainer's account. Changes by PR only, squash merges; one approval, and code owner approval for protected paths; the CI check "Check and assemble" required; branch up to date; review threads resolved; approvals dismissed on new pushes; no force pushes or deletion; no bypass, so the maintainer's own protected-path changes go through a PR from the agent's account.
- **Claude Code hooks:** a `PreToolUse` hook refuses any shell command that looks like a merge, an approval or a ruleset change, in any spelling, unless it's a plain call to `maintainer.py`. If the hook itself fails, the command is refused. A `Stop` hook won't let the agent finish while `./gradlew check` fails. These guard against mistakes: an agent determined to get round them could, so the maintainer's review of `.claude/**` is what keeps them honest.
- **Bolt summary:** at the end of each bolt the agent lists what it merged on its own, with the tier and a line on each medium PR, and alerts the maintainer only about PRs waiting for them. The summary is where the maintainer checks that this policy is sending them the right PRs.
