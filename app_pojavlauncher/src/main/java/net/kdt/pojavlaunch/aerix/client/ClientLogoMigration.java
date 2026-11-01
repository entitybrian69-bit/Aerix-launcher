package net.kdt.pojavlaunch.aerix.client;

/** Identifies the old generated Client icon without overwriting a player's custom icon. */
public final class ClientLogoMigration {
    private ClientLogoMigration() { }

    private static final int[][] SAMPLES = {
        {64, 41, 141, 239, 224}, {64, 45, 147, 238, 225},
        {64, 61, 11, 20, 32}, {64, 76, 224, 184, 109},
        {64, 80, 224, 184, 109}, {40, 85, 215, 226, 234},
        {35, 50, 14, 21, 30}, {92, 55, 21, 32, 46},
        {80, 88, 215, 226, 234}, {50, 40, 13, 21, 33}
    };

    public static int sampleCount() { return SAMPLES.length; }
    public static int sampleX(int index) { return SAMPLES[index][0]; }
    public static int sampleY(int index) { return SAMPLES[index][1]; }

    public static boolean matchesLegacy(int width, int height, int[] pixels) {
        if (width != 128 || height != 128 || pixels == null || pixels.length != SAMPLES.length) return false;
        for (int i = 0; i < SAMPLES.length; i++) {
            int color = pixels[i];
            int[] expected = SAMPLES[i];
            if (Math.abs(((color >>> 16) & 255) - expected[2]) > 42 ||
                Math.abs(((color >>> 8) & 255) - expected[3]) > 42 ||
                Math.abs((color & 255) - expected[4]) > 42) return false;
        }
        return true;
    }
}
