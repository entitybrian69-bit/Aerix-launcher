package com.kdt;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.aerix.log.AerixLogHost;

/**
 * A class able to display logs to the user.
 * The view itself is now the Aerix log box, the port of mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/game/elements/LogBox.kt
 * via net.kdt.pojavlaunch.aerix.log.AerixLogBox, which consumes the same
 * net.kdt.pojavlaunch.Logger.setLogListener contract this view used before.
 * Visibility is the log switch: VISIBLE registers the log listener, anything else releases it, exactly
 * like the "Output" toggle the previous implementation drew.
 */
public class LoggerView extends ConstraintLayout {
    private boolean mLogVisible;

    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mLogVisible = visibility == VISIBLE;
        // Starts and stops the log box (and with it the JNI log listener) together with the view.
        AerixLogHost.setVisible(mLogVisible);
    }

    public boolean isLogVisible() {
        return mLogVisible;
    }

    /**
     * Build the Aerix log box. The box draws the log area, the close / clear / auto-scroll / jump-to-end
     * controls and the highlighted log lines; the view tree owners are installed by the host.
     */
    private void init() {
        AerixLogHost.attach(this);
        setVisibility(GONE);
    }
}
