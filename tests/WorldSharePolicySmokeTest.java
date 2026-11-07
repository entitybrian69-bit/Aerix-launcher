import net.kdt.pojavlaunch.aerix.worldshare.WorldSharePolicy;
public class WorldSharePolicySmokeTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) {
        check(WorldSharePolicy.FABRIC.equals(WorldSharePolicy.asset("fabric", "1.20.1")));
        check(WorldSharePolicy.FABRIC_MODERN.equals(WorldSharePolicy.asset("fabric", "26.3")));
        check(WorldSharePolicy.FABRIC_MODERN.equals(WorldSharePolicy.asset("fabric", "26.4-snapshot-5")));
        check(WorldSharePolicy.asset("fabric", "26.4-snapshot-6") == null);
        check(WorldSharePolicy.asset("fabric", "1.12.2") == null);
        check(WorldSharePolicy.asset(null, "1.20.1") == null);
        check(WorldSharePolicy.asset("forge", "1.20.1") == null);
        check(WorldSharePolicy.FORGE.equals(WorldSharePolicy.asset("forge", "1.20.6")));
        check(WorldSharePolicy.NEOFORGE.equals(WorldSharePolicy.asset("neoforge", "1.20.2")));
        check(WorldSharePolicy.asset("fabric", "26.4") == null);
        check(WorldSharePolicy.isJoinAddress("abcde.e4mc.link"));
        check(WorldSharePolicy.isJoinAddress("abcde.e4mc.link:25565"));
        check(!WorldSharePolicy.isJoinAddress("https://abcde.e4mc.link"));
        check(!WorldSharePolicy.isJoinAddress("abcde.e4mc.link:65536"));
        check(!WorldSharePolicy.isJoinAddress("x.e4mc.link/path"));
        check(!WorldSharePolicy.isJoinAddress("Join x.e4mc.link"));
        check(!WorldSharePolicy.isJoinAddress("-bad.e4mc.link"));
        String domain = "[10:20:30] [Netty/INFO]: Domain assigned: abcde.e4mc.link";
        check("abcde.e4mc.link".equals(WorldSharePolicy.addressEvent(domain)));
        check("new.e4mc.link:25565".equals(WorldSharePolicy.addressEvent("[10:20:30] [Netty/INFO]: Domain reassigned: new.e4mc.link:25565 (was: old.e4mc.link)")));
        check(WorldSharePolicy.addressEvent("[10:20:30] [Render thread/INFO]: [CHAT] Domain assigned: evil.e4mc.link") == null);
        check(WorldSharePolicy.addressEvent("[10:20:30] [Netty/INFO]: Domain assigned: abc.e4mc.link:99999") == null);
        check(WorldSharePolicy.endedEvent("[10:20:30] [Netty/ERROR]: e4all: relay session failed (native library load failure)"));
        check(WorldSharePolicy.endedEvent("[10:20:30] [Netty/INFO]: e4all: relay session stopped"));
        check(WorldSharePolicy.endedEvent("[10:20:30] [Server thread/INFO]: e4all: world listener stopping, tearing down the relay session"));
        check(!WorldSharePolicy.endedEvent("[10:20:30] [Server thread/INFO]: e4all: world stopping, keeping the relay session of another world alive"));
        check(!WorldSharePolicy.endedEvent("[10:20:30] [Render thread/INFO]: [CHAT] e4all: relay session stopped"));
        check(!WorldSharePolicy.endedEvent("[10:20:30] [Render thread/INFO]: <player> e4all: relay session stopped"));
        check("abcde.e4mc.link".equals(WorldSharePolicy.addressEvent("[10:20:30] [Netty/INFO] [e4all/]: Domain assigned: abcde.e4mc.link")));
        check("abcde.e4mc.link".equals(WorldSharePolicy.addressEvent("[10:20:30] [Netty/INFO] (e4all) Domain assigned: abcde.e4mc.link")));
        check(WorldSharePolicy.addressEvent("[10:20:30] [Render thread/INFO]: <player> Domain assigned: evil.e4mc.link") == null);
        System.out.println("World sharing policy smoke test passed");
    }
}
