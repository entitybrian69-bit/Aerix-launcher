#!/usr/bin/env python3
"""Smoke checks for the install-with-modpack-or-client choice popup across Mx Inspire & FastClient."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
AERIX = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix"

fast_installer = (AERIX / "fastclient/FastClientInstaller.kt").read_text()
fast_screen = (AERIX / "ui/screens/FastClientScreen.kt").read_text()
modpacks_screen = (AERIX / "ui/screens/ModpacksScreen.kt").read_text()
pack_dialog = (AERIX / "ui/screens/AerixPackSearchDialog.kt").read_text()
manifest = json.loads((ROOT / "modpacks/modpacks.json").read_text())

problems = []


def check(cond, msg):
    if not cond:
        problems.append(msg)


# 1. Mx Boost (Mx Inspire modpack) exists in modpacks.json and matches FastClient 1.21.1
mx_boost = next((p for p in manifest["packs"] if p.get("id") == "mx-boost"), None)
check(mx_boost is not None, "mx-boost pack must exist in modpacks/modpacks.json")
if mx_boost is not None:
    mc_versions = [v.get("mcVersion") for v in mx_boost.get("versions", [])] or [mx_boost.get("mcVersion")]
    check("1.21.1" in mc_versions, "mx-boost must offer Minecraft 1.21.1 matching FastClient 1.21.1")
    check('FastClientBuild("1.21.1"' in fast_installer, "FastClientInstaller must ship 1.21.1")

# 2. FastClientInstaller supports adding FastClient + Fabric API safely into an existing modpack
check("suspend fun installIntoInstance(" in fast_installer,
      "FastClientInstaller must provide installIntoInstance for modpack+client installs")
check("downloadClient(build, client)" in fast_installer,
      "installIntoInstance must verify the downloaded FastClient jar via downloadClient")
check("overwrite = false" in fast_installer,
      "installIntoInstance must never overwrite existing modpack mods")
check("hasModIdInDir(modsDir, setOf(\"fabric-api\"))" in fast_installer,
      "installIntoInstance must only download Fabric API when missing from the modpack")

# 3. ModpacksScreen (Mx Boost & Our modpacks) shows the popup and downloads matching client version
check("showInstallChoice" in modpacks_screen,
      "ModpacksScreen must gate install with a popup dialog when a matching client exists")
check("FastClientInstaller.builds.firstOrNull { it.minecraft == selected.mcVersion }" in modpacks_screen,
      "ModpacksScreen must match FastClient build by selected.mcVersion")
check("selected.mcVersion in ClientVersions.RELEASES" in modpacks_screen,
      "ModpacksScreen must check Aerix Client release compatibility by selected.mcVersion")
check("Install modpack only (${selected.mcVersion})" in modpacks_screen,
      "ModpacksScreen popup must offer installing the modpack only")
check("Install with FastClient (${fastClientBuild.minecraft})" in modpacks_screen,
      "ModpacksScreen popup must offer installing with the matching FastClient version")
check("Install with Aerix Client (${selected.mcVersion})" in modpacks_screen,
      "ModpacksScreen popup must offer installing with the matching Aerix Client version")
check("FastClientInstaller.installIntoInstance(context, created, fastClientBuild)" in modpacks_screen,
      "ModpacksScreen must install the matching FastClient build into the created modpack instance")

# 4. FastClientScreen shows the popup for FastClient version cards & links matching modpacks
check("installChoiceFor" in fast_screen,
      "FastClientScreen must show an install choice popup when tapping Install on a FastClient version")
check("Install client only (FastClient ${build.minecraft})" in fast_screen,
      "FastClientScreen popup must offer standalone client install")
check("Install with ${pack.name} modpack (${version.mcVersion})" in fast_screen,
      "FastClientScreen popup must offer matching Team Aerix modpack + FastClient install")
check("Install with modpack (search ${build.minecraft})" in fast_screen,
      "FastClientScreen popup must allow searching modpacks locked to build.minecraft")
check("AerixModpackCatalog.load()" in fast_screen,
      "FastClientScreen must load AerixModpackCatalog so Mx Boost matches 1.21.1")
check("FastClientInstaller.installIntoInstance(activity, created, build)" in fast_screen,
      "FastClientScreen must add the matching FastClient build into the installed modpack instance")

# 5. AerixPackSearchDialog asks for both SAHIL and MX_INSPIRE and dispatches the right client
check("lockedMinecraft: String? = null" in pack_dialog,
      "AerixPackSearchDialog must support narrowing pack versions to a chosen Minecraft version")
check("FastClientInstaller.builds.firstOrNull { it.minecraft == offer.minecraft }" in pack_dialog,
      "AerixPackSearchDialog must match FastClient build to offer.minecraft")
check("FastClientInstaller.installIntoInstance(activity, created, build)" in pack_dialog,
      "AerixPackSearchDialog must install FastClient when MX_INSPIRE confirms Install with client")
check("AerixClientInstaller.updateOwnMods(activity, created)" in pack_dialog,
      "AerixPackSearchDialog must install Aerix Client when SAHIL confirms Install with client")

if problems:
    print("mx_inspire_install_choice_smoke: FAILED")
    for p in problems:
        print("  -", p)
    sys.exit(1)

print("mx_inspire_install_choice_smoke: all checks passed")
