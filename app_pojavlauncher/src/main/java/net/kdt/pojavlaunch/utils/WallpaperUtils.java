package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Wallpaper catalog, bounded image decoding, and wallpaper-derived theme color. */
public final class WallpaperUtils {
    public static final String PREFERENCE_KEY = "aerix_launcher_wallpaper_uri";
    public static final String SELECTED_CATALOG_KEY = "aerix_launcher_wallpaper_catalog_id";
    public static final String DEFAULT_WALLPAPER_ID = "01-crystal-river";
    private static final String ASSET_DIRECTORY = "aerix_wallpapers/";

    private static final Wallpaper[] WALLPAPERS = {
            new Wallpaper("01-crystal-river", "Crystal River"),
            new Wallpaper("02-cherry-blossom", "Cherry Blossom"),
            new Wallpaper("03-golden-sunset", "Golden Sunset"),
            new Wallpaper("04-lush-caves", "Lush Caves"),
            new Wallpaper("05-ocean-monument", "Ocean Monument"),
            new Wallpaper("06-nether-portal", "Nether Portal"),
            new Wallpaper("07-snowy-peaks", "Snowy Peaks"),
            new Wallpaper("08-mushroom-island", "Mushroom Island"),
            new Wallpaper("09-desert-temple", "Desert Temple"),
            new Wallpaper("10-bamboo-grove", "Bamboo Grove"),
            new Wallpaper("11-autumn-forest", "Autumn Forest"),
            new Wallpaper("12-coral-reef", "Coral Reef"),
            new Wallpaper("13-flower-meadow", "Flower Meadow"),
            new Wallpaper("14-dark-oak", "Dark Oak"),
            new Wallpaper("15-savanna-storm", "Savanna Storm"),
            new Wallpaper("16-frozen-lake", "Frozen Lake"),
            new Wallpaper("17-floating-islands", "Floating Islands"),
            new Wallpaper("18-village-morning", "Village Morning"),
            new Wallpaper("19-badlands-canyon", "Badlands Canyon"),
            new Wallpaper("20-moonlit-taiga", "Moonlit Taiga"),
            new Wallpaper("21-rose-grove", "Rose Grove"),
            new Wallpaper("22-aurora-lake", "Aurora Lake"),
            new Wallpaper("23-violet-river", "Violet River"),
            new Wallpaper("24-deep-ocean", "Deep Ocean"),
            new Wallpaper("25-golden-meadow", "Golden Meadow")
    };

    private WallpaperUtils() {
    }

    public static final class Wallpaper {
        public final String id;
        public final String name;
        public final String assetFile;

        private Wallpaper(String id, String name) {
            this.id = id;
            this.name = name;
            this.assetFile = id + ".jpg";
        }
    }

    public static Wallpaper[] catalog() {
        return WALLPAPERS.clone();
    }

    public static Wallpaper find(String id) {
        if (id == null) return null;
        for (Wallpaper wallpaper : WALLPAPERS) if (wallpaper.id.equals(id)) return wallpaper;
        return null;
    }

    public static String selectedId(Context context) {
        String id = LauncherPreferences.DEFAULT_PREF.getString(SELECTED_CATALOG_KEY, DEFAULT_WALLPAPER_ID);
        return find(id) == null ? DEFAULT_WALLPAPER_ID : id;
    }

    public static Bitmap decodeBundled(Context context, String id, int maxWidth, int maxHeight) throws IOException {
        Wallpaper wallpaper = find(id);
        if (wallpaper == null) wallpaper = find(DEFAULT_WALLPAPER_ID);
        if (wallpaper == null) throw new IOException("Wallpaper catalog is empty");
        String assetPath = ASSET_DIRECTORY + wallpaper.assetFile;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getAssets().open(assetPath)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Invalid bundled wallpaper: " + wallpaper.name);
        BitmapFactory.Options options = boundedOptions(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
        try (InputStream input = context.getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Could not decode bundled wallpaper: " + wallpaper.name);
            return bitmap;
        }
    }

    public static Bitmap decode(Context context, Uri uri, int maxWidth, int maxHeight) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open wallpaper image");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported wallpaper image");
        BitmapFactory.Options options = boundedOptions(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not reopen wallpaper image");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Could not decode wallpaper image");
            return bitmap;
        }
    }

    private static BitmapFactory.Options boundedOptions(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        int safeWidth = Math.max(1, maxWidth);
        int safeHeight = Math.max(1, maxHeight);
        int sampleSize = 1;
        while (sourceWidth / sampleSize > safeWidth * 2 || sourceHeight / sampleSize > safeHeight * 2) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    /** Picks a stable accent from the most saturated useful hue in the wallpaper. */
    public static int sampleAccentColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return Color.rgb(32, 217, 223);
        final int bins = 36;
        float[] hueWeight = new float[bins];
        float[] saturationSum = new float[bins];
        float[] valueSum = new float[bins];
        int[] count = new int[bins];
        int stepX = Math.max(1, bitmap.getWidth() / 60);
        int stepY = Math.max(1, bitmap.getHeight() / 34);
        float[] hsv = new float[3];
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int color = bitmap.getPixel(x, y);
                Color.colorToHSV(color, hsv);
                if (hsv[1] < 0.24f || hsv[2] < 0.20f || hsv[2] > 0.98f) continue;
                int bin = Math.min(bins - 1, (int) (hsv[0] / 360f * bins));
                float weight = hsv[1] * (0.35f + 0.65f * hsv[2]);
                hueWeight[bin] += weight;
                saturationSum[bin] += hsv[1];
                valueSum[bin] += hsv[2];
                count[bin]++;
            }
        }
        int dominant = 0;
        for (int i = 1; i < bins; i++) if (hueWeight[i] > hueWeight[dominant]) dominant = i;
        if (count[dominant] == 0) return Color.rgb(32, 217, 223);
        float hue = ((dominant + 0.5f) / bins) * 360f;
        float saturation = Math.max(0.55f, Math.min(0.82f, saturationSum[dominant] / count[dominant]));
        float value = Math.max(0.82f, Math.min(0.95f, valueSum[dominant] / count[dominant] + 0.15f));
        return Color.HSVToColor(new float[]{hue, saturation, value});
    }

    public static String colorToHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }
}
