package net.kdt.pojavlaunch.customcontrols;

/**
 * Translation of the keycodes used by Fold Craft Launcher control layouts.
 *
 * Those layouts are built for LWJGL 2 (KEY_W is 17, KEY_A is 30, KEY_SPACE is 57, ...), while this
 * launcher, like PojavLauncher and Zalith Launcher, sends GLFW keycodes. Only the keys that have an
 * equivalent on both sides are translated; the others are reported as unusable.
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
final class Lwjgl2Keycodes {
    /** Returned for a keycode that has no equivalent. */
    static final int UNKNOWN = Integer.MIN_VALUE;

    /** LWJGL 2 keycode, indexed by the GLFW keycode it maps to. */
    private static final int[][] PAIRS = {
            // Letters: LWJGL 2 runs A, S, D, F... in keyboard order, GLFW runs them alphabetically.
            {30, 65}, {48, 66}, {46, 67}, {32, 68}, {18, 69}, {33, 70}, {34, 71}, {35, 72},
            {23, 73}, {36, 74}, {37, 75}, {38, 76}, {50, 77}, {49, 78}, {24, 79}, {25, 80},
            {16, 81}, {19, 82}, {31, 83}, {20, 84}, {22, 85}, {47, 86}, {17, 87}, {45, 88},
            {21, 89}, {44, 90},
            // Digits
            {11, 48}, {2, 49}, {3, 50}, {4, 51}, {5, 52}, {6, 53}, {7, 54}, {8, 55}, {9, 56}, {10, 57},
            // Editing and navigation
            {1, 256}, {28, 257}, {15, 258}, {14, 259}, {210, 260}, {211, 261}, {205, 262},
            {203, 263}, {208, 264}, {200, 265}, {201, 266}, {209, 267}, {199, 268}, {207, 269},
            {58, 280}, {70, 281}, {69, 282}, {183, 283}, {197, 284},
            // Punctuation
            {12, 45}, {13, 61}, {26, 91}, {27, 93}, {39, 59}, {40, 39}, {41, 96}, {43, 92},
            {51, 44}, {52, 46}, {53, 47}, {57, 32},
            // Function keys
            {59, 290}, {60, 291}, {61, 292}, {62, 293}, {63, 294}, {64, 295}, {65, 296}, {66, 297},
            {67, 298}, {68, 299}, {87, 300}, {88, 301}, {100, 302}, {101, 303}, {102, 304}, {103, 305},
            // Keypad
            {82, 320}, {79, 321}, {80, 322}, {81, 323}, {75, 324}, {76, 325}, {77, 326}, {71, 327},
            {72, 328}, {73, 329}, {83, 330}, {181, 331}, {55, 332}, {74, 333}, {78, 334}, {156, 335},
            {117, 336},
            // Modifiers
            {42, 340}, {29, 341}, {56, 342}, {219, 343}, {54, 344}, {157, 345}, {184, 346}, {220, 347}
    };

    private static final int[] LWJGL_TO_GLFW = new int[256];

    static {
        for (int i = 0; i < LWJGL_TO_GLFW.length; i++) LWJGL_TO_GLFW[i] = UNKNOWN;
        for (int[] pair : PAIRS) {
            if (pair[0] >= 0 && pair[0] < LWJGL_TO_GLFW.length) LWJGL_TO_GLFW[pair[0]] = pair[1];
        }
    }

    private Lwjgl2Keycodes() {}

    /**
     * GLFW keycode matching a LWJGL 2 keycode.
     *
     * @param lwjglKeycode the keycode read from a Fold Craft Launcher layout
     * @return the GLFW keycode, or {@link #UNKNOWN} when there is no equivalent
     */
    static int toGlfw(int lwjglKeycode) {
        if (lwjglKeycode < 0 || lwjglKeycode >= LWJGL_TO_GLFW.length) return UNKNOWN;
        return LWJGL_TO_GLFW[lwjglKeycode];
    }
}
