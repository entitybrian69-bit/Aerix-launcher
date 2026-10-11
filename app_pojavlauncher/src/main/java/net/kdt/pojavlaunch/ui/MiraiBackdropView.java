package net.kdt.pojavlaunch.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;

/**
 * The backdrop behind every Mirai screen.
 *
 * Mirai paints one cached aurora pass: a single diagonal gradient, cyan at
 * 6.5% fading to violet at 5.5%, over either the wallpaper the user picked or
 * the palette background. It does not animate, which is why the launcher stays
 * smooth while pages scroll over it.
 */
public class MiraiBackdropView extends View {

    /** Mirai's aurora endpoints: top left down to bottom right. */
    private static final float AURORA_START_Y = 0.08f;
    private static final float AURORA_END_Y = 0.92f;

    private final Paint mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAuroraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF mDestRect = new RectF();

    @Nullable
    private Bitmap mWallpaper;

    public MiraiBackdropView(Context context) {
        this(context, null);
    }

    public MiraiBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    /**
     * Shows a picture instead of the plain backdrop. Mirai dims it with the
     * palette background at (opacity / 100) cubed, so the picture stays
     * luminous through the glass at the default value.
     */
    public void setWallpaperPath(@Nullable String path) {
        if (mWallpaper != null) {
            mWallpaper.recycle();
            mWallpaper = null;
        }
        if (path != null && !path.isEmpty()) {
            mWallpaper = decodeSampled(path);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mWallpaper != null) {
            mWallpaper.recycle();
            mWallpaper = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildAurora(w, h);
    }

    private void buildAurora(int w, int h) {
        int cyan = getResources().getColor(R.color.mirai_aurora_cyan, getContext().getTheme());
        int violet = getResources().getColor(R.color.mirai_aurora_violet, getContext().getTheme());
        // The same alphas Mirai uses: 0.065 cyan, nothing in the middle, 0.055 violet.
        int cyanFaded = (cyan & 0x00FFFFFF) | 0x10000000;
        int violetFaded = (violet & 0x00FFFFFF) | 0x0E000000;
        float startY = h * AURORA_START_Y;
        float endY = h * AURORA_END_Y;
        mAuroraPaint.setShader(new LinearGradient(0f, startY, w, endY,
                new int[]{cyanFaded, 0x00000000, violetFaded},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int background = resolveColor(R.attr.miraiBackground, 0xFF1C110E);
        mBasePaint.setShader(null);
        mBasePaint.setColor(background);
        canvas.drawRect(0, 0, w, h, mBasePaint);

        if (mWallpaper != null && !mWallpaper.isRecycled()) {
            drawWallpaper(canvas, w, h);
            // The wallpaper is dimmed by the palette colour at a soft cubic curve.
            mBasePaint.setColor(background);
            mBasePaint.setAlpha(Math.round(255 * scrim()));
            canvas.drawRect(0, 0, w, h, mBasePaint);
            mBasePaint.setAlpha(255);
        }

        canvas.drawRect(0, 0, w, h, mAuroraPaint);
    }

    /** Centre crops the picked picture, the way a wallpaper is expected to. */
    private void drawWallpaper(Canvas canvas, int w, int h) {
        float viewRatio = w / (float) h;
        float imageRatio = mWallpaper.getWidth() / (float) mWallpaper.getHeight();
        float scale;
        float dx = 0;
        float dy = 0;
        if (imageRatio > viewRatio) {
            scale = h / (float) mWallpaper.getHeight();
            dx = (w - mWallpaper.getWidth() * scale) / 2f;
        } else {
            scale = w / (float) mWallpaper.getWidth();
            dy = (h - mWallpaper.getHeight() * scale) / 2f;
        }
        mDestRect.set(dx, dy, dx + mWallpaper.getWidth() * scale, dy + mWallpaper.getHeight() * scale);
        canvas.drawBitmap(mWallpaper, null, mDestRect, mBitmapPaint);
    }

    /** Mirai's default dimmer: 70% opacity reads as roughly a third of the veil. */
    private float scrim() {
        float opacity = 0.7f;
        return opacity * opacity * opacity;
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private static Bitmap decodeSampled(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, decode);
    }
}
