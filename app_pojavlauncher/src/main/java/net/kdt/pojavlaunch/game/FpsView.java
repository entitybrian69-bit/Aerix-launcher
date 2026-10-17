package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.backend.GLFWBackend;

/** Draggable game-render FPS readout. Android UI vsync is deliberately not a frame source. */
public class FpsView extends View {
    private static final long UPDATE_INTERVAL_MS = 500;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF backgroundRect = new RectF(), bounds = new RectF();
    private final FrameRateSampler sampler = new FrameRateSampler();
    private SharedPreferences preferences;
    private boolean running, positionLoaded, dragging;
    private int pointerId = -1, touchSlop;
    private float downX, downY, startX, startY;
    private String text = "-- FPS";
    private View host;
    private final int[] parentLocation = new int[2];
    private final OnLayoutChangeListener hostLayout = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l != or - ol || b - t != ob - ot) post(this::restorePosition);
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            // Backends that bypass GLFW swaps must not substitute UI refresh rate for game FPS.
            long count = Platform.PLATFORM instanceof GLFWBackend ? NativeFrameCounter.read() : -1;
            if (count < 0) {
                sampler.reset();
                updateText(getContext().getString(R.string.fps_overlay_unavailable));
            } else if (sampler.sample(count, SystemClock.elapsedRealtimeNanos())) {
                updateText(getContext().getString(R.string.fps_overlay_value, sampler.fps(), sampler.average()));
            }
            postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public FpsView(Context context) { super(context); init(); }
    public FpsView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public FpsView(Context context, @Nullable AttributeSet attrs, int style) { super(context, attrs, style); init(); }
    private void init() {
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
        preferences = getContext().getSharedPreferences("fps_overlay", Context.MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        background.setColor(0xB0000000);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 12f);
        setContentDescription(getContext().getString(R.string.fps_overlay_drag));
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> { post(this::restorePosition); return insets; });
    }
    private void updateText(String value) {
        if (value.equals(text)) return;
        text = value;
        setContentDescription(value + ". " + getContext().getString(R.string.fps_overlay_drag));
        requestLayout();
        invalidate();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof View) {
            host = (View) getParent();
            host.addOnLayoutChangeListener(hostLayout);
        }
        post(this::restorePosition);
        updateRunning();
    }
    @Override protected void onDetachedFromWindow() {
        stopCounting();
        if (host != null) { host.removeOnLayoutChangeListener(hostLayout); host = null; }
        super.onDetachedFromWindow();
    }
    @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); updateRunning(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateRunning(); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); updateRunning(); }
    private void updateRunning() {
        if (isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE && hasWindowFocus()) {
            if (running) return;
            running = true;
            sampler.reset();
            updateText(getContext().getString(R.string.fps_overlay_waiting));
            post(poll);
        } else stopCounting();
    }
    private void stopCounting() {
        if (!running) return;
        running = false;
        removeCallbacks(poll);
        pointerId = -1;
        dragging = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    private boolean updateBounds() {
        if (host == null || host.getWidth() == 0 || getWidth() == 0) return false;
        float left = host.getPaddingLeft(), top = host.getPaddingTop();
        float right = host.getWidth() - host.getPaddingRight();
        float bottom = host.getHeight() - host.getPaddingBottom();
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(this);
        if (windowInsets != null) {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
            host.getLocationInWindow(parentLocation);
            left = Math.max(left, safe.left - parentLocation[0]);
            top = Math.max(top, safe.top - parentLocation[1]);
            right = Math.min(right, getRootView().getWidth() - safe.right - parentLocation[0]);
            bottom = Math.min(bottom, getRootView().getHeight() - safe.bottom - parentLocation[1]);
        }
        bounds.set(left, top, Math.max(left, right - getWidth()), Math.max(top, bottom - getHeight()));
        return true;
    }
    private void moveTo(float x, float y) {
        if (!updateBounds()) return;
        setX(OverlayPosition.clamp(x, bounds.left, bounds.right));
        setY(OverlayPosition.clamp(y, bounds.top, bounds.bottom));
    }
    private void restorePosition() {
        if (pointerId != -1 || !updateBounds()) return;
        if (preferences.contains("x") && preferences.contains("y")) {
            setX(OverlayPosition.restore(preferences.getFloat("x", 0), bounds.left, bounds.right));
            setY(OverlayPosition.restore(preferences.getFloat("y", 0), bounds.top, bounds.bottom));
        } else moveTo(getX(), getY());
        positionLoaded = true;
    }
    private void savePosition() {
        if (!updateBounds()) return;
        preferences.edit()
            .putFloat("x", OverlayPosition.fraction(getX(), bounds.left, bounds.right))
            .putFloat("y", OverlayPosition.fraction(getY(), bounds.top, bounds.bottom)).apply();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerId = event.getPointerId(0);
                downX = event.getRawX(); downY = event.getRawY();
                startX = getX(); startY = getY(); dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pointerId == -1 || event.getPointerId(0) != pointerId) return true;
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (dx * dx + dy * dy > touchSlop * touchSlop) dragging = true;
                if (dragging) moveTo(startX + dx, startY + dy);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) != pointerId) return true;
                // Do not transfer the drag to another finger and jump across the game screen.
                if (dragging) savePosition();
                pointerId = -1; dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_UP:
                if (pointerId != -1) { if (dragging) savePosition(); else performClick(); }
                pointerId = -1; dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) moveTo(startX, startY);
                pointerId = -1; dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(() -> { if (!positionLoaded) restorePosition(); else moveTo(getX(), getY()); });
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = (int) Math.ceil(textPaint.measureText(text)) + getPaddingLeft() + getPaddingRight();
        int height = (int) Math.ceil(textPaint.getFontMetrics().descent - textPaint.getFontMetrics().ascent) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        backgroundRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(backgroundRect, getHeight() / 4f, getHeight() / 4f, background);
        canvas.drawText(text, getPaddingLeft(), -textPaint.getFontMetrics().ascent + getPaddingTop(), textPaint);
    }
}
