#!/usr/bin/env python3
"""Check every resource-qualified Home layout contains the IDs used by MainMenuFragment."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

LAYOUTS = sorted(Path("app_pojavlauncher/src/main/res").glob("layout*/fragment_launcher.xml"))
REQUIRED_IDS = {
    "news_button",
    "social_media_button",
    "custom_control_button",
    "install_jar_button",
    "share_logs_button",
    "open_files_button",
    "hero_create_button",
    "hero_library_button",
    "account_manage_button",
    "home_account_summary",
    "home_ram_label",
    "edit_profile_button",
    "play_button",
    "mc_version_spinner",
}
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def main() -> int:
    if not LAYOUTS:
        print("No fragment_launcher.xml resources found", file=sys.stderr)
        return 2
    failed = False
    for layout in LAYOUTS:
        try:
            root = ET.parse(layout).getroot()
        except ET.ParseError as error:
            print(f"{layout}: invalid XML: {error}", file=sys.stderr)
            failed = True
            continue
        found = set()
        for element in root.iter():
            value = element.attrib.get(ANDROID_ID, "")
            if value.startswith("@+id/") or value.startswith("@id/"):
                found.add(value.split("/", 1)[1])
        missing = sorted(REQUIRED_IDS - found)
        if missing:
            print(f"{layout}: missing IDs required by MainMenuFragment: {', '.join(missing)}", file=sys.stderr)
            failed = True
        else:
            print(f"Verified {layout}: all {len(REQUIRED_IDS)} Home view IDs are present.")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
