# Working on Drying Day

Read first: [docs/PLAN.md](docs/PLAN.md) (goals, decisions, bolts), the current bolt spec in
[docs/bolts/](docs/bolts/), [REVIEW.md](REVIEW.md) (what matters in review) and
[docs/review-policy.md](docs/review-policy.md) (tiers, protected paths, who merges).

## Build

```
./gradlew check assembleDebug                      # tests, lint, ktlint, detekt, non-free check
./gradlew spotlessApply                            # fix formatting
python3 -m unittest discover -s .claude/hooks      # tests for the hooks below
```

iOS targets are off unless `-Pdryingday.ios=true` (needs macOS; linking needs Xcode).

## Each PR

1. One branch per PR, `bolt/NN-short-name`, from an up-to-date `main`.
2. Tests first, then code. The Stop hook won't let you finish while `./gradlew check` fails.
3. Open it from the agent's account, with `frangipanes` as reviewer (a draft is fine while
   CI and reviews run).
4. Review rounds, each in a fresh context: `/code-review`; the `spec-reviewer` subagent;
   `/security-review` when it touches network, location, permissions, dependencies or release.
   Fix confirmed findings only.
5. The `tier-reviewer` subagent sets the tier; apply the `risk:*` label.
6. The description follows `.github/pull_request_template.md`: what, evidence at the current
   commit, every review finding and what happened to it, the tier and why.
7. Watch the PR. When the maintainer approves its current commit, merge it (below). Comments
   and requested changes: act on them and reply on the PR.

## GitHub accounts

- **The agent's account, `frangipanes-bot`**, for everything you write: PRs, comments, labels,
  pushes. Run gh with `GH_CONFIG_DIR=~/.config/gh-drying-bot`, and check
  `gh api user --jq .login` first. The default gh config is the maintainer's.
- **The maintainer's account, only through `.claude/hooks/maintainer.py`:**
  `merge N` (after their approval, or for a `risk:low` PR touching no protected path),
  `approve N --reason "<tier reviewer's reason>"` (`risk:low` and unprotected only) and
  `apply-ruleset`. The merge gate hook refuses every other way of merging, approving or changing
  a ruleset.

## Pitfalls

- **The merge gate reads shell text.** A command that merely mentions a PR alongside "merge",
  or approving a review (an echo, a commit message, a PR title), is refused. Write such text
  with the Write tool and pass it as a file (`git commit -F`, `gh pr edit --body-file`).
- **Spotless can report UP-TO-DATE after a change.** Before saying formatting is clean, run
  `./gradlew spotlessCheck --rerun-tasks`. CI is the backstop.
- **Check facts before stating them.** Versions come from the Maven metadata
  (`repo1.maven.org`, `dl.google.com/android/maven2`), not memory. Test a claim about a tool
  before putting it in a PR or commit. Say what wasn't verified.
- **Dependencies:** Google Maven and Maven Central only; no Gradle Plugin Portal. After any
  dependency or plugin change, run `python3 scripts/update-verification-metadata.py` and check
  the diff. The non-free check and F-Droid's own scanner must stay green.
- **No real places** anywhere in the repository: no default location, no personal data in
  fixtures.
