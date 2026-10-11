package net.kdt.pojavlaunch.ui;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The moving aurora that sits behind every launcher screen.
 *
 * Everything is painted into a small off screen buffer (a sixth of the real
 * size) and then scaled up, which keeps the fill rate low enough for old
 * devices while still looking like a soft, slowly drifting light field.
 */
public class AuroraBackdropView extends View {

    /** Resolution divisor for the off screen buffer. */
    private static final float BUFFER_SCALE = 1f / 6f;

    private static final int[] BLOB_COLORS = {
            0xFF6EF0FF, 0xFFA78BFA, 0xFF60A5FA,
            0xFF7CFFCB, 0xFF6EF0FF, 0xFFFF8AB0
    };
    private static final float[] BLOB_X = {0.18f, 0.86f, 0.62f, 0.10f, 0.92f, 0.42f};
    private static final float[] BLOB_Y = {0.13f, 0.22f, 0.52f, 0.74f, 0.90f, 0.96f};
    private static final float[] BLOB_RADIUS = {0.55f, 0.50f, 0.58f, 0.52f, 0.56f, 0.48f};
    private static final float[] BLOB_ALPHA = {0.30f, 0.26f, 0.24f, 0.20f, 0.24f, 0.14f};
    private static final float[] BLOB_SPEED = {0.017f, 0.013f, 0.011f, 0.019f, 0.009f, 0.015f};

    private final Paint mBlobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mVignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBufferPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF mDestRect = new RectF();

    private final Shader[] mBlobShaders = new Shader[BLOB_COLORS.length];
    private final float[] mBlobPr = new float[BLOB_COLORS.length];

    @Nullable
    private Bitmap mBuffer;
    @Nullable
    private Canvas mBufferCanvas;
    private int mBufferWidth;
    private int mBufferHeight;
    private boolean mAnimated = true;
    private boolean mAttached;

    public AuroraBackdropView(Context context) {
        this(context, null);
    }

    public AuroraBackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
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
        releaseBuffer();
    }

    private void releaseBuffer() {
        if (mBuffer != null) {
            mBuffer.recycle();
            mBuffer = null;
            mBufferCanvas = null;
        }
    }

    /** Honours "remove animations" and battery saver: both make the aurora static. */
    private boolean motionAllowed() {
        try {
            float scale = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) return false;
        } catch (Throwable ignored) {
            // Setting is not readable on every device, animations stay on.
        }
        try {
            Object service = getContext().getSystemService(Context.POWER_SERVICE);
            if (service instanceof PowerManager) {
                if (((PowerManager) service).isPowerSaveMode()) return false;
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
        mBasePaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF060A14, 0xFF08182C, 0xFF03080F},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        mVignettePaint.setShader(new RadialGradient(w * 0.5f, h * 0.48f,
                Math.max(w, h) * 0.78f,
                new int[]{0x00000000, 0x00000000, 0x99030810},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        // One gradient per blob, built once and then moved around with the
        // canvas matrix, so a frame costs no allocations at all.
        float base = Math.max(w, h) * 0.55f;
        for (int i = 0; i < mBlobShaders.length; i++) {
            mBlobPr[i] = Math.max(1f, BLOB_RADIUS[i] * base);
            int color = BLOB_COLORS[i];
            int argb = Color.argb(Math.round(255 * BLOB_ALPHA[i]),
                    Color.red(color), Color.green(color), Color.blue(color));
            mBlobShaders[i] = new RadialGradient(0, 0, mBlobPr[i], argb,
                    Color.TRANSPARENT, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBuffer == null || mBufferCanvas == null || getWidth() == 0) return;
        paintAurora(SystemClock.uptimeMillis());
        mDestRect.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(mBuffer, null, mDestRect, mBufferPaint);
        if (mAnimated && mAttached) postInvalidateOnAnimation();
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
        canvas.drawRect(0, 0, w, h, mVignettePaint);
    }

    /** Freeze or resume the drift, for example while a dialog covers the screen. */
    public void setMotionEnabled(boolean enabled) {
        mAnimated = enabled && motionAllowed();
        if (mAnimated) invalidate();
    }
}
