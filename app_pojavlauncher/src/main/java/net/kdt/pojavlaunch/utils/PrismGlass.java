package net.kdt.pojavlaunch.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A wallpaper-aligned, downsampled backdrop for the launcher shell and Home cards. */
public final class PrismGlass {
    private static Bitmap backdrop;
    private PrismGlass() { }

    public static synchronized void setWallpaper(Bitmap wallpaper) {
        // Filtered downsampling produces a soft background even on pre-Android 12 devices.
        int w = Math.max(1, wallpaper.getWidth() / 4);
        int h = Math.max(1, wallpaper.getHeight() / 4);
        backdrop = Bitmap.createScaledBitmap(wallpaper, w, h, true);
    }

    public static synchronized void clear() {
        backdrop = null;
    }

    public static void apply(View target) {
        if (target != null) target.setBackground(new GlassDrawable(target));
    }

    private static final class GlassDrawable extends Drawable {
        private final View target;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int[] location = new int[2];
        private final int[] decorLocation = new int[2];

        GlassDrawable(View target) { this.target = target; }

        @Override public void draw(Canvas canvas) {
            RectF area = new RectF(getBounds());
            float radius = 22f * target.getResources().getDisplayMetrics().density;
            Path outline = new Path();
            outline.addRoundRect(area, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(outline);
            Bitmap image = backdrop;
            if (image != null && !image.isRecycled()) {
                View decor = target.getRootView();
                target.getLocationOnScreen(location);
                decor.getLocationOnScreen(decorLocation);
                float screenX = location[0] - decorLocation[0];
                float screenY = location[1] - decorLocation[1];
                float screenW = decor.getWidth();
                float screenH = decor.getHeight();
                float scale = Math.max(screenW / image.getWidth(), screenH / image.getHeight());
                float offsetX = (screenW - image.getWidth() * scale) / 2f;
                float offsetY = (screenH - image.getHeight() * scale) / 2f;
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawBitmap(image, null, new RectF(offsetX - screenX, offsetY - screenY,
                        offsetX - screenX + image.getWidth() * scale,
                        offsetY - screenY + image.getHeight() * scale), paint);
            }
            paint.setColor(0x24D9ECFA);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(area, paint);
            canvas.restore();
            paint.setColor(0xB7ECFAFF);
            paint.setStrokeWidth(target.getResources().getDisplayMetrics().density);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(area, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public void setAlpha(int alpha) { invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
