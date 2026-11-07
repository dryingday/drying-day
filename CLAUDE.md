# Working on Drying Day

Read first: [docs/PLAN.md](docs/PLAN.md) (goals, decisions, bolts), the current bolt spec in
[docs/bolts/](docs/bolts/), [REVIEW.md](REVIEW.md) (what matters in review) and
[docs/review-policy.md](docs/review-policy.md). The policy is the rulebook for tiers, review
rounds and who merges; this file doesn't repeat it, only how to work with it here.

## Build

```
./gradlew check assembleDebug                      # tests, lint, ktlint, detekt, non-free check
./gradlew spotlessApply                            # fix formatting
python3 -m unittest discover -s .claude/hooks      # tests for the hooks below
```

iOS targets are off unless `-Pdryingday.ios=true`. They need macOS: the Kotlin compiles with
the Command Line Tools alone, but linking and running tests need Xcode.

## Each PR

Follow "How work is done" in [docs/PLAN.md](docs/PLAN.md) and the policy. In practice:

- One branch per PR, `bolt/NN-short-name`, from an up-to-date `main`. Tests first.
- Open it from the agent's account as a draft, with `frangipanes` as reviewer once it's ready.
- Review rounds in fresh contexts: `/code-review`, the `spec-reviewer` subagent, and
  `/security-review` where the policy asks. Mind the policy's three-round limit.
- The `tier-reviewer` subagent sets the tier; apply the `risk:*` label (one only).
- Fill in `.github/pull_request_template.md`, with evidence from the current commit.
- Watch the PR. Act on comments, reply, and resolve the threads: the ruleset won't merge with
  open ones. When the maintainer approves the current commit, merge it. Approve and merge a
  `risk:low` PR yourself only when it meets every condition the policy's Tiers table and
  "Approvals cover one commit" section set; `maintainer.py` checks some of them, not all.

## GitHub accounts

- **The agent's account, `frangipanes-bot`**, for everything you write: PRs, comments, labels,
  pushes.
  - gh: `GH_CONFIG_DIR=~/.config/gh-drying-bot gh …`, after checking
    `gh api user --jq .login`. The default gh config is the maintainer's.
  - git: pushes go out as the bot only because this clone's `.git/config` sets
    `credential.https://github.com.helper` to
    `!GH_CONFIG_DIR=$HOME/.config/gh-drying-bot gh auth git-credential`. In a fresh clone, set
    that first, or pushes use whoever is in the system keychain.
- **The maintainer's account, only through `.claude/hooks/maintainer.py`.** Run it from the
  repository root, exactly like this, with nothing before or after it:

  ```
  python3 .claude/hooks/maintainer.py merge 12
  python3 .claude/hooks/maintainer.py approve 12 --reason "Docs only, no protected paths"
  python3 .claude/hooks/maintainer.py apply-ruleset
  ```

  A double-quoted reason can't contain `` ` ``, `$`, `"` or `\`; a single-quoted one can't
  contain `'`. It refuses unless the policy allows the action (see its docstring). The merge
  gate hook refuses the other ways of merging, approving or changing a ruleset that it can
  recognise; like all the hooks, it guards against mistakes, not a determined agent.

## Pitfalls

- **The merge gate reads shell text.** A command that merely mentions a PR alongside "merge",
  or approving a review, is refused: an echo, a commit message, a PR body, a PR title. Write
  such text with the Write tool and pass it as a file (`git commit -F`, `gh pr edit
  --body-file`), and word titles and branch names without "merge".
- **Spotless can report UP-TO-DATE after a change.** Before saying formatting is clean, run
  `./gradlew spotlessCheck --rerun-tasks`. The Stop hook does, and CI is the backstop.
- **Check facts before stating them.** Versions come from the Maven metadata
  (`repo1.maven.org`, `dl.google.com/android/maven2`), not memory. Test a claim about a tool
  before putting it in a PR or commit, and say what wasn't verified.
- **Dependencies:** Google Maven and Maven Central only; no Gradle Plugin Portal. After any
  dependency or plugin change, run `python3 scripts/update-verification-metadata.py` on a Mac
  and check the diff.
- **No real places** anywhere in the repository: no default location, no homes, no personal
  data. One exception: the reference set (its fetch script, fixtures, tests and
  `docs/reference-set.md`) uses public landmarks, coordinates to two decimal places. See
  `docs/bolts/02-drying-rules.md`.
