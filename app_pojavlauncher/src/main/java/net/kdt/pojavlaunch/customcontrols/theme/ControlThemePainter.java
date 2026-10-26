package net.kdt.pojavlaunch.customcontrols.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.HashMap;

/**
 * Draws a {@link ControlTheme} on a control: its pixel art, the motion that art suggests, and the
 * animation under the finger.
 *
 * <p>Two things are animated, and they are kept apart on purpose:
 *
 * <ul>
 *   <li><b>Ambience</b> - the picture itself, always moving in the way its art implies. Cheap by
 *   construction: the art is one <i>BitmapShader</i> on one rounded rectangle, moved by a matrix,
 *   and the extras on top are a handful of dots and one gradient. It is capped at
 *   {@value #AMBIENT_FRAME_MS} ms per frame so a busy game is not charged for it.</li>
 *   <li><b>Press</b> - a small damped spring, run only while a finger is on the control.</li>
 * </ul>
 *
 * <p>Neither ever touches input: the key event is still sent by the control on the frame the
 * finger lands. Ambience can be switched off entirely from the editor, and then a themed control
 * costs one shader draw and nothing else - no frames are requested at all.
 */
public final class ControlThemePainter {

    /** Spring pull towards the target state. Higher is snappier. */
    private static final float STIFFNESS = 620f;
    /** Spring friction. Lower bounces more; this one overshoots slightly and settles fast. */
    private static final float DAMPING = 21f;
    private static final float REST_POSITION = 0.003f;
    private static final float REST_SPEED = 0.06f;
    private static final float MAX_STEP = 0.05f;
    private static final float SUB_STEP = 0.008f;
    private static final float MAX_PRESS = 1.3f;
    /** Ambience is redrawn at this interval at most - about 25 fps, enough for slow motion. */
    private static final long AMBIENT_FRAME_MS = 40L;
    /** How many twinkling dots a starry theme draws. */
    private static final int TWINKLES = 14;

    /** Decoded artwork, shared by every control wearing the same theme. */
    private static final HashMap<Integer, Bitmap> BITMAPS = new HashMap<>(16);

    private final Paint mArt = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExtra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final RectF mRect = new RectF();
    private final Path mClip = new Path();
    private final float[] mTwinkleX = new float[TWINKLES];
    private final float[] mTwinkleY = new float[TWINKLES];
    private final float[] mTwinklePhase = new float[TWINKLES];

    @Nullable private ControlTheme mTheme;
    @Nullable private Bitmap mBitmap;
    @Nullable private BitmapShader mShader;
    @Nullable private AudioManager mAudio;
    @Nullable private Context mContext;

    private float mPress, mVelocity, mTarget;
    private long mLastFrame;
    private boolean mAnimating;
    /** Random enough starting point, so two controls never twinkle in lock step. */
    private final long mSeed = (long) (Math.random() * 4096);

    private int mCachedWidth = -1, mCachedHeight = -1;
    private float mCachedRadius = -1f;
    @Nullable private SweepGradient mOrbitShader;
    @Nullable private RadialGradient mGlowShader;
    @Nullable private LinearGradient mStreakShader;

    private float mDensity = 1f;
    private long mLastAmbientFrame;

    public ControlThemePainter() {
        mEdge.setStyle(Paint.Style.STROKE);
    }

