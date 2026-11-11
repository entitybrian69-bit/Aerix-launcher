#!/usr/bin/env python3
"""Checks that Aerix Nightly is the only testing channel and stays installable over the debug app."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher"
GRADLE = (APP / "build.gradle").read_text()
WORKFLOW = (ROOT / ".github/workflows/aerix-debug.yml").read_text()
DOCS = (ROOT / "docs/BUILD_AND_RELEASE.md").read_text()


class NightlyChannelSmoke(unittest.TestCase):
    def test_nightly_build_type_keeps_debug_identity(self):
        nightly = GRADLE.split("nightly {", 1)[1].split("\n        }", 1)[0]
        self.assertIn("initWith debug", nightly)
        self.assertIn("signingConfig signingConfigs.customDebug", nightly)
        self.assertIn('matchingFallbacks += List.of("debug")', nightly)
        self.assertIn('resValue "string", "aerix_app_label", "Aerix Nightly"', nightly)
        self.assertNotIn("applicationIdSuffix", nightly)
        self.assertIn('onVariants(selector().withBuildType("nightly"))', GRADLE)
        self.assertIn("output.versionCode.set(AERIX_DEBUG_VERSION_CODE)", GRADLE)

    def test_ci_builds_and_publishes_only_nightly(self):
        self.assertIn(":app_pojavlauncher:assembleNoruntimeNightly", WORKFLOW)
        self.assertIn("APK_NAME: Aerix-Nightly.apk", WORKFLOW)
        self.assertIn("apk/noruntime/nightly/app_pojavlauncher-noruntime-nightly.apk", WORKFLOW)
        self.assertNotIn("assembleNoruntimeDebug", WORKFLOW)
        self.assertNotIn('STAMPED_NAME="Aerix-Launcher-debug-', WORKFLOW)
        self.assertNotIn("$STAGE/DEBUG_BUILD.txt", WORKFLOW)
        self.assertIn('TAG="debug-latest"', WORKFLOW)
        self.assertIn('INFO_NAME="NIGHTLY_BUILD.txt"', WORKFLOW)
        # The testing build kept the debug identity, and the debug identity is now
        # net.al.aerixlauncher.debug: Aerix moved off net.kdt.pojavlaunch (see docs/APP_ID_MIGRATION.md).
        self.assertIn('package: name=\'net.al.aerixlauncher.debug\'', WORKFLOW)
        self.assertIn("Aerix-Launcher-debug*", WORKFLOW)
        self.assertIn('--title "Aerix Nightly (latest)"', WORKFLOW)

    def test_docs_advertise_the_nightly_download(self):
        self.assertIn("Aerix-Nightly.apk", DOCS)
        self.assertIn("noruntimeNightly", DOCS)
        self.assertNotIn("download/debug-latest/Aerix-Launcher-debug.apk", DOCS)


if __name__ == "__main__":
    unittest.main()
