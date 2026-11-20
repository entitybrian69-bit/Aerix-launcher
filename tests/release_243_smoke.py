#!/usr/bin/env python3
"""2.4.3 has to install over what people already have, and has to be the version it says it is.

Three separate things decide whether an update lands, and all three have failed before:

1. **versionCode must go up.** It is derived from versionName in build.gradle, so a version that
   matches or trails a release cannot install over it at all.
2. **The in-app updater must accept the archive.** It refused every -noruntime build, because the
   flavor appends a suffix to versionName and the check compared that against the bare tag; and it
   demanded identical signer sets, which is stricter than Android and would have refused the
   double-signed transition build that moves old installs across.
3. **Nothing may slip back to the retired key.** The 2.4.3 cut is signed by the current key alone,
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
check(version == "2.4.3", f"this release is 2.4.3, version.properties says {version}")
check("def AERIX_VERSION_CODE = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]"
      in gradle,
      "the versionCode must keep deriving from versionName, or the two can drift apart")
parts = [int(p) for p in version.split(".")]
code = parts[0] * 10000 + parts[1] * 100 + parts[2]
check(code == 20403, f"2.4.3 must be versionCode 20403, got {code}")
check(code > 20402, "it must be above 2.4.1's 20402, or Android refuses it as a downgrade")
check((ROOT / "docs/releases/v2.4.3.md").is_file(),
      "the publish step requires docs/releases/v2.4.3.md and fails without it")

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
check("toSet() == newSignatures.map { it.toCharsString() }.toSet()" not in updater,
      "the exact-set requirement is the bug; it must not come back")

# ── 3. The release cut still refuses the retired key ─────────────────────────────────────────
check("LEGACY=false" in release_wf,
      "2.4.3 is signed by the current key alone: the retired key was published with its "
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
notes = (ROOT / "docs/releases/v2.4.3.md").read_text()
check("2.4.1" in notes,
      "the notes must tell 2.3.2-and-older users to install 2.4.1 first, since 2.4.3 does not "
      "carry the retired key")

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
notes = (ROOT / "docs/releases/v2.4.3.md").read_text()
check("will not install over 2.4.1" in notes,
      "the notes must not promise an in-place update that the device will refuse")
# An uninstall deletes app-specific external storage AND app-private storage on API 29+, so the notes
# must not tell the user their files survive it. Export first is the only honest instruction.
check("Uninstalling deletes all of it" in notes and "**Export**" in notes,
      "the notes must warn that an uninstall deletes instances and accounts, and say to export first")
# The advice has to describe a button that exists. Export is PojavInstanceBridge.exportModpack, reached
# from an instance in the Library; if that goes away the notes are telling the user to press nothing.
bridge_src = (JAVA / "aerix/bridge/PojavInstanceBridge.kt").read_text()
library_src = (JAVA / "aerix/ui/screens/LibraryScreen.kt").read_text()
check("fun exportModpack(" in bridge_src and "PojavInstanceBridge.exportModpack(" in library_src,
      "the notes tell the user to Export their instance, so that button must still exist")
check("\"saves\"" in library_src,
      "and it must offer Worlds (saves/), or the export cannot carry the one thing worth saving")
check("Android.data" in notes or "Android/data" in notes,
      "and must name where the files actually are, instead of a path that only exists on Android 9")

if problems:
    print("release 2.4.3 check: FAILED")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print(f"release 2.4.3: versionCode {code}, the updater accepts its own archives, and the cut "
      "still refuses the retired key")
