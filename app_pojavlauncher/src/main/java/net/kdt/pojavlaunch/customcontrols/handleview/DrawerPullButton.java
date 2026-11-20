package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.R;

/**
 * The button that opens the in game menu.
 *
 * It draws the mark of the launcher and can be dragged anywhere on screen; the place it was left at
 * is remembered for the next time the game is started. A tap opens the menu, while a drag only moves
 * the button.
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
public class DrawerPullButton extends View {
    /** Where the button is remembered between two game sessions. */
    private static final String PREFERENCES_NAME = MenuButtonSize.PREFERENCES;
    private static final String KEY_X = "menuButtonX";
    private static final String KEY_Y = "menuButtonY";

    private int mSizeDp;
    private boolean mLongPressHandled;
    private AlertDialog mSizeDialog;
    /** The player asked for the logo to stay out of the way during play. */
    private boolean mHiddenWhilePlaying;
    /** The game menu is open right now, so the logo has to be reachable from it. */
    private boolean mMenuShowing;
    /**
     * The layout carries its own MENU button, so a second way to open the menu is clutter.
     *
     * Starts false on purpose, and the button starts visible: until the activity has loaded a layout
     * and said otherwise, showing the logo is the safe answer. A logo that should not be there is
     * clutter for a moment; a logo that is missing is a player with no way into the menu.
     */
    private boolean mLayoutHasMenuButton;
    /** The editor needs the logo whatever the player asked for, or they cannot leave the editor. */
    private boolean mEditorMode;


    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBackgroundRect = new RectF();

    private Drawable mDrawable;

    private int mTouchSlop;
    private boolean mPositionRestored = false;
    private boolean mDragging = false;
    private float mTouchDownX;
    private float mTouchDownY;
    private float mDragStartX;
    private float mDragStartY;

    private final Runnable mLongPress = () -> {
        if (!mDragging && isAttachedToWindow()) {
            mLongPressHandled = performLongClick();
        }
    };

    public DrawerPullButton(Context context) {super(context); init();}
    public DrawerPullButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    private void init(){
        mSizeDp = MenuButtonSize.read(getContext());
        mHiddenWhilePlaying = MenuButtonVisibility.isHiddenWhilePlaying(getContext());
        setContentDescription(getContext().getString(R.string.aerix_logo_size_accessibility));
        setOnLongClickListener(view -> { showSizeDialog(); return true; });
        mDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_aerix_mark);
        mBackgroundPaint.setColor(0xB0121A24);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        mBorderPaint.setColor(0x66FFFFFF);
        setAlpha(0.85f);
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = dpToPx(mSizeDp);
        setMeasuredDimension(
                resolveSize(size, widthMeasureSpec),
                resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = getWidth() / 3f;
        mBackgroundRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(mBackgroundRect, radius, radius, mBackgroundPaint);
        canvas.drawRoundRect(mBackgroundRect, radius, radius, mBorderPaint);

        if (mDrawable == null) return;
        int iconSize = MenuButtonSize.iconPixels(Math.min(getWidth(), getHeight()));
        int insetX = (getWidth() - iconSize) / 2;
        int insetY = (getHeight() - iconSize) / 2;
        mDrawable.setBounds(insetX, insetY, insetX + iconSize, insetY + iconSize);
        mDrawable.draw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View parent = (View) getParent();
        if (parent == null || parent.getWidth() == 0) return;
        if (!mPositionRestored) restorePosition(parent);
        else {
            // Enlarging near an edge must not put the logo out of reach.
            setTranslationX(clampX(parent, getTranslationX()));
            setTranslationY(clampY(parent, getTranslationY()));
        }
    }

    /** Put the button back where the player left it, or in the middle of the top edge. */
    private void restorePosition(View parent) {
        SharedPreferences preferences = getContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        float savedX = preferences.getFloat(KEY_X, Float.NaN);
        float savedY = preferences.getFloat(KEY_Y, Float.NaN);
        mPositionRestored = true;
        if (Float.isNaN(savedX) || Float.isNaN(savedY)) {
            // First start: above the middle of the screen, out of the way of the controls.
            setTranslationX((parent.getWidth() - getWidth()) / 2f);
            setTranslationY(dpToPx(12));
            return;
        }
        setTranslationX(clampX(parent, savedX));
        setTranslationY(clampY(parent, savedY));
    }

    private void savePosition() {
        getContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_X, getTranslationX())
                .putFloat(KEY_Y, getTranslationY())
                .apply();
    }

    private float clampX(View parent, float x) {
        return Math.max(0, Math.min(parent.getWidth() - getWidth(), x));
    }

    private float clampY(View parent, float y) {
        return Math.max(0, Math.min(parent.getHeight() - getHeight(), y));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // The parent is the control layout, which would otherwise read the gesture as a button
        // press of the game controls. requestDisallowInterceptTouchEvent belongs to ViewParent,
        // while the bounds used to keep the button on screen come from the View.
        View parent = (View) getParent();
        ViewParent touchParent = getParent();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = false;
                mLongPressHandled = false;
                removeCallbacks(mLongPress);
                postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
                mTouchDownX = event.getRawX();
                mTouchDownY = event.getRawY();
                mDragStartX = getTranslationX();
                mDragStartY = getTranslationY();
                if (touchParent != null) touchParent.requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mLongPressHandled) return true;
                float dx = event.getRawX() - mTouchDownX;
                float dy = event.getRawY() - mTouchDownY;
                if (!mDragging && Math.hypot(dx, dy) > mTouchSlop) {
                    mDragging = true;
                    removeCallbacks(mLongPress);
                }
                if (mDragging && parent != null) {
                    setTranslationX(clampX(parent, mDragStartX + dx));
                    setTranslationY(clampY(parent, mDragStartY + dy));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(mLongPress);
                if (mDragging) {
                    savePosition();
                } else if (!mLongPressHandled) {
                    performClick();
                }
                if (touchParent != null) touchParent.requestDisallowInterceptTouchEvent(false);
                mDragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPress);
                if (mDragging) savePosition();
                if (touchParent != null) touchParent.requestDisallowInterceptTouchEvent(false);
                mDragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * Tell the logo whether the in-game menu is on screen.
     *
     * With hide-while-playing on, the menu is the one place the logo has to be: it is how the menu
     * was opened, and how a player with no MENU button reaches it again after closing.
     */
    public void setMenuShowing(boolean showing) {
        if (mMenuShowing == showing) return;
        mMenuShowing = showing;
        applyVisibility();
    }

    /** The layout has its own MENU button, so this logo is not needed to reach the menu. */
    public void setLayoutHasMenuButton(boolean hasMenuButton) {
        if (mLayoutHasMenuButton == hasMenuButton) return;
        mLayoutHasMenuButton = hasMenuButton;
        applyVisibility();
    }

    /** While the controls are being edited the logo is always reachable, editor rules aside. */
    public void setEditorMode(boolean editing) {
        if (mEditorMode == editing) return;
        mEditorMode = editing;
        applyVisibility();
    }

    /** Re-read the hide-while-playing preference, for when it is changed from the launcher. */
    public void refreshVisibility() {
        mHiddenWhilePlaying = MenuButtonVisibility.isHiddenWhilePlaying(getContext());
        applyVisibility();
    }

    /**
     * Show the logo when it is wanted, hide it when it is not.
     *
     * The caller still owns the "does this layout have its own MENU button" decision - a logo for a
     * menu the player can already open is clutter. This only answers the other question: did the
     * player ask for the logo to be out of the way during play?
     */
    private void applyVisibility() {
        boolean wanted;
        if (mEditorMode) {
            wanted = true;
        } else if (mLayoutHasMenuButton) {
            // A second way to open a menu that already has a button is clutter, as before.
            wanted = false;
        } else {
            wanted = !mHiddenWhilePlaying || mMenuShowing;
        }
        // GONE, never INVISIBLE: an invisible view still takes the touches meant for the game.
        setVisibility(wanted ? VISIBLE : GONE);
    }

    private void setSizeDp(int sizeDp) {
        mSizeDp = MenuButtonSize.clamp(sizeDp);
        requestLayout();
        invalidate();
    }

    /**
     * Long-press opens a live preview: the size slider resizes the logo as it is dragged, and the
     * hide switch takes it away as it is flipped. Only OK/Default persist; Cancel restores both.
     */
    public void showSizeDialog() {
        if (mSizeDialog != null && mSizeDialog.isShowing()) return;
        final int originalSize = mSizeDp;
        final boolean originalHidden = mHiddenWhilePlaying;
        final float originalX = getTranslationX();
        final float originalY = getTranslationY();
        final Runnable restorePreview = () -> {
            setSizeDp(originalSize);
            setTranslationX(originalX);
            setTranslationY(originalY);
            // Cancel has to undo the switch as well, or the preview setting would survive as if it
            // had been confirmed - the one outcome a Cancel button must never produce.
            mHiddenWhilePlaying = originalHidden;
            applyVisibility();
        };
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(24);
        panel.setPadding(padding, dpToPx(8), padding, dpToPx(8));

        // Hide-while-playing sits above the size slider: it is the decision a player makes once,
        // while the size is the one they come back to tune.
        Switch hideSwitch = new Switch(getContext());
        hideSwitch.setText(R.string.aerix_logo_hide_while_playing);
        hideSwitch.setChecked(mHiddenWhilePlaying);
        hideSwitch.setContentDescription(getContext()
                .getString(R.string.aerix_logo_hide_while_playing));
        hideSwitch.setOnCheckedChangeListener((button, checked) -> {
            mHiddenWhilePlaying = checked;
            // Live, like the size: the point is to see whether the screen is cleaner without it.
            applyVisibility();
        });
        panel.addView(hideSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hideHint = new TextView(getContext());
        hideHint.setText(R.string.aerix_logo_hide_while_playing_hint);
        hideHint.setPadding(dpToPx(4), 0, 0, dpToPx(8));
        hideHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        hideHint.setAlpha(0.75f);
        panel.addView(hideHint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(getContext());
        label.setText(getContext().getString(R.string.aerix_logo_size_value, mSizeDp));
        panel.addView(label);
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(MenuButtonSize.MAX_DP - MenuButtonSize.MIN_DP);
        slider.setProgress(mSizeDp - MenuButtonSize.MIN_DP);
        slider.setContentDescription(getContext().getString(R.string.aerix_logo_size_title));
        panel.addView(slider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hint = new TextView(getContext());
        hint.setText(R.string.aerix_logo_size_hint);
        panel.addView(hint);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                setSizeDp(progress + MenuButtonSize.MIN_DP);
                label.setText(getContext().getString(R.string.aerix_logo_size_value, mSizeDp));
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        mSizeDialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.aerix_logo_size_title).setView(panel)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    MenuButtonSize.save(getContext(), mSizeDp);
                    MenuButtonVisibility.setHiddenWhilePlaying(getContext(), mHiddenWhilePlaying);
                    applyVisibility();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> restorePreview.run())
                .setNeutralButton(R.string.aerix_logo_size_default, (dialog, which) -> {
                    // Default means the default size. The hide switch is its own decision, so it is
                    // left alone here rather than silently reset with the slider.
                    setSizeDp(MenuButtonSize.DEFAULT_DP);
                    MenuButtonSize.save(getContext(), mSizeDp);
                }).create();
        mSizeDialog.setOnCancelListener(dialog -> restorePreview.run());
        mSizeDialog.setOnDismissListener(dialog -> mSizeDialog = null);
        mSizeDialog.show();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mLongPress);
        if (mSizeDialog != null) mSizeDialog.dismiss();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Forget the place the button was dragged to. */
    public static void resetPosition(Context context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_X).remove(KEY_Y).apply();
    }
}
