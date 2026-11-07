"""Tests for merge_gate.py and maintainer.py. Run: python3 -m unittest discover -s .claude/hooks"""

import unittest

from merge_gate import AGENT_APPROVAL_PREFIX, Pr, classify, decide, is_protected, parse_codeowners

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
            f'python3 {WRAPPER} approve 7 --reason "docs only, no protected paths"',
            f"python3 /Users/x/drying-day/{WRAPPER} apply-ruleset",
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
        ]:
            with self.subTest(command=command):
                self.assertIsNone(classify(command))


class Decisions(unittest.TestCase):
    def test_merge_allowed_after_maintainer_approval_of_head(self):
        self.assertTrue(decide("merge", pr(files=[".github/x.yml"], reviews=[review()]), PATTERNS).allow)

    def test_merge_denied_when_approval_is_on_an_older_commit(self):
        self.assertFalse(decide("merge", pr(files=[".github/x.yml"], reviews=[review(commit="old0000")]), PATTERNS).allow)

    def test_agent_approval_does_not_count_as_maintainers(self):
        agent = review(body=AGENT_APPROVAL_PREFIX + " reason")
        self.assertFalse(decide("merge", pr(labels=["risk:medium"], reviews=[agent]), PATTERNS).allow)

    def test_later_changes_requested_cancels_approval(self):
        reviews = [review(), review(state="CHANGES_REQUESTED")]
        self.assertFalse(decide("merge", pr(reviews=reviews), PATTERNS).allow)

    def test_low_risk_unprotected_merge_allowed(self):
        self.assertTrue(decide("merge", pr(labels=["risk:low"]), PATTERNS).allow)

    def test_low_risk_label_on_protected_path_denied(self):
        self.assertFalse(decide("merge", pr(labels=["risk:low"], files=["build.gradle"]), PATTERNS).allow)

    def test_highest_risk_label_wins(self):
        self.assertFalse(decide("merge", pr(labels=["risk:low", "risk:high"]), PATTERNS).allow)
        self.assertFalse(decide("approve", pr(labels=["risk:low", "risk:medium"]), PATTERNS).allow)

    def test_medium_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(labels=["risk:medium"]), PATTERNS).allow)

    def test_unlabelled_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(), PATTERNS).allow)

    def test_agent_approval_allowed_only_for_low_unprotected(self):
        self.assertTrue(decide("approve", pr(labels=["risk:low"]), PATTERNS).allow)
        self.assertFalse(decide("approve", pr(labels=["risk:low"], files=["gradlew"]), PATTERNS).allow)
        self.assertFalse(decide("approve", pr(labels=["risk:medium"]), PATTERNS).allow)

    def test_bot_reviews_never_count(self):
        bot = review(user="frangipanes-bot")
        self.assertFalse(decide("merge", pr(labels=["risk:medium"], reviews=[bot]), PATTERNS).allow)


if __name__ == "__main__":
    unittest.main()
