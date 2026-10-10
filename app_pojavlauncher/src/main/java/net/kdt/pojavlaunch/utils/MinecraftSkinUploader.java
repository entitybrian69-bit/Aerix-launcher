package net.kdt.pojavlaunch.utils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Uploads a user-selected skin through Mojang's official Minecraft Services endpoint. */
public final class MinecraftSkinUploader {
    private static final String SKIN_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final int MAX_SKIN_BYTES = 1024 * 1024;

    private MinecraftSkinUploader() {
    }

    public static void upload(String accessToken, byte[] png, String variant) throws IOException {
        if (accessToken == null || accessToken.trim().isEmpty() || accessToken.length() > 8192
                || accessToken.indexOf('\r') >= 0 || accessToken.indexOf('\n') >= 0) {
            throw new IOException("The selected Minecraft account has no usable access token. Sign in again.");
        }
        if (png == null || png.length == 0 || png.length > MAX_SKIN_BYTES) {
            throw new IOException("Skin image is empty or exceeds the 1 MiB upload limit.");
        }
        if (!"classic".equals(variant) && !"slim".equals(variant)) {
            throw new IOException("Choose either the classic or slim player model.");
        }
        if (png.length < 8 || (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N'
                || png[3] != 'G' || png[4] != 0x0d || png[5] != 0x0a
                || png[6] != 0x1a || png[7] != 0x0a) {
            throw new IOException("Skin uploads must be PNG images.");
        }

        String boundary = "AerixSkin" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = (HttpURLConnection) new URL(SKIN_ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken.trim());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "Aerix-Launcher/1.0.0 (Android)");
        connection.setChunkedStreamingMode(8192);
        try {
            try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                writeUtf8(output, "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                        + variant + "\r\n");
                writeUtf8(output, "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n");
                output.write(png);
                writeUtf8(output, "\r\n--" + boundary + "--\r\n");
                output.flush();
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Minecraft Services rejected the skin upload (HTTP " + status
                        + "). Check the account, image, and network, then sign in again if needed.");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
