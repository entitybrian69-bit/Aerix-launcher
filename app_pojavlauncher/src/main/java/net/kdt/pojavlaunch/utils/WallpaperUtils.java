package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/** Small, bounded decoder for user-selected launcher wallpaper images. */
public final class WallpaperUtils {
    public static final String PREFERENCE_KEY = "aerix_launcher_wallpaper_uri";

    private WallpaperUtils() {
    }

    public static Bitmap decode(Context context, Uri uri, int maxWidth, int maxHeight) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open wallpaper image");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported wallpaper image");
        }

        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > Math.max(1, maxWidth) * 2
                || bounds.outHeight / sampleSize > Math.max(1, maxHeight) * 2) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not reopen wallpaper image");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, decode);
            if (bitmap == null) throw new IOException("Could not decode wallpaper image");
            return bitmap;
        }
    }
}
