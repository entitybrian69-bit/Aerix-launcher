package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursor;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursorImplementor;

import net.kdt.pojavlaunch.R;

/**
 * A view that draws the platform cursor on the screen
 */
public class GameCursorView extends View implements PlatformCursorImplementor {
    private final Paint customCursorPaint = new Paint();
    private final Paint aerixSkinPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF aerixSkinRect = new android.graphics.RectF();
    private final Drawable cursorDrawable;
    private boolean noDraw = false;
    private float mouseScale = 1f;
    /** The skin the player picked in Settings -> Mouse settings; null keeps the built-in arrow. */
    private android.graphics.Bitmap aerixSkin;
    /** True when the image sits on the pointer rather than having its tip there. */
    private boolean aerixSkinCentered = true;

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GameCursorView(Context context) {
        this(context, null);
    }

    public GameCursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        cursorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
        assert cursorDrawable != null;
        cursorDrawable.setBounds(0, 0, 36, 54);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (noDraw) return;
        // Scale coordinates back to the full unresized screen size
        int dx = (int) (Platform.cursorX * ((GameView)getParent()).cursorRatioX);
        int dy = (int) (Platform.cursorY * ((GameView)getParent()).cursorRatioY);
        canvas.translate(dx, dy);
        PlatformCursor cursor = Platform.getCursor();
        canvas.scale(mouseScale, mouseScale);
        if (cursor == null && aerixSkin != null && !aerixSkin.isRecycled()) {
            // Pojav draws this cursor itself, so the player's image replaces the white arrow here
            // and only here - the game's own crosshair comes through the branch below untouched.
            float box = 54f * mouseScale;
            float ratio = Math.min(box / aerixSkin.getWidth(), box / aerixSkin.getHeight());
            float width = aerixSkin.getWidth() * ratio;
            float height = aerixSkin.getHeight() * ratio;
            float left = aerixSkinCentered ? -width / 2f : 0f;
            float top = aerixSkinCentered ? -height / 2f : 0f;
            aerixSkinRect.set(left, top, left + width, top + height);
            canvas.drawBitmap(aerixSkin, null, aerixSkinRect, aerixSkinPaint);
        } else if (cursor == null) {
            cursorDrawable.draw(canvas);
        } else {
            canvas.drawBitmap(cursor.bitmap, -cursor.hotX, -cursor.hotY, customCursorPaint);
        }
    }

    @Override
    public void onCursorPosition() {
        if (!noDraw) post(this::invalidate);
    }

    @Override
    public void onCursorChanged() {
        post(this::invalidate);
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        noDraw = isGrabbing;
        invalidate();
    }

    public void setCursorScale(float scale) {
        this.mouseScale = scale;
        invalidate();
    }

    /** Apply the player's cursor skin, its opacity and its anchor. Null [bitmap] restores the arrow. */
    public void setCursorSkin(android.graphics.Bitmap bitmap, int opacityPercent, boolean centered) {
        aerixSkin = bitmap;
        aerixSkinCentered = centered;
        aerixSkinPaint.setAlpha(Math.max(20, Math.min(100, opacityPercent)) * 255 / 100);
        invalidate();
    }
}
