package net.kdt.pojavlaunch.customcontrols;

import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads control layouts written by other launchers and converts them into the layout model this
 * launcher uses.
 *
 * Supported sources:
 *  - this launcher / PojavLauncher / MojoLauncher ("version" + "mControlDataList"), handled by
 *    {@link LayoutConverter} directly;
 *  - Zalith Launcher 2 ("editorVersion" + "layers"/"styles"), the format of the Zalith/ZL2 control
 *    editor, whose buttons carry GLFW key events;
 *  - Fold Craft Launcher ("controllerVersion" + "viewGroups"), whose buttons carry LWJGL2 keycodes.
 *
 * The conversion is deliberately forgiving: every value is clamped into the range the launcher can
 * display, entries that cannot be represented are skipped instead of throwing, and the number of
 * skipped entries is reported so the caller can tell the user what was dropped.
 *
 * The JSON schemas of Zalith Launcher 2 and Fold Craft Launcher inform this adapter;
 * it only reads their published layout files. Both are GPL-3.0 projects, like this launcher.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */
public class ControlLayoutImporter {
    /** Highest keycode count a single button can hold in this launcher. */
    private static final int MAX_KEYCODES = 4;
    /** Positions are stored in ten-thousandths of the screen. */
    private static final float POSITION_SCALE = 10000f;
    /** Fold Craft Launcher positions are stored in thousandths of the screen. */
    private static final float FCL_POSITION_SCALE = 1000f;

    /** Returned by the keycode readers when a button has no equivalent in this launcher. */
    private static final int UNKNOWN_KEYCODE = Integer.MIN_VALUE;

    private static final float MIN_BUTTON_DP = 12f;
    private static final float MAX_BUTTON_DP = 240f;

    /** Layout formats the importer understands. */
    public enum Format {
        /** This launcher, PojavLauncher or MojoLauncher. */
        POJAV,
        /** Zalith Launcher 2 (ZL2) or a launcher using its editor format. */
        ZALITH,
        /** Fold Craft Launcher. */
        FCL,
        /** Some other JSON file, which cannot be used as a control layout. */
        UNKNOWN
    }

    /** Result of a conversion: the layout, plus what had to be dropped to build it. */
    public static class Result {
        public final CustomControls controls;
        /** Entries that could not be represented in this launcher. */
        public int skippedEntries;
        /** Number of buttons that ended up without any key or launcher action. */
        public int inertButtons;
        public int unsupportedEvents;

        Result(CustomControls controls) {
            this.controls = controls;
        }

        public boolean wasModified() {
            return skippedEntries > 0 || inertButtons > 0 || unsupportedEvents > 0;
        }
    }

    /**
     * Tell which launcher wrote a layout, by looking at the keys only that format has.
     *
     * @param layout the parsed layout file
     * @return the format of the file
     */
    public static Format detectFormat(JSONObject layout) {
        if (layout == null) return Format.UNKNOWN;
        if (layout.has("mControlDataList") || layout.has("mJoystickDataList")) return Format.POJAV;
        if (layout.has("layers") && (layout.has("editorVersion") || layout.has("styles"))) return Format.ZALITH;
        if (layout.has("viewGroups") || layout.has("controllerVersion") || layout.has("buttonStyles")) return Format.FCL;
        return Format.UNKNOWN;
    }

