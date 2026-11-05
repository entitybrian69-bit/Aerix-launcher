import java.lang.reflect.*;
import java.net.Proxy;
import java.net.InetAddress;
import java.util.*;

/** Calls the actual Minecraft authlib session API through the bundled injector. */
public class AerixAuthlibIntegration {
    public static void main(String[] args) throws Exception {
        String name = System.getenv("AERIX_TEST_NAME"), token = System.getenv("AERIX_TEST_TOKEN");
        if (name == null || token == null) throw new IllegalStateException("fixture missing");
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = gp.getConstructor(UUID.class, String.class).newInstance(id, name);
        Class<?> auth = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService");
        Object service = null;
        for (Constructor<?> c : auth.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0] == Proxy.class) { service = c.newInstance(Proxy.NO_PROXY); break; }
            if (p.length == 2 && p[0] == Proxy.class && p[1] == String.class) { service = c.newInstance(Proxy.NO_PROXY, "test"); break; }
        }
        if (service == null) throw new IllegalStateException("Unsupported authlib service constructor " + Arrays.toString(auth.getConstructors()));
        Object sessions = auth.getMethod("createMinecraftSessionService").invoke(service);
        Method join = Arrays.stream(sessions.getClass().getMethods()).filter(m -> m.getName().equals("joinServer")).findFirst().orElseThrow();
        Method has = Arrays.stream(sessions.getClass().getMethods()).filter(m -> m.getName().equals("hasJoinedServer")).findFirst().orElseThrow();
        String serverId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Object[] joinArgs = new Object[join.getParameterCount()];
        int s = 0;
        for (int i = 0; i < joinArgs.length; i++) {
            Class<?> p = join.getParameterTypes()[i];
            if (p == UUID.class) joinArgs[i] = id;
            else if (p == gp) joinArgs[i] = profile;
            else if (p == String.class) joinArgs[i] = s++ == 0 ? token : serverId;
            else throw new IllegalStateException("Unsupported join param " + p);
        }
        join.invoke(sessions, joinArgs);
        Object[] hasArgs = new Object[has.getParameterCount()];
        s = 0;
        for (int i = 0; i < hasArgs.length; i++) {
            Class<?> p = has.getParameterTypes()[i];
            if (p == gp) hasArgs[i] = profile;
            else if (p == String.class) hasArgs[i] = s++ == 0 && Arrays.stream(has.getParameterTypes()).noneMatch(x -> x == gp) ? name : serverId;
            else if (p == InetAddress.class) hasArgs[i] = null;
            else throw new IllegalStateException("Unsupported hasJoined param " + p);
        }
        Object result = has.invoke(sessions, hasArgs);
        if (result == null) throw new AssertionError("Server could not verify valid session");
        // Newer authlib returns ProfileResult instead of GameProfile.
        if (!gp.isInstance(result)) result = result.getClass().getMethod("profile").invoke(result);
        if (!id.equals(gp.getMethod("getId").invoke(result))) throw new AssertionError("Wrong authenticated UUID");
        if (has.invoke(sessions, hasArgs) != null) throw new AssertionError("Join replay accepted");
        System.out.println("Real Minecraft authlib plus bundled injector: authenticated join, verification and replay rejection passed");
    }
}
