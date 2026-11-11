#!/usr/bin/env python3
"""A launcher that crashes on open must still know its CPU and install its own Java.

Two screenshots, one root cause. The player on Android 13 saw the display-context crash on opening the
launcher, and then "Runtime not available - Failed to install Java 8. Please check your Internet
connection" with Java 8/17/21 all showing Not Installed - on a *full* APK, which ships a runtime inside
itself. Their internet was fine.

The reason is the shape `PojavApplication.onCreate` used to have: one try block, in this order -

    LauncherPreferences.loadPreferences(this);          <- threw (the display query)
    Tools.DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture();
    AsyncAssetManager.unpackRuntime(getAssets());       <- the bundled JRE install

- so the throw jumped past both of the lines below it into `catch (Throwable)`. `DEVICE_ARCHITECTURE` is
an `int`, so an unassigned one is 0, and 0 is not an architecture (they are 0x1/0x2/0x4/0x8). Every
runtime file name is then built as `"bin-" + archAsString(0) + ".tar.xz"`, which matches nothing in the
bundle and nothing in the download - so the *bundled* install failed silently in a log line, and the
*manual* install failed with a sentence blaming the player's connection.

What is pinned here, in order of how it went wrong:

  1. the three startup steps are separate attempts, so no optional read can cost the runtime;
  2. the architecture is computed on demand rather than only if an earlier line ran;
  3. nobody reads the raw field any more;
  4. an install failure the launcher can explain carries the explanation as its cause, because
     "check your Internet connection" is the only thing the dialog can say without one;
  5. the crash screen keeps the trace instead of scolding about a permission that no longer exists.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
failures = []


def read(rel: str) -> str:
    return (SRC / rel).read_text()


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def locate(source: str, needle: str, what: str) -> int:
    """Index that reports a missing anchor as a failure instead of a traceback."""
    if needle not in source:
        check(False, f"{what}: `{needle}` is not in the file, so the structure this test measures is gone")
        return 0
    return source.index(needle)


# ---- 1. startup: three attempts, one report ----
app = read("PojavApplication.java")
check("Throwable startupFailure = null;" in app,
      "PojavApplication must collect the startup failure instead of letting the first throw jump past "
      "the rest of onCreate")
prefs_at = locate(app, "LauncherPreferences.loadPreferences(this);", "PojavApplication")
arch_at = locate(app, "Tools.DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture();", "PojavApplication")
unpack_at = locate(app, "AsyncAssetManager.unpackRuntime(getAssets());", "PojavApplication")
report_at = locate(app, "if (startupFailure != null) {", "PojavApplication")

check(prefs_at < arch_at < unpack_at < report_at,
      "PojavApplication must read preferences, then the architecture, then unpack the runtime, then "
      "report - the report moved to the end precisely so the last two always run")

# Each step sits in its own try; find the catch that closes the preferences block.
between_prefs_and_arch = app[prefs_at:arch_at]
check(between_prefs_and_arch.count("} catch (Throwable throwable) {") == 1,
      "the preference load must end in its own catch, not share one with the steps after it")
between_arch_and_unpack = app[arch_at:unpack_at]
check(between_arch_and_unpack.count("} catch (Throwable throwable) {") == 1,
      "the architecture read must end in its own catch too")
check("if (startupFailure == null) startupFailure = throwable;" in between_arch_and_unpack,
      "a later step must not overwrite the first failure - the first one is the cause, the rest are noise")
# The first step claims the failure outright; the two after it only take it if nobody has spoken yet,
# so the dialog shows the cause rather than the third symptom of it.
plain_claims = len(re.findall(r"\n\s*startupFailure = throwable;", app))
guarded_claims = app.count("if (startupFailure == null) startupFailure = throwable;")
check(plain_claims == 1 and guarded_claims == 2,
      "exactly one step owns the failure outright and the other two defer to it "
      f"(saw {plain_claims} plain, {guarded_claims} guarded)")

# ---- 2 and 3. the architecture accessor, and no raw reads left ----
tools = read("Tools.java")
if "public static int deviceArchitecture()" not in tools:
    check(False, "Tools.deviceArchitecture() must exist - the architecture has to be computed on demand")
    body = ""
else:
    body = tools[tools.index("public static int deviceArchitecture()"):]
    body = body[:body.index("\n    }")]
check("int arch = DEVICE_ARCHITECTURE;" in body, "deviceArchitecture must start from the field")
check("if (arch == 0) {" in body, "deviceArchitecture must treat 0 as 'never assigned', which is the bug")
check("arch = Architecture.getDeviceArchitecture();" in body, "deviceArchitecture must compute it lazily")
check("DEVICE_ARCHITECTURE = arch;" in body,
      "deviceArchitecture must write it back, so the cost is paid once and the field stays consistent")

raw_reads = []
for rel in ("NewJREUtil.java", "multirt/MultiRTUtils.java", "multirt/RTRecyclerViewAdapter.java",
            "tasks/AsyncAssetManager.java", "aerix/ui/screens/SettingsScreen.kt"):
    text = read(rel)
    for line in text.splitlines():
        if "Tools.DEVICE_ARCHITECTURE" in line:
            raw_reads.append(f"{rel}: {line.strip()[:70]}")
check(not raw_reads,
      "these still read the raw field instead of Tools.deviceArchitecture(): " + "; ".join(raw_reads))
check("archAsString(Tools.DEVICE_ARCHITECTURE)" not in "".join(read(r) for r in
      ("NewJREUtil.java", "tasks/AsyncAssetManager.java")),
      "a runtime file name must never be built from a possibly-unset field - that is `bin-null.tar.xz`")

# ---- 4. an explained failure carries its explanation ----
jre = read("NewJREUtil.java")
check("throwInstallFail(internalRuntime);" not in jre,
      "NewJREUtil must not be able to raise an install failure with no cause; the dialog has nothing to "
      "show then except 'check your Internet connection'")
check('private static void throwInstallFail(InternalRuntime internalRuntime, String reason)' in jre,
      "the reason-taking throwInstallFail overload is what turns 'missing entry' into something readable")
check(jre.count("initCause(") >= 3,
      "each install-failure path should attach the failure it actually saw")
check('new IOException("the runtime source has no entry named " + platformBinFile)' in jre,
      "the external-runtime signature-bundle miss must name the file it looked for")
check("failed.initCause(e);" in jre,
      "the network path must keep the IOException it caught, not convert it into a shrug")

# ---- 5. the crash screen keeps the trace without a permission ----
fatal = read("FatalErrorActivity.java")
check("Storage permission is required to save crash stack trace!" not in fatal,
      "the fatal screen must not demand a permission that Android 11+ does not offer for this")
check("private String saveIntoAppFiles(String stackTrace)" in fatal,
      "the trace has to be kept in the app's own files, which needs no permission (and no static, "
      "because getFilesDir() is an instance call)")
check("new java.io.FileWriter(out)" in fatal and 'dir, "crash-"' in fatal,
      "saveIntoAppFiles must actually write the file it promises")
check('"Could not write it to a file - use COPY below' in fatal,
      "when even the private write fails, say that and point at the clipboard")

if failures:
    for failure in failures:
        print(f"  FAIL {failure}")
    sys.exit(1)

print("startup and runtime: steps independent, architecture self-healing, install failures carry a cause, "
      "crash traces kept without a permission")
