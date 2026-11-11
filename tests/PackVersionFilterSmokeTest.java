import net.kdt.pojavlaunch.aerix.client.ClientVersions;
import net.kdt.pojavlaunch.aerix.client.PackVersionFilter;
import net.kdt.pojavlaunch.aerix.client.PackVersionFilter.Match;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The rule behind the "which Minecraft version?" window, run against the client's real version list.
 *
 * <p>Compiled with javac alone by the debug workflow, so the answer is not "the code looks right":
 * the files a pack publishes are fed in one by one, exactly as the picker walks them, and the ones
 * that come out on screen are asserted. This is the check that keeps the old behaviour - install
 * whatever the platform happened to put first - from coming back.
 */
public final class PackVersionFilterSmokeTest {
    private static final List<String> CLIENT = Arrays.asList(ClientVersions.RELEASES);

    private static void equal(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    private static void none(String url, List<String> gameVersions, List<String> loaders, String loader, String what) {
        Match match = PackVersionFilter.match(url, gameVersions, loaders, CLIENT, loader);
        if (match != null) {
            throw new AssertionError(what + ": should not have been offered, got " + match.minecraft);
        }
    }

    private static Match match(String url, List<String> gameVersions, List<String> loaders, String loader) {
        return PackVersionFilter.match(url, gameVersions, loaders, CLIENT, loader);
    }

    public static void main(String[] args) {
        // A pack that publishes three files. The first in the list - which is the one the old install
        // took - is a Forge build for a Minecraft the client has never shipped.
        List<String> forgeOld = Arrays.asList("1.8.9");
        List<String> fabricNew = Arrays.asList("1.21.4");
        List<String> fabricMid = Arrays.asList("1.20.1", "1.19.4");
        none("https://x/a.jar", forgeOld, Arrays.asList("forge"), "fabric", "the Forge file for an old game");
        equal("1.21.4", match("https://x/b.jar", fabricNew, Arrays.asList("fabric"), "fabric").minecraft,
            "the newest fabric build the client has");
        equal("1.20.1", match("https://x/c.jar", fabricMid, Arrays.asList("fabric", "quilt"), "fabric").minecraft,
            "a file naming several versions is offered for the newest the client has");

        // What the window shows, in the order it shows it: the picker walks the files, keeps the
        // matches and sorts them by the client's own list, newest first.
        List<Match> offers = new ArrayList<>();
        for (List<String> versions : Arrays.asList(fabricMid, forgeOld, fabricNew)) {
            Match one = match("https://x/f.jar", versions, Arrays.asList("fabric"), "fabric");
            if (one != null) offers.add(one);
        }
        Collections.sort(offers, (left, right) -> Integer.compare(left.clientRank, right.clientRank));
        equal(2, offers.size(), "two of the three files are installable here");
        equal("1.21.4", offers.get(0).minecraft, "first offer");
        equal("1.20.1", offers.get(1).minecraft, "second offer");

        // Refusals, each one a way the old install could put the player on a broken instance.
        none(null, fabricNew, Arrays.asList("fabric"), "fabric", "a file with no download url");
        none("   ", fabricNew, Arrays.asList("fabric"), "fabric", "a blank download url");
        none("https://x/f.jar", fabricNew, Arrays.asList("neoforge"), "fabric", "the wrong loader");
        none("https://x/f.jar", Arrays.asList("1.8.9"), Arrays.asList("fabric"), "fabric",
            "a Minecraft older than the client");
        none("https://x/f.jar", Arrays.asList("1.19.3"), Arrays.asList("fabric"), "fabric",
            "a patch beside the client's, which must not be guessed either way");
        none("https://x/f.jar", Collections.<String>emptyList(), Arrays.asList("fabric"), "fabric",
            "a file that names no version");
        if (PackVersionFilter.match("https://x/f.jar", fabricNew, Arrays.asList("fabric"),
            Collections.<String>emptyList(), "fabric") != null) {
            throw new AssertionError("a client with no versions of its own must offer nothing");
        }

        // The client's own list is newest first, and the rank is what orders the window - a plain
        // string sort would put "1.21.11" under "1.21.2" and show the player the wrong top row.
        equal(0, PackVersionFilter.rankOf("26.3", CLIENT), "the newest client release ranks first");
        equal(CLIENT.size() - 1, PackVersionFilter.rankOf("1.16.5", CLIENT), "the oldest ranks last");
        if (PackVersionFilter.rankOf("1.19.2", CLIENT) >= 0) {
            throw new AssertionError("1.19.2 is not a client release and must not be offered");
        }
        int previous = -1;
        for (String version : Arrays.asList("26.3", "1.21.11", "1.21.1", "1.20.1", "1.16.5")) {
            int rank = PackVersionFilter.rankOf(version, CLIENT);
            if (rank < 0) throw new AssertionError("missing " + version + " from the client's own list");
            if (rank <= previous) throw new AssertionError("the client list is not newest first at " + version);
            previous = rank;
        }

        // Names arrive spelled however the publisher felt like writing them.
        equal("1.20.1", match("https://x/f.jar", Arrays.asList("1.20.1"), Arrays.asList("FABRIC"), "Fabric").minecraft,
            "loader names are matched without regard to case");
        equal("1.20.1", match("https://x/f.jar", Arrays.asList("1.20.1 "), Arrays.asList("fabric"), "fabric").minecraft,
            "a stray space in a version list is not a different version");
        equal("1.19.4", match("https://x/f.jar", Arrays.asList("1.19"), Arrays.asList("fabric"), "fabric").minecraft,
            "a version line resolves to the newest client patch inside it");

        System.out.println("Pack version filter smoke tests passed");
    }
}
