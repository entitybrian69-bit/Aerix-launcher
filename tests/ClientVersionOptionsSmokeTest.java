import java.util.LinkedHashMap;
import java.util.Map;
import net.kdt.pojavlaunch.aerix.client.ClientVersionOptions;
public class ClientVersionOptionsSmokeTest {
    public static void main(String[] args) {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("graphicsMode", "0"); input.put("viewBobbing", "false"); input.put("renderDistance", "8");
        Map<String, String> modern = ClientVersionOptions.translate("26.3", input);
        if (!"\"fast\"".equals(modern.get("graphicsPreset")) || modern.containsKey("graphicsMode") ||
            !"false".equals(modern.get("bobView")) || !"8".equals(modern.get("renderDistance"))) throw new AssertionError(modern);
        Map<String, String> old = ClientVersionOptions.translate("1.20.1", input);
        if (!"0".equals(old.get("graphicsMode")) || old.containsKey("graphicsPreset") || !input.containsKey("viewBobbing")) throw new AssertionError(old);
        System.out.println("Versioned option smoke tests passed");
    }
}
