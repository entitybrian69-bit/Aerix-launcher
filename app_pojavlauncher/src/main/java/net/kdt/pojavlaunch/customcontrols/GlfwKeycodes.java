package net.kdt.pojavlaunch.customcontrols;

import java.util.HashMap;
import java.util.Map;

/**
 * Names of GLFW keys, and the keycode this launcher expects for them.
 *
 * Control layouts of this launcher, of PojavLauncher and of MojoLauncher store GLFW keycodes
 * (GLFW_KEY_W is 87, GLFW_KEY_TAB is 258, ...), so the names used by the Zalith Launcher 2
 * control editor can be mapped onto them directly.
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
final class GlfwKeycodes {
    /** Returned for a key name that cannot be used as a keycode. */
    static final int UNKNOWN = Integer.MIN_VALUE;

    private static final Map<String, Integer> KEYCODES = new HashMap<>();

    private GlfwKeycodes() {}

    /**
     * Keycode of a GLFW key name.
     *
     * @param name a name such as GLFW_KEY_W, or a mouse button such as GLFW_MOUSE_BUTTON_LEFT
     * @return the keycode, or {@link #UNKNOWN} when the name is not known
     */
    static int of(String name) {
        if (name == null) return UNKNOWN;
        Integer keycode = KEYCODES.get(name);
        return keycode == null ? UNKNOWN : keycode;
    }

    private static void put(String name, int keycode) {
        KEYCODES.put(name, keycode);
    }

    static {
        // Letters, digits and function keys follow the GLFW numbering.
        for (int i = 0; i < 26; i++) put("GLFW_KEY_" + (char) ('A' + i), 65 + i);
        for (int i = 0; i <= 9; i++) put("GLFW_KEY_" + i, 48 + i);
        for (int i = 1; i <= 25; i++) put("GLFW_KEY_F" + i, 289 + i);

        put("GLFW_KEY_SPACE", 32);
        put("GLFW_KEY_APOSTROPHE", 39);
        put("GLFW_KEY_COMMA", 44);
        put("GLFW_KEY_MINUS", 45);
        put("GLFW_KEY_PERIOD", 46);
        put("GLFW_KEY_SLASH", 47);
        put("GLFW_KEY_SEMICOLON", 59);
        put("GLFW_KEY_EQUAL", 61);
        put("GLFW_KEY_LEFT_BRACKET", 91);
        put("GLFW_KEY_BACKSLASH", 92);
        put("GLFW_KEY_RIGHT_BRACKET", 93);
        put("GLFW_KEY_GRAVE_ACCENT", 96);
        put("GLFW_KEY_WORLD_1", 161);
        put("GLFW_KEY_WORLD_2", 162);

        put("GLFW_KEY_ESCAPE", 256);
        put("GLFW_KEY_ENTER", 257);
        put("GLFW_KEY_TAB", 258);
        put("GLFW_KEY_BACKSPACE", 259);
        put("GLFW_KEY_INSERT", 260);
        put("GLFW_KEY_DELETE", 261);
        put("GLFW_KEY_RIGHT", 262);
        put("GLFW_KEY_LEFT", 263);
        put("GLFW_KEY_DOWN", 264);
        put("GLFW_KEY_UP", 265);
        put("GLFW_KEY_PAGE_UP", 266);
        put("GLFW_KEY_PAGE_DOWN", 267);
        put("GLFW_KEY_HOME", 268);
        put("GLFW_KEY_END", 269);
        put("GLFW_KEY_CAPS_LOCK", 280);
        put("GLFW_KEY_SCROLL_LOCK", 281);
        put("GLFW_KEY_NUM_LOCK", 282);
        put("GLFW_KEY_PRINT_SCREEN", 283);
        put("GLFW_KEY_PAUSE", 284);

        for (int i = 0; i <= 9; i++) put("GLFW_KEY_KP_" + i, 320 + i);
        put("GLFW_KEY_KP_DECIMAL", 330);
        put("GLFW_KEY_KP_DIVIDE", 331);
        put("GLFW_KEY_KP_MULTIPLY", 332);
        put("GLFW_KEY_KP_SUBTRACT", 333);
        put("GLFW_KEY_KP_ADD", 334);
        put("GLFW_KEY_KP_ENTER", 335);
        put("GLFW_KEY_KP_EQUAL", 336);

        put("GLFW_KEY_LEFT_SHIFT", 340);
        put("GLFW_KEY_LEFT_CONTROL", 341);
        put("GLFW_KEY_LEFT_ALT", 342);
        put("GLFW_KEY_LEFT_SUPER", 343);
        put("GLFW_KEY_RIGHT_SHIFT", 344);
        put("GLFW_KEY_RIGHT_CONTROL", 345);
        put("GLFW_KEY_RIGHT_ALT", 346);
        put("GLFW_KEY_RIGHT_SUPER", 347);
        put("GLFW_KEY_MENU", 348);

        // Shorter spellings some editors write.
        put("GLFW_KEY_LSHIFT", 340);
        put("GLFW_KEY_LCONTROL", 341);
        put("GLFW_KEY_LALT", 342);
        put("GLFW_KEY_RSHIFT", 344);
        put("GLFW_KEY_RCONTROL", 345);
        put("GLFW_KEY_RALT", 346);
        put("GLFW_KEY_FN", 348);

        // Mouse buttons have no GLFW keyboard keycode: this launcher sends them as special buttons.
        put("GLFW_MOUSE_BUTTON_LEFT", ControlData.SPECIALBTN_MOUSEPRI);
        put("GLFW_MOUSE_BUTTON_RIGHT", ControlData.SPECIALBTN_MOUSESEC);
        put("GLFW_MOUSE_BUTTON_MIDDLE", ControlData.SPECIALBTN_MOUSEMID);
        put("GLFW_MOUSE_BUTTON_4", ControlData.SPECIALBTN_SCROLLUP);
        put("GLFW_MOUSE_BUTTON_5", ControlData.SPECIALBTN_SCROLLDOWN);
    }
}
