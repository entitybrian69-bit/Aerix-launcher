package net.kdt.pojavlaunch.utils;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Bounded, anonymous lookup of a Java player's current public skin through Mojang APIs. */
public final class MinecraftSkinLookup {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID = Pattern.compile("(?i)[0-9a-f]{32}");
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int MAX_TEXTURE_BYTES = 1024 * 1024;

    private MinecraftSkinLookup() {
    }

    public static final class Result {
        public final String username;
        public final byte[] png;

        private Result(String username, byte[] png) {
            this.username = username;
            this.png = png;
        }
    }

    public static Result lookup(String username) throws IOException {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new IOException("Enter a Minecraft Java username (1–16 letters, numbers, or underscores).");
        }
        String name = username.trim();
        String encoded;
        try {
            encoded = URLEncoder.encode(name, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IOException("Could not encode the player name", impossible);
        }

        JSONObject profile = parseJson(readText(
                "https://api.mojang.com/users/profiles/minecraft/" + encoded, MAX_JSON_BYTES));
        String uuid = profile.optString("id", "");
        String resolvedName = profile.optString("name", name);
        if (!UUID.matcher(uuid).matches()) throw new IOException("No public Minecraft profile was found for that name.");

        JSONObject session = parseJson(readText(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false",
                MAX_JSON_BYTES));
        JSONArray properties = session.optJSONArray("properties");
        if (properties == null) throw new IOException("The public profile has no skin texture.");
        String textureData = null;
        for (int i = 0; i < properties.length(); i++) {
            JSONObject property = properties.optJSONObject(i);
            if (property != null && "textures".equals(property.optString("name"))) {
                textureData = property.optString("value", null);
                break;
            }
        }
        if (textureData == null || textureData.length() > MAX_JSON_BYTES) {
            throw new IOException("The public profile has no usable skin texture.");
        }

        final JSONObject textureRoot;
        try {
            byte[] decoded = Base64.decode(textureData, Base64.DEFAULT);
            textureRoot = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JSONException e) {
            throw new IOException("Mojang returned an invalid skin profile.", e);
        }
        JSONObject textures = textureRoot.optJSONObject("textures");
        JSONObject skin = textures == null ? null : textures.optJSONObject("SKIN");
        String textureUrl = skin == null ? null : skin.optString("url", null);
        if (textureUrl == null || !textureUrl.startsWith("https://textures.minecraft.net/texture/")) {
            throw new IOException("The public profile does not expose a supported skin texture.");
        }
        byte[] image = readBytes(textureUrl, MAX_TEXTURE_BYTES);
        if (image.length < 8 || (image[0] & 0xff) != 0x89 || image[1] != 'P' || image[2] != 'N'
                || image[3] != 'G' || image[4] != 0x0d || image[5] != 0x0a
                || image[6] != 0x1a || image[7] != 0x0a) {
            throw new IOException("The skin texture was not a valid PNG image.");
        }
        return new Result(resolvedName, image);
    }

    private static JSONObject parseJson(String value) throws IOException {
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            throw new IOException("Mojang returned invalid profile data.", e);
        }
    }

    private static String readText(String url, int limit) throws IOException {
        return new String(readBytes(url, limit), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String url, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json,image/png;q=0.9,*/*;q=0.1");
        connection.setRequestProperty("User-Agent", "Aerix-Launcher/1.0.0 (Android)");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new IOException("No public Minecraft profile was found for that name.");
                }
                throw new IOException("Mojang skin lookup failed (HTTP " + status + ").");
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > limit) throw new IOException("The profile response exceeded the size limit.");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192))) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) throw new IOException("The profile response exceeded the size limit.");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
