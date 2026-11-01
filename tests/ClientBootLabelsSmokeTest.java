import net.kdt.pojavlaunch.aerix.client.ClientBootLabels;
import net.kdt.pojavlaunch.aerix.client.ClientVersions;

public class ClientBootLabelsSmokeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (String version : ClientVersions.RELEASES) {
            String actual = ClientBootLabels.fromProfile("fabric-loader-0.19.5-" + version);
            check(version.equals(actual), "profile: " + version);
            check(ClientBootLabels.header(actual).equals("Aerix Client " + version), "header: " + version);
            check(ClientBootLabels.loadingMods(actual).contains("Minecraft " + version + " "), "mods: " + version);
            check(ClientBootLabels.textureAtlases(actual).contains("Minecraft " + version + " "), "atlases: " + version);
            check(version.equals(ClientBootLabels.fromLoadingLog("[main/INFO]: Loading Minecraft " + version + " with Fabric Loader 0.19.5")), "log: " + version);
        }
        check("1.21.11".equals(ClientBootLabels.fromProfile("fabric-loader-0.18.2-1.21.11")), "patch version");
        check("26.3".equals(ClientBootLabels.fromProfile("fabric-loader-0.19.5-26.3")), "26.3");
        check(ClientBootLabels.fromProfile(null) == null, "null profile");
        check(ClientBootLabels.fromProfile("unknown") == null, "unknown profile");
        check(ClientBootLabels.fromProfile("fabric-loader-0.19.5-1.21.11-pre1") == null, "do not truncate prerelease");
        check("1.21.11-pre1".equals(ClientBootLabels.fromLoadingLog("Loading Minecraft 1.21.11-pre1 with Fabric Loader 0.19.5")), "full log version");
        check(ClientBootLabels.fromLoadingLog("Loading Minecraft 26.3 textures") == null, "ignore unrelated messages");
        check(ClientBootLabels.header(null).equals("Aerix Client"), "unknown header");
        check(!ClientBootLabels.loadingMods(null).contains("1.16.5"), "unknown mods");
        check(!ClientBootLabels.textureAtlases(null).contains("1.16.5"), "unknown textures");
        System.out.println("Client boot labels smoke test passed");
    }
}
