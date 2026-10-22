package net.kdt.pojavlaunch.customcontrols;

import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import net.kdt.pojavlaunch.Tools;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, application = android.app.Application.class)
public class ControlLayoutImporterTest {
    private final Point screen = new Point(1200, 600);

    @Before public void metrics() {
        Tools.currentDisplayMetrics = new DisplayMetrics();
        Tools.currentDisplayMetrics.density = 1;
        Tools.currentDisplayMetrics.widthPixels = 1200;
        Tools.currentDisplayMetrics.heightPixels = 600;
    }
    private JSONObject event(String key) throws Exception {
        return new JSONObject().put("type", "key").put("key", key);
    }
    private JSONObject button() throws Exception {
        return new JSONObject().put("text", new JSONObject().put("default", "Jump"))
                .put("position", new JSONObject().put("x", 5000).put("y", 10000))
                .put("buttonSize", new JSONObject().put("type", "dp").put("widthDp", 80).put("heightDp", 60))
                .put("clickEvents", new JSONArray().put(event("GLFW_KEY_SPACE")))
                .put("isToggleable", true);
    }
    private JSONObject layout(JSONObject layer) throws Exception {
        return new JSONObject().put("editorVersion", 12).put("layers", new JSONArray().put(layer));
    }
    private ControlLayoutImporter.Result convert(JSONObject json) throws Exception {
        return ControlLayoutImporter.convert(null, screen, json, ControlLayoutImporter.detectFormat(json));
    }
    @Test public void detectsMainFormatFamiliesWithoutMistakingOtherJson() throws Exception {
        assertEquals(ControlLayoutImporter.Format.ZALITH, ControlLayoutImporter.detectFormat(layout(new JSONObject())));
        assertEquals(ControlLayoutImporter.Format.POJAV, ControlLayoutImporter.detectFormat(new JSONObject("{\"mControlDataList\":[]}")));
        assertEquals(ControlLayoutImporter.Format.FCL, ControlLayoutImporter.detectFormat(new JSONObject("{\"viewGroups\":[]}")));
        assertEquals(ControlLayoutImporter.Format.UNKNOWN, ControlLayoutImporter.detectFormat(new JSONObject("{\"unrelated\":true}")));
    }
    @Test public void preservesZalithToggleKeysAndAdaptiveEdgePosition() throws Exception {
        ControlLayoutImporter.Result result = convert(layout(new JSONObject().put("normalButtons", new JSONArray().put(button()))));
        ControlData data = result.controls.mControlDataList.get(0);
        assertTrue(data.isToggle);
        assertEquals(KeyEvent.KEYCODE_SPACE, data.keycodes[0]);
        assertEquals("${right} * 0.5", data.dynamicX);
        assertEquals("${bottom} * 1.0", data.dynamicY);
        assertEquals(80, data.getWidth(), 0);
    }
    @Test public void importsJoystickOnlyLayoutAndCustomDirections() throws Exception {
        JSONObject directions = new JSONObject().put("north", new JSONArray().put(event("GLFW_KEY_UP")))
                .put("east", new JSONArray().put(event("GLFW_KEY_RIGHT")));
        JSONObject stick = new JSONObject().put("position", new JSONObject().put("x", 1000).put("y", 9000))
                .put("sizeType", "dp").put("sizeDp", 180).put("directionEvents", directions)
                .put("deadZoneRatio", 0.25).put("canLock", false);
        ControlLayoutImporter.Result result = convert(layout(new JSONObject().put("joystickButtons", new JSONArray().put(stick))));
        assertNotNull(result);
        ControlJoystickData data = result.controls.mJoystickDataList.get(0);
        assertEquals(180, data.getWidth(), 0);
        assertArrayEquals(new int[]{KeyEvent.KEYCODE_DPAD_UP}, data.directionKeycodes[2]);
        assertArrayEquals(new int[]{KeyEvent.KEYCODE_DPAD_RIGHT}, data.directionKeycodes[0]);
        assertEquals(0, data.directionKeycodes[4].length);
        assertEquals(25, data.deadzone);
        assertFalse(data.forwardLock);
    }
    @Test public void omittedJoystickMapsKeepLegacyDefaultsAndCloningPreservesCustomMaps() throws Exception {
        JSONObject stick = new JSONObject().put("position", new JSONObject());
        ControlJoystickData defaults = convert(layout(new JSONObject().put("joystickButtons", new JSONArray().put(stick))))
                .controls.mJoystickDataList.get(0);
        assertNull(defaults.directionKeycodes);
        assertTrue(defaults.forwardLock);
        defaults.directionKeycodes = new int[][]{new int[]{KeyEvent.KEYCODE_X}};
        ControlJoystickData copied = new ControlJoystickData(defaults);
        defaults.directionKeycodes[0][0] = KeyEvent.KEYCODE_Y;
        assertEquals(KeyEvent.KEYCODE_X, copied.directionKeycodes[0][0]);
    }
    @Test public void countsUnsupportedMacrosAndTextWidgets() throws Exception {
        JSONObject data = button().put("clickEvents", new JSONArray().put(event("GLFW_KEY_SPACE"))
                .put(new JSONObject().put("type", "send_text").put("key", "/op someone")));
        ControlLayoutImporter.Result result = convert(layout(new JSONObject().put("normalButtons", new JSONArray().put(data))
                .put("textBoxes", new JSONArray().put(new JSONObject()))));
        assertEquals(1, result.skippedEntries);
        assertEquals(1, result.unsupportedEvents);
        assertTrue(result.wasModified());
    }
    @Test public void readsOldAndNewArgbSerialization() throws Exception {
        JSONObject style = new JSONObject().put("uuid", "style").put("lightStyle", new JSONObject().put("backgroundColor", 0xFF123456L << 32));
        JSONObject json = layout(new JSONObject().put("normalButtons", new JSONArray().put(button().put("buttonStyle", "style"))))
                .put("styles", new JSONArray().put(style));
        assertEquals(0xFF123456, convert(json).controls.mControlDataList.get(0).bgColor);
        style.getJSONObject("lightStyle").put("backgroundColor", 0xFF123456L);
        assertEquals(0xFF123456, convert(json).controls.mControlDataList.get(0).bgColor);
        style.getJSONObject("lightStyle").put("backgroundColor", (int) 0xFF123456L);
        assertEquals(0xFF123456, convert(json).controls.mControlDataList.get(0).bgColor);
    }
    @Test public void preservesLayerAndWidgetVisibilityRestrictions() throws Exception {
        ControlData data = convert(layout(new JSONObject().put("visibilityType", "in_menu")
                .put("normalButtons", new JSONArray().put(button())))).controls.mControlDataList.get(0);
        assertFalse(data.displayInGame);
        assertTrue(data.displayInMenu);
    }
    @Test public void loadsLegacyNativeLayoutsWithOmittedLists() throws Exception {
        for (int version : new int[]{2, 3, 8, 9}) {
            JSONObject nativeLayout = new JSONObject().put("version", version)
                    .put("mControlDataList", new JSONArray().put(new JSONObject()
                            .put("name", "W").put("keycodes", new JSONArray().put(version == 9 ? KeyEvent.KEYCODE_W : 87))
                            .put("dynamicX", "0").put("dynamicY", "0").put("width", 50).put("height", 50)));
            CustomControls result = LayoutConverter.loadFromString(null, screen, nativeLayout.toString());
            assertEquals(9, result.version);
            assertNotNull(result.mJoystickDataList);
            assertNotNull(result.mDrawerDataList);
            assertEquals(KeyEvent.KEYCODE_W, result.mControlDataList.get(0).keycodes[0]);
        }
    }
    @Test public void preservesFclCustomJoystickBindings() throws Exception {
        JSONObject direction = new JSONObject().put("baseInfo", new JSONObject().put("sizeType", "ABSOLUTE")
                .put("absoluteWidth", 140).put("absoluteHeight", 140))
                .put("event", new JSONObject().put("upKeycode", new JSONArray().put(200))
                        .put("rightKeycode", new JSONArray().put(205)));
        JSONObject fcl = new JSONObject().put("controllerVersion", 1).put("viewGroups", new JSONArray()
                .put(new JSONObject().put("viewData", new JSONObject().put("directionList", new JSONArray().put(direction)))));
        ControlJoystickData data = convert(fcl).controls.mJoystickDataList.get(0);
        assertArrayEquals(new int[]{KeyEvent.KEYCODE_DPAD_UP}, data.directionKeycodes[2]);
        assertArrayEquals(new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT}, data.directionKeycodes[1]);
    }

    @Test public void rejectsEmptyForeignLayoutInsteadOfReportingSuccess() throws Exception {
        assertNull(convert(layout(new JSONObject())));
    }
}
