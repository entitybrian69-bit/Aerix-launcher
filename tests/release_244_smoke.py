#!/usr/bin/env python3
"""2.4.4 has to install over what people already have, and has to be the version it says it is.

Three separate things decide whether an update lands, and all three have failed before:

1. **versionCode must go up.** It is derived from versionName in build.gradle, so a version that
   matches or trails a release cannot install over it at all.
2. **The in-app updater must accept the archive.** It refused every -noruntime build, because the
   flavor appends a suffix to versionName and the check compared that against the bare tag; and it
   demanded identical signer sets, which is stricter than Android and would have refused the
   double-signed transition build that moves old installs across.
3. **Every release from 2.4.4 on is double-signed permanently.** The published 2.4.1 is
   multi-signed, and AOSP requires an identical signer set (`signaturesMatchExactly`) for updates
   over a multi-signed package, so 2.4.4 carries both the current key and the retired key.
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
check(version == "2.4.4", f"this release is 2.4.4, version.properties says {version}")
check("def AERIX_VERSION_CODE = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]"
      in gradle,
      "the versionCode must keep deriving from versionName, or the two can drift apart")
parts = [int(p) for p in version.split(".")]
code = parts[0] * 10000 + parts[1] * 100 + parts[2]
check(code == 20404, f"2.4.4 must be versionCode 20404, got {code}")
check(code > 20403, "it must be above 2.4.1's 20403, or Android refuses it as a downgrade")
check((ROOT / "docs/releases/v2.4.4.md").is_file(),
      "the publish step requires docs/releases/v2.4.4.md and fails without it")

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
      "a single-signed install accepts any shared signer, which is how rotation history is proven")
# The overlap rule above is only the single-signer case. A multi-signed install needs the exact set
# back, and that is the wall the published 2.4.1 sits behind - so the updater must call the rule that
# knows both cases, not the overlap one on its own.
check("LauncherUpdatePolicy.canInstallOver(" in updater,
      "the updater must ask the question Android asks, including the multi-signer case")
# The exact-set rule is not the bug any more - canInstallOver applies it where Android applies it, to
# multi-signed installs. What must not come back is an inline set comparison in the updater bypassing
# the tested rule, in either direction.
check("newSignatures.map { it.toCharsString() }.toSet()" not in updater,
      "the updater must not compare sets inline; canInstallOver owns that rule and its tests")

# ── 3. The release cut double-signs, on purpose and permanently ────────────────────────────────
# 2.4.4 reverses the ban on purpose: the multi-signed 2.4.1 cannot be replaced by anything that does
# not carry the retired certificate too. This is the one assertion that must flip when the policy
# flips, and it is asserted here so a silent revert fails the cut.
check("LEGACY=true" in release_wf,
      "2.4.4 must be double-signed; a single-signed build cannot install over the published 2.4.1")
check("leaked_cert_fingerprint.txt" in release_wf,
      "the workflow must still read the fingerprint record")
check("does not carry the retired certificate" in release_wf,
      "the gate must now fail when the retired certificate is ABSENT, not when it is present")
marker = [l for l in release_wf.splitlines() if l.startswith("# RELEASE_MARK=")]
check(len(marker) == 1 and marker[0].split("=", 1)[1].strip() == version,
      f"RELEASE_MARK must carry {version}, or release_signing_smoke fails the cut")
condition = [l for l in release_wf.splitlines() if "RELEASE_MARK=" in l and "head_commit" in l]
check(condition and "contains(github.event.head_commit.message, 'RELEASE_MARK=')" in condition[0],
      "the trigger must accept the marker it carries: it once named a literal version, so a bump "
      "built nothing at all and said nothing")

# A `#` inside an `if: >-` block is not a comment - every line of the block is the expression. A
# comment there made the whole run fail before any job was created, which reads as "the release did
# not trigger" and is invisible from the outside.
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
          "a comment inside the if: >- block is expression text, not a comment: it makes the "
          "expression invalid and the run fails before any job exists - "
          + (offenders[0].strip() if offenders else ""))

# ── The version reaches the places a player reads it ─────────────────────────────────────────
notes = (ROOT / "docs/releases/v2.4.4.md").read_text()
check("2.4.1" in notes,
      "the notes must explain the 2.4.1 multi-signer wall that 2.4.4 solves")

# ── 4. A testing build must not pretend it can update itself ────────────────────────────────
#    Nightly and debug are net.al.aerixlauncher.debug; every release is net.al.aerixlauncher.
#    Android treats those as two different apps, so no signature makes the release install over a
#    testing build. Reporting that as "no update available" is the misleading half of the bug.
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
      "the publish step must mark the new release latest, or releases/latest keeps serving the old "
      "one and the updater honestly reports that nothing is newer")

# ── 6. The device's own signature rule, not the one we wish it were ─────────────────────────
#    AOSP SigningDetails.checkCapability: a multi-signed install needs an exact set back. 2.4.1 is
#    multi-signed, so it cannot move to a single-signed release - and the notes must say so instead
#    of promising an update that Android will refuse.
policy_src = (JAVA / "aerix/bridge/LauncherUpdatePolicy.kt").read_text()
check("fun canInstallOver(" in policy_src,
      "the updater must ask the question Android asks, including the multi-signer case")
check("installed.toSet() == incoming.toSet()" in policy_src,
      "a multi-signed install requires an identical certificate set")
check("installedIsMultiSigned" in updater_src,
      "the launcher must detect its own multi-signed install and say so")
notes = (ROOT / "docs/releases/v2.4.4.md").read_text()
check("installs over 2.4.1 in place" in notes,
      "2.4.4 is the release that installs over 2.4.1, and the notes must say so")
check("permanently" in notes,
      "the notes must not hide that every future release is double-signed too")
# 2.4.4 needs no uninstall, so the warning belongs only where the export route is still described as
# the alternative. What must never come back is the claim that an uninstall is harmless on API 29+.
check("Uninstalling deletes all of it" not in notes or "sdcard" in notes,
      "if the notes mention uninstalling, they must keep the API-29 path and the data-loss warning")
# The advice has to describe a button that exists. Export is PojavInstanceBridge.exportModpack, reached
# from an instance in the Library; if that goes away the notes are telling the user to press nothing.
bridge_src = (JAVA / "aerix/bridge/PojavInstanceBridge.kt").read_text()
library_src = (JAVA / "aerix/ui/screens/LibraryScreen.kt").read_text()
check("fun exportModpack(" in bridge_src and "PojavInstanceBridge.exportModpack(" in library_src,
      "the notes tell the user to Export their instance, so that button must still exist")
check("\"saves\"" in library_src,
      "and it must offer Worlds (saves/), or the export cannot carry the one thing worth saving")
# The old advice told users their files live in /sdcard/games/PojavLauncher, which is API 28 and below.
# That false claim is what nearly cost a player their worlds; it must not reappear anywhere.
check("/sdcard/games/PojavLauncher -" not in notes and "survive" not in notes.lower(),
      "the notes must not claim data survives an uninstall")

if problems:
    print("release 2.4.4 check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print(f"release 2.4.4: versionCode {code}, the updater accepts its own archives, and the cut is "
      "double-signed on purpose")
