package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.util.Log;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;

public class GamepadMapStore {
    private static final File STORE_FILE = new File(Tools.DIR_DATA, "gamepad_map.json");
    private static GamepadMapStore sMapStore;
    private GamepadMap mInMenuMap;
    private GamepadMap mInGameMap;
    private static GamepadMapStore createDefault() {
        GamepadMapStore mapStore = new GamepadMapStore();
        mapStore.mInGameMap = GamepadMap.getDefaultGameMap();
        mapStore.mInMenuMap = GamepadMap.getDefaultMenuMap();
        return mapStore;
    }

    /** Load the stored maps once, before the first controller event is dispatched. */
    public static synchronized void ensureLoaded() {
        if (sMapStore == null) load();
    }

    /** Force a reload after the user changes mappings. */
    public static synchronized void load() {
        GamepadMapStore mapStore = null;
        if(STORE_FILE.exists() && STORE_FILE.canRead()) {
            try {
                String storeFileContent = Tools.read(STORE_FILE);
                mapStore = Tools.GLOBAL_GSON.fromJson(storeFileContent, GamepadMapStore.class);
            } catch (JsonParseException | IOException e) {
                Log.w("GamepadMapStore", "Map store failed to load!", e);
            }
        }
        if(mapStore == null) mapStore = createDefault();
        sMapStore = mapStore;
    }

    public static synchronized void save() throws IOException {
        ensureLoaded();
        FileUtils.ensureParentDirectory(STORE_FILE);
        String jsonData = Tools.GLOBAL_GSON.toJson(sMapStore);
        Tools.write(STORE_FILE, jsonData);
    }

    public static synchronized GamepadMap getGameMap() {
        ensureLoaded();
        return sMapStore.mInGameMap;
    }

    public static synchronized GamepadMap getMenuMap() {
        ensureLoaded();
        return sMapStore.mInMenuMap;
    }
}
