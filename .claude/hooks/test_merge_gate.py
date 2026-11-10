"""Tests for merge_gate.py and maintainer.py. Run: python3 -m unittest discover -s .claude/hooks"""

import unittest

import os

from merge_gate import (
    AGENT_MARKER,
    Pr,
    agent_approval_body,
    agent_tier,
    classify,
    decide,
    is_protected,
    parse_codeowners,
)

CODEOWNERS = """
# comment
/.github/                @frangipanes
*.gradle.kts             @frangipanes
*.gradle                 @frangipanes
/buildSrc/               @frangipanes
/gradlew                 @frangipanes
gradle.properties        @frangipanes
/gradle/                 @frangipanes
AndroidManifest.xml      @frangipanes
*baseline*.xml           @frangipanes
/fastlane/**/changelogs/ @frangipanes
/docs/review-policy.md   @frangipanes
"""
PATTERNS = parse_codeowners(CODEOWNERS)
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".github", "CODEOWNERS")) as _f:
    REAL = parse_codeowners(_f.read())  # the repository's own, where the spec-gated files aren't listed
HEAD = "abc1234"


def pr(labels=(), files=("docs/notes.md",), reviews=()):
    return Pr(number=7, head=HEAD, labels=set(labels), files=list(files), reviews=list(reviews))


def review(user="frangipanes", state="APPROVED", commit=HEAD, body=""):
    return {"user": user, "state": state, "commit": commit, "body": body}


class ProtectedPaths(unittest.TestCase):
    def test_protected(self):
        for path in [
            ".github/workflows/ci.yml",
            "build.gradle.kts",
            "shared/build.gradle.kts",
            "shared/build.gradle",
            "buildSrc/src/main/kotlin/X.kt",
            "gradlew",
            "shared/gradle.properties",
            "gradle/verification-metadata.xml",
            "androidApp/src/main/AndroidManifest.xml",
            "shared/detekt-baseline.xml",
            "fastlane/metadata/android/en-US/changelogs/1.txt",
            "docs/review-policy.md",
        ]:
            with self.subTest(path=path):
                self.assertTrue(is_protected(path, PATTERNS))

    def test_not_protected(self):
        for path in [
            "docs/PLAN.md",
            "README.md",
            "shared/src/commonMain/kotlin/App.kt",
            "gradlew-notes.md",
            "src/gradle/x.kt",  # /gradle/ is anchored to the root
            "docs/github/notes.md",
            "fastlane/metadata/android/en-US/title.txt",
        ]:
            with self.subTest(path=path):
                self.assertFalse(is_protected(path, PATTERNS))

    def test_no_codeowners_means_everything_protected(self):
        self.assertTrue(is_protected("docs/PLAN.md", None))


WRAPPER = ".claude/hooks/maintainer.py"


