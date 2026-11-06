package net.kdt.pojavlaunch.aerix.servers;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Offline index of public server-media manifests; never sends an entered address to a lookup service. */
public final class ServerArtworkCatalog {
    private static final String REVISION = "325aff8938bfeac4ea67232896538fade6985165";
    public static final class Artwork {
        public final String url;
        public final boolean banner;
        Artwork(String slug, String file) {
            url = "https://raw.githubusercontent.com/LabyMod/server-media/" + REVISION + "/minecraft_servers/" + slug + "/" + file;
            banner = file.equals("banner.png");
        }
    }
    private static final class Entry {
        final ServerAddress direct; final List<String> aliases; final Artwork artwork;
        Entry(ServerAddress direct, List<String> aliases, Artwork artwork) { this.direct=direct; this.aliases=aliases; this.artwork=artwork; }
    }
    private final List<Entry> entries;
    private ServerArtworkCatalog(List<Entry> entries) { this.entries=entries; }
    public static ServerArtworkCatalog load(Reader reader) {
        List<Entry> entries = new ArrayList<>();
        try {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (!REVISION.equals(json.get("revision").getAsString())) return new ServerArtworkCatalog(entries);
            for (JsonElement item : json.getAsJsonArray("servers")) {
                JsonObject obj = item.getAsJsonObject();
                String slug = obj.get("slug").getAsString(), file = obj.get("file").getAsString();
                ServerAddress direct = ServerAddress.parse(obj.get("direct").getAsString());
                if (!slug.matches("[a-z0-9_-]{1,80}") || direct == null || !(file.equals("banner.png") || file.equals("background.png"))) continue;
                List<String> aliases = new ArrayList<>();
                for (JsonElement alias : obj.getAsJsonArray("aliases")) aliases.add(alias.getAsString().toLowerCase(Locale.ROOT));
                entries.add(new Entry(direct, aliases, new Artwork(slug, file)));
            }
        } catch (RuntimeException ignored) { entries.clear(); }
        return new ServerArtworkCatalog(entries);
    }
    public Artwork find(String address) {
        ServerAddress parsed = ServerAddress.parse(address);
        if (parsed == null || parsed.ipv6) return null;
        for (Entry entry : entries) {
            if (parsed.port == entry.direct.port && parsed.host.equals(entry.direct.host)) return entry.artwork;
        }
        for (Entry entry : entries) {
            if (parsed.port != entry.direct.port) continue;
            for (String alias : entry.aliases) {
                if (alias.startsWith("%.")) {
                    String domain = alias.substring(2);
                    // Treat the root as an alias only when the advertised direct address is under it.
                    if (domain.indexOf('.') > 0 && ServerAddress.parse(domain) != null &&
                        (parsed.host.endsWith("." + domain) || (parsed.host.equals(domain) && entry.direct.host.endsWith("." + domain)))) return entry.artwork;
                } else {
                    ServerAddress other = ServerAddress.parse(alias);
                    if (other != null && other.host.equals(parsed.host) && (other.explicitPort ? other.port == parsed.port : parsed.port == entry.direct.port)) return entry.artwork;
                }
            }
        }
        return null;
    }
}
