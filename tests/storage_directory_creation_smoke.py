#!/usr/bin/env python3
"""Smoke checks for Samsung/OEM scoped-storage directory creation fixes.

Guards against "Unable to create target directory" caused by:
1. Unwritable getExternalFilesDir(null) paths passing checkStorageRoot without a mkdir probe.
2. Static final sInstancePath / SHARED_DATA_DIRECTORY / sLauncherProfiles freezing to
   /sdcard/games/PojavLauncher before Tools.initStorageConstants runs.
3. Missing Files.createDirectories / stepwise mkdir fallback in FileUtils.ensureDirectory.
4. FabriclikeUtils / FastClientInstaller / PojavCreateInstanceBridge running before storage init.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

tools_src = (JAVA / "Tools.java").read_text()
file_utils_src = (JAVA / "utils/FileUtils.java").read_text()
instances_src = (JAVA / "instances/Instances.java").read_text()
instance_src = (JAVA / "instances/Instance.java").read_text()
watcher_src = (JAVA / "instances/profcompat/ProfileWatcher.java").read_text()
fabric_src = (JAVA / "modloaders/FabriclikeUtils.java").read_text()
fast_client_src = (JAVA / "aerix/fastclient/FastClientInstaller.kt").read_text()
create_bridge_src = (JAVA / "aerix/bridge/PojavCreateInstanceBridge.kt").read_text()
launcher_src = (JAVA / "LauncherActivity.java").read_text()
executor_src = (JAVA / "lifecycle/ContextExecutor.java").read_text()

problems = []


def check(cond, msg):
    if not cond:
        problems.append(msg)


check("isWritableStorageRoot(" in tools_src,
      "Tools.getPojavStorageRoot must probe directory creation instead of trusting getExternalFilesDir blindly")
check('new File(ctx.getFilesDir(), "pojav_data")' in tools_src,
      "Tools.getPojavStorageRoot must fall back to internal files/pojav_data when external storage is unwritable")
check("ensureStorageConstants(" in tools_src and "ensureStorageConstantsIfUnset(" in tools_src,
      "Tools must provide idempotent storage initialization helpers")
check("getApplication()" in executor_src,
      "ContextExecutor.getApplication() is needed by ensureStorageConstantsIfUnset()")

check("Files.createDirectories(" in file_utils_src and "createParentsAndSelf(" in file_utils_src,
      "FileUtils.ensureDirectory must fall back to Files.createDirectories and stepwise mkdir")
check('"Unable to create target directory: " + targetFile.getAbsolutePath()' in file_utils_src,
      "FileUtils.ensureDirectory must report the target path on failure")

check("private static final File sInstancePath" not in instances_src,
      "Instances.sInstancePath must not be frozen statically before initStorageConstants runs")
check("public static final File SHARED_DATA_DIRECTORY" not in instances_src,
      "Instances.SHARED_DATA_DIRECTORY must not be frozen statically before initStorageConstants runs")
check("public static File getInstancePath()" in instances_src,
      "Instances.getInstancePath() must resolve Tools.DIR_GAME_HOME dynamically")
check("Instances.getSharedDataDirectory()" in instance_src,
      "Instance.getGameDirectory() must use Instances.getSharedDataDirectory()")
check("private static final File sLauncherProfiles" not in watcher_src,
      "ProfileWatcher must not freeze sLauncherProfiles statically before initStorageConstants runs")

check("Tools.ensureStorageConstantsIfUnset()" in fabric_src,
      "FabriclikeUtils.install must ensure storage constants before creating version directories")
check("Tools.ensureStorageConstants(context)" in fast_client_src,
      "FastClientInstaller.install must ensure storage constants before creating instance/mods directories")
check("Tools.ensureStorageConstants(context)" in create_bridge_src,
      "PojavCreateInstanceBridge.performInstall must ensure storage constants before creating instance/version directories")
check("Tools.ensureStorageConstants(getApplicationContext())" in launcher_src,
      "LauncherActivity.onCreate must ensure storage constants even if restored directly")

if problems:
    print("storage directory creation smoke: FAILED")
    for p in problems:
        print("  -", p)
    sys.exit(1)

print("storage directory creation smoke: OK")