class Classify(unittest.TestCase):
    """The hook allows only plain calls to the wrapper; anything else that could merge, approve
    or change a ruleset is refused, however it's spelled."""

    def test_wrapper_calls_allowed(self):
        for command in [
            f"python3 {WRAPPER} merge 7",
            f"{WRAPPER} merge 7",
            f"./{WRAPPER} merge 7",
            f'python3 {WRAPPER} approve 7 --reason "docs only, no protected paths"',
            f"python3 {WRAPPER} apply-ruleset",
        ]:
            with self.subTest(command=command):
                self.assertEqual(classify(command), "wrapper")

    def test_wrapper_chained_with_anything_refused(self):
        for command in [
            f"python3 {WRAPPER} merge 7; gh pr merge 8",
            f"python3 {WRAPPER} merge 7 && echo done",
            f"python3 {WRAPPER} merge $(cat n)",
            f"python3 {WRAPPER} merge `cat n`",
            f"python3 {WRAPPER} merge 7 | tee log",
        ]:
            with self.subTest(command=command):
                self.assertNotIn(classify(command), ("wrapper", None))

    def test_direct_merges_approvals_and_ruleset_writes_refused(self):
        for command in [
            "gh pr merge 7 --squash",
            "gh pr merge --squash 12",
            "gh pr merge https://github.com/o/r/pull/12",
            "GH_CONFIG_DIR=~/.config/gh-drying-bot gh pr merge 12",
            "gh -R o/r pr merge 7",
            "bash -c 'gh pr merge 7'",
            "command gh pr merge 7",
            "/opt/homebrew/bin/gh pr merge 7",
            "{ gh pr merge 7; }",
            "if gh pr merge 7; then echo ok; fi",
            'gh pr review 7 --approve --body "x"',
            'gh pr review 9 --body "docs; tidy" --approve',
            "gh pr review 7 -a",
            "gh api -X PUT repos/o/r/pulls/9/merge",
            "gh api repos/o/r/pulls/9/reviews -f event=APPROVE",
            "gh api graphql -f query='mutation{mergePullRequest(input:{})}'",
            "gh api graphql -f query='mutation{addPullRequestReview(input:{event:APPROVE})}'",
            "gh api -X DELETE repos/o/r/rulesets/1",
            "gh api --method=PUT repos/o/r/rulesets/1 --input x.json",
            "gh api repos/o/r/rulesets --input x.json",
            "gh alias set m 'pr merge'",
            "git push origin HEAD:main",
            "git push origin main",
            "git push --force origin refs/heads/main",
            "git status && git push origin main",
            "git push origin +main",
            "git push origin HEAD:refs/heads/main",
            "git -C . push origin main",
            "git -c k=v push origin main",
            "git push --all origin",
            "git push --mirror origin",
            "git push 2>&1 origin main",
            "git push &>/dev/null origin main",
            'git push -o "note=a;b" origin main',
            "git push --push-option='x&y' origin HEAD:main",
            "git push \\\n  origin main",
            # Other spellings gh accepts.
            'gh pr review 12 --approve=true -b "lgtm"',
            'gh pr review 12 -ab "ok"',
            "gh pr -R frangipanes/drying-day review 12 --approve",
            "gh pr -R frangipanes/drying-day merge 12 --squash",
            "gh pr --repo frangipanes/drying-day merge 12",
            # Quoting that splits a word.
            'gh pr "merge" 8 --squash',
            "gh pr mer''ge 8",
            "gh pr m\\erge 8",
            'gh pr "review" 8 --approve',
            'gh api repos/o/r/pulls/8/reviews -f event=APP""ROVE',
            'gh api -X DELETE repos/o/r/rule""sets/1',
            # The wrapper only from this repo's relative path.
            "/tmp/pr12/.claude/hooks/maintainer.py merge 5",
            f"true&&{WRAPPER} merge 5",
        ]:
            with self.subTest(command=command):
                self.assertNotIn(classify(command), ("wrapper", None))

    def test_ordinary_commands_pass(self):
        for command in [
            "gh pr view 7 --json state",
            "gh pr create --draft --title x --body y",
            "gh api repos/o/r/pulls/9/reviews",
            "gh api repos/o/r/rulesets/1",
            "gh pr review 7 --comment -b hi",
            "git push -u origin bolt/01-x",
            "./gradlew check",
            f"chmod +x {WRAPPER}",
            f"git add {WRAPPER} && git commit -m wrapper",
            f"python3 {WRAPPER} --help",
            # "main" later in the line belongs to another command, not the push.
            "git push -u origin bolt/01-x && gh pr create --base main --title t --body b",
            "git push -q origin bolt/01-x; git checkout main",
            "git push origin main-fix",
            "git push origin feature # not main",
            "git push -u origin bolt/x 2>&1 | grep -v remote: && git log main",
        ]:
            with self.subTest(command=command):
                self.assertIsNone(classify(command))


class RealCodeowners(unittest.TestCase):
    """The repository's own CODEOWNERS: the guardrails and specs are protected, and the files
    the policy leaves to an approved spec are not."""

    @classmethod
    def setUpClass(cls):
        root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
        with open(os.path.join(root, ".github", "CODEOWNERS")) as f:
            cls.patterns = parse_codeowners(f.read())

    def test_guardrails_build_scripts_and_specs_protected(self):
        for path in [
            ".github/CODEOWNERS",
            ".github/workflows/ci.yml",
            ".claude/hooks/merge_gate.py",
            ".claude/agents/tier-reviewer.md",
            "CLAUDE.md",
            "REVIEW.md",
            "docs/review-policy.md",
            "docs/bolts/04-location.md",
            "build.gradle.kts",
            "shared/build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradle/wrapper/gradle-wrapper.jar",
            "scripts/update-verification-metadata.py",
            "config/detekt/detekt.yml",
            "LICENSE",
        ]:
            with self.subTest(path=path):
                self.assertTrue(is_protected(path, self.patterns))

    def test_spec_gated_files_and_product_code_not_protected(self):
        for path in [
            "gradle/libs.versions.toml",
            "gradle/verification-metadata.xml",
            "androidApp/src/main/AndroidManifest.xml",
            "docs/PLAN.md",
            "docs/reference-set.md",
            "shared/src/commonMain/kotlin/io/github/dryingday/App.kt",
        ]:
            with self.subTest(path=path):
                self.assertFalse(is_protected(path, self.patterns))


