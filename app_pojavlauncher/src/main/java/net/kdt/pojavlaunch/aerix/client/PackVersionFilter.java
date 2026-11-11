package net.kdt.pojavlaunch.aerix.client;

import java.util.List;

/**
 * Which of a modpack's files may be offered to a player at all.
 *
 * <p>A pack publishes every build it has ever made: many Minecraft versions, several loaders, and
 * the occasional entry with no download behind it. Taking the first item of that list - which is what
 * the client page's install used to do - installs whatever the platform happened to put on top, so a
 * player ends up on a Minecraft version their client has no build for and the pack simply will not
 * load. This answers the two questions the version picker needs: does this file run on a version the
 * client exists for, and where should it sort among the others.
 *
 * <p>Deliberately free of Android, Kotlin and the network, so it is compiled and asserted with javac
 * alone - see {@code tests/PackVersionFilterSmokeTest.java}.
 */
public final class PackVersionFilter {
    private PackVersionFilter() { }

    /** A file that was accepted, with the client version it was accepted for. */
    public static final class Match {
        public final String minecraft;
        public final int clientRank;

        Match(String minecraft, int clientRank) {
            this.minecraft = minecraft;
            this.clientRank = clientRank;
        }
    }

    /**
     * @param url            the download the file points at; null or blank means there is nothing to fetch
     * @param gameVersions   the Minecraft versions the file claims, as the platform spells them
     * @param loaders        the mod loaders the file was built for
     * @param clientVersions the versions the client itself exists for, newest first
     * @param requiredLoader the loader the client needs, or null/blank when any loader will do
     * @return the match, or null when this file must not be offered
     */
    public static Match match(String url, List<String> gameVersions, List<String> loaders,
                              List<String> clientVersions, String requiredLoader) {
        if (url == null || url.trim().isEmpty()) return null;
        if (clientVersions == null || clientVersions.isEmpty()) return null;
        if (requiredLoader != null && !requiredLoader.isEmpty() && !containsIgnoreCase(loaders, requiredLoader)) {
            return null;
        }
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        if (gameVersions != null) {
            for (String version : gameVersions) {
                int rank = rankOf(version, clientVersions);
                if (rank >= 0 && rank < bestRank) {
                    bestRank = rank;
                    // The client's own spelling is what the instance is created with, so "1.19" in a
                    // pack's list resolves to "1.19.4", the version the client can actually provide.
                    best = clientVersions.get(rank);
                }
            }
        }
        return best == null ? null : new Match(best, bestRank);
    }

    /**
     * Where [version] belongs in the client's list, or -1 when the client has nothing for it.
     *
     * <p>An exact match wins. Otherwise a pack that names a version line ("1.19") matches the newest
     * client version inside it, and a pack that names a patch the client does not carry ("1.20.2"
     * against a client offering "1.20.1" and "1.20.4") does not match at all, in either direction:
     * guessing a neighbour is how an install ends up with the wrong jars.
     */
    public static int rankOf(String version, List<String> clientVersions) {
        if (version == null || clientVersions == null) return -1;
        String wanted = version.trim();
        if (wanted.isEmpty()) return -1;
        for (int i = 0; i < clientVersions.size(); i++) {
            if (wanted.equalsIgnoreCase(clientVersions.get(i))) return i;
        }
        int best = -1;
        for (int i = 0; i < clientVersions.size(); i++) {
            String candidate = clientVersions.get(i);
            if (candidate != null && (candidate.startsWith(wanted + ".") || wanted.startsWith(candidate + "."))) {
                if (best < 0 || i < best) best = i;
            }
        }
        return best;
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        if (values == null) return false;
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }
}
