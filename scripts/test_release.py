"""Tests for release.py and the fastlane limits. Run: python3 -m unittest discover -s scripts"""

import importlib.util
import unittest
from pathlib import Path
from unittest import mock

_spec = importlib.util.spec_from_file_location("release", Path(__file__).with_name("release.py"))
rel = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(rel)

BUILD = """
    defaultConfig {
        applicationId = "io.github.dryingday"
        versionCode = 7
        versionName = "1.2.3"
    }
"""


class PureFunctionsTest(unittest.TestCase):
    def test_reads_version_name_and_code(self):
        self.assertEqual(("1.2.3", 7), rel.read_version(BUILD))

    def test_missing_version_is_an_error(self):
        with self.assertRaises(rel.ReleaseError):
            rel.read_version("android { }")

    def test_file_names(self):
        self.assertEqual("drying-day-1.0.0.apk", rel.apk_name("1.0.0"))
        self.assertEqual("drying-day-1.0.0.apk.sha256", rel.sha_name("1.0.0"))

    def test_changelog_path_uses_the_version_code(self):
        self.assertEqual("fastlane/metadata/android/en-US/changelogs/7.txt", rel.changelog_path(7))

    def test_tag_and_artifact_names(self):
        self.assertEqual("v1.0.0", rel.tag_name("1.0.0"))
        self.assertEqual("release-unsigned-abc123", rel.artifact_name("abc123"))

    def test_sha256_line_is_sha256sum_format(self):
        self.assertEqual("ab12  drying-day-1.0.0.apk\n", rel.sha256_line("ab12", "drying-day-1.0.0.apk"))

    def test_parses_a_sha256_file(self):
        self.assertEqual("ab12", rel.parse_sha256_line("ab12  androidApp-release-unsigned.apk\n"))

    def test_hashes_must_all_match(self):
        rel.require_same_hash({"ci": "a", "mac1": "a", "mac2": "a"})
        with self.assertRaises(rel.ReleaseError) as cm:
            rel.require_same_hash({"ci": "a", "mac1": "a", "mac2": "b"})
        self.assertIn("mac2", str(cm.exception))

    def test_reads_the_certificate_fingerprint(self):
        digest = "AB" * 32
        out = f"Signer #1 certificate DN: CN=Drying Day\nSigner #1 certificate SHA-256 digest: {digest}\nWARNING: x\n"
        self.assertEqual(digest.lower(), rel.certificate_sha256(out))


class SigningPasswordTest(unittest.TestCase):
    def test_a_real_run_reads_the_password_with_getpass_and_sends_it_on_stdin(self):
        calls = []

        def fake_run(cmd, **kwargs):
            calls.append((cmd, kwargs))
            return mock.Mock(returncode=0, stdout="", stderr="")

        with mock.patch.object(rel.getpass, "getpass", return_value="s3cret") as gp, \
                mock.patch.object(rel.subprocess, "run", fake_run):
            rel.sign(Path("/bt/apksigner"), Path("/k.p12"), "alias", Path("u.apk"), Path("s.apk"), password=None)
        gp.assert_called_once()
        cmd, kwargs = calls[0]
        self.assertNotIn("s3cret", " ".join(cmd))
        self.assertEqual("s3cret\n", kwargs["input"])
        self.assertIn("stdin", cmd)

    def test_signing_flags_follow_the_spec(self):
        cmd = rel.sign_command(Path("/bt/apksigner"), Path("/k.p12"), "alias", Path("u.apk"), Path("s.apk"), "stdin")
        joined = " ".join(cmd)
        for flag in ("--min-sdk-version 26", "--v1-signing-enabled false", "--v2-signing-enabled true",
                     "--v3-signing-enabled true", "--ks-key-alias alias", "--out s.apk"):
            self.assertIn(flag, joined)


class FastlaneLimitsTest(unittest.TestCase):
    """F-Droid's limits, checked for every file that exists."""

    ROOT = Path(__file__).resolve().parent.parent / "fastlane/metadata/android/en-US"
    LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}

    def test_text_lengths(self):
        for name, limit in self.LIMITS.items():
            path = self.ROOT / name
            if path.exists():
                self.assertLessEqual(len(path.read_text().strip()), limit, name)

    def test_changelogs(self):
        for path in (self.ROOT / "changelogs").glob("*.txt"):
            self.assertTrue(path.stem.isdigit(), path.name)
            self.assertLessEqual(len(path.read_bytes()), 500, path.name)

    def test_screenshots_are_numbered_pngs(self):
        for path in (self.ROOT / "images/phoneScreenshots").glob("*"):
            self.assertRegex(path.name, r"^\d+\.png$")


if __name__ == "__main__":
    unittest.main()
