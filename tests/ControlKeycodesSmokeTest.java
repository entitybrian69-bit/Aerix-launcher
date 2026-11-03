import net.kdt.pojavlaunch.customcontrols.ControlKeycodes;
import java.util.Arrays;

public class ControlKeycodesSmokeTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(Arrays.equals(ControlKeycodes.ensureEditorSlots(null), new int[]{0, 0, 0, 0}), "null bindings");
        check(Arrays.equals(ControlKeycodes.ensureEditorSlots(new int[]{11, 22}), new int[]{11, 22, 0, 0}), "short bindings");
        int[] four = {1, 2, 3, 4};
        check(ControlKeycodes.ensureEditorSlots(four) == four, "four bindings remain intact");
        int[] many = {10, 20, 30, 40, 50, 60};
        int[] editor = ControlKeycodes.ensureEditorSlots(many);
        check(editor == many && editor.length == 6, "extra bindings retained");
        for (int i = 0; i < ControlKeycodes.EDITOR_SLOTS; i++) editor[i] += 1;
        check(editor[4] == 50 && editor[5] == 60, "editing leaves hidden bindings intact");
        int[] copy = ControlKeycodes.copyWithEditorSlots(many);
        check(Arrays.equals(many, copy) && copy != many, "clone retains all bindings independently");
        copy[4] = 99;
        check(many[4] == 50, "clone cannot alter the source");
        check(Arrays.equals(ControlKeycodes.copyWithEditorSlots(null), new int[]{0, 0, 0, 0}), "null clone");
        System.out.println("Control keycode regression tests passed");
    }
}
