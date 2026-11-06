import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kdt.pojavlaunch.lifecycle.LaunchDiagnostics;

public class LaunchDiagnosticsSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("aerix-launch-test");
        Context context = new Context(dir.toFile());
        LaunchDiagnostics.mark(context, "Game activity requested");
        Path file = dir.resolve(LaunchDiagnostics.FILE_NAME);
        String first = Files.readString(file, StandardCharsets.UTF_8);
        if (!first.contains("Game activity requested") || !first.contains("not a crash diagnosis")) throw new AssertionError(first);
        LaunchDiagnostics.mark(context, "Game window resumed and service connected");
        String second = Files.readString(file, StandardCharsets.UTF_8);
        if (second.contains("Game activity requested") || !second.contains("Game window resumed")) throw new AssertionError(second);
        // A storage failure must never crash the launch path.
        LaunchDiagnostics.mark(new Context(dir.resolve("missing").toFile()), "Game activity requested");
        Files.delete(file);
        Files.delete(dir);
        System.out.println("Launch diagnostics smoke test passed");
    }
}
