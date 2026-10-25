package net.kdt.pojavlaunch.customcontrols;

public class ControlJoystickData extends ControlData {

    /* Whether the joystick can stay forward */
    public boolean forwardLock = false;
    /** Optional imported bindings, clockwise from east; null preserves legacy WASD behavior. */
    public int[][] directionKeycodes;
    public int[] forwardLockKeycodes;
    public int deadzone = 35;

    public ControlJoystickData(ControlData properties) { super(properties); }
    /*
     * Whether the finger tracking is absolute (joystick jumps to where you touched)
     * or relative (joystick stays in the center)
     */
    public boolean absolute = false;

    public ControlJoystickData(){
        super();
    }

    public ControlJoystickData(ControlJoystickData properties) {
        super(properties);
        forwardLock = properties.forwardLock;
        absolute = properties.absolute;
        deadzone = properties.deadzone;
        forwardLockKeycodes = properties.forwardLockKeycodes == null ? null : properties.forwardLockKeycodes.clone();
        if (properties.directionKeycodes != null) {
            directionKeycodes = new int[properties.directionKeycodes.length][];
            for (int i = 0; i < directionKeycodes.length; i++)
                directionKeycodes[i] = properties.directionKeycodes[i] == null ? null : properties.directionKeycodes[i].clone();
        }
    }
}
