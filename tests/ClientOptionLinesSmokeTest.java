import java.util.*;
import net.kdt.pojavlaunch.aerix.client.ClientOptionLines;

public final class ClientOptionLinesSmokeTest {
    static void eq(Object a, Object b) { if (!a.equals(b)) throw new AssertionError("expected=" + a + " actual=" + b); }
    public static void main(String[] args) {
        List<String> original = Arrays.asList("resourcePacks:[\"vanilla\"]", "key_key.jump:key.keyboard.space", "unknown entry", "key_key.attack:key.mouse.left", "key_key.jump:key.keyboard.space", "music:0.5");
        Map<String,String> keys = ClientOptionLines.keybinds(original);
        eq(2, keys.size());
        Map<String,String> change = new LinkedHashMap<>();
        change.put("key_key.jump", "key.keyboard.j");
        change.put("key_key.sneak", "key.keyboard.left.shift");
        eq(Arrays.asList("resourcePacks:[\"vanilla\"]", "key_key.jump:key.keyboard.j", "unknown entry", "key_key.attack:key.mouse.left", "key_key.jump:key.keyboard.j", "music:0.5", "key_key.sneak:key.keyboard.left.shift"), ClientOptionLines.merge(original, change));
        eq("key.keyboard.space", keys.get("key_key.jump"));
        System.out.println("Client options merge smoke tests passed");
    }
}
