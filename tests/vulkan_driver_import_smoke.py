#!/usr/bin/env python3
"""Keep custom Vulkan driver import, selection, and game-launch wiring connected."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


manager = source("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/vulkan/VulkanDriverManager.java")
settings = source("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/SettingsScreen.kt")
instance_settings = source("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/InstanceEditorScreen.kt")
game_activity = source("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/GameActivity.java")
game_renderer = source("app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/game/renderer/GameRenderer.java")

assert "VulkanDriverManager.importDriver(activity, uri)" in settings
assert "ActivityResultContracts.OpenDocument()" in settings
assert 'title = "Add Vulkan Driver"' in settings
assert "VulkanDriverManager.installedDrivers(activity)" in settings
assert "VulkanDriverManager.installedDrivers(activity)" in instance_settings
assert "normalizeInstanceSelection" in instance_settings

assert '"libvulkan_freedreno.so"' in manager
assert "MAX_EXTRACTED_BYTES" in manager
assert "validateNativeLibraries(staging, abi)" in manager
assert "selectedDriverId(this, instanceDriverKey)" in game_activity
assert "libraryDirectory(this, vulkanDriverId)" in game_activity
assert "configureVulkanDriver(customVulkanDriverPath, useTurnip)" in game_activity
assert "appendSearchPath(searchPath, customVulkanDriverPath)" in game_renderer
assert "MojoExec.setNativeLibraryDir(searchPath.toString())" in game_renderer

print("Custom Vulkan driver import, ABI validation, selection and launch wiring passed")
