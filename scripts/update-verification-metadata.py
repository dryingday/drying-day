#!/usr/bin/env python3
"""Regenerate gradle/verification-metadata.xml after a dependency or plugin change.

Gradle only records the artifacts the current machine downloads. AAPT2 and the
Kotlin/Native compiler ship one build per OS, so this script also hashes the
builds other machines need: AAPT2 for Linux (CI, F-Droid), Windows and macOS,
and Kotlin/Native for both Mac architectures. Each download is checked against
the SHA-1 its repository publishes.

Run from the repository root:  python3 scripts/update-verification-metadata.py
"""

import hashlib
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

METADATA = Path("gradle/verification-metadata.xml")
GOOGLE = "https://dl.google.com/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"

GRADLE_RUNS = [
    ["check", "assembleDebug", "assembleRelease"],
    [":shared:compileKotlinIosArm64", ":shared:compileKotlinIosSimulatorArm64",
     ":shared:compileTestKotlinIosSimulatorArm64", "-Pdryingday.ios=true"],
]

# (group, name, repository, file name pattern, classifiers every machine may need)
PLATFORM_ARTIFACTS = [
    ("com.android.tools.build", "aapt2", GOOGLE, "{name}-{version}-{c}.jar", ["linux", "osx", "windows"]),
    ("org.jetbrains.kotlin", "kotlin-native-prebuilt", CENTRAL, "{name}-{version}-{c}.tar.gz",
     ["macos-aarch64", "macos-x86_64"]),
]


def gradle(args):
    cmd = ["./gradlew", "--write-verification-metadata", "sha256", "--refresh-dependencies", "-q", *args]
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)


def fetch(url):
    with urllib.request.urlopen(url, timeout=600) as response:
        return response.read()


def add_platform_builds(xml, group, name, repo, pattern, classifiers):
    component = re.search(
        rf'<component group="{re.escape(group)}" name="{re.escape(name)}" version="([^"]+)">(.*?)</component>',
        xml, re.S)
    if not component:
        sys.exit(f"{group}:{name} isn't in {METADATA}; did the Gradle runs fail?")
    version, body = component.group(1), component.group(2)
    entries = ""
    for c in classifiers:
        file_name = pattern.format(name=name, version=version, c=c)
        if f'<artifact name="{file_name}">' in body:
            continue
        url = f"{repo}/{group.replace('.', '/')}/{name}/{version}/{file_name}"
        print("  hashing", url, flush=True)
        data = fetch(url)
        published = fetch(url + ".sha1").decode().split()[0].strip()
        if hashlib.sha1(data).hexdigest() != published:
            sys.exit(f"{file_name}: SHA-1 doesn't match the one {repo} publishes. Stopping.")
        entries += (
            f'         <artifact name="{file_name}">\n'
            f'            <sha256 value="{hashlib.sha256(data).hexdigest()}" '
            f'origin="Downloaded and hashed by scripts/update-verification-metadata.py; matches its published SHA-1"/>\n'
            f'         </artifact>\n')
    # Sorted by name, as Gradle writes them, so a second run changes nothing.
    artifacts = re.findall(r'[ \t]*<artifact name="[^"]+">.*?</artifact>\n', entries + body, re.S)
    artifacts.sort(key=lambda a: re.search(r'name="([^"]+)"', a).group(1))
    return xml[:component.start(2)] + "\n" + "".join(artifacts) + "      " + xml[component.end(2):]


def main():
    if not Path("gradlew").exists():
        sys.exit("Run from the repository root.")
    for args in GRADLE_RUNS:
        gradle(args)
    xml = METADATA.read_text()
    for artifact in PLATFORM_ARTIFACTS:
        xml = add_platform_builds(xml, *artifact)
    METADATA.write_text(xml)
    print(f"Updated {METADATA}. Review the diff before committing.")


if __name__ == "__main__":
    main()