    /** Called once the view exists, so density and the audio service can be resolved. */
    public void attach(View view) {
        mDensity = view.getResources().getDisplayMetrics().density;
        mContext = view.getContext().getApplicationContext();
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < TWINKLES; i++) {
            mTwinkleX[i] = hash(i * 3 + 1);
            mTwinkleY[i] = hash(i * 3 + 2);
            mTwinklePhase[i] = hash(i * 3 + 3) * 6.283f;
        }
    }

    public void setTheme(@Nullable ControlTheme theme) {
        if (mTheme == theme) return;
        mTheme = theme;
        mBitmap = theme == null ? null : art(theme.art);
        mShader = null;
        mPress = 0f;
        mVelocity = 0f;
        mTarget = 0f;
        mAnimating = false;
        mCachedWidth = mCachedHeight = -1;
        mCachedRadius = -1f;
        mOrbitShader = null;
        mGlowShader = null;
        mStreakShader = null;
    }

    public boolean hasTheme() {
        return mTheme != null;
    }

    /** True while something - a press or the ambient motion - still wants frames. */
    public boolean wantsFrame() {
        return mAnimating || (mTheme != null && ambientRunning());
    }

    private boolean ambientRunning() {
        return LauncherPreferences.PREF_CONTROL_THEME_ANIMATED;
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
     * Paints the art, its motion and the wash that keeps the label readable, and leaves the canvas
     * transformed so the label squashes or grows with the theme.
     *
     * @return the save count to hand back to {@link Canvas#restoreToCount(int)}
     */
    public int drawBack(Canvas canvas, int width, int height, float radius, boolean activated,
                        boolean overBitmap) {
        int save = canvas.save();
        ControlTheme theme = mTheme;
        if (theme == null || width <= 0 || height <= 0 || mBitmap == null) return save;

        advanceSpring();
        float press = clampPress();
        if (width != mCachedWidth || height != mCachedHeight || radius != mCachedRadius) {
            mCachedWidth = width;
            mCachedHeight = height;
            mCachedRadius = radius;
            mOrbitShader = null;
            mGlowShader = null;
            mStreakShader = null;
            mRect.set(0f, 0f, width, height);
            mClip.reset();
            mClip.addRoundRect(mRect, radius, radius, Path.Direction.CW);
            buildShader();
        }
        if (mShader == null) return save;

        float scale = 1f;
        if (theme.press == ControlTheme.Press.SQUASH) scale = 1f - 0.085f * press;
        else if (theme.press == ControlTheme.Press.LIFT) scale = 1f + 0.05f * press;
        if (scale != 1f) canvas.scale(scale, scale, width / 2f, height / 2f);

        float time = ambientTime();
        applyArtMatrix(width, height, time, press);

        // A control that already has the player's own picture keeps it: the theme becomes a moving
        // tint over it instead of covering it up.
        mArt.setAlpha(overBitmap ? 105 : 255);
        mArt.setShader(mShader);
        canvas.drawRoundRect(mRect, radius, radius, mArt);
        mArt.setShader(null);

        mScrim.setShader(null);
        mScrim.setColor(theme.scrim);
        mScrim.setAlpha(overBitmap ? Math.min(255, (theme.scrim >>> 24) / 2) : (theme.scrim >>> 24));
        canvas.drawRoundRect(mRect, radius, radius, mScrim);

        paintAmbient(canvas, width, height, radius, theme, time, press, activated);
        return save;
    }

    /** Paints the light, ripples and sweeps that belong over the label. */
    public void drawFront(Canvas canvas, int width, int height, float radius) {
        ControlTheme theme = mTheme;
        if (theme == null || width <= 0 || height <= 0) return;
        float press = clampPress();
        if (press <= 0.001f) return;

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

    /** Scales the art so it always covers the control, then lets the theme move it. */
    private void applyArtMatrix(int width, int height, float time, float press) {
        Bitmap bitmap = mBitmap;
        if (bitmap == null) return;
        float cover = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        ControlTheme theme = mTheme;
        if (theme == null) return;

        float extra = 1f;
        float dx = 0f, dy = 0f, rotate = 0f;
        switch (theme.ambient) {
            case DRIFT: {
                float period = 14000f;
                dx = -wrap(time, period) / period * bitmap.getWidth() * cover;
                dy = -wrap(time + 3000f, period) / period * bitmap.getHeight() * cover * 0.5f;
                break;
            }
            case RISE: {
                // Rockets climbing: the pattern moves up and wraps, slower than a scroll so it reads
                // as floating rather than as a treadmill.
                float period = 9000f;
                dy = -wrap(time, period) / period * bitmap.getHeight() * cover;
                break;
            }
            case SCROLL: {
                float period = 16000f;
                dx = -wrap(time, period) / period * bitmap.getWidth() * cover;
                break;
            }
            case SWAY: {
                float swell = (float) Math.sin(time / 2600f);
                dx = swell * dp(3f);
                dy = (float) Math.cos(time / 3100f) * dp(2f);
                break;
            }
            case SHIMMER: {
                // A little over-scale, so a turning rectangle never shows its corners.
                extra = 1.22f;
                rotate = wrap(time, 120000f) / 120000f * 360f;
                break;
            }
            case FLICKER:
            case TWINKLE:
            case ORBIT:
            case GLOW:
            case SWEEP:
            default:
                break;
        }
        if (press > 0.001f) extra *= 1f + 0.06f * press;

        float total = cover * extra;
        mMatrix.reset();
        mMatrix.postScale(total, total);
        if (rotate != 0f) mMatrix.postRotate(rotate, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        // Centre first, then the theme's own offset.
        dx += (width - bitmap.getWidth() * total) / 2f;
        dy += (height - bitmap.getHeight() * total) / 2f;
        mMatrix.postTranslate(dx, dy);
        mShader.setLocalMatrix(mMatrix);
    }

    private void paintAmbient(Canvas canvas, int width, int height, float radius,
                              ControlTheme theme, float time, float press, boolean activated) {
        switch (theme.ambient) {
            case TWINKLE: paintTwinkles(canvas, width, height, time, press); break;
            case ORBIT: paintOrbit(canvas, width, height, radius, time, press); break;
            case GLOW: paintMoonGlow(canvas, width, height, radius, time, press, activated); break;
            case SWEEP: paintAmbientStreak(canvas, width, height, time, press); break;
            case FLICKER: paintFlicker(canvas, width, height, radius, time); break;
            default: break;
        }
    }

    /** Stars that light up and dim at their own pace - the stardust and star-cross skies. */
    private void paintTwinkles(Canvas canvas, int width, int height, float time, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        for (int i = 0; i < TWINKLES; i++) {
            float phase = time / 900f + mTwinklePhase[i] + mSeed;
            float pulse = 0.5f + 0.5f * (float) Math.sin(phase);
            float alpha = (0.18f + 0.72f * pulse) * (1f + 0.3f * press);
            mExtra.setColor(0xFFFFFFFF);
            mExtra.setAlpha(alpha(alpha));
            float size = dp(i % 3 == 0 ? 2.2f : 1.3f) * (0.8f + 0.5f * pulse);
            canvas.drawCircle(mTwinkleX[i] * width, mTwinkleY[i] * height, size, mExtra);
        }
    }

    /** The accretion disk of the black hole, turning. */
    private void paintOrbit(Canvas canvas, int width, int height, float radius, float time,
                            float press) {
        if (mOrbitShader == null) {
            int[] colours = {0x00FFFFFF, 0xAAE1BEE7, 0x00FFFFFF, 0x66B39DDB, 0x00FFFFFF};
            mOrbitShader = new SweepGradient(width / 2f, height / 2f, colours, null);
        }
        int save = canvas.save();
        canvas.rotate(wrap(time, 12000f) / 12000f * 360f, width / 2f, height / 2f);
        mExtra.setShader(mOrbitShader);
        mExtra.setStyle(Paint.Style.STROKE);
        mExtra.setStrokeWidth(dp(2f + 1.5f * press));
        mExtra.setAlpha(alpha(0.55f + 0.35f * press));
        mRect.set(width * 0.10f, height * 0.16f, width * 0.90f, height * 0.84f);
        canvas.drawOval(mRect, mExtra);
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mRect.set(0f, 0f, width, height);
        canvas.restoreToCount(save);
    }

    /** Moonlight breathing behind the label. */
    private void paintMoonGlow(Canvas canvas, int width, int height, float radius, float time,
                               float press, boolean activated) {
        if (mGlowShader == null) {
            mGlowShader = new RadialGradient(width * 0.5f, height * 0.42f, Math.max(width, height) * 0.7f,
                    new int[]{0x66FFF7DC, 0x00FFF7DC}, null, Shader.TileMode.CLAMP);
        }
        float breathe = 0.5f + 0.5f * (float) Math.sin(time / 1500f);
        mExtra.setShader(mGlowShader);
        mExtra.setAlpha(alpha((0.25f + 0.35f * breathe + 0.3f * press) * (activated ? 1.25f : 1f)));
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
        mExtra.setShader(null);
    }

    /** The comet's own streak, crossing every few seconds. */
    private void paintAmbientStreak(Canvas canvas, int width, int height, float time, float press) {
        float period = 2600f;
        float progress = wrap(time, period) / period;
        if (progress > 0.45f) return; // Off-screen half the time, so it reads as occasional.
        paintStreak(canvas, width, height, progress / 0.45f, 0.55f + 0.4f * press);
    }

    /** Interference: the whole control's brightness jitters in short steps. */
    private void paintFlicker(Canvas canvas, int width, int height, float radius, float time) {
        long step = (long) (time / 120f);
        float noise = hash(step + mSeed);
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(noise > 0.5f ? 0xFFFFFFFF : 0xFF0A1014);
        mExtra.setAlpha(alpha(0.06f + 0.16f * noise));
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
    }

    // ---------------------------------------------------------------- press effects

    private void paintGlow(Canvas canvas, float press, float radius) {
        mEdge.setShader(null);
        // Three shrinking rings, brightest at the edge: reads as light spilling inwards, without a
        // blur, which would cost far too much to do every frame in game.
        for (int i = 0; i < 3; i++) {
            float inset = dp(1.5f + i * 2.5f);
            mRect.inset(inset, inset);
            mEdge.setStrokeWidth(dp(2.5f - i * 0.5f));
            mEdge.setColor(i == 0 ? 0xFFFFFFFF : 0xFFB3E5FC);
            mEdge.setAlpha(alpha((0.55f - i * 0.16f) * press));
            canvas.drawRoundRect(mRect, Math.max(0f, radius - inset), Math.max(0f, radius - inset), mEdge);
            mRect.inset(-inset, -inset);
        }
    }

    private void paintRipple(Canvas canvas, int width, int height, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(0xFFFFFFFF);
        mExtra.setAlpha(alpha(0.34f * (1f - press)));
        float maxRadius = (float) Math.hypot(width, height) / 2f;
        canvas.drawCircle(width / 2f, height / 2f, maxRadius * (0.10f + 0.95f * press), mExtra);
    }

    private void paintRing(Canvas canvas, int width, int height, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.STROKE);
        mExtra.setStrokeWidth(dp(2.5f));
        mExtra.setColor(0xFFE1BEE7);
        mExtra.setAlpha(alpha(0.6f * (1f - press)));
        float maxRadius = (float) Math.hypot(width, height) / 2f;
        canvas.drawCircle(width / 2f, height / 2f, maxRadius * (0.25f + 0.85f * press), mExtra);
        mExtra.setStyle(Paint.Style.FILL);
    }

    private void paintFlash(Canvas canvas, float radius, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(0xFFFFFFFF);
        mExtra.setAlpha(alpha(0.32f * press));
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
    }

    private void paintSheen(Canvas canvas, int width, int height, float press) {
        paintStreak(canvas, width, height, press, 1f);
    }

    /** A diagonal band of light travelling left to right; used by sheen and by the comet theme. */
    private void paintStreak(Canvas canvas, int width, int height, float progress, float strength) {
        float band = Math.max(dp(22f), width * 0.55f);
        float x = -band + (width + band * 2f) * progress;
        if (mStreakShader == null || mCachedWidth != width) {
            mStreakShader = new LinearGradient(0f, 0f, band, 0f,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        }
        mExtra.setShader(mStreakShader);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setAlpha(alpha(Math.min(1f, strength)));
        canvas.save();
        canvas.translate(x, 0f);
        canvas.skew(-0.35f, 0f);
        canvas.drawRect(0f, -height, band, height * 2f, mExtra);
        canvas.restore();
        mExtra.setShader(null);
    }

    private void paintFillUp(Canvas canvas, int width, int height, float radius, float press) {
        mExtra.setShader(null);
        mExtra.setStyle(Paint.Style.FILL);
        mExtra.setColor(0xFFFFFFFF);
        mExtra.setAlpha(alpha(0.22f * press));
        float top = height * (1f - press);
        mRect.set(0f, top - radius, width, height);
        canvas.drawRoundRect(mRect, radius, radius, mExtra);
        mRect.set(0f, 0f, width, height);
    }

    // ---------------------------------------------------------------- animation

    private void advanceSpring() {
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

    /** Clock for the ambience, quantised so a 120 Hz screen does not ask for twice the work. */
    private float ambientTime() {
        long now = SystemClock.uptimeMillis();
        if (now - mLastAmbientFrame < AMBIENT_FRAME_MS) {
            // Reuse the last tick: the picture simply holds for a frame or two.
            return (mLastAmbientFrame + mSeed) / 1000f;
        }
        mLastAmbientFrame = now;
        return (now + mSeed) / 1000f;
    }

    private float clampPress() {
        if (mPress < 0f) return 0f;
        return Math.min(mPress, MAX_PRESS);
    }

    // ---------------------------------------------------------------- helpers

    private void buildShader() {
        Bitmap bitmap = mBitmap;
        if (bitmap == null) return;
        mShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    @Nullable
    private Bitmap art(int res) {
        Bitmap cached = BITMAPS.get(res);
        if (cached != null) return cached;
        if (mContext == null) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), res, options);
            if (bitmap != null) BITMAPS.put(res, bitmap);
            return bitmap;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static float wrap(float value, float period) {
        float v = value % period;
        return v < 0f ? v + period : v;
    }

    /** Cheap deterministic 0..1 noise, so the twinkles are steady instead of re-rolled per frame. */
    private static float hash(float value) {
        float x = (float) Math.sin(value * 12.9898f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static int alpha(float value) {
        int a = (int) (value * 255f);
        if (a < 0) return 0;
        return Math.min(a, 255);
    }

    /** Applies the theme's text colour to a control, or the fallback when it wants none. */
    public void applyTextColor(TextView view, int fallbackColor) {
        int color = mTheme == null ? 0 : mTheme.textColor;
        view.setTextColor(color == 0 ? fallbackColor : color);
    }
}
