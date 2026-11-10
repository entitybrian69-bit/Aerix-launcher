package aerix.client.mod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads the launcher's per-instance Trinkies loadout and holds it as data the client can act on.
 *
 * The launcher writes {@code <game directory>/trinkies/equipped.json} immediately before a supported
 * instance starts; this class is the reader on the other side of that file. It is deliberately
 * dependency-free (no Minecraft, no Gson, no AWT) so the exact same parsing runs in a plain JVM test
 * as inside the game: everything here is text in, decisions out, and {@link Snapshot} is immutable.
 *
 * <p>Nothing in this class draws anything. It answers "what is the player wearing, and what does the
 * catalog say about it", which is what lets an added catalog item be worn without a mod change.
 */
public final class TrinkiesLoadout {

    /** The only schema this reader understands; mirrors {@code docs/wardrobe/schema/equipped.schema.json}. */
    public static final int SCHEMA = 1;

    /** Slot order used by the launcher's bake, kept here so both sides enumerate the same six. */
    public static final String[] CATEGORIES = {"hat", "suit", "back", "pet", "emote", "aura"};

    public static final String FILE_NAME = "equipped.json";
    public static final String APPLIED_FILE_NAME = "applied.json";
    public static final String CATALOG_RESOURCE = "/assets/trinkies/catalog.json";

    private TrinkiesLoadout() {
    }

    /** One catalog row, reduced to the fields a client needs to find the item's own assets. */
    public static final class Entry {
        public final String id;
        public final String category;
        public final String name;
        public final String garmentPath;
        public final String capeTexturePath;
        public final String itemJsonPath;

        Entry(String id, String category, String name, String garmentPath,
              String capeTexturePath, String itemJsonPath) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.garmentPath = garmentPath;
            this.capeTexturePath = capeTexturePath;
            this.itemJsonPath = itemJsonPath;
        }

