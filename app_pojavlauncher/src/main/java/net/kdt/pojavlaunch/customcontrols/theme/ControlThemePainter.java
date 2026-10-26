package net.kdt.pojavlaunch.customcontrols.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Draws a {@link ControlTheme} on a control and animates it under the finger.
 *
 * <p>The animation is a small damped spring, advanced inside the view's own draw pass and driven by
 * {@link View#postInvalidateOnAnimation()} only while it is still moving. At rest a themed control
 * is a couple of rounded rectangles, the same order of cost as a plain one, and nothing runs while
 * the screen is not being drawn.
 *
 * <p>Presses and releases only ever change numbers here. The key event is sent by the control on
 * the frame the finger lands, exactly as before - a theme can never delay or swallow an input.
 */
public final class ControlThemePainter {

    /** Spring pull towards the target state. Higher is snappier. */
    private static final float STIFFNESS = 620f;
    /** Spring friction. Lower bounces more; this one overshoots slightly and settles fast. */
    private static final float DAMPING = 21f;
    /** Below this the spring is close enough to call it done and stop asking for frames. */
    private static final float REST_POSITION = 0.003f;
    private static final float REST_SPEED = 0.06f;
    /** Longest step integrated at once, and the size of the fixed sub steps. */
    private static final float MAX_STEP = 0.05f;
    private static final float SUB_STEP = 0.008f;
    /** How far past 1 the overshoot is allowed to push the press value. */
    private static final float MAX_PRESS = 1.3f;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExtra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private final RectF mRect = new RectF();

    @Nullable private ControlTheme mTheme;
    @Nullable private AudioManager mAudio;

    private float mPress, mVelocity, mTarget;
    private long mLastFrame;
    private boolean mAnimating;

    private int mCachedWidth = -1, mCachedHeight = -1;
    private float mCachedRadius = -1f;
    @Nullable private LinearGradient mCachedFill, mCachedGloss, mCachedSheen;

    private float mDensity = 1f;

    public ControlThemePainter() {
        mEdge.setStyle(Paint.Style.STROKE);
    }

    /** Called once the view exists, so density and the audio service can be resolved. */
    public void attach(View view) {
        mDensity = view.getResources().getDisplayMetrics().density;
        mAudio = (AudioManager) view.getContext().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    public void setTheme(@Nullable ControlTheme theme) {
        if (mTheme == theme) return;
        mTheme = theme;
        mPress = 0f;
        mVelocity = 0f;
        mTarget = 0f;
        mAnimating = false;
        clearShaders();
    }

    public boolean hasTheme() {
        return mTheme != null;
    }

    public boolean isAnimating() {
        return mAnimating;
    }

    // ---------------------------------------------------------------- input

    public void press() {
        mTarget = 1f;
        startAnimation();
    }

    public void release() {
        mTarget = 0f;
        startAnimation();
    }

    /** Runs one press and release, for the editor's preview when a theme chip is tapped. */
    public void preview(View view) {
        press();
        view.postDelayed(this::release, 260);
    }

    /**
     * Fires the theme's touch feedback: a haptic tick and, unless the player turned it off, the
     * system key sound. Both are the platform's own, so they follow the device's sound and
     * vibration settings instead of fighting them, and neither needs a permission or an asset.
     */
    public void playFeedback(View view) {
        ControlTheme theme = mTheme;
        if (theme == null) return;
        if (LauncherPreferences.PREF_CONTROL_THEME_HAPTICS) view.performHapticFeedback(theme.haptic);
        if (LauncherPreferences.PREF_CONTROL_THEME_SOUND && mAudio != null) {
            try {
                mAudio.playSoundEffect(theme.sound);
            } catch (RuntimeException ignored) {
                // Some devices ship an incomplete sound table; a missing click is not worth a crash.
            }
        }
    }

    private void startAnimation() {
        mAnimating = true;
        mLastFrame = 0L;
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Paints everything that belongs under the control's text, and leaves the canvas transformed
     * so the text squashes or grows with the theme.
     *
     * @return the save count to hand back to {@link Canvas#restoreToCount(int)}
     */
    public int drawBack(Canvas canvas, int width, int height, float radius, boolean activated,
                        boolean overBitmap) {
        int save = canvas.save();
        ControlTheme theme = mTheme;
        if (theme == null || width <= 0 || height <= 0) return save;

        advance();
        float press = clampPress();
        if (width != mCachedWidth || height != mCachedHeight || radius != mCachedRadius) {
            rebuildGeometry(width, height, radius);
        }

        float scale = 1f;
        if (theme.press == ControlTheme.Press.SQUASH) scale = 1f - 0.085f * press;
        else if (theme.press == ControlTheme.Press.LIFT) scale = 1f + 0.05f * press;
        if (scale != 1f) canvas.scale(scale, scale, width / 2f, height / 2f);

        // No clipping here: every shape below is drawn as a rounded rectangle already, and clipping
        // a path is not antialiased on Android - it would give the corners a jagged edge.
        paintFill(canvas, width, height, radius, activated, press, overBitmap);
        return save;
    }

    /** Paints the light, ripples and sweeps that belong over the text. */
    public void drawFront(Canvas canvas, int width, int height, float radius) {
        ControlTheme theme = mTheme;
        if (theme == null || width <= 0 || height <= 0) return;
        float press = clampPress();
        if (press <= 0.001f) return;

        // These are the only shapes that could spill out of the control's rounded corners, and they
        // are translucent, so clipping them here costs nothing visible.
        int save = canvas.save();
        canvas.clipPath(mClip);
        switch (theme.press) {
            case GLOW: paintGlow(canvas, press, radius); break;
            case RIPPLE: paintRipple(canvas, width, height, press); break;
            case RING: paintRing(canvas, width, height, press); break;
            case FLASH: paintFlash(canvas, radius, press); break;
            case SHEEN: paintSheen(canvas, width, height, press); break;
            case FILL: paintFillUp(canvas, width, height, radius, press); break;
            default: break;
        }
        canvas.restoreToCount(save);
    }

    private void rebuildGeometry(int width, int height, float radius) {
        mCachedWidth = width;
        mCachedHeight = height;
        mCachedRadius = radius;
        clearShaders();
        mRect.set(0f, 0f, width, height);
        mClip.reset();
        mClip.addRoundRect(mRect, radius, radius, Path.Direction.CW);
    }

    private void clearShaders() {
        mCachedFill = null;
        mCachedGloss = null;
        mCachedSheen = null;
    }

    private void paintFill(Canvas canvas, int width, int height, float radius,
                           boolean activated, float press, boolean overBitmap) {
        ControlTheme theme = mTheme;
        float strength = 0.55f + (activated ? 0.25f : 0f) + 0.20f * press;

        if (overBitmap) {
            // A control that already has a picture keeps it: the theme becomes a tint and a set of
            // press effects on top, instead of throwing the player's chosen image away.
            strength *= 0.45f;
        } else {
            // Everything starts from a dark coat, so the stock background is genuinely covered: a
            // themed control looks like the theme, not like the theme laid over the old button.
            mFill.setShader(null);
            mFill.setColor(0xFF0E141A);
            mFill.setAlpha(alpha(0.80f));
            canvas.drawRoundRect(mRect, radius, radius, mFill);
        }

        switch (theme.fill) {
            case SOLID: {
                mFill.setColor(theme.accent);
                mFill.setAlpha(alpha(strength));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                paintTopGloss(canvas, width, height, radius, 0.10f + 0.12f * press);
                break;
            }
            case GRADIENT: {
                mFill.setShader(fillGradient(width, height));
                mFill.setAlpha(alpha(strength));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                mFill.setShader(null);
                paintTopGloss(canvas, width, height, radius, 0.12f + 0.10f * press);
                break;
            }
            case GLASS: {
                mFill.setColor(theme.accent);
                mFill.setAlpha(alpha(0.10f + 0.18f * press));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                float inset = dp(1.5f);
                mRect.inset(inset, inset);
                mEdge.setShader(null);
                mEdge.setStrokeWidth(dp(1.5f));
                mEdge.setColor(0xFFFFFFFF);
                mEdge.setAlpha(alpha(0.20f + 0.22f * press));
                canvas.drawRoundRect(mRect, Math.max(0f, radius - inset), Math.max(0f, radius - inset), mEdge);
                mRect.inset(-inset, -inset);
                paintTopGloss(canvas, width, height, radius, 0.16f + 0.10f * press);
                break;
            }
            case OUTLINE: {
                // Hollow middle, hot edge - the neon family.
                mFill.setColor(theme.accent);
                mFill.setAlpha(alpha(0.07f + 0.13f * press));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                float inset = dp(1.5f);
                mRect.inset(inset, inset);
                mEdge.setShader(null);
                mEdge.setStrokeWidth(dp(2f));
                mEdge.setColor(theme.accent);
                mEdge.setAlpha(alpha(0.80f + 0.20f * press));
                canvas.drawRoundRect(mRect, Math.max(0f, radius - inset), Math.max(0f, radius - inset), mEdge);
                mRect.inset(-inset, -inset);
                break;
            }
            case STRIPES: {
                mFill.setColor(theme.accent2);
                mFill.setAlpha(alpha(strength));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                paintStripes(canvas, width, height, press);
                break;
            }
            case SCANLINE: {
                mFill.setColor(theme.accent2);
                mFill.setAlpha(alpha(strength));
                canvas.drawRoundRect(mRect, radius, radius, mFill);
                paintScanlines(canvas, width, height, press);
                break;
            }
            default: break;
        }
    }

    /** A soft light catch along the top edge - what separates a surface from a flat colour. */
    private void paintTopGloss(Canvas canvas, int width, int height, float radius, float strength) {
        float glossHeight = Math.min(height * 0.45f, dp(28f));
        if (glossHeight <= 0f) return;
        mExtra.setShader(glossGradient(glossHeight));
        mExtra.setAlpha(alpha(Math.min(1f, strength)));
        mExtra.setStyle(Paint.Style.FILL);
        mRect.set(0f, 0f, width, glossHeight);
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
        mRect.set(0f, 0f, width, height);
        mExtra.setShader(null);
    }

    private void paintStripes(Canvas canvas, int width, int height, float press) {
        ControlTheme theme = mTheme;
        mExtra.setShader(null);
        mExtra.setColor(theme.accent);
        mExtra.setAlpha(alpha(0.30f + 0.35f * press));
        mExtra.setStrokeWidth(dp(3f));
        mExtra.setStyle(Paint.Style.STROKE);
        float step = dp(9f);
        float offset = press * step; // The texture slides under the finger.
        for (float start = -height + offset; start < width + height; start += step) {
            canvas.drawLine(start, height, start + height, 0f, mExtra);
        }
        mExtra.setStyle(Paint.Style.FILL);
    }

    private void paintScanlines(Canvas canvas, int width, int height, float press) {
        ControlTheme theme = mTheme;
        mExtra.setShader(null);
        mExtra.setStrokeWidth(dp(1f));
        mExtra.setStyle(Paint.Style.STROKE);
        float step = dp(4f);
        int line = 0;
        for (float y = step; y < height; y += step, line++) {
            boolean bright = (line & 1) == 0;
            mExtra.setColor(bright ? theme.accent : 0xFF000000);
            mExtra.setAlpha(alpha(bright ? 0.16f + 0.25f * press : 0.12f));
            canvas.drawLine(0f, y, width, y, mExtra);
        }
        mExtra.setStyle(Paint.Style.FILL);
    }

    private void paintGlow(Canvas canvas, float press, float radius) {
        ControlTheme theme = mTheme;
        mEdge.setShader(null);
        // Three shrinking rings, brightest at the edge: reads as light spilling inwards, without a
        // blur, which would cost far too much to do every frame in game.
        for (int i = 0; i < 3; i++) {
            float inset = dp(1.5f + i * 2.5f);
            mRect.inset(inset, inset);
            mEdge.setStrokeWidth(dp(2.5f - i * 0.5f));
            mEdge.setColor(i == 0 ? theme.accent2 : theme.accent);
            mEdge.setAlpha(alpha((0.55f - i * 0.16f) * press));
            canvas.drawRoundRect(mRect, Math.max(0f, radius - inset), Math.max(0f, radius - inset), mEdge);
            mRect.inset(-inset, -inset);
        }
    }

    private void paintRipple(Canvas canvas, int width, int height, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(mTheme.accent);
        mExtra.setAlpha(alpha(0.38f * (1f - press)));
        float maxRadius = (float) Math.hypot(width, height) / 2f;
        canvas.drawCircle(width / 2f, height / 2f, maxRadius * (0.10f + 0.95f * press), mExtra);
    }

    private void paintRing(Canvas canvas, int width, int height, float press) {
        mEdge.setShader(null);
        mEdge.setStrokeWidth(dp(2.5f));
        mEdge.setColor(mTheme.accent2);
        mEdge.setAlpha(alpha(0.55f * (1f - press)));
        float maxRadius = (float) Math.hypot(width, height) / 2f;
        canvas.drawCircle(width / 2f, height / 2f, maxRadius * (0.25f + 0.85f * press), mEdge);
    }

    private void paintFlash(Canvas canvas, float radius, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(0xFFFFFFFF);
        mExtra.setAlpha(alpha(0.30f * press));
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
    }

    private void paintSheen(Canvas canvas, int width, int height, float press) {
        float bandWidth = Math.max(dp(18f), width * 0.55f);
        float x = -bandWidth + (width + bandWidth * 2f) * press;
        mExtra.setShader(sheenGradient(bandWidth));
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setAlpha(alpha(Math.min(1f, press * 2f)));
        canvas.drawRect(x, 0f, x + bandWidth, height, mExtra);
        mExtra.setShader(null);
    }

    private void paintFillUp(Canvas canvas, int width, int height, float radius, float press) {
        mFill.setShader(null);
        mFill.setColor(mTheme.accent);
        mFill.setAlpha(alpha(0.45f * press));
        float top = height * (1f - press);
        mRect.set(0f, top - radius, width, height);
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        mRect.set(0f, 0f, width, height);
    }

    // ---------------------------------------------------------------- animation

    private void advance() {
        if (!mAnimating) return;
        long now = SystemClock.uptimeMillis();
        if (mLastFrame == 0L) {
            mLastFrame = now;
            return;
        }
        float total = Math.min(MAX_STEP, (now - mLastFrame) / 1000f);
        mLastFrame = now;

        // Fixed sub steps keep the spring stable however late a frame arrives.
        float remaining = total;
        while (remaining > 0f) {
            float step = Math.min(SUB_STEP, remaining);
            remaining -= step;
            mVelocity += (mTarget - mPress) * STIFFNESS * step;
            mVelocity *= (float) Math.exp(-DAMPING * step);
            mPress += mVelocity * step;
        }

        if (Math.abs(mTarget - mPress) < REST_POSITION && Math.abs(mVelocity) < REST_SPEED) {
            mPress = mTarget;
            mVelocity = 0f;
            mAnimating = false;
        }
    }

    private float clampPress() {
        // Some overshoot past 1 is wanted - it is what makes the squash feel springy - but bounded.
        if (mPress < 0f) return 0f;
        if (mPress > MAX_PRESS) return MAX_PRESS;
        return mPress;
    }

    // ---------------------------------------------------------------- shaders

    private LinearGradient fillGradient(int width, int height) {
        if (mCachedFill == null) {
            ControlTheme theme = mTheme;
            mCachedFill = new LinearGradient(0f, 0f, width * 0.25f, height,
                    theme.accent, theme.accent2, Shader.TileMode.CLAMP);
        }
        return mCachedFill;
    }

    private LinearGradient glossGradient(float glossHeight) {
        if (mCachedGloss == null) {
            mCachedGloss = new LinearGradient(0f, 0f, 0f, glossHeight,
                    0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
        return mCachedGloss;
    }

    private LinearGradient sheenGradient(float bandWidth) {
        if (mCachedSheen == null) {
            mCachedSheen = new LinearGradient(0f, 0f, bandWidth, 0f,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
        }
        return mCachedSheen;
    }

    // ---------------------------------------------------------------- helpers

    private float dp(float value) {
        return value * mDensity;
    }

    private static int alpha(float value) {
        int a = (int) (value * 255f);
        if (a < 0) return 0;
        if (a > 255) return 255;
        return a;
    }

    /** Applies the theme's text colour to a control, or the fallback when it wants none. */
    public void applyTextColor(TextView view, int fallbackColor) {
        int color = mTheme == null ? 0 : mTheme.textColor;
        view.setTextColor(color == 0 ? fallbackColor : color);
    }
}
