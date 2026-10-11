package net.kdt.pojavlaunch.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;

/**
 * The backdrop Mirai draws behind every screen.
 *
 * Either a wallpaper the user picked, or the launcher's own slowly drifting
 * light field. Everything is painted into an off screen buffer a sixth of the
 * real size and then scaled up, which keeps old devices smooth. The blob
 * colours come from the palette theme, so changing palette repaints it.
 */
public class MiraiBackdropView extends View {

    private static final float BUFFER_SCALE = 1f / 6f;

    private static final float[] BLOB_X = {0.18f, 0.86f, 0.62f, 0.10f, 0.92f, 0.42f};
    private static final float[] BLOB_Y = {0.13f, 0.22f, 0.52f, 0.74f, 0.90f, 0.96f};
    private static final float[] BLOB_RADIUS = {0.55f, 0.50f, 0.58f, 0.52f, 0.56f, 0.48f};
    private static final float[] BLOB_ALPHA = {0.30f, 0.26f, 0.24f, 0.20f, 0.24f, 0.14f};
    private static final float[] BLOB_SPEED = {0.017f, 0.013f, 0.011f, 0.019f, 0.009f, 0.015f};

    private final Paint mBlobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBufferPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF mDestRect = new RectF();
    private final Shader[] mBlobShaders = new Shader[BLOB_X.length];
    private final float[] mBlobPr = new float[BLOB_X.length];
    private final int[] mBlobColors = new int[BLOB_X.length];

    @Nullable
    private Bitmap mBuffer;
    @Nullable
    private Canvas mBufferCanvas;
    private int mBufferWidth;
    private int mBufferHeight;
    @Nullable
    private Bitmap mWallpaper;
    private boolean mAnimated = true;
    private boolean mAttached;

    public MiraiBackdropView(Context context) {
        this(context, null);
    }

    public MiraiBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        readPalette();
    }

    /** Pulls the blob colours out of the palette theme currently applied. */
    private void readPalette() {
        int primary = resolveColor(R.attr.miraiPrimary, 0xFFB59F);
        int secondary = resolveColor(R.attr.miraiSecondary, 0xE0BDCB);
        int tertiary = resolveColor(R.attr.miraiTertiary, 0xF0D44C);
        int[] cycle = {primary, tertiary, secondary, secondary, primary, tertiary};
        System.arraycopy(cycle, 0, mBlobColors, 0, cycle.length);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }

    /**
     * Shows a picture instead of the light field. Pass null to go back to the
     * launcher drawn backdrop.
     */
    public void setWallpaperPath(@Nullable String path) {
        if (mWallpaper != null) {
            mWallpaper.recycle();
            mWallpaper = null;
        }
        if (path != null && !path.isEmpty()) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            int maxSide = 1600;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            decode.inPreferredConfig = Bitmap.Config.RGB_565;
            mWallpaper = BitmapFactory.decodeFile(path, decode);
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        mAnimated = motionAllowed();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        if (mWallpaper != null) {
            mWallpaper.recycle();
            mWallpaper = null;
        }
        releaseBuffer();
    }

    private void releaseBuffer() {
        if (mBuffer != null) {
            mBuffer.recycle();
            mBuffer = null;
            mBufferCanvas = null;
        }
    }

    /** Honours "remove animations" and battery saver: both freeze the drift. */
    private boolean motionAllowed() {
        try {
            float scale = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) return false;
        } catch (Throwable ignored) {
            // Not readable on every device, animations stay on.
        }
        try {
            Object service = getContext().getSystemService(Context.POWER_SERVICE);
            if (service instanceof PowerManager && ((PowerManager) service).isPowerSaveMode()) {
                return false;
            }
        } catch (Throwable ignored) {
            // Power manager is optional, animations stay on.
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        releaseBuffer();
        if (w <= 0 || h <= 0) return;
        mBufferWidth = Math.max(2, Math.round(w * BUFFER_SCALE));
        mBufferHeight = Math.max(2, Math.round(h * BUFFER_SCALE));
        mBuffer = Bitmap.createBitmap(mBufferWidth, mBufferHeight, Bitmap.Config.ARGB_8888);
        mBufferCanvas = new Canvas(mBuffer);
        buildShaders(mBufferWidth, mBufferHeight);
    }

    private void buildShaders(int w, int h) {
        int background = resolveColor(R.attr.miraiBackground, 0x1C110E);
        int surface = resolveColor(R.attr.miraiSurfaceContainerHighest, 0x3F322E);
        mBasePaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{background, surface, background},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        float base = Math.max(w, h) * 0.55f;
        for (int i = 0; i < mBlobShaders.length; i++) {
            mBlobPr[i] = Math.max(1f, BLOB_RADIUS[i] * base);
            int color = mBlobColors[i];
            int argb = Color.argb(Math.round(255 * BLOB_ALPHA[i]),
                    Color.red(color), Color.green(color), Color.blue(color));
            mBlobShaders[i] = new RadialGradient(0, 0, mBlobPr[i], argb,
                    Color.TRANSPARENT, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        if (mWallpaper != null) {
            drawWallpaper(canvas);
            return;
        }
        if (mBuffer == null || mBufferCanvas == null) return;
        paintAurora(SystemClock.uptimeMillis());
        mDestRect.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(mBuffer, null, mDestRect, mBufferPaint);
        if (mAnimated && mAttached) postInvalidateOnAnimation();
    }

    /** Centre crops the picked picture, the way a wallpaper is expected to. */
    private void drawWallpaper(Canvas canvas) {
        float viewRatio = getWidth() / (float) getHeight();
        float imageRatio = mWallpaper.getWidth() / (float) mWallpaper.getHeight();
        float scale;
        float dx = 0;
        float dy = 0;
        if (imageRatio > viewRatio) {
            scale = getHeight() / (float) mWallpaper.getHeight();
            dx = (getWidth() - mWallpaper.getWidth() * scale) / 2f;
        } else {
            scale = getWidth() / (float) mWallpaper.getWidth();
            dy = (getHeight() - mWallpaper.getHeight() * scale) / 2f;
        }
        mDestRect.set(dx, dy, dx + mWallpaper.getWidth() * scale, dy + mWallpaper.getHeight() * scale);
        canvas.drawBitmap(mWallpaper, null, mDestRect, mBufferPaint);
    }

    private void paintAurora(long timeMillis) {
        Canvas canvas = mBufferCanvas;
        int w = mBufferWidth;
        int h = mBufferHeight;
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawRect(0, 0, w, h, mBasePaint);

        float seconds = timeMillis / 1000f;
        for (int i = 0; i < mBlobShaders.length; i++) {
            if (mBlobShaders[i] == null) continue;
            float phase = seconds * BLOB_SPEED[i] * (float) (2 * Math.PI);
            float drift = 0.06f;
            float cx = (BLOB_X[i] + (float) Math.sin(phase) * drift) * w;
            float cy = (BLOB_Y[i] + (float) Math.cos(phase * 0.83f) * drift * 0.8f) * h;
            float pulse = 1f + 0.12f * (float) Math.sin(phase * 1.7f);

            mBlobPaint.setShader(mBlobShaders[i]);
            int saved = canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(pulse, pulse);
            canvas.drawCircle(0, 0, mBlobPr[i], mBlobPaint);
            canvas.restoreToCount(saved);
        }
        mBlobPaint.setShader(null);
    }
}
