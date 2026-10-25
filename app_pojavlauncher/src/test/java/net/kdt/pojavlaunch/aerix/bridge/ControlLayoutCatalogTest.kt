package net.kdt.pojavlaunch.aerix.bridge

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class ControlLayoutCatalogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var directory: File
    private lateinit var preferences: SharedPreferences

    @Before fun setup() {
        directory = temporaryFolder.newFolder("controlmap")
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("layout-catalog-test", 0)
        preferences.edit().clear().commit()
    }

    private fun layout(name: String) = File(directory, name).also { it.writeText("{}") }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    @Test fun returningFromImportRefreshesFilesWithoutRefreshButton() {
        val original = layout("default.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        val owner = Owner()
        owner.lifecycle.addObserver(catalog)
        owner.resume()
        owner.stop()
        val imported = layout("New Controls.JSON")
        assertEquals(listOf(original), catalog.files)
        owner.resume()
        assertEquals(listOf(original, imported), catalog.files)
        // Importing does not silently replace the user's existing selection.
        assertEquals(original.absolutePath, catalog.selectedPath)
    }

    @Test fun ignoresTemporaryImportsAndDirectoriesButKeepsAllInstalledLayouts() {
        layout("TMP_IMPORT_FILE.json")
        File(directory, "folder.json").mkdir()
        layout("notes.txt")
        val z = layout("z.json")
        val a = layout("A.JSON")
        assertEquals(listOf(a, z), ControlLayoutCatalog(directory, preferences).files)
    }

    @Test fun completedStagedImportAppearsAfterResume() {
        val staged = layout("TMP_IMPORT_FILE.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        assertTrue(catalog.files.isEmpty())
        val imported = File(directory, "Imported.json")
        assertTrue(staged.renameTo(imported))
        catalog.onResume(Owner())
        assertEquals(listOf(imported), catalog.files)
    }

    @Test fun selectingLayoutUpdatesHighlightRuntimeAndPersistentDefault() {
        val first = layout("first.json")
        val second = layout("second.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        assertTrue(catalog.select(first))
        assertTrue(catalog.select(second))
        assertEquals(second.absolutePath, catalog.selectedPath)
        assertEquals(second.absolutePath, LauncherPreferences.PREF_DEFAULTCTRL_PATH)
        assertEquals(second.absolutePath, preferences.getString("defaultCtrl", null))
        assertEquals(second.absolutePath, ControlLayoutCatalog(directory, preferences).selectedPath)
    }

    @Test fun missingLayoutCannotReplaceSelection() {
        val file = layout("layout.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        val original = catalog.selectedPath
        assertTrue(file.delete())
        assertFalse(catalog.select(file))
        assertEquals(original, catalog.selectedPath)
        assertFalse(preferences.contains("defaultCtrl"))
    }

    @Test fun resumingReflectsEditorRenameAndPreferenceChange() {
        val old = layout("old.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        val renamed = File(directory, "renamed.json")
        assertTrue(old.renameTo(renamed))
        preferences.edit().putString("defaultCtrl", renamed.absolutePath).commit()
        catalog.onResume(Owner())
        assertEquals(listOf(renamed), catalog.files)
        assertEquals(renamed.absolutePath, catalog.selectedPath)
    }

    @Test fun deletingSelectedLayoutRestoresDefaultAndProtectsBuiltinFile() {
        val default = layout("default.json")
        val file = layout("custom.json")
        val catalog = ControlLayoutCatalog(directory, preferences)
        assertTrue(catalog.select(file))
        assertTrue(catalog.delete(file))
        assertEquals(default.absolutePath, catalog.selectedPath)
        assertEquals(default.absolutePath, LauncherPreferences.PREF_DEFAULTCTRL_PATH)
        assertFalse(preferences.contains("defaultCtrl"))
        assertEquals(listOf(default), catalog.files)
        assertFalse(catalog.delete(default))
        assertTrue(default.exists())
    }

    @Test fun removedObserverDoesNotKeepRefreshingDisposedScreen() {
        val catalog = ControlLayoutCatalog(directory, preferences)
        val owner = Owner()
        owner.lifecycle.addObserver(catalog)
        owner.resume()
        owner.stop()
        owner.lifecycle.removeObserver(catalog)
        layout("import.json")
        owner.resume()
        assertTrue(catalog.files.isEmpty())
    }
}
