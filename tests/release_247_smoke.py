#!/usr/bin/env python3
"""2.4.7 has to install over what people already have, and has to be the version it says it is.

Three separate things decide whether an update lands, and all three must hold:

1. **versionCode must go up.** It is derived from versionName in build.gradle, so 2.4.7 must produce
   20407 > 20406 (2.4.6) so Android never refuses it as a downgrade.
2. **The in-app updater must accept the archive.** It must accept -noruntime suffixes and apply
   AOSP's single-signer vs. multi-signer compatibility rule via LauncherUpdatePolicy.canInstallOver.
3. **Every release from 2.4.4 on is double-signed permanently.** The published 2.4.1 is
   multi-signed, and AOSP requires an identical signer set (`signaturesMatchExactly`) for updates
   over a multi-signed package, so 2.4.7 carries both the current key and the retired key.
"""
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
check(version == "2.4.7", f"this release is 2.4.7, version.properties says {version}")
check("def AERIX_VERSION_CODE = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]"
      in gradle,
      "the versionCode must keep deriving from versionName, or the two can drift apart")
parts = [int(p) for p in version.split(".")]
code = parts[0] * 10000 + parts[1] * 100 + parts[2]
check(code == 20407, f"2.4.7 must be versionCode 20407, got {code}")
check(code > 20406, "it must be above 2.4.6's 20406, or Android refuses it as a downgrade")
check((ROOT / "docs/releases/v2.4.7.md").is_file(),
      "the publish step requires docs/releases/v2.4.7.md and fails without it")

# ── 2. The in-app updater accepts the archive ────────────────────────────────────────────────
check("fun versionMatchesTag(" in policy,
      "the version rule belongs in the tested policy, not buried in a Compose class")
check('removeSuffix("-noruntime")' in policy,
      "the noruntime flavor appends a suffix to versionName; the rule must account for it")
check("LauncherUpdatePolicy.versionMatchesTag(next.versionName, update.tag)" in updater,
      "the updater must use that rule, not its own comparison")
check("next.versionName == expectedVersion" not in updater,
      "the exact-match comparison is the bug; it must not come back")

check("fun signaturesCompatible(" in policy,
      "the signer rule belongs where it can be tested")
check("installed.any { it in incoming }" in policy,
      "a single-signed install accepts any shared signer, which is how rotation history is proven")
check("LauncherUpdatePolicy.canInstallOver(" in updater,
      "the updater must ask the question Android asks, including the multi-signer case")
check("newSignatures.map { it.toCharsString() }.toSet()" not in updater,
      "the updater must not compare sets inline; canInstallOver owns that rule and its tests")

# ── 3. The release cut double-signs, on purpose and permanently ────────────────────────────────
check("LEGACY=true" in release_wf,
      "2.4.7 must be double-signed so it installs over 2.4.6, 2.4.5, and 2.4.1 in place")
check("leaked_cert_fingerprint.txt" in release_wf,
      "the workflow must still read the fingerprint record")
check("does not carry the retired certificate" in release_wf,
      "the gate must fail when the retired certificate is ABSENT, not when it is present")
marker = [l for l in release_wf.splitlines() if l.startswith("# RELEASE_MARK=")]
check(len(marker) == 1 and marker[0].split("=", 1)[1].strip() == version,
      f"RELEASE_MARK must carry {version}, or release_signing_smoke fails the cut")
condition = [l for l in release_wf.splitlines() if "RELEASE_MARK=" in l and "head_commit" in l]
check(condition and "contains(github.event.head_commit.message, 'RELEASE_MARK=')" in condition[0],
      "the trigger must accept the marker it carries")

lines = release_wf.splitlines()
try:
    start = next(i for i, l in enumerate(lines) if l.strip() == "if: >-")
except StopIteration:
    start = None
check(start is not None, "the build job's if: expression must be findable for this check")
if start is not None:
    block = []
    for line in lines[start + 1:]:
        if line.strip() and not line.startswith("      "):
            break
        block.append(line)
    offenders = [l for l in block if "#" in l]
    check(not offenders,
          "a comment inside the if: >- block is expression text, not a comment - "
          + (offenders[0].strip() if offenders else ""))

# ── The version reaches the places a player reads it ─────────────────────────────────────────
notes = (ROOT / "docs/releases/v2.4.7.md").read_text()
check("2.4.6" in notes and "2.4.1" in notes,
      "the notes must explain in-place upgrading over 2.4.6 and 2.4.1")

# ── 4. A testing build must not pretend it can update itself ────────────────────────────────
updater_src = (JAVA / "aerix/bridge/LauncherUpdater.kt").read_text()
dialog_src = (JAVA / "aerix/ui/screens/LauncherUpdateDialog.kt").read_text()
check("applicationIdSuffix '.debug'" in gradle,
      "the testing channel must still be a different package, which is why it cannot be updated")
check('fun isTestChannel(): Boolean = BuildConfig.APPLICATION_ID.endsWith(".debug")' in updater_src,
      "the updater must know when it is the testing channel")
check("releaseChannelNotice" in updater_src,
      "and it must have something to say about it instead of 'no update available'")
check("releaseChannelNotice" in dialog_src,
      "the update screen must show that notice, or it is a variable nobody reads")

# ── 5. The cut must publish where the updater looks ─────────────────────────────────────────
check("releases/latest" in updater_src,
      "the updater reads releases/latest, so a release that is not the latest is not an update")
check('gh release create "v$VERSION"' in release_wf and "--latest" in release_wf,
      "the publish step must mark the new release latest")

# ── 6. The device's own signature rule ─────────────────────────────────────────────────────────
policy_src = (JAVA / "aerix/bridge/LauncherUpdatePolicy.kt").read_text()
check("fun canInstallOver(" in policy_src,
      "the updater must ask the question Android asks, including the multi-signer case")
check("installed.toSet() == incoming.toSet()" in policy_src,
      "a multi-signed install requires an identical certificate set")
check("installedIsMultiSigned" in updater_src,
      "the launcher must detect its own multi-signed install and say so")
check("permanently" in notes,
      "the notes must not hide that every future release is double-signed too")
check("Uninstalling deletes all of it" not in notes or "sdcard" in notes,
      "if the notes mention uninstalling, they must keep the API-29 path and the data-loss warning")
check("/sdcard/games/PojavLauncher -" not in notes and "survive" not in notes.lower(),
      "the notes must not claim data survives an uninstall")

if problems:
    print("release 2.4.7 check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print(f"release 2.4.7: versionCode {code}, the updater accepts its own archives, and the cut is "
      "double-signed on purpose")
