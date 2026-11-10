#!/usr/bin/env python3
"""Build, check, sign and publish a Drying Day release. See docs/releasing.md and docs/bolts/06-release.md.

    python3 scripts/release.py 1.0.0 --keystore PATH --alias NAME [--build-tools DIR]
    python3 scripts/release.py --dry-run COMMIT [--build-tools DIR]

Run from a clone of the repository, as the maintainer. The keystore password is read with getpass
and handed to apksigner on its standard input: never a file, an argument or the environment. The
dry run signs with a throwaway key and stops before publishing, so it needs no tag and no password.
"""

import argparse
import getpass
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

BUILD_FILE = "androidApp/build.gradle.kts"
UNSIGNED = "androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk"
CI_WAIT_SECONDS = 30 * 60


class ReleaseError(Exception):
    pass


# Pure helpers (tested without any tool).

def read_version(build_gradle: str) -> tuple[str, int]:
    name = re.search(r'versionName\s*=\s*"([^"]+)"', build_gradle)
    code = re.search(r"versionCode\s*=\s*(\d+)", build_gradle)
    if not name or not code:
        raise ReleaseError(f"no versionName and versionCode in {BUILD_FILE}")
    return name.group(1), int(code.group(1))


def tag_name(version: str) -> str:
    return f"v{version}"


def apk_name(version: str) -> str:
    return f"drying-day-{version}.apk"


def sha_name(version: str) -> str:
    return f"{apk_name(version)}.sha256"


def changelog_path(version_code: int) -> str:
    return f"fastlane/metadata/android/en-US/changelogs/{version_code}.txt"


def artifact_name(commit: str) -> str:
    return f"release-unsigned-{commit}"


def sha256_line(digest: str, file_name: str) -> str:
    return f"{digest}  {file_name}\n"


def parse_sha256_line(text: str) -> str:
    return text.split()[0].lower()


def require_same_hash(hashes: dict[str, str]) -> None:
    if len(set(hashes.values())) != 1:
        raise ReleaseError("unsigned APKs differ: " + ", ".join(f"{k}={v}" for k, v in hashes.items()))


def certificate_sha256(verify_output: str) -> str:
    found = re.search(r"certificate SHA-256 digest: ([0-9A-Fa-f]+)\s*$", verify_output, re.MULTILINE)
    if not found:
        raise ReleaseError("no certificate SHA-256 in apksigner's output")
    return found.group(1).lower()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sign_command(apksigner: Path, keystore: Path, alias: str, unsigned: Path, out: Path, ks_pass: str) -> list[str]:
    return [str(apksigner), "sign", "--ks", str(keystore), "--ks-key-alias", alias,
            "--ks-pass", ks_pass, "--min-sdk-version", "26",
            "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
            "--out", str(out), str(unsigned)]


def sign(apksigner: Path, keystore: Path, alias: str, unsigned: Path, out: Path, password: str | None) -> None:
    """With no password (a real run) ask for one and send it on stdin. A given password is the dry run's
    throwaway one, the only time a password is on a command line."""
    if password is None:
        secret = getpass.getpass(f"Keystore password for {keystore}: ")
        cmd, stdin = sign_command(apksigner, keystore, alias, unsigned, out, "stdin"), secret + "\n"
    else:
        cmd, stdin = sign_command(apksigner, keystore, alias, unsigned, out, f"pass:{password}"), None
    done = subprocess.run(cmd, input=stdin, text=True, capture_output=True)
    if done.returncode != 0:
        raise ReleaseError("apksigner sign failed: " + done.stderr.strip())


# Steps that call tools.

def run(cmd, cwd=None, check=True) -> str:
    print("+", " ".join(str(c) for c in cmd), flush=True)
    done = subprocess.run([str(c) for c in cmd], cwd=cwd, text=True, capture_output=True)
    if check and done.returncode != 0:
        raise ReleaseError(f"{cmd[0]} failed:\n{done.stdout}{done.stderr}")
    return done.stdout


def find_zipalign(build_tools: Path) -> Path:
    """The -P 16 check needs zipalign from build-tools 35 or later, which build-tools 34.0.0 (kept for
    apksigner, see docs/releasing.md) lacks. Use the newest sibling directory."""
    versions = sorted((d for d in build_tools.parent.iterdir() if (d / "zipalign").exists()),
                      key=lambda d: [int(n) for n in re.findall(r"\d+", d.name)])
    if not versions or [int(n) for n in re.findall(r"\d+", versions[-1].name)][0] < 35:
        raise ReleaseError("zipalign from build-tools 35 or later is needed for the -P 16 check: install one with sdkmanager")
    return versions[-1] / "zipalign"


def clone_and_build(origin: str, commit: str, parent: Path, name: str) -> Path:
    clone = parent / name
    run(["git", "clone", "--quiet", origin, clone])
    run(["git", "checkout", "--quiet", commit], cwd=clone)
    print(f"+ ./gradlew --no-build-cache assembleRelease (in {clone})", flush=True)
    done = subprocess.run(["./gradlew", "--no-build-cache", "assembleRelease"], cwd=clone, text=True)
    if done.returncode != 0:
        raise ReleaseError(f"the build in {clone} failed")
    return clone / UNSIGNED


