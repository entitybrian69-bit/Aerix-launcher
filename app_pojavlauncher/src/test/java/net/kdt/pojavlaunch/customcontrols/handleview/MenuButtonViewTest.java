package net.kdt.pojavlaunch.customcontrols.handleview;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class MenuButtonViewTest {
    private Activity activity;
    private FrameLayout parent;
    private DrawerPullButton button;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        activity.getSharedPreferences(MenuButtonSize.PREFERENCES, 0).edit().clear().commit();
        parent = new FrameLayout(activity);
        activity.setContentView(parent);
        button = new DrawerPullButton(activity);
        parent.addView(button, new FrameLayout.LayoutParams(-2, -2));
        layout();
    }
    private void layout() {
        parent.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1200, 700);
    }
    private SeekBar slider(View root) {
        if (root instanceof SeekBar) return (SeekBar) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            SeekBar found = slider(((ViewGroup) root).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    private void touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        button.onTouchEvent(event);
        event.recycle();
    }
    @Test public void livePreviewChangesMeasuredSizeAndPersistsOnOk() {
        button.showSizeDialog();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        slider(dialog.getWindow().getDecorView()).setProgress(MenuButtonSize.MAX_DP - MenuButtonSize.MIN_DP);
        layout();
        assertEquals(Math.round(96 * activity.getResources().getDisplayMetrics().density), button.getMeasuredWidth());
        assertTrue(button.getTranslationX() + button.getWidth() <= parent.getWidth());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(96, MenuButtonSize.read(activity));
    }
    @Test public void cancelRestoresSizeAndDoesNotSavePreview() {
        int originalWidth = button.getMeasuredWidth();
        button.showSizeDialog();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        slider(dialog.getWindow().getDecorView()).setProgress(0);
        layout();
        assertTrue(button.getMeasuredWidth() < originalWidth);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        layout();
        assertEquals(originalWidth, button.getMeasuredWidth());
        assertEquals(46, MenuButtonSize.read(activity));
    }
    @Test public void longPressOpensSizeDialogWithoutAlsoOpeningGameMenu() {
        int[] clicks = {0};
        button.setOnClickListener(v -> clicks[0]++);
        touch(MotionEvent.ACTION_DOWN, 5, 5);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 20));
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        touch(MotionEvent.ACTION_UP, 5, 5);
        assertEquals(0, clicks[0]);
    }
    @Test public void ordinaryTapStillOpensMenuAndDragDoesNot() {
        int[] clicks = {0};
        button.setOnClickListener(v -> clicks[0]++);
        touch(MotionEvent.ACTION_DOWN, 5, 5);
        touch(MotionEvent.ACTION_UP, 5, 5);
        assertEquals(1, clicks[0]);
        touch(MotionEvent.ACTION_DOWN, 5, 5);
        touch(MotionEvent.ACTION_MOVE, 150, 100);
        touch(MotionEvent.ACTION_UP, 150, 100);
        assertEquals(1, clicks[0]);
    }
}
