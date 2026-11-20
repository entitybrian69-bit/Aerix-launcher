#!/usr/bin/env python3
"""2.4.2 has to install over what people already have, and has to be the version it says it is.

Three separate things decide whether an update lands, and all three have failed before:

1. **versionCode must go up.** It is derived from versionName in build.gradle, so a version that
   matches or trails a release cannot install over it at all.
2. **The in-app updater must accept the archive.** It refused every -noruntime build, because the
   flavor appends a suffix to versionName and the check compared that against the bare tag; and it
   demanded identical signer sets, which is stricter than Android and would have refused the
   double-signed transition build that moves old installs across.
3. **Nothing may slip back to the retired key.** The 2.4.2 cut is signed by the current key alone,
   which is a deliberate choice: everyone on 2.4.1 updates directly, and anyone on 2.3.2 or older
   goes through 2.4.1 once.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

version_file = (ROOT / "version.properties").read_text()
version = [l.split("=", 1)[1].strip() for l in version_file.splitlines()
           if l.startswith("versionName=")][0]
gradle = (ROOT / "app_pojavlauncher/build.gradle").read_text()
policy = (JAVA / "aerix/bridge/LauncherUpdatePolicy.kt").read_text()
updater = (JAVA / "aerix/bridge/LauncherUpdater.kt").read_text()
release_wf = (ROOT / ".github/workflows/release.yml").read_text()

problems = []


def check(condition, message):
    if not condition:
        problems.append(message)


# ── 1. The version, and a versionCode that actually goes up ───────────────────────────────────
check(version == "2.4.2", f"this release is 2.4.2, version.properties says {version}")
check("def AERIX_VERSION_CODE = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]"
      in gradle,
      "the versionCode must keep deriving from versionName, or the two can drift apart")
parts = [int(p) for p in version.split(".")]
code = parts[0] * 10000 + parts[1] * 100 + parts[2]
check(code == 20402, f"2.4.2 must be versionCode 20402, got {code}")
check(code > 20401, "it must be above 2.4.1's 20401, or Android refuses it as a downgrade")
check((ROOT / "docs/releases/v2.4.2.md").is_file(),
      "the publish step requires docs/releases/v2.4.2.md and fails without it")

# ── 2. The in-app updater accepts the archive ────────────────────────────────────────────────
check("fun versionMatchesTag(" in policy,
      "the version rule belongs in the tested policy, not buried in a Compose class")
check('removeSuffix("-noruntime")' in policy,
      "the noruntime flavor appends a suffix to versionName; the rule must account for it, or "
      "every noruntime update is refused with 'version or runtime edition does not match'")
check("LauncherUpdatePolicy.versionMatchesTag(next.versionName, update.tag)" in updater,
      "the updater must use that rule, not its own comparison")
check("next.versionName == expectedVersion" not in updater,
      "the exact-match comparison is the bug; it must not come back")

check("fun signaturesCompatible(" in policy,
      "the signer rule belongs where it can be tested")
check("installed.any { it in incoming }" in policy,
      "Android accepts an update when ANY signer matches; an exact-set comparison refuses the "
      "double-signed transition build, which is the one build that moves old installs across")
check("LauncherUpdatePolicy.signaturesCompatible(" in updater,
      "the updater must use that rule")
check("toSet() == newSignatures.map { it.toCharsString() }.toSet()" not in updater,
      "the exact-set requirement is the bug; it must not come back")

# ── 3. The release cut still refuses the retired key ─────────────────────────────────────────
check("LEGACY=false" in release_wf,
      "2.4.2 is signed by the current key alone: the retired key was published with its "
      "password and must never sign a public build again")
check("leaked_cert_fingerprint.txt" in release_wf,
      "the ban list must still be enforced")
check("is signed by the retired key" in release_wf,
      "the gate must still fail the build when it catches it")
marker = [l for l in release_wf.splitlines() if l.startswith("# RELEASE_MARK=")]
check(len(marker) == 1 and marker[0].split("=", 1)[1].strip() == version,
      f"RELEASE_MARK must carry {version}, or release_signing_smoke fails the cut")
condition = [l for l in release_wf.splitlines() if "RELEASE_MARK=" in l and "head_commit" in l]
check(condition and "contains(github.event.head_commit.message, 'RELEASE_MARK=')" in condition[0],
      "the trigger must accept the marker it carries: it once named a literal version, so a bump "
      "built nothing at all and said nothing")

# ── The version reaches the places a player reads it ─────────────────────────────────────────
notes = (ROOT / "docs/releases/v2.4.2.md").read_text()
check("2.4.1" in notes,
      "the notes must tell 2.3.2-and-older users to install 2.4.1 first, since 2.4.2 does not "
      "carry the retired key")

if problems:
    print("release 2.4.2 check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print(f"release 2.4.2: versionCode {code}, the updater accepts its own archives, and the cut "
      "still refuses the retired key")