def download_ci_apk(commit: str, branch: str, into: Path) -> Path:
    """Wait for CI's push run on this commit, then download its unsigned APK and its .sha256."""
    deadline = time.time() + CI_WAIT_SECONDS
    while True:
        runs = json.loads(run(["gh", "run", "list", "--workflow", "CI", "--event", "push", "--branch", branch,
                               "--commit", commit, "--json", "databaseId,status,conclusion"]))
        if runs and runs[0]["status"] == "completed":
            if runs[0]["conclusion"] != "success":
                raise ReleaseError(f"CI's run for {commit} ended {runs[0]['conclusion']}")
            break
        if time.time() > deadline:
            raise ReleaseError("CI didn't finish within 30 minutes")
        time.sleep(30)
    run(["gh", "run", "download", str(runs[0]["databaseId"]), "--name", artifact_name(commit), "--dir", into])
    return into / "androidApp-release-unsigned.apk"


def make_throwaway_keystore(directory: Path) -> tuple[Path, str]:
    password = secrets.token_hex(16)
    keystore = directory / "throwaway.p12"
    run(["keytool", "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12", "-alias", "dryrun",
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=Drying Day",
         "-storepass", password])
    return keystore, password


def resolve_tag(origin: str, version: str) -> str:
    tag = tag_name(version)
    out = run(["git", "ls-remote", origin, f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}"]).split("\n")
    refs = {line.split("\t")[1]: line.split("\t")[0] for line in out if line}
    commit = refs.get(f"refs/tags/{tag}^{{}}") or refs.get(f"refs/tags/{tag}")
    if not commit:
        raise ReleaseError(f"{tag} isn't on origin")
    return commit


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", nargs="?", help="the version name, e.g. 1.0.0")
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--alias")
    parser.add_argument("--dry-run", metavar="COMMIT", help="a commit on origin/main: no tag, throwaway key, no publishing")
    default_tools = Path(os.environ.get("ANDROID_HOME", "")) / "build-tools" / "34.0.0"
    parser.add_argument("--build-tools", type=Path, default=default_tools)
    args = parser.parse_args(argv)

    dry = args.dry_run is not None
    if dry == bool(args.version):
        parser.error("give a version, or --dry-run COMMIT, not both or neither")
    if not dry and not (args.keystore and args.alias):
        parser.error("--keystore and --alias are required")
    apksigner = args.build_tools / "apksigner"
    if not apksigner.exists():
        raise ReleaseError(f"{apksigner} not found: install build-tools 34.0.0 with sdkmanager, or pass --build-tools")
    zipalign = find_zipalign(args.build_tools)
    os.environ["PATH"] = f"{args.build_tools}{os.pathsep}{os.environ['PATH']}"  # apksigcopier finds apksigner here
    if not dry and not args.keystore.exists():
        raise ReleaseError(f"{args.keystore} not found")

    origin = run(["git", "remote", "get-url", "origin"]).strip()
    run(["git", "fetch", "--quiet", "--tags", "origin", "main"])
    if dry:
        commit = run(["git", "rev-parse", args.dry_run]).strip()
        run(["git", "merge-base", "--is-ancestor", commit, "origin/main"])
        branch = "main"
    else:
        commit = resolve_tag(origin, args.version)
        branch = tag_name(args.version)
    print(f"Commit {commit}")

    version, code = read_version(run(["git", "show", f"{commit}:{BUILD_FILE}"]))
    if not dry:
        if version != args.version:
            raise ReleaseError(f"versionName at {tag_name(args.version)} is {version}, not {args.version}")
        run(["git", "cat-file", "-e", f"{commit}:{changelog_path(code)}"])

    work = Path(tempfile.mkdtemp(prefix="drying-day-release-"))
    try:
        # Two clones whose paths have different lengths: a path that leaks into the build shows up as a diff.
        apk_one = clone_and_build(origin, commit, work, "a")
        longer = work / "a-longer-parent-directory-name"
        longer.mkdir()
        apk_two = clone_and_build(origin, commit, longer, "b")
        ci_apk = download_ci_apk(commit, branch, work / "ci")
        ci_recorded = parse_sha256_line((ci_apk.parent / "androidApp-release-unsigned.apk.sha256").read_text())
        hashes = {"CI .sha256 file": ci_recorded, "CI APK": sha256_file(ci_apk),
                  "Mac build 1": sha256_file(apk_one), "Mac build 2": sha256_file(apk_two)}
        for name, digest in hashes.items():
            print(f"  {digest}  {name}")
        require_same_hash(hashes)

        run([zipalign, "-c", "-P", "16", "-v", "4", apk_one])
        if dry:
            keystore, password = make_throwaway_keystore(work)
            alias = "dryrun"
        else:
            keystore, alias, password = args.keystore, args.alias, None
        signed = work / apk_name(version)
        sign(apksigner, keystore, alias, apk_one, signed, password)
        certs = run([apksigner, "verify", "--print-certs", signed])
        run(["apksigcopier", "compare", signed, "--unsigned", apk_one])
        print(f"Certificate SHA-256 (for the F-Droid recipe): {certificate_sha256(certs)}")
        print("\n".join(line for line in certs.splitlines() if "DN:" in line))

        if dry:
            print("Dry run: stopping before publishing.")
            return 0
        sha_file = work / sha_name(version)
        sha_file.write_text(sha256_line(sha256_file(signed), apk_name(version)))
        notes = work / "notes.txt"
        notes.write_text(run(["git", "show", f"{commit}:{changelog_path(code)}"]))
        run(["gh", "release", "create", tag_name(version), signed, sha_file, "--verify-tag",
             "--title", f"Drying Day {version}", "--notes-file", notes])
        print(f"Published {tag_name(version)}.")
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ReleaseError as error:
        print(f"Stopped, nothing published: {error}", file=sys.stderr)
        sys.exit(1)
