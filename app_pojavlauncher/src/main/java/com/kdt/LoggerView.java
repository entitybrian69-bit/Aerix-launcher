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
    private boolean mAttached;

    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == VISIBLE && !mAttached) {
            mAttached = true;
            AerixLogHost.attach(this);
        }
        super.setVisibility(visibility);
        mLogVisible = visibility == VISIBLE;
        if (mAttached) {
            AerixLogHost.setVisible(mLogVisible);
        }
    }

    public boolean isLogVisible() {
        return mLogVisible;
    }

    /**
     * Defer ComposeView creation until the user opens the in-game log view (visibility == VISIBLE),
     * avoiding an eager ComposeView/Recomposer attachment during GameActivity / AWTActivity startup.
     */
    private void init() {
        super.setVisibility(GONE);
    }
}
