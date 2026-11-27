package aerix.flashback.compat;

import java.lang.reflect.Method;

public final class AerixFFmpegProbe {

    private static volatile Boolean cachedAvailable;

    private AerixFFmpegProbe() {}

    public static boolean isAvailable() {
        Boolean cached = cachedAvailable;
        if (cached != null) return cached.booleanValue();

        synchronized (AerixFFmpegProbe.class) {
            if (cachedAvailable != null) return cachedAvailable.booleanValue();
            boolean ok = false;
            try {
                Class<?> avcodec = Class.forName("org.bytedeco.ffmpeg.global.avcodec");
                Method ver = avcodec.getMethod("avcodec_version");
                ver.invoke(null);
                Class<?> avformat = Class.forName("org.bytedeco.ffmpeg.global.avformat");
                Method fver = avformat.getMethod("avformat_version");
                fver.invoke(null);
                ok = true;
            } catch (Throwable t) {
                System.err.println("[Aerix Flashback] FFmpeg native library unavailable on this runtime (" + t.getClass().getSimpleName() + "); using safe PNG fallback.");
                ok = false;
            }
            cachedAvailable = Boolean.valueOf(ok);
            return ok;
        }
    }
}
