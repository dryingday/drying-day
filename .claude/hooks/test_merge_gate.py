"""Tests for merge_gate.py. Run: python3 -m unittest discover -s .claude/hooks"""

import unittest

from merge_gate import AGENT_APPROVAL_PREFIX, Pr, actions_in, decide, is_protected, parse_codeowners

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


class CommandParsing(unittest.TestCase):
    def test_finds_merges_and_approvals(self):
        cases = {
            "gh pr merge 7 --squash --delete-branch": [("merge", 7)],
            "GH_CONFIG_DIR=~/.config/gh-drying-bot gh pr merge 12": [("merge", 12)],
            "gh pr merge --squash": [("merge", None)],
            'gh pr review 7 --approve --body "x"': [("approve", 7)],
            "gh pr review 7 -a": [("approve", 7)],
            "gh pr review 7 --comment -b hi": [],
            "gh api -X PUT repos/o/r/pulls/9/merge": [("merge", 9)],
            "gh api repos/o/r/pulls/9/reviews -f event=APPROVE": [("approve", 9)],
            "gh api repos/o/r/pulls/9/reviews -f event=COMMENT": [],
            "gh api -X PUT repos/o/r/rulesets/1 --input .github/rulesets/main.json": [("ruleset", None)],
            "gh pr view 7 --json state": [],
            "git log && gh pr merge 3": [("merge", 3)],
            "cd x; gh pr merge 4": [("merge", 4)],
            "export A=1 && GH_CONFIG_DIR=y gh pr merge 5": [("merge", 5)],
            "x=$(gh pr merge 6)": [("merge", 6)],
            # Text that only mentions a command isn't one.
            "echo 'run gh pr merge 3 later'": [],
            'git commit -m "then gh pr review 7 --approve"': [],
            "grep -n 'gh api repos/o/r/pulls/9/merge' notes.md": [],
        }
        for command, expected in cases.items():
            with self.subTest(command=command):
                self.assertEqual(actions_in(command), expected)


class Decisions(unittest.TestCase):
    def test_merge_allowed_after_maintainer_approval_of_head(self):
        self.assertTrue(decide("merge", pr(files=[".github/x.yml"], reviews=[review()]), PATTERNS, "").allow)

    def test_merge_denied_when_approval_is_on_an_older_commit(self):
        d = decide("merge", pr(files=[".github/x.yml"], reviews=[review(commit="old0000")]), PATTERNS, "")
        self.assertFalse(d.allow)

    def test_agent_approval_does_not_count_as_maintainers(self):
        agent = review(body=AGENT_APPROVAL_PREFIX + " reason")
        self.assertFalse(decide("merge", pr(labels=["risk:medium"], reviews=[agent]), PATTERNS, "").allow)

    def test_later_changes_requested_cancels_approval(self):
        reviews = [review(), review(state="CHANGES_REQUESTED")]
        self.assertFalse(decide("merge", pr(reviews=reviews), PATTERNS, "").allow)

    def test_low_risk_unprotected_merge_allowed(self):
        self.assertTrue(decide("merge", pr(labels=["risk:low"]), PATTERNS, "").allow)

    def test_low_risk_label_on_protected_path_denied(self):
        self.assertFalse(decide("merge", pr(labels=["risk:low"], files=["build.gradle"]), PATTERNS, "").allow)

    def test_medium_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(labels=["risk:medium"]), PATTERNS, "").allow)

    def test_unlabelled_without_approval_denied(self):
        self.assertFalse(decide("merge", pr(), PATTERNS, "").allow)

    def test_agent_approval_allowed_for_low_unprotected_with_marked_text(self):
        command = f'gh pr review 7 --approve --body "{AGENT_APPROVAL_PREFIX} docs only"'
        self.assertTrue(decide("approve", pr(labels=["risk:low"]), PATTERNS, command).allow)

    def test_agent_approval_needs_marked_text(self):
        command = 'gh pr review 7 --approve --body "LGTM"'
        self.assertFalse(decide("approve", pr(labels=["risk:low"]), PATTERNS, command).allow)

    def test_agent_approval_denied_for_protected_or_not_low(self):
        command = f'gh pr review 7 --approve --body "{AGENT_APPROVAL_PREFIX} x"'
        self.assertFalse(decide("approve", pr(labels=["risk:low"], files=["gradlew"]), PATTERNS, command).allow)
        self.assertFalse(decide("approve", pr(labels=["risk:medium"]), PATTERNS, command).allow)

    def test_bot_reviews_never_count(self):
        bot = review(user="frangipanes-bot")
        self.assertFalse(decide("merge", pr(labels=["risk:medium"], reviews=[bot]), PATTERNS, "").allow)


if __name__ == "__main__":
    unittest.main()
