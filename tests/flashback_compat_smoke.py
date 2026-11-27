#!/usr/bin/env python3
"""Smoke checks for automatic Flashback Fabric mod compatibility on Android ARM64."""
from pathlib import Path
import json
import struct

ROOT = Path(__file__).resolve().parent.parent

# 1. Native ARM64 ImGui + C++ runtime libraries in jniLibs
imgui_so = ROOT / "app_pojavlauncher/src/main/jniLibs/arm64-v8a/libimgui-moulberry90-java64.so"
libcxx_so = ROOT / "app_pojavlauncher/src/main/jniLibs/arm64-v8a/libc++_flashtoch.so"
for so in (imgui_so, libcxx_so):
    assert so.is_file() and so.stat().st_size > 1_000_000, f"missing or truncated {so}"
    hdr = so.read_bytes()[:20]
    assert hdr[:4] == b"\x7fELF" and hdr[4] == 2, f"{so} must be 64-bit ELF"
    e_machine = struct.unpack("<H", hdr[18:20])[0]
    assert e_machine == 0xB7, f"{so} must be AArch64 (EM_AARCH64=183), got {e_machine}"

# 2. Subproject & APK asset registration
settings = (ROOT / "settings.gradle").read_text()
assert "':aerix_flashback_compat'" in settings

app_gradle = (ROOT / "app_pojavlauncher/build.gradle").read_text()
assert 'project(":aerix_flashback_compat"), "components/aerix_flashback_compat"' in app_gradle

compat_gradle = (ROOT / "aerix_flashback_compat/build.gradle").read_text()
for excluded in ("net/fabricmc/**", "org/spongepowered/**", "com/moulberry/**", "com/mojang/**", "org/bytedeco/**", "org/lwjgl/**"):
    assert excluded in compat_gradle, f"{excluded} must be excluded from packaged JAR"

# 3. Fabric mod descriptor & mixin registry
fmod = json.loads((ROOT / "aerix_flashback_compat/src/main/resources/fabric.mod.json").read_text())
assert fmod["id"] == "aerix_flashback_compat"
assert "aerix.flashback.compat.AerixFlashbackPreLaunch" in fmod["entrypoints"]["preLaunch"]
assert "aerix_flashback_compat.mixins.json" in fmod["mixins"]

mixins_cfg = json.loads((ROOT / "aerix_flashback_compat/src/main/resources/aerix_flashback_compat.mixins.json").read_text())
assert mixins_cfg.get("required") is False
assert mixins_cfg.get("injectors", {}).get("defaultRequire") == 0
mixin_dir = ROOT / "aerix_flashback_compat/src/main/java/aerix/flashback/compat/mixin"
for m in mixins_cfg["client"]:
    src = mixin_dir / f"{m}.java"
    assert src.is_file(), f"missing mixin source {src}"
    text = src.read_text()
    assert "require = 0" in text, f"{m} must use require = 0 for cross-version safety"

# 4. Launcher bridge & GameRunner wiring
bridge = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/bridge/AerixFlashbackBridge.kt").read_text()
assert "-Dimgui.library.path=" in bridge
assert "-Dorg.bytedeco.javacpp.cachedir=" in bridge
assert "-Dfabric.addMods=" in bridge
assert "hasFlashback" in bridge

runner = (ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java").read_text()
assert "AerixFlashbackBridge.appendLaunchArgs(activity, gamedir, javaArgList)" in runner
assert "AerixFlashbackBridge.hasFlashback(gamedir)" in runner

print("flashback_compat_smoke: OK")