class Decisions(unittest.TestCase):
    def test_merge_allowed_after_maintainer_approval_of_head(self):
        self.assertTrue(decide("merge", pr(files=[".github/x.yml"], reviews=[review()]), PATTERNS).allow)

    def test_merge_denied_when_approval_is_on_an_older_commit(self):
        self.assertFalse(decide("merge", pr(files=[".github/x.yml"], reviews=[review(commit="old0000")]), PATTERNS).allow)

    def test_agent_approval_does_not_count_as_maintainers(self):
        for tier in ("risk:low", "risk:medium"):
            agent = review(body=agent_approval_body(tier, "reason"))
            self.assertTrue(agent["body"].startswith(AGENT_MARKER))
            self.assertFalse(decide("merge", pr(labels=["risk:high"], reviews=[agent]), PATTERNS).allow)

    def test_agent_approval_names_its_tier_and_reason(self):
        body = agent_approval_body("risk:medium", " Within the bolt 4 spec ")
        self.assertEqual(body, "Approved by the agent: risk:medium under docs/review-policy.md. Within the bolt 4 spec")

    def test_later_changes_requested_cancels_approval(self):
        reviews = [review(), review(state="CHANGES_REQUESTED")]
        self.assertFalse(decide("merge", pr(labels=["risk:high"], reviews=reviews), PATTERNS).allow)

    def test_changes_requested_blocks_an_agent_tier_too(self):
        for tier in ("risk:low", "risk:medium"):
            blocked = pr(labels=[tier], reviews=[review(state="CHANGES_REQUESTED")])
            self.assertFalse(decide("merge", blocked, PATTERNS).allow)
            self.assertFalse(decide("approve", blocked, PATTERNS).allow)

    def test_changes_requested_on_an_older_commit_still_blocks(self):
        stale = pr(labels=["risk:medium"], reviews=[review(state="CHANGES_REQUESTED", commit="old0000")])
        self.assertFalse(decide("merge", stale, PATTERNS).allow)

    def test_changes_requested_is_cleared_by_dismissal_or_a_later_approval(self):
        for later in (review(state="DISMISSED", commit="old0000"), review(commit="old0000")):
            cleared = pr(labels=["risk:medium"],
                         reviews=[review(state="CHANGES_REQUESTED", commit="old0000"), later])
            self.assertTrue(decide("merge", cleared, PATTERNS).allow)

    def test_the_agents_own_approval_does_not_clear_changes_requested(self):
        reviews = [review(state="CHANGES_REQUESTED"), review(body=agent_approval_body("risk:medium", "x"))]
        self.assertFalse(decide("merge", pr(labels=["risk:medium"], reviews=reviews), PATTERNS).allow)

    def test_low_and_medium_unprotected_allowed(self):
        for tier in ("risk:low", "risk:medium"):
            for action in ("merge", "approve"):
                with self.subTest(tier=tier, action=action):
                    self.assertTrue(decide(action, pr(labels=[tier]), PATTERNS).allow)

    def test_low_and_medium_on_a_protected_path_denied(self):
        for tier in ("risk:low", "risk:medium"):
            for action in ("merge", "approve"):
                with self.subTest(tier=tier, action=action):
                    self.assertFalse(decide(action, pr(labels=[tier], files=["build.gradle"]), PATTERNS).allow)
                    self.assertFalse(decide(action, pr(labels=[tier], files=["a.md", "gradlew"]), PATTERNS).allow)

    def test_spec_gated_files_need_medium_not_low(self):
        for path in ("gradle/libs.versions.toml", "gradle/verification-metadata.xml",
                     "androidApp/src/main/AndroidManifest.xml", "shared/src/androidMain/AndroidManifest.xml"):
            for action in ("merge", "approve"):
                with self.subTest(path=path, action=action):
                    low = decide(action, pr(labels=["risk:low"], files=["docs/notes.md", path]), REAL)
                    self.assertFalse(low.allow)
                    self.assertIn("protected change", low.reason)
                    self.assertTrue(decide(action, pr(labels=["risk:medium"], files=[path]), REAL).allow)

    def test_spec_gated_matches_whole_names_only(self):
        for path in ("docs/AndroidManifest.xml.md", "notes/gradle/libs.versions.toml", "MyAndroidManifest.xml"):
            with self.subTest(path=path):
                self.assertTrue(decide("merge", pr(labels=["risk:low"], files=[path]), REAL).allow)

    def test_maintainer_approval_still_lands_a_low_pr_on_a_spec_gated_file(self):
        approved = pr(labels=["risk:low"], files=["gradle/libs.versions.toml"], reviews=[review()])
        self.assertTrue(decide("merge", approved, REAL).allow)

    def test_high_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(labels=["risk:high"]), PATTERNS).allow)
        self.assertFalse(decide("approve", pr(labels=["risk:high"]), PATTERNS).allow)

    def test_more_than_one_risk_label_denied(self):
        for labels in (["risk:low", "risk:high"], ["risk:low", "risk:medium"], ["risk:medium", "risk:high"],
                       ["risk:medium", "risk:unknown"]):
            with self.subTest(labels=labels):
                self.assertIsNone(agent_tier(pr(labels=labels)))
                self.assertFalse(decide("merge", pr(labels=labels), PATTERNS).allow)
                self.assertFalse(decide("approve", pr(labels=labels), PATTERNS).allow)

    def test_other_labels_do_not_matter(self):
        self.assertEqual(agent_tier(pr(labels=["risk:medium", "bolt:4"])), "risk:medium")

    def test_unlabelled_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(), PATTERNS).allow)
        self.assertFalse(decide("approve", pr(), PATTERNS).allow)

    def test_any_approval_starting_with_the_agent_marker_is_the_agents(self):
        agent = review(body="Approved by the agent: something else")
        self.assertFalse(decide("merge", pr(labels=["risk:high"], reviews=[agent]), PATTERNS).allow)

    def test_bot_reviews_never_count(self):
        bot = review(user="frangipanes-bot")
        self.assertFalse(decide("merge", pr(labels=["risk:high"], reviews=[bot]), PATTERNS).allow)


if __name__ == "__main__":
    unittest.main()
