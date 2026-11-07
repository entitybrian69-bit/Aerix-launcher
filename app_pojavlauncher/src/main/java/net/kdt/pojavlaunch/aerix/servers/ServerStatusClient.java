package net.kdt.pojavlaunch.aerix.servers;

import com.google.gson.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads Minecraft Java's public server-list status, without logging in. */
public final class ServerStatusClient {
    private static final int MAX_PACKET = 262144;
    public interface Resolver { Target resolve(String host); }
    public static final class Target {
        public final String host; public final int port;
        public Target(String host, int port) { this.host = host; this.port = port; }
    }
    public static final class Status {
        public final String version, motd, favicon;
        public final Integer protocol, online, max;
        public final Long pingMs;
        public final List<String> sample;
        Status(String version, String motd, String favicon, Integer protocol, Integer online, Integer max, Long pingMs, List<String> sample) {
            this.version=version; this.motd=motd; this.favicon=favicon; this.protocol=protocol;
            this.online=online; this.max=max; this.pingMs=pingMs; this.sample=sample;
        }
    }
    private ServerStatusClient() {}
    public static Status ping(ServerAddress address, Resolver resolver, int timeoutMs) throws IOException {
        if (address == null) throw new IOException("Invalid address");
        Target target = address.shouldResolveSrv() && resolver != null ? resolver.resolve(address.host) : null;
        String host = target == null ? address.host : target.host;
        int port = target == null ? address.port : target.port;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(bytes);
            writeVarInt(handshake, 0); writeVarInt(handshake, 767);
            byte[] hostname = address.host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(handshake, hostname.length); handshake.write(hostname);
            handshake.writeShort(address.port); writeVarInt(handshake, 1);
            writeVarInt(out, bytes.size()); out.write(bytes.toByteArray());
            out.writeByte(1); out.writeByte(0); out.flush();
            int length = readVarInt(in);
            if (length < 2 || length > MAX_PACKET) throw new IOException("Invalid status packet size");
            byte[] packet = new byte[length]; in.readFully(packet);
            DataInputStream response = new DataInputStream(new ByteArrayInputStream(packet));
            if (readVarInt(response) != 0) throw new IOException("Unexpected status response");
            int jsonLength = readVarInt(response);
            if (jsonLength < 0 || jsonLength > response.available()) throw new IOException("Invalid status payload");
            byte[] payload = new byte[jsonLength]; response.readFully(payload);
            Status parsed = parse(new String(payload, StandardCharsets.UTF_8));
            Long ping = null;
            try {
                long token = System.nanoTime();
                out.writeByte(9); out.writeByte(1); out.writeLong(token); out.flush();
                int pongSize = readVarInt(in);
                if (pongSize == 9 && readVarInt(in) == 1 && in.readLong() == token)
                    ping = Math.max(0L, (System.nanoTime() - token) / 1_000_000L);
            } catch (IOException ignored) { }
            return new Status(parsed.version, parsed.motd, parsed.favicon, parsed.protocol, parsed.online, parsed.max, ping, parsed.sample);
        } catch (RuntimeException e) { throw new IOException("Invalid server status", e); }
    }
    static Status parse(String json) throws IOException {
        try {
            checkJsonDepth(json);
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonObject version = object(root, "version"), players = object(root, "players");
            String name = clean(string(version, "name"), 120);
            StringBuilder description = new StringBuilder();
            appendComponent(root.get("description"), description, 0);
            String favicon = string(root, "favicon");
            if (favicon != null && (!favicon.startsWith("data:image/png;base64,") || favicon.length() > 100000)) favicon = null;
            List<String> sample = new ArrayList<>();
            if (players != null && players.has("sample") && players.get("sample").isJsonArray()) {
                for (JsonElement element : players.getAsJsonArray("sample")) {
                    if (sample.size() == 12) break;
                    if (element.isJsonObject()) {
                        String n = clean(string(element.getAsJsonObject(), "name"), 64);
                        if (n != null && !n.isEmpty()) sample.add(n);
                    }
                }
            }
            return new Status(name, clean(description.toString(), 1000), favicon, integer(version,"protocol"),
                integer(players,"online"), integer(players,"max"), null, Collections.unmodifiableList(sample));
        } catch (RuntimeException e) { throw new IOException("Unreadable status response", e); }
    }
    private static void checkJsonDepth(String json) throws IOException {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 64) throw new IOException("Status JSON too deep"); }
            else if (c == '}' || c == ']') depth--;
        }
    }
    private static JsonObject object(JsonObject o, String key) { return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null; }
    private static String string(JsonObject o, String key) {
        try { return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null; }
        catch (RuntimeException e) { return null; }
    }
    private static Integer integer(JsonObject o, String key) {
        try { return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : null; }
        catch (RuntimeException e) { return null; }
    }
    private static String clean(String text, int limit) {
        if (text == null) return null;
        String value = text.replaceAll("(?i)\\u00a7[0-9a-fk-orx]", "").replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        return value.length() > limit ? value.substring(0, limit) : value;
    }
    private static void appendComponent(JsonElement e, StringBuilder out, int depth) {
        if (e == null || e.isJsonNull() || depth > 12 || out.length() > 4000) return;
        if (e.isJsonPrimitive()) { out.append(e.getAsString()); return; }
        if (e.isJsonArray()) { for (JsonElement child : e.getAsJsonArray()) appendComponent(child, out, depth+1); return; }
        JsonObject obj=e.getAsJsonObject();
        if (obj.has("text")) appendComponent(obj.get("text"), out, depth+1);
        if (obj.has("extra")) appendComponent(obj.get("extra"), out, depth+1);
    }
    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7f) != 0) { out.writeByte((value & 0x7f) | 0x80); value >>>= 7; }
        out.writeByte(value);
    }
    static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int i = 0; i < 5; i++) { int b = in.readUnsignedByte(); result |= (b & 0x7f) << (7*i); if ((b & 0x80) == 0) return result; }
        throw new IOException("VarInt too long");
    }
}
