package net.kdt.pojavlaunch.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.PojavApplication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Anonymous GitHub Releases check; it never fetches credentials or installs an APK. */
public final class UpdateChecker {
    private static final String RELEASE_API = "https://api.github.com/repos/entitybrian69-bit/Aerix-launcher/releases/latest";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private UpdateChecker() {
    }

    public interface Callback {
        void onComplete(Release release, Exception error);
    }

    public static final class Release {
        public final String version;
        public final String pageUrl;

        private Release(String version, String pageUrl) {
            this.version = version;
            this.pageUrl = pageUrl;
        }

        public boolean isNewerThan(String installedVersion) {
            return VersionComparator.compare(version, installedVersion) > 0;
        }
    }

    public static void check(Callback callback) {
        PojavApplication.sExecutorService.execute(() -> {
            Release release = null;
            Exception error = null;
            try {
                release = requestLatestRelease();
            } catch (Exception e) {
                error = e;
            }
            Release result = release;
            Exception failure = error;
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(result, failure));
        });
    }

    private static Release requestLatestRelease() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(10000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Aerix-Launcher/1.0.0 (Android)");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub Releases returned HTTP " + responseCode);
            }
            byte[] body;
            try (InputStream input = connection.getInputStream()) {
                body = readBounded(input);
            }
            JsonObject release = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String version = release.has("tag_name") ? release.get("tag_name").getAsString() : null;
            String pageUrl = release.has("html_url") ? release.get("html_url").getAsString() : null;
            if (version == null || version.trim().isEmpty() || pageUrl == null || pageUrl.trim().isEmpty()) {
                throw new IOException("The latest GitHub release is missing its tag or web page URL");
            }
            return new Release(version, pageUrl);
        } catch (IllegalStateException | com.google.gson.JsonParseException e) {
            throw new IOException("GitHub returned malformed release metadata", e);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_RESPONSE_BYTES) {
                throw new IOException("GitHub release response exceeded the size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
