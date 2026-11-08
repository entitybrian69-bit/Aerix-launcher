"""Guards the old-install migration flow and its non-destructive copy contract."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher"


class DataMigrationSmoke(unittest.TestCase):
    def test_importer_preflights_and_never_replaces_existing_files(self):
        migrator = (APP / "src/main/java/net/kdt/pojavlaunch/tasks/DataMigrator.java").read_text()
        self.assertIn("if (uri == null)", migrator)
        self.assertIn("DataMigrationPolicy.isTrustedSourceAuthority(authority)", migrator)
        self.assertIn("calculateBytesToCopy(source, destinationRoot, 0)", migrator)
        self.assertIn("DataMigrationPolicy.missingMegabytes(availableBytes, bytesToCopy)", migrator)
        self.assertIn("if (destinationEntry.exists())", migrator)
        self.assertIn('File.createTempFile(".aerix-migration-", ".part", parent)', migrator)
        self.assertIn("Files.move(temporary.toPath(), destination.toPath())", migrator)
        self.assertIn("Os.link(temporary.getAbsolutePath(), destination.getAbsolutePath())", migrator)
        self.assertNotIn("temporary.renameTo(destination)", migrator)
        self.assertIn("destination.exists()", migrator)
        self.assertIn("Refusing to write through a folder outside the launcher directory", migrator)
        self.assertNotIn("Tools.write(resolver.openInputStream", migrator)

    def test_only_known_launcher_providers_are_migration_sources(self):
        policy = (APP / "src/main/java/net/kdt/pojavlaunch/tasks/DataMigrationPolicy.java").read_text()
        for authority in (
            "net.kdt.pojavlaunch.scoped.gamefolder",
            "net.kdt.pojavlaunch.scoped.gamefolder.debug",
            "net.kdt.pojavlaunch.pub.scoped.gamefolder",
            "net.al.aerixlauncher.scoped.gamefolder",
            "net.al.aerixlauncher.scoped.gamefolder.debug",
            "net.al.aerixlauncher.pub.scoped.gamefolder",
        ):
            self.assertIn(f'"{authority}"', policy)

    def test_first_run_import_is_optional_and_settings_import_remains(self):
        activity = (APP / "src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").read_text()
        self.assertIn("new ActivityResultContracts.OpenDocumentTree()", activity)
        self.assertIn("R.string.migration_prompt_import", activity)
        self.assertIn("R.string.migration_prompt_later", activity)
        self.assertIn("R.string.migration_prompt_never", activity)
        self.assertIn("snoozeMigrationNotice()", activity)
        self.assertIn("MIGRATION_PICKER_SNOOZE_MILLIS", activity)
        self.assertIn("uri == null", activity)
        self.assertIn("Build.VERSION.SDK_INT < Build.VERSION_CODES.Q", activity)
        self.assertIn("R.string.migration_notice_shared", activity)

        settings = (APP / "src/main/java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceMiscellaneousFragment.java").read_text()
        self.assertIn('new DataMigrator(getLauncherActivity(), uri).migrateData()', settings)

    def test_cancellation_and_failure_leave_a_retry_path(self):
        activity = (APP / "src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").read_text()
        self.assertIn("clearMigrationNoticeSnooze();", activity)
        self.assertIn('setNegativeButton(android.R.string.cancel, (d, which) -> clearMigrationNoticeSnooze())', activity)
        migrator = (APP / "src/main/java/net/kdt/pojavlaunch/tasks/DataMigrator.java").read_text()
        self.assertIn("allowMigrationPromptRetry();", migrator)

        root = ET.parse(APP / "src/main/res/values/strings.xml").getroot()
        failure = root.find("string[@name='migration_progress_failed']").text.lower()
        self.assertIn("source installation was not changed", failure)
        self.assertIn("retry from settings", failure)

    def test_confirmation_promises_non_destructive_copy(self):
        root = ET.parse(APP / "src/main/res/values/strings.xml").getroot()
        message = root.find("string[@name='migration_progress_warning_summary']").text.lower()
        self.assertIn("existing destination files are kept", message)
        self.assertIn("source is never changed", message)
        self.assertIn("not copied", message)


if __name__ == "__main__":
    unittest.main()