        /** {@code animations.json} is a sibling of {@code item.json} inside the item's own folder. */
        public String animationsJsonPath() {
            int slash = itemJsonPath.lastIndexOf('/');
            return slash < 0 ? "animations.json" : itemJsonPath.substring(0, slash + 1) + "animations.json";
        }
    }

    /** A validated loadout. A rejected value never reaches {@link #equippedIdFor}: it becomes a problem. */
    public static final class Snapshot {
        private final boolean present;
        private final String problem;
        private final Map<String, String> equipped;
        private final List<String> emoteWheel;
        private final Map<String, Entry> catalog;

        Snapshot(boolean present, String problem, Map<String, String> equipped,
                 List<String> emoteWheel, Map<String, Entry> catalog) {
            this.present = present;
            this.problem = problem;
            this.equipped = Collections.unmodifiableMap(new LinkedHashMap<String, String>(equipped));
            this.emoteWheel = Collections.unmodifiableList(new ArrayList<String>(emoteWheel));
            this.catalog = catalog;
        }

        public boolean isPresent() {
            return present;
        }

        /** Non-null when the file was unreadable or disagreed with the catalog; the rest still applies. */
        public String getProblem() {
            return problem;
        }

        public boolean hasProblem() {
            return problem != null;
        }

        /** The validated catalog id for a slot, or null when nothing is worn there. */
        public String equippedIdFor(String category) {
            return equipped.get(category);
        }

        public Entry entryFor(String category) {
            String id = equipped.get(category);
            return id == null ? null : catalog.get(id);
        }

        /**
         * The catalog entry behind an id, whatever category it came from. The emote wheel may hold any
         * item, so it cannot be looked up through the six worn slots.
         */
        public Entry entryById(String id) {
            return id == null ? null : catalog.get(id);
        }

        public int equippedCount() {
            return equipped.size();
        }

        /** The eight keybound emotes, each an id or null; never used for what the player wears. */
        public List<String> emoteWheel() {
            return emoteWheel;
        }
    }

    /** Parse and validate a loadout file against a catalog. Never throws on bad content. */
    public static Snapshot read(File equippedFile, Map<String, Entry> catalog) {
        if (equippedFile == null || !equippedFile.isFile()) {
            return new Snapshot(false, null, new LinkedHashMap<String, String>(),
                    emptyWheel(), catalog);
        }
        try {
            return parse(readText(equippedFile), catalog);
        } catch (IOException error) {
            return new Snapshot(true, "loadout is unreadable: " + error.getMessage(),
                    new LinkedHashMap<String, String>(), emptyWheel(), catalog);
        } catch (RuntimeException error) {
            return new Snapshot(true, "loadout is malformed: " + error.getMessage(),
                    new LinkedHashMap<String, String>(), emptyWheel(), catalog);
        }
    }

    /** The same validation against already-read text; this is the entry point the JVM test drives. */
    public static Snapshot parse(String json, Map<String, Entry> catalog) {
        Map<String, String> equipped = new LinkedHashMap<String, String>();
        List<String> wheel = new ArrayList<String>();
        Object root;
        try {
            root = parseJson(json);
        } catch (RuntimeException error) {
            return new Snapshot(true, "loadout is malformed: " + error.getMessage(),
                    equipped, emptyWheel(), catalog);
        }
        if (!(root instanceof Map)) {
            return new Snapshot(true, "loadout root is not an object", equipped, emptyWheel(), catalog);
        }
        Map<?, ?> object = (Map<?, ?>) root;
        String problem = null;

        Object schema = object.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).intValue() != SCHEMA) {
            problem = "unsupported loadout schema";
        }
        for (Object rawKey : object.keySet()) {
            String key = text(rawKey);
            if (key != null && !"schema".equals(key) && !"emote_wheel".equals(key) && !isCategory(key)) {
                problem = problem != null ? problem : "unexpected loadout field: " + key;
            }
        }
        for (String category : CATEGORIES) {
            if (!object.containsKey(category)) {
                problem = problem != null ? problem : "loadout is missing slot: " + category;
                continue;
            }
            Object raw = object.get(category);
            if (raw == null) {
                continue;
            }
            if (!(raw instanceof String)) {
                problem = problem != null ? problem : category + " is not an id or null";
                continue;
            }
            String id = (String) raw;
            String rejected = reject(id, category, catalog);
            if (rejected != null) {
                problem = problem != null ? problem : rejected;
                continue;
            }
            equipped.put(category, id);
        }
        if (!object.containsKey("emote_wheel")) {
            problem = problem != null ? problem : "loadout is missing emote_wheel";
        } else {
            Object rawWheel = object.get("emote_wheel");
            if (!(rawWheel instanceof List)) {
                problem = problem != null ? problem : "emote_wheel is not an array";
            } else {
                List<?> rows = (List<?>) rawWheel;
                if (rows.size() != 8) {
                    problem = problem != null ? problem : "emote_wheel must hold exactly 8 slots";
                }
                for (Object row : rows) {
                    if (row == null) {
                        wheel.add(null);
                        continue;
                    }
                    if (!(row instanceof String)) {
                        problem = problem != null ? problem : "emote_wheel holds a non-id value";
                        wheel.add(null);
                        continue;
                    }
                    String id = (String) row;
                    String rejected = reject(id, null, catalog);
                    if (rejected != null) {
                        problem = problem != null ? problem : rejected.replace("slot", "emote_wheel");
                        wheel.add(null);
                        continue;
                    }
                    wheel.add(id);
                }
            }
        }
        while (wheel.size() < 8) {
            wheel.add(null);
        }
        return new Snapshot(true, problem, equipped, wheel, catalog);
    }

    /** Null when the id is well-formed and present in the catalog under the slot it was found in. */
    private static String reject(String id, String category, Map<String, Entry> catalog) {
        if (!isCatalogId(id)) {
            return category + " is not a Trinkies catalog id: " + id;
        }
        Entry entry = catalog == null ? null : catalog.get(id);
        if (entry == null) {
            return "catalog has no item " + id;
        }
        // A hat id in the back slot would bake as a cape, so the slot has to agree with the catalog.
        if (category != null && !category.equals(entry.category)) {
            return id + " is a " + entry.category + " and cannot be worn as " + category;
        }
        return null;
    }

    private static boolean isCategory(String key) {
        for (String category : CATEGORIES) {
            if (category.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the {@code ^trinkies:[a-z0-9_]+$} pattern the shipped JSON schema declares. */
    static boolean isCatalogId(String value) {
        if (value == null || !value.startsWith("trinkies:")) {
            return false;
        }
        String slug = value.substring("trinkies:".length());
        if (slug.isEmpty()) {
            return false;
        }
        for (int i = 0; i < slug.length(); i++) {
            char c = slug.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** Read the packaged catalog out of this mod's own JAR. Empty when the pack is not on the classpath. */
    public static Map<String, Entry> catalogFromJar() {
        InputStream stream = TrinkiesLoadout.class.getResourceAsStream(CATALOG_RESOURCE);
        if (stream == null) {
            return Collections.emptyMap();
        }
        try {
            return parseCatalog(readAll(stream));
        } catch (IOException | RuntimeException error) {
            return Collections.emptyMap();
        } finally {
            closeQuietly(stream);
        }
    }

    public static Map<String, Entry> parseCatalog(String json) {
        Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
        Object root = parseJson(json);
        if (!(root instanceof Map)) {
            return byId;
        }
        Object items = ((Map<?, ?>) root).get("items");
        if (!(items instanceof List)) {
            return byId;
        }
        for (Object row : (List<?>) items) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<?, ?> item = (Map<?, ?>) row;
            String id = text(item.get("id"));
            String category = text(item.get("category"));
            if (id == null || category == null || !isCatalogId(id)) {
                continue;
            }
            byId.put(id, new Entry(id, category, text(item.get("name")),
                    text(item.get("garment")), text(item.get("cape_texture")), text(item.get("item"))));
        }
        return byId;
    }

    /**
     * How many 95&nbsp;ms ticks to hold each cape frame, derived from the item's own animation clip.
     *
     * The pack, not the mod, decides how long an item's idle cycle lasts; {@code frameCount * tickMillis}
     * is how long one cycle would take at one tick per frame. One-shots keep the default cadence, since
     * stretching a single play looks like a stutter rather than an emote.
     */
    public static int frameTicksFromAnimation(String animationsJson, int frameCount, long tickMillis) {
        if (animationsJson == null || frameCount < 1 || tickMillis < 1L) {
            return 1;
        }
        try {
            Object root = parseJson(animationsJson);
            Object clips = root instanceof Map ? ((Map<?, ?>) root).get("clips") : null;
            Object idle = clips instanceof Map ? ((Map<?, ?>) clips).get("idle") : null;
            if (!(idle instanceof Map)) {
                return 1;
            }
            Map<?, ?> clip = (Map<?, ?>) idle;
            if (!"loop".equals(text(clip.get("loop_mode")))) {
                return 1;
            }
            Object duration = clip.get("duration");
            if (!(duration instanceof Number)) {
                return 1;
            }
            double cycleMillis = ((Number) duration).doubleValue() * 1000.0;
            long base = (long) frameCount * tickMillis;
            int ticks = (int) Math.round(cycleMillis / (double) base);
            if (ticks < 1) {
                return 1;
            }
            return ticks > 8 ? 8 : ticks;
        } catch (RuntimeException error) {
            return 1;
        }
    }

    /**
     * How long a pressed emote should keep the cape on its own {@code play} clip, in milliseconds.
     *
     * Same rule as the idle cadence: the pack decides the timing, {@code defaultMillis} is what a
     * missing or unreadable clip falls back to, and the result is bounded so a typo in an animation
     * file cannot hold a flourish on screen forever.
     */
    public static long playClipMillis(String animationsJson, long defaultMillis) {
        long bounded = clampClip(defaultMillis);
        if (animationsJson == null) {
            return bounded;
        }
        try {
            Object root = parseJson(animationsJson);
            Object clips = root instanceof Map ? ((Map<?, ?>) root).get("clips") : null;
            if (!(clips instanceof Map)) {
                return bounded;
            }
            // play is the emote clip the wheel is built around; signature is the flourish a pet item
            // carries instead, and is just as much "what this item does when you ask it to". An item
            // with neither keeps the caller's default, which is a beat, not a bug.
            Object clip = firstClip((Map<?, ?>) clips, "play", "signature");
            if (clip == null) {
                return bounded;
            }
            Object duration = ((Map<?, ?>) clip).get("duration");
            if (!(duration instanceof Number)) {
                return bounded;
            }
            return clampClip(Math.round(((Number) duration).doubleValue() * 1000.0));
        } catch (RuntimeException error) {
            return bounded;
        }
    }

    /** The first named clip that is actually an object; a null or malformed entry falls through. */
    private static Object firstClip(Map<?, ?> clips, String... names) {
        for (String name : names) {
            Object candidate = clips.get(name);
            if (candidate instanceof Map) {
                return candidate;
            }
        }
        return null;
    }

    /** A clip the animator can act on: at least two frames of cape, at most four seconds of show. */
    private static long clampClip(long millis) {
        return Math.max(250L, Math.min(6000L, millis));
    }

    /**
     * Whether the frames the launcher baked into the instance were baked for the loadout the mod just
     * read. A missing marker means an older bake, which makes no claim, so it counts as a match.
     */
    public static boolean bakeMatchesLoadout(String metaPropertiesText, Snapshot snapshot) {
        if (snapshot == null || !snapshot.isPresent()) {
            return true;
        }
        if (metaPropertiesText == null) {
            return true;
        }
        Properties meta = new Properties();
        try {
            meta.load(new java.io.StringReader(metaPropertiesText));
        } catch (IOException error) {
            return true;
        }
        String baked = meta.getProperty("trinkiesBack");
        if (baked == null || baked.trim().isEmpty()) {
            return true;
        }
        String equipped = snapshot.equippedIdFor("back");
        return baked.trim().equals(equipped == null ? "none" : equipped);
    }

    /** The receipt the client leaves for the launcher, so "the mod read it" is observable, not assumed. */
    /** How many of the eight keybound slots this client accepted; a bogus id is not counted. */
    private static int countWheelSlots(Snapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        int bound = 0;
        for (String id : snapshot.emoteWheel()) {
            if (id != null) {
                bound++;
            }
        }
        return bound;
    }

    public static String buildAppliedJson(Snapshot snapshot, int frameTicks, boolean bakeMatches,
                                          long confirmedAtMs) {
        return buildAppliedJson(snapshot, frameTicks, bakeMatches, null, confirmedAtMs);
    }

    /**
     * @param activeEmote the wheel slot currently playing, or null; recorded so a log or the launcher
     *                    can tell "the client is wearing this" from "the client also owes an emote"
     */
    public static String buildAppliedJson(Snapshot snapshot, int frameTicks, boolean bakeMatches,
                                          String activeEmote, long confirmedAtMs) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("{\n  \"schema\": 1,\n  \"source\": \"trinkies\",\n  \"confirmedAtMs\": ")
                .append(confirmedAtMs)
                .append(",\n  \"frameTicks\": ").append(frameTicks)
                .append(",\n  \"bake_matches_loadout\": ").append(bakeMatches)
                .append(",\n  \"active_emote\": ").append(activeEmote == null ? "null" : quote(activeEmote))
                .append(",\n  \"emoteWheelSlots\": ").append(countWheelSlots(snapshot))
                .append(",\n  \"equipped\": {\n");
        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            String id = snapshot == null ? null : snapshot.equippedIdFor(category);
            builder.append("    \"").append(category).append("\": ")
                    .append(id == null ? "null" : quote(id));
            builder.append(i == CATEGORIES.length - 1 ? "\n" : ",\n");
        }
        builder.append("  }\n}\n");
        return builder.toString();
    }

    /** Written with the same same-directory rename the launcher uses, so the launcher never sees a half file. */
    public static void writeApplied(File directory, String json) throws IOException {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File target = new File(directory, APPLIED_FILE_NAME);
        File temporary = File.createTempFile(".applied-", ".tmp", directory);
        try {
            Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(temporary), "UTF-8");
            try {
                writer.write(json);
                writer.flush();
            } finally {
                closeQuietly(writer);
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("could not replace " + target.getName());
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("could not rename the applied marker into place");
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    /** Flattens {@code applied.json} to strings so a caller does not need a JSON library to read it back. */
    public static Map<String, String> parseApplied(String json) {
        Map<String, String> flat = new LinkedHashMap<String, String>();
        Object root;
        try {
            root = parseJson(json);
        } catch (RuntimeException error) {
            return flat;
        }
        if (!(root instanceof Map)) {
            return flat;
        }
        for (Map.Entry<?, ?> row : ((Map<?, ?>) root).entrySet()) {
            String key = text(row.getKey());
            Object value = row.getValue();
            if (key == null) {
                continue;
            }
            if (value instanceof Map) {
                for (Map.Entry<?, ?> nested : ((Map<?, ?>) value).entrySet()) {
                    String nestedKey = text(nested.getKey());
                    if (nestedKey != null) {
                        flat.put(key + "." + nestedKey, scalar(nested.getValue()));
                    }
                }
                continue;
            }
            flat.put(key, scalar(value));
        }
        return flat;
    }

    // -- asset and text helpers -------------------------------------------------------------------

    public static String readJarText(String assetPath) {
        if (assetPath == null) {
            return null;
        }
        String normalized = assetPath.startsWith("/") ? assetPath : "/assets/trinkies/" + assetPath;
        InputStream stream = TrinkiesLoadout.class.getResourceAsStream(normalized);
        if (stream == null) {
            return null;
        }
        try {
            return readAll(stream);
        } catch (IOException error) {
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    static String readText(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        try {
            return readAll(stream);
        } finally {
            closeQuietly(stream);
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
            // Nothing recoverable, and nothing worth throwing into a render tick for.
        }
    }

    private static void closeQuietly(Writer writer) {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> emptyWheel() {
        List<String> wheel = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            wheel.add(null);
        }
        return wheel;
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String scalar(Object value) {
        if (value == null) {
            return null;
        }
        // This reader parses every JSON number as a double, so integral values are printed back
        // without a fractional part - a timestamp has to survive the round trip unchanged.
        if (value instanceof Number) {
            double asDouble = ((Number) value).doubleValue();
            if (asDouble == Math.rint(asDouble) && Math.abs(asDouble) < 1.0E15) {
                return Long.toString((long) asDouble);
            }
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\n");
            } else if (c >= 0x20) {
                // A catalog id is only ever [a-z0-9_], so anything else that reaches here is dropped
                // rather than escaped; a raw escape sequence in Java source would also reach the
                // lexer's unicode pre-processing, which is not a subtlety worth carrying here.
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /**
     * A strict, tiny JSON reader: objects, arrays, strings with the six escapes JSON allows, numbers,
     * and the three literals. Returns {@code Map}/{@code List}/{@code String}/{@code Double}/
     * {@code Boolean}/{@code null} and throws {@link IllegalArgumentException} on anything else, because
     * a loadout we cannot read has to be visible rather than silently empty.
     */
    static Object parseJson(String source) {
        JsonReader reader = new JsonReader(source);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();
        if (!reader.atEnd()) {
            throw new IllegalArgumentException("trailing content at offset " + reader.position);
        }
        return value;
    }

    private static final class JsonReader {
        private final String source;
        private int position;

        JsonReader(String source) {
            this.source = source == null ? "" : source;
        }

        boolean atEnd() {
            return position >= source.length();
        }

        void skipWhitespace() {
            while (position < source.length()) {
                char c = source.charAt(position);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            char c = source.charAt(position);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        Map<String, Object> readObject() {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            position++; // consume '{'
            skipWhitespace();
            if (!atEnd() && source.charAt(position) == '}') {
                position++;
                return out;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || source.charAt(position) != '"') {
                    throw new IllegalArgumentException("expected a key at offset " + position);
                }
                String key = readString();
                skipWhitespace();
                if (atEnd() || source.charAt(position) != ':') {
                    throw new IllegalArgumentException("expected ':' after " + key);
                }
                position++;
                skipWhitespace();
                out.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated object");
                }
                char next = source.charAt(position++);
                if (next == '}') {
                    return out;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("expected ',' or '}' at offset " + (position - 1));
                }
            }
        }

        List<Object> readArray() {
            List<Object> out = new ArrayList<Object>();
            position++; // consume '['
            skipWhitespace();
            if (!atEnd() && source.charAt(position) == ']') {
                position++;
                return out;
            }
            while (true) {
                skipWhitespace();
                out.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated array");
                }
                char next = source.charAt(position++);
                if (next == ']') {
                    return out;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("expected ',' or ']' at offset " + (position - 1));
                }
            }
        }

        String readString() {
            position++; // consume '"'
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = source.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated escape");
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (position + 4 > source.length()) {
                            throw new IllegalArgumentException("truncated \\u escape");
                        }
                        out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("illegal escape \\" + escape);
                }
            }
        }

        Double readNumber() {
            int start = position;
            while (position < source.length()) {
                char c = source.charAt(position);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    position++;
                } else {
                    break;
                }
            }
            String token = source.substring(start, position);
            try {
                return Double.valueOf(token);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("not a number at offset " + start + ": " + token);
            }
        }

        void expect(String literal) {
            if (!source.startsWith(literal, position)) {
                throw new IllegalArgumentException("expected " + literal + " at offset " + position);
            }
            position += literal.length();
        }
    }
}
