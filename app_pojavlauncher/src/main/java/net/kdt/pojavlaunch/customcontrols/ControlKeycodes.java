package net.kdt.pojavlaunch.customcontrols;

import java.util.Arrays;

/** Key binding array operations shared by the control data and its editor. */
public final class ControlKeycodes {
    public static final int EDITOR_SLOTS = 4;

    private ControlKeycodes() { }

    /** Provide enough editable slots without removing any existing bindings. */
    public static int[] ensureEditorSlots(int[] keycodes) {
        if (keycodes != null && keycodes.length >= EDITOR_SLOTS) return keycodes;
        return copyWithEditorSlots(keycodes);
    }

    /** Copy bindings, padding missing slots with KEYCODE_UNKNOWN (zero). */
    public static int[] copyWithEditorSlots(int[] keycodes) {
        if (keycodes == null) return new int[EDITOR_SLOTS];
        return Arrays.copyOf(keycodes, Math.max(EDITOR_SLOTS, keycodes.length));
    }
}
