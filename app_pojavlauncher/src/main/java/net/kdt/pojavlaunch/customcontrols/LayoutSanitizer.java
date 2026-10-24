package net.kdt.pojavlaunch.customcontrols;

import android.util.Log;

import java.util.Iterator;
import java.util.List;

public class LayoutSanitizer {

    // Maybe add more conditions here later?
    private static boolean isValidFormula(String formula) {
        if(formula == null) return false;
        return !formula.contains("Infinity") && !formula.contains("NaN");
    }

    private static boolean isSaneData(ControlData controlData) {
        if(controlData.getWidth() == 0 || controlData.getHeight() == 0) return false;
        return isValidFormula(controlData.dynamicX) && isValidFormula(controlData.dynamicY);
    }

    private static boolean checkEntry(Object entry) {
        if(entry instanceof ControlData) {
            return isSaneData((ControlData) entry);
        }else if(entry instanceof ControlDrawerData) {
            ControlDrawerData drawerData = (ControlDrawerData) entry;
            if(!isSaneData(drawerData.properties)) return false;
            sanitizeList(drawerData.buttonProperties);
            return true;
        }else{
            // A layout written by another launcher may hold entries this one cannot display.
            // Drop them instead of failing while the game is starting.
            Log.w("LayoutSanitizer", "Dropping unsupported control entry " +
                    (entry == null ? "null" : entry.getClass().getName()));
            return false;
        }
    }

    private static boolean sanitizeList(List<?> controlDataList) {
        if(controlDataList == null) return false;
        boolean madeChanges = false;
        Iterator<?> iterator = controlDataList.iterator();
        while(iterator.hasNext()) {
            if(!checkEntry(iterator.next())) {
                madeChanges = true;
                iterator.remove();
            }
        }
        return madeChanges;
    }

    /**
     * Check all buttons in a control layout and ensure they're sane (contain values valid enough
     * to be displayed properly). Removes any buttons deemed not sane.
     * @param controls the original control layout.
     * @return whether the sanitization process made any changes to the layout
     */
    public static boolean sanitizeLayout(CustomControls controls) {
        if (controls == null) return false;
        boolean madeChanges = sanitizeList(controls.mControlDataList);
        if(sanitizeList(controls.mDrawerDataList)) madeChanges = true;
        if(sanitizeList(controls.mJoystickDataList)) madeChanges = true;
        return madeChanges;
    }
}
