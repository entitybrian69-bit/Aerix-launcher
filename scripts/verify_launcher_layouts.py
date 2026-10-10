#!/usr/bin/env python3
"""Check required view IDs in Home, Create, and Discover layout variants."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

RES = Path("app_pojavlauncher/src/main/res")
HOME_LAYOUTS = sorted(RES.glob("layout*/fragment_launcher.xml"))
PROFILE_LAYOUTS = sorted(RES.glob("layout*/fragment_profile_type.xml"))
DISCOVER_FILTER_LAYOUT = RES / "layout/dialog_mod_filters.xml"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"

HOME_REQUIRED_IDS = {
    "hero_create_button",
    "hero_library_button",
    "account_manage_button",
    "home_account_icon",
    "home_account_summary",
    "home_ram_label",
    "quick_tools_button",
    "edit_profile_button",
    "play_button",
    "mc_version_spinner",
}
PROFILE_REQUIRED_IDS = {
    "title_textview",
    "title_modded_textview",
    "vanilla_profile",
    "optifine_profile",
    "modded_profile_fabric",
    "modded_profile_quilt",
    "modded_profile_legacy_fabric",
    "modded_profile_forge",
    "modded_profile_neoforge",
    "modded_profile_modpack",
    "modded_profile_bta",
}
DISCOVER_REQUIRED_IDS = {
    "search_mod_source_spinner",
    "search_mod_connect_curseforge",
    "search_mod_project_type_spinner",
    "search_mod_selected_mc_version_textview",
    "search_mod_mc_version_button",
    "search_mod_apply_filters",
}


def verify(path: Path, required_ids: set[str], label: str) -> bool:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        print(f"{path}: invalid XML: {error}", file=sys.stderr)
        return False
    found = set()
    for element in root.iter():
        value = element.attrib.get(ANDROID_ID, "")
        if value.startswith("@+id/") or value.startswith("@id/"):
            found.add(value.split("/", 1)[1])
    missing = sorted(required_ids - found)
    if missing:
        print(f"{path}: missing {label} IDs: {', '.join(missing)}", file=sys.stderr)
        return False
    print(f"Verified {path}: all {len(required_ids)} {label} IDs are present.")
    return True


def verify_variants(paths: list[Path], required_ids: set[str], label: str) -> bool:
    if not paths:
        print(f"No {label} layouts found", file=sys.stderr)
        return False
    result = True
    for path in paths:
        result = verify(path, required_ids, label) and result
    return result


def main() -> int:
    passed = verify_variants(HOME_LAYOUTS, HOME_REQUIRED_IDS, "Home")
    passed = verify_variants(PROFILE_LAYOUTS, PROFILE_REQUIRED_IDS, "Create profile") and passed
    passed = verify(DISCOVER_FILTER_LAYOUT, DISCOVER_REQUIRED_IDS, "Discover filter") and passed
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
