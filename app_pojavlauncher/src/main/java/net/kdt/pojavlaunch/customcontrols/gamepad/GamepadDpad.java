package net.kdt.pojavlaunch.customcontrols.gamepad;

import static android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC;
import static android.view.InputDevice.SOURCE_DPAD;
import static android.view.InputDevice.SOURCE_GAMEPAD;

import android.view.InputDevice;
import android.view.KeyEvent;

public class GamepadDpad {
    public static boolean isDpadEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        boolean controllerSource = event.isFromSource(SOURCE_GAMEPAD)
                || event.isFromSource(SOURCE_DPAD)
                || (device != null && (device.getSources() & SOURCE_GAMEPAD) == SOURCE_GAMEPAD);
        return controllerSource && (device == null || device.getKeyboardType() != KEYBOARD_TYPE_ALPHABETIC);
    }
}
