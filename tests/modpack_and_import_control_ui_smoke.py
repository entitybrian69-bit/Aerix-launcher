#!/usr/bin/env python3
"""Smoke test verifying the redesigned landscape Modpack Browser & Import Controls screens."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
JAVA_ROOT = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"
RES_ROOT = ROOT / "app_pojavlauncher/src/main/res"


def read(path: Path) -> str:
    assert path.exists(), f"Missing file: {path}"
    return path.read_text(encoding="utf-8")


def main() -> int:
    # 1. ModpackBrowserScreen.kt
    modpack_kt = read(JAVA_ROOT / "aerix/ui/screens/ModpackBrowserScreen.kt")
    for token in [
        "fun ModpackBrowserScreen(",
        "object ModpackBrowserHost",
        "ModpackSourceFilter",
        "AerixBackground",
        "AerixWindowInsets.padding()",
        "PillTabs(",
        "AerixPrimaryButton(",
        "GlassPanel(",
        "ModpackDetailDialog(",
        "ModIconCache",
        "Constants.SOURCE_MODRINTH",
        "Constants.SOURCE_CURSEFORGE",
        "R.string.hint_search_modpack",
        "R.string.search_modpack_download_error",
        "R.string.generic_install",
        "R.drawable.ic_aerix_mark",
        "R.drawable.ic_modrinth",
        "R.drawable.ic_curseforge",
        "R.drawable.aerix_ic_home_filled",
    ]:
        assert token in modpack_kt, f"ModpackBrowserScreen.kt missing {token}"

    # 2. SearchModFragment.java
    search_frag = read(JAVA_ROOT / "fragments/SearchModFragment.java")
    for token in [
        'public static final String TAG = "SearchModFragment"',
        "ModpackBrowserHost.createView(",
        "ModpackBrowserHost.newTasksRunningState(",
        "performLocalInstall(",
        "VersionSelectorDialog.open(",
        "ProgressKeeper.addTaskCountListener",
        "Tools.backToMainMenu(",
    ]:
        assert token in search_frag, f"SearchModFragment.java missing {token}"

    # 3. LauncherActivity.java duplicate home button guard
    launcher_act = read(JAVA_ROOT / "LauncherActivity.java")
    assert "import net.kdt.pojavlaunch.fragments.SearchModFragment;" in launcher_act, (
        "LauncherActivity.java must import SearchModFragment"
    )
    assert "top instanceof SearchModFragment" in launcher_act, (
        "LauncherActivity.updateHomeButton must hide floating home button for SearchModFragment"
    )

    # 4. ImportControlScreen.kt
    import_kt = read(JAVA_ROOT / "aerix/ui/screens/ImportControlScreen.kt")
    for token in [
        "fun ImportControlScreen(",
        "class ImportControlUiState",
        "object ImportControlHost",
        "AerixBackground",
        "AerixWindowInsets.padding()",
        "GlassPanel(",
        "AerixGhostButton(",
        "AerixPrimaryButton(",
        "R.drawable.aerix_ic_sports_esports_outlined",
    ]:
        assert token in import_kt, f"ImportControlScreen.kt missing {token}"

    # 5. ImportControlActivity.java
    import_act = read(JAVA_ROOT / "ImportControlActivity.java")
    for token in [
        "extends BaseActivity",
        "AerixPreferences.INSTANCE.init(",
        "ImportControlHost.createView(",
        "public boolean setFullscreen()",
        "protected boolean shouldIgnoreNotch()",
        "TMP_IMPORT_FILE.json",
        "ControlLayoutImporter.detectFormat",
        "LayoutConverter.loadFromString",
        "ControlLayoutImporter.convert",
        "new AlertDialog.Builder(this)",
    ]:
        assert token in import_act, f"ImportControlActivity.java missing {token}"

    # 6. AndroidManifest.xml orientation & Aerix theme for ImportControlActivity
    manifest_tree = ET.parse(ROOT / "app_pojavlauncher/src/main/AndroidManifest.xml")
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    import_nodes = [
        el
        for el in manifest_tree.findall(".//activity")
        if el.attrib.get(f"{{{ns['android']}}}name") == "net.kdt.pojavlaunch.ImportControlActivity"
    ]
    assert len(import_nodes) == 1, "Expected exactly one ImportControlActivity in AndroidManifest.xml"
    act = import_nodes[0]
    assert act.attrib.get(f"{{{ns['android']}}}screenOrientation") == "sensorLandscape", (
        "ImportControlActivity must use sensorLandscape orientation"
    )
    assert act.attrib.get(f"{{{ns['android']}}}theme") == "@style/Aerix.Launcher", (
        "ImportControlActivity must use @style/Aerix.Launcher theme"
    )

    # 7. Verify all referenced string names exist in strings.xml / aerix_strings.xml
    defined_strings = set()
    for xml_name in ["strings.xml", "aerix_strings.xml"]:
        tree = ET.parse(RES_ROOT / "values" / xml_name)
        for s in tree.findall(".//string"):
            name = s.attrib.get("name")
            if name:
                defined_strings.add(name)
    for req in [
        "hint_search_modpack",
        "search_modpack_error",
        "search_modpack_no_result",
        "search_modpack_download_error",
        "generic_install",
        "aerix_nav_back_home",
        "import_control_invalid_file",
        "import_control_invalid_name",
        "import_control_verifying_file",
        "import_control_compatibility_title",
        "import_control_compatibility_warning",
        "import_control_import_button",
        "import_control_done",
    ]:
        assert req in defined_strings, f"Missing string resource: {req}"

    print("modpack_and_import_control_ui_smoke.py: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
