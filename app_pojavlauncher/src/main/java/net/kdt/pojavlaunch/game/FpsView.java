package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Small frame rate readout drawn over the game.
 *
 * The counter is driven by the frame callback of the window, so it measures the frames the device
 * actually presents, and it is only active while it is visible: a hidden readout costs nothing.
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
public class FpsView extends View implements Choreographer.FrameCallback {
    /** How often the readout is refreshed, in milliseconds. */
    private static final long UPDATE_INTERVAL_MS = 500;
    /** How many refreshes the average covers. */
    private static final int AVERAGE_SAMPLES = 10;

    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBackgroundRect = new RectF();
    private final int[] mSamples = new int[AVERAGE_SAMPLES];

    private boolean mRunning = false;
    private long mIntervalStart = 0;
    private int mFrames = 0;
    private int mCurrentFps = 0;
    private int mSampleCount = 0;
    private int mSampleIndex = 0;
    private String mText = "-- FPS";

    public FpsView(Context context) {
        super(context);
        init();
    }

    public FpsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FpsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // The readout must never take a touch away from the game controls.
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);

        mBackgroundPaint.setColor(0xB0000000);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        float textSize = getResources().getDisplayMetrics().scaledDensity * 12f;
        mTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopCounting();
        super.onDetachedFromWindow();
    }

    /** Start or stop the counter, depending on whether the readout can be seen. */
    private void updateRunning() {
        if (isAttachedToWindow() && getVisibility() == VISIBLE && getWindowVisibility() == VISIBLE) {
            startCounting();
        } else {
            stopCounting();
        }
    }

    private void startCounting() {
        if (mRunning) return;
        mRunning = true;
        mFrames = 0;
        mSampleCount = 0;
        mSampleIndex = 0;
        mIntervalStart = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopCounting() {
        if (!mRunning) return;
        mRunning = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mRunning) return;
        Choreographer.getInstance().postFrameCallback(this);

        long now = frameTimeNanos / 1000000L;
        if (mIntervalStart == 0) {
            mIntervalStart = now;
            return;
        }
        mFrames++;
        long elapsed = now - mIntervalStart;
        if (elapsed < UPDATE_INTERVAL_MS) return;

        mCurrentFps = (int) (mFrames * 1000L / elapsed);
        mFrames = 0;
        mIntervalStart = now;
        mSamples[mSampleIndex] = mCurrentFps;
        mSampleIndex = (mSampleIndex + 1) % AVERAGE_SAMPLES;
        if (mSampleCount < AVERAGE_SAMPLES) mSampleCount++;

        mText = String.format(Locale.US, "%d FPS (avg. %d)", mCurrentFps, averageFps());
        requestLayout();
        invalidate();
    }

    private int averageFps() {
        if (mSampleCount == 0) return mCurrentFps;
        int total = 0;
        for (int i = 0; i < mSampleCount; i++) total += mSamples[i];
        return Math.round((float) total / mSampleCount);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int horizontalPadding = getPaddingLeft() + getPaddingRight();
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        int desiredWidth = (int) Math.ceil(mTextPaint.measureText(mText)) + horizontalPadding;
        int desiredHeight = (int) Math.ceil(mTextPaint.getFontMetrics().descent
                - mTextPaint.getFontMetrics().ascent) + verticalPadding;

        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 4f;
        mBackgroundRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(mBackgroundRect, radius, radius, mBackgroundPaint);

        float textY = -mTextPaint.getFontMetrics().ascent + getPaddingTop();
        canvas.drawText(mText, getPaddingLeft(), textY, mTextPaint);
    }
}