    /**
     * Convert a layout written by another launcher into the layout model of this launcher.
     *
     * @param context used to convert dp values, may be null
     * @param size size of the control layout view, in pixels
     * @param layout the parsed layout file
     * @param format format of the file, as returned by {@link #detectFormat(JSONObject)}
     * @return the converted layout, or null when the format has no usable content
     */
    @Nullable
    public static Result convert(Context context, Point size, JSONObject layout, Format format)
            throws JSONException {
        if (layout == null) return null;
        switch (format) {
            case ZALITH:
                return convertZalith(context, size, layout);
            case FCL:
                return convertFcl(context, size, layout);
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Zalith Launcher 2
    // ---------------------------------------------------------------------------------------

    /**
     * Convert a Zalith Launcher 2 layout. Its buttons live in layers, and draw their look from a
     * shared style list; both are flattened here, as this launcher shows one set of buttons.
     */
    private static Result convertZalith(Context context, Point size, JSONObject layout) throws JSONException {
        CustomControls controls = new CustomControls();
        controls.version = LayoutConverter.TARGET_VERSION;
        List<ControlData> buttons = new ArrayList<>();
        List<ControlDrawerData> drawers = new ArrayList<>();
        List<ControlJoystickData> joysticks = new ArrayList<>();
        Result result = new Result(controls);

        Map<String, JSONObject> styles = new HashMap<>();
        JSONArray styleArray = layout.optJSONArray("styles");
        if (styleArray != null) {
            for (int i = 0; i < styleArray.length(); i++) {
                JSONObject style = styleArray.optJSONObject(i);
                if (style == null) continue;
                String uuid = style.optString("uuid", "");
                if (!uuid.isEmpty()) styles.put(uuid, style);
            }
        }

        JSONArray layers = layout.optJSONArray("layers");
        if (layers == null) return result;

        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.optJSONObject(i);
            if (layer == null) continue;
            String layerVisibility = layer.optString("visibilityType", "always");

            JSONArray layerButtons = layer.optJSONArray("normalButtons");
            if (layerButtons != null) {
                for (int j = 0; j < layerButtons.length(); j++) {
                    JSONObject button = layerButtons.optJSONObject(j);
                    if (button == null) continue;
                    ControlData data = convertZalithButton(context, size, button, layerVisibility, styles, result);
                    if (data == null) {
                        result.skippedEntries++;
                        continue;
                    }
                    buttons.add(data);
                }
            }
            // Text rendering, layer switching and source-only styling are not executable widgets.
            result.skippedEntries += countEntries(layer.optJSONArray("textBoxes"));
            JSONArray sticks = layer.optJSONArray("joystickButtons");
            if (sticks != null) for (int j = 0; j < sticks.length(); j++) {
                JSONObject stick = sticks.optJSONObject(j);
                if (stick == null) { result.skippedEntries++; continue; }
                joysticks.add(convertZalithJoystick(context, size, stick, layerVisibility, result));
            }
        }

        if (buttons.isEmpty() && joysticks.isEmpty()) return null;
        controls.mControlDataList = buttons;
        controls.mDrawerDataList = drawers;
        controls.mJoystickDataList = joysticks;
        return result;
    }

    /** Convert a single Zalith Launcher 2 button, or return null when it holds nothing usable. */
    @Nullable
    private static ControlData convertZalithButton(Context context, Point size, JSONObject button,
                                                   String layerVisibility, Map<String, JSONObject> styles,
                                                   Result result) {
        JSONObject text = button.optJSONObject("text");
        String name = text == null ? "" : text.optString("default", "");
        if (name.isEmpty()) name = button.optString("uuid", "");

        boolean[] keycodes = new boolean[1];
        JSONArray events = button.optJSONArray("clickEvents");
        countUnsupportedEvents(events, result);
        int[] codes = zalithEventKeycodes(events, keycodes);
        JSONObject switchEvent = firstEventOfType(button.optJSONArray("clickEvents"), "switch_layer");
        // A button that only switches between Zalith layers has no equivalent: the layers it
        // switched to are imported as visible buttons instead.
        if (!keycodes[0] && switchEvent != null) return null;
        result.inertButtons += keycodes[0] ? 0 : 1;


        float[] dimensions = zalithDimensions(context, size, button.optJSONObject("buttonSize"));

        JSONObject style = findZalithStyle(styles, button.optString("buttonStyle", ""));
        int bgColor = zalithColor(style, "backgroundColor", 0x4D000000);
        int strokeColor = zalithColor(style, "borderColor", 0xFFFFFFFF);
        float strokeWidth = (float) zalithDouble(style, "borderWidth", 0);
        float cornerRadius = zalithCornerRadius(style);
        float opacity = (float) clamp(zalithDouble(style, "alpha", 1), 0, 1);

        String visibility = button.optString("visibilityType", "");
        if (visibility.isEmpty()) visibility = layerVisibility;
        boolean inGame = !"in_menu".equals(visibility) && !"in_menu".equals(layerVisibility);
        boolean inMenu = !"in_game".equals(visibility) && !"in_game".equals(layerVisibility);

        String dynamicX = zalithDynamicPosition(button.optJSONObject("position"), "x", "right");
        String dynamicY = zalithDynamicPosition(button.optJSONObject("position"), "y", "bottom");

        return new ControlData(name, codes, dynamicX, dynamicY,
                dimensions[0], dimensions[1], button.optBoolean("isToggleable", false), opacity,
                bgColor, strokeColor, strokeWidth, cornerRadius,
                inGame, inMenu, button.optBoolean("isSwipple", false),
                button.optBoolean("isPenetrable", false), null);
    }

    /** Style of a Zalith button, preferring the light style the launcher uses on screen. */
    @Nullable
    private static JSONObject findZalithStyle(Map<String, JSONObject> styles, String uuid) {
        JSONObject style = styles.get(uuid);
        if (style == null) return null;
        JSONObject light = style.optJSONObject("lightStyle");
        if (light != null) return light;
        JSONObject dark = style.optJSONObject("darkStyle");
        return dark != null ? dark : style;
    }

    private static String zalithDynamicPosition(JSONObject position, String axis, String edge) {
        double fraction = position == null ? 0 : clamp(position.optDouble(axis, 0) / POSITION_SCALE, 0, 1);
        return "${" + edge + "} * " + Double.toString(fraction);
    }

    private static void countUnsupportedEvents(JSONArray events, Result result) {
        if (events == null) return;
        int represented = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || zalithEventKeycode(event.optString("type"), event.optString("key")) == UNKNOWN_KEYCODE
                    || represented >= MAX_KEYCODES) result.unsupportedEvents++;
            else represented++;
        }
    }

    private static int[] joystickEvents(JSONArray events, Result result) {
        boolean[] hasKeys = new boolean[1];
        countUnsupportedEvents(events, result);
        int[] codes = zalithEventKeycodes(events, hasKeys);
        List<Integer> keyboard = new ArrayList<>();
        for (int code : codes) {
            if (code > 0) keyboard.add(code);
            else result.unsupportedEvents++; // Native joystick dispatch is keyboard-only.
        }
        return toKeycodeArray(keyboard);
    }

    private static ControlJoystickData convertZalithJoystick(Context context, Point size,
            JSONObject stick, String layerVisibility, Result result) {
        float pixels = "dp".equals(stick.optString("sizeType", "percentage"))
                ? dpToPx(context, (float) stick.optDouble("sizeDp", 200))
                : size.y * (float) clamp(stick.optDouble("sizePercentage", 2500) / POSITION_SCALE, 0.01, 1);
        float dp = (float) clamp(pxToDp(context, pixels), MIN_BUTTON_DP, MAX_BUTTON_DP);
        String visibility = stick.optString("visibilityType", layerVisibility);
        ControlData base = new ControlData("Joystick", new int[0],
                zalithDynamicPosition(stick.optJSONObject("position"), "x", "right"),
                zalithDynamicPosition(stick.optJSONObject("position"), "y", "bottom"), dp, dp, false);
        ControlJoystickData joystick = new ControlJoystickData(base);
        joystick.displayInGame = !"in_menu".equals(visibility) && !"in_menu".equals(layerVisibility);
        joystick.displayInMenu = !"in_game".equals(visibility) && !"in_game".equals(layerVisibility);
        joystick.deadzone = (int) clamp(stick.optDouble("deadZoneRatio", 0.5) * 100, 0, 95);
        joystick.forwardLock = stick.optBoolean("canLock", true);
        JSONObject directions = stick.optJSONObject("directionEvents");
        if (directions != null) {
            String[] names = {"east", "north_east", "north", "north_west", "west", "south_west", "south", "south_east"};
            joystick.directionKeycodes = new int[8][];
            for (int i = 0; i < names.length; i++)
                joystick.directionKeycodes[i] = joystickEvents(directions.optJSONArray(names[i]), result);
            if (directions.has("none")) result.unsupportedEvents += countEntries(directions.optJSONArray("none"));
        }
        if (stick.has("lockEvents")) joystick.forwardLockKeycodes = joystickEvents(stick.optJSONArray("lockEvents"), result);
        // Omitted direction/lock maps use the native WASD/Ctrl defaults, as in the ZL2 schema.
        return joystick;
    }

    /** Zalith sizes are either absolute dp, or a fraction of the screen sent to this pair. */
    private static float[] zalithDimensions(Context context, Point size, JSONObject buttonSize) {
        float widthDp = 50;
        float heightDp = 50;
        if (buttonSize == null) return new float[]{widthDp, heightDp};
        String type = buttonSize.optString("type", "percentage");
        if ("absolute".equals(type) || "dp".equals(type) || "wrap_content".equals(type)) {
            widthDp = (float) buttonSize.optDouble("widthDp", 50);
            heightDp = (float) buttonSize.optDouble("heightDp", 50);
        } else {
            float widthPx = zalithReferenceSize(size, buttonSize.optString("widthReference", "screen_width"))
                    * (float) buttonSize.optDouble("widthPercentage", 500) / POSITION_SCALE;
            float heightPx = zalithReferenceSize(size, buttonSize.optString("heightReference", "screen_height"))
                    * (float) buttonSize.optDouble("heightPercentage", 500) / POSITION_SCALE;
            widthDp = pxToDp(context, widthPx);
            heightDp = pxToDp(context, heightPx);
        }
        return new float[]{clamp(widthDp, MIN_BUTTON_DP, MAX_BUTTON_DP),
                clamp(heightDp, MIN_BUTTON_DP, MAX_BUTTON_DP)};
    }

    private static float zalithReferenceSize(Point size, String reference) {
        return "screen_height".equals(reference) ? size.y : size.x;
    }

    private static float zalithCornerRadius(@Nullable JSONObject style) {
        if (style == null) return 0;
        JSONObject radius = style.optJSONObject("borderRadius");
        if (radius == null) return 0;
        double sum = radius.optDouble("topStart", 0) + radius.optDouble("topEnd", 0)
                + radius.optDouble("bottomEnd", 0) + radius.optDouble("bottomStart", 0);
        return (float) clamp(sum / 4d, 0, 100);
    }

    private static int zalithColor(@Nullable JSONObject style, String key, int fallback) {
        if (style == null || !style.has(key)) return fallback;
        // The colours are plain ARGB values, which may be written as 64 bit numbers.
        long color = style.optLong(key, fallback & 0xFFFFFFFFL);
        // Old Compose exports stored ARGB in the high word; modern exports use uint32.
        return (int) ((color >>> 32) != 0 ? color >>> 32 : color);
    }

    private static double zalithDouble(@Nullable JSONObject style, String key, double fallback) {
        if (style == null) return fallback;
        return style.optDouble(key, fallback);
    }

    // ---------------------------------------------------------------------------------------
    // Fold Craft Launcher
    // ---------------------------------------------------------------------------------------

    /** Convert a Fold Craft Launcher layout, flattening its view groups into one button set. */
    private static Result convertFcl(Context context, Point size, JSONObject layout) throws JSONException {
        CustomControls controls = new CustomControls();
        controls.version = LayoutConverter.TARGET_VERSION;
        List<ControlData> buttons = new ArrayList<>();
        List<ControlDrawerData> drawers = new ArrayList<>();
        List<ControlJoystickData> joysticks = new ArrayList<>();
        Result result = new Result(controls);

        JSONArray groups = layout.optJSONArray("viewGroups");
        if (groups == null) return null;

        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String groupVisibility = group.optString("visibility", "VISIBLE");
            JSONObject viewData = group.optJSONObject("viewData");
            if (viewData == null) continue;

            JSONArray buttonList = viewData.optJSONArray("buttonList");
            if (buttonList != null) {
                for (int j = 0; j < buttonList.length(); j++) {
                    JSONObject button = buttonList.optJSONObject(j);
                    if (button == null) continue;
                    ControlData data = convertFclButton(context, size, button, groupVisibility, result);
                    if (data == null) {
                        result.skippedEntries++;
                        continue;
                    }
                    buttons.add(data);
                }
            }

            JSONArray directionList = viewData.optJSONArray("directionList");
            if (directionList != null) {
                for (int j = 0; j < directionList.length(); j++) {
                    JSONObject direction = directionList.optJSONObject(j);
                    if (direction == null) continue;
                    ControlJoystickData joystick = convertFclJoystick(context, size, direction, groupVisibility);
                    if (joystick == null) {
                        result.skippedEntries++;
                        continue;
                    }
                    joysticks.add(joystick);
                }
            }
        }

        if (buttons.isEmpty() && joysticks.isEmpty()) return null;
        controls.mControlDataList = buttons;
        controls.mDrawerDataList = drawers;
        controls.mJoystickDataList = joysticks;
        return result;
    }

    @Nullable
    private static ControlData convertFclButton(Context context, Point size, JSONObject button,
                                                String groupVisibility, Result result) {
        JSONObject baseInfo = button.optJSONObject("baseInfo");
        if (baseInfo == null) return null;
        String name = button.optString("text", "");
        if (name.isEmpty()) name = button.optString("id", "");

        int[] codes = fclEventKeycodes(button.optJSONObject("event"));
        result.inertButtons += codes.length > 0 ? 0 : 1;

        float[] position = fclPosition(size, baseInfo);
        float[] dimensions = fclDimensions(context, size, baseInfo);

        String visibility = baseInfo.optString("visibilityType", groupVisibility);
        boolean inGame = !"MENU".equalsIgnoreCase(visibility);
        boolean inMenu = !"IN_GAME".equalsIgnoreCase(visibility);

        float widthPx = dpToPx(context, dimensions[0]);
        float heightPx = dpToPx(context, dimensions[1]);
        String dynamicX = positionString(clamp(position[0], 0, Math.max(0, size.x - widthPx)));
        String dynamicY = positionString(clamp(position[1], 0, Math.max(0, size.y - heightPx)));

        return new ControlData(name, codes, dynamicX, dynamicY,
                dimensions[0], dimensions[1], false, 1f,
                0x4D000000, 0xFFFFFFFF, 0f, 0f,
                inGame, inMenu, false, false, null);
    }

    @Nullable
    private static ControlJoystickData convertFclJoystick(Context context, Point size, JSONObject direction,
                                                          String groupVisibility) {
        JSONObject baseInfo = direction.optJSONObject("baseInfo");
        if (baseInfo == null) return null;
        JSONObject event = direction.optJSONObject("event");
        int[] up = event == null ? new int[0] : fclKeycodeList(event.optJSONArray("upKeycode"));
        int[] down = event == null ? new int[0] : fclKeycodeList(event.optJSONArray("downKeycode"));
        int[] left = event == null ? new int[0] : fclKeycodeList(event.optJSONArray("leftKeycode"));
        int[] right = event == null ? new int[0] : fclKeycodeList(event.optJSONArray("rightKeycode"));

        float[] position = fclPosition(size, baseInfo);
        float[] dimensions = fclDimensions(context, size, baseInfo);
        String visibility = baseInfo.optString("visibilityType", groupVisibility);

        ControlJoystickData joystick = new ControlJoystickData();
        joystick.name = direction.optString("id", "Joystick");
        joystick.keycodes = mergeKeycodes(up, down, left, right);
        joystick.directionKeycodes = new int[][]{right, mergeKeycodes(up, right), up,
                mergeKeycodes(up, left), left, mergeKeycodes(down, left), down, mergeKeycodes(down, right)};
        float widthPx = dpToPx(context, dimensions[0]);
        float heightPx = dpToPx(context, dimensions[1]);
        joystick.dynamicX = positionString(clamp(position[0], 0, Math.max(0, size.x - widthPx)));
        joystick.dynamicY = positionString(clamp(position[1], 0, Math.max(0, size.y - heightPx)));
        // The setters take pixels, while the values read above are in dp.
        joystick.setWidth(dpToPx(context, dimensions[0]));
        joystick.setHeight(dpToPx(context, dimensions[1]));
        joystick.opacity = 1f;
        joystick.bgColor = 0x4D000000;
        joystick.strokeColor = 0xFFFFFFFF;
        joystick.strokeWidth = 0f;
        joystick.cornerRadius = 0f;
        joystick.displayInGame = !"MENU".equalsIgnoreCase(visibility);
        joystick.displayInMenu = !"IN_GAME".equalsIgnoreCase(visibility);
        joystick.absolute = false;
        joystick.forwardLock = false;
        return joystick;
    }

    private static float[] fclPosition(Point size, JSONObject baseInfo) {
        float x = (float) (baseInfo.optDouble("xPosition", 0) / FCL_POSITION_SCALE * size.x);
        float y = (float) (baseInfo.optDouble("yPosition", 0) / FCL_POSITION_SCALE * size.y);
        return new float[]{x, y};
    }

    private static float[] fclDimensions(Context context, Point size, JSONObject baseInfo) {
        float widthDp;
        float heightDp;
        if ("ABSOLUTE".equalsIgnoreCase(baseInfo.optString("sizeType", "PERCENTAGE"))) {
            widthDp = (float) baseInfo.optDouble("absoluteWidth", 50);
            heightDp = (float) baseInfo.optDouble("absoluteHeight", 50);
        } else {
            float widthPx = fclReferenceSize(size, baseInfo.optJSONObject("percentageWidth"))
                    * (float) percentSize(baseInfo.optJSONObject("percentageWidth")) / FCL_POSITION_SCALE;
            float heightPx = fclReferenceSize(size, baseInfo.optJSONObject("percentageHeight"))
                    * (float) percentSize(baseInfo.optJSONObject("percentageHeight")) / FCL_POSITION_SCALE;
            widthDp = pxToDp(context, widthPx);
            heightDp = pxToDp(context, heightPx);
        }
        return new float[]{clamp(widthDp, MIN_BUTTON_DP, MAX_BUTTON_DP),
                clamp(heightDp, MIN_BUTTON_DP, MAX_BUTTON_DP)};
    }

    private static double percentSize(@Nullable JSONObject percentage) {
        return percentage == null ? 100 : percentage.optDouble("size", 100);
    }

    private static float fclReferenceSize(Point size, @Nullable JSONObject percentage) {
        String reference = percentage == null ? "SCREEN_WIDTH" : percentage.optString("reference", "SCREEN_WIDTH");
        return "SCREEN_HEIGHT".equals(reference) ? size.y : size.x;
    }

    // ---------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------

    /**
     * Read the keycodes of a Zalith button.
     *
     * @param hasKeys set to true when at least one of the events could be converted
     */
    private static int[] zalithEventKeycodes(@Nullable JSONArray events, boolean[] hasKeys) {
        if (events == null) return new int[0];
        List<Integer> codes = new ArrayList<>(MAX_KEYCODES);
        for (int i = 0; i < events.length() && codes.size() < MAX_KEYCODES; i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String key = event.optString("key", "");
            int keycode = zalithEventKeycode(event.optString("type", ""), key);
            if (keycode == UNKNOWN_KEYCODE) continue;
            codes.add(keycode);
        }
        hasKeys[0] = !codes.isEmpty();
        return toKeycodeArray(codes);
    }

    private static int zalithEventKeycode(String type, String key) {
        if ("key".equals(type)) return launcherKeycode(GlfwKeycodes.of(key));
        if ("launcher_event".equals(type)) return launcherEventKeycode(key);
        return UNKNOWN_KEYCODE;
    }

    /**
     * Translate a GLFW keycode into the keycode the launcher works with, keeping the special
     * buttons as they are. Only the buttons of the launcher itself use negative keycodes.
     *
     * @return the keycode, or {@link #UNKNOWN_KEYCODE} when the button has no equivalent here
     */
    private static int launcherKeycode(int glfwKeycode) {
        if (glfwKeycode == GlfwKeycodes.UNKNOWN) return UNKNOWN_KEYCODE;
        if (glfwKeycode < 0) return glfwKeycode;
        int keycode = LayoutConverter.keyCodeFromGLFW(glfwKeycode);
        return keycode == KeyEvent.KEYCODE_UNKNOWN ? UNKNOWN_KEYCODE : keycode;
    }

    /** Convert the keycodes of a Fold Craft Launcher button. */
    private static int[] fclEventKeycodes(@Nullable JSONObject event) {
        if (event == null) return new int[0];
        JSONObject press = event.optJSONObject("pressEvent");
        List<Integer> codes = new ArrayList<>(MAX_KEYCODES);
        if (press != null) {
            JSONArray keycodes = press.optJSONArray("outputKeycodes");
            if (keycodes != null) {
                for (int i = 0; i < keycodes.length() && codes.size() < MAX_KEYCODES; i++) {
                    int keycode = launcherKeycode(Lwjgl2Keycodes.toGlfw(keycodes.optInt(i, -1)));
                    if (keycode == UNKNOWN_KEYCODE) continue;
                    codes.add(keycode);
                }
            }
            if (codes.isEmpty()) {
                if (press.optBoolean("openMenu", false)) codes.add(ControlData.SPECIALBTN_MENU);
                else if (press.optBoolean("input", false) || press.optBoolean("quickInput", false))
                    codes.add(ControlData.SPECIALBTN_KEYBOARD);
            }
        }
        return toKeycodeArray(codes);
    }

    private static int[] fclKeycodeList(@Nullable JSONArray array) {
        if (array == null) return new int[0];
        List<Integer> codes = new ArrayList<>(MAX_KEYCODES);
        for (int i = 0; i < array.length() && codes.size() < MAX_KEYCODES; i++) {
            int keycode = launcherKeycode(Lwjgl2Keycodes.toGlfw(array.optInt(i, -1)));
            if (keycode == UNKNOWN_KEYCODE) continue;
            codes.add(keycode);
        }
        return toKeycodeArray(codes);
    }

    /** Launcher actions of the Zalith editor, mapped onto the special buttons of this launcher. */
    private static int launcherEventKeycode(String key) {
        switch (key) {
            case "launcher.event.switch_ime": return ControlData.SPECIALBTN_KEYBOARD;
            case "launcher.event.switch_menu": return ControlData.SPECIALBTN_MENU;
            case "launcher.event.toggle_control": return ControlData.SPECIALBTN_TOGGLECTRL;
            case "launcher.event.switch_mouse": return ControlData.SPECIALBTN_VIRTUALMOUSE;
            case "launcher.event.mouse_primary": return ControlData.SPECIALBTN_MOUSEPRI;
            case "launcher.event.mouse_secondary": return ControlData.SPECIALBTN_MOUSESEC;
            case "launcher.event.mouse_middle": return ControlData.SPECIALBTN_MOUSEMID;
            case "launcher.event.scroll_up": return ControlData.SPECIALBTN_SCROLLUP;
            case "launcher.event.scroll_down": return ControlData.SPECIALBTN_SCROLLDOWN;
            default: return UNKNOWN_KEYCODE;
        }
    }

    @Nullable
    private static JSONObject firstEventOfType(@Nullable JSONArray events, String type) {
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null && type.equals(event.optString("type", ""))) return event;
        }
        return null;
    }

    private static int[] toKeycodeArray(List<Integer> codes) {
        int[] result = new int[codes.size()];
        for (int i = 0; i < result.length; i++) result[i] = codes.get(i);
        return result;
    }

    private static int[] mergeKeycodes(int[]... groups) {
        List<Integer> codes = new ArrayList<>(MAX_KEYCODES);
        for (int[] group : groups) {
            for (int code : group) {
                if (codes.size() >= MAX_KEYCODES) break;
                if (!codes.contains(code)) codes.add(code);
            }
        }
        return toKeycodeArray(codes);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static int countEntries(@Nullable JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static String positionString(float value) {
        return Integer.toString(Math.round(value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Convert pixels into density independent pixels, using the screen the layout is displayed on.
     * Falls back onto the metrics of the launcher when no context is available, and on the value
     * itself as a last resort, so a conversion never fails because of a missing screen size.
     */
    private static float pxToDp(@Nullable Context context, float px) {
        DisplayMetrics metrics = context == null ? null : context.getResources().getDisplayMetrics();
        if (metrics == null) metrics = Tools.currentDisplayMetrics;
        if (metrics == null || metrics.density == 0) return px;
        return px / metrics.density;
    }

    private static float dpToPx(@Nullable Context context, float dp) {
        DisplayMetrics metrics = context == null ? null : context.getResources().getDisplayMetrics();
        if (metrics == null) metrics = Tools.currentDisplayMetrics;
        if (metrics == null) return dp;
        return dp * metrics.density;
    }
}
