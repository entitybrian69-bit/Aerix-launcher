import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kdt.pojavlaunch.lifecycle.LaunchDiagnostics;

/**
 * The breadcrumb has to survive the launch, not just the last line of it.
 *
 * A player who is "kicked back to the home screen" cannot supply a log, so the file must already
 * hold the run-up and, when the launch failed, the reason and the exception. The older version of
 * this test asserted that each mark() *replaced* the previous stage; that behaviour was the bug -
 * it left exactly one line of evidence, and usually the wrong one.
 */
public class LaunchDiagnosticsSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("aerix-launch-test");
        Context context = new Context(dir.toFile());
        Path file = dir.resolve(LaunchDiagnostics.FILE_NAME);

        LaunchDiagnostics.mark(context, "Game activity requested");
        String first = Files.readString(file, StandardCharsets.UTF_8);
        if (!first.contains("Game activity requested") || !first.contains("not a crash diagnosis")) throw new AssertionError(first);

        LaunchDiagnostics.mark(context, "Game window resumed and service connected");
        String second = Files.readString(file, StandardCharsets.UTF_8);
        if (!second.contains("Game window resumed")) throw new AssertionError(second);
        // The stage line reports the latest stage, but the history keeps the run-up: the older
        // stage is what says how far the launch got before it stopped.
        if (!second.contains("Last stage: Game window resumed and service connected")) throw new AssertionError(second);
        if (!second.contains("Game activity requested")) throw new AssertionError("history dropped the earlier stage:\n" + second);

        // A failure must be recorded with its reason, so the next report names a cause.
        LaunchDiagnostics.fail(context, "Stopped: game setup failed",
                new IllegalStateException("no renderer for this instance"));
        String third = Files.readString(file, StandardCharsets.UTF_8);
        if (!third.contains("Stopped: game setup failed")) throw new AssertionError(third);
        if (!third.contains("no renderer for this instance")) throw new AssertionError("no reason:\n" + third);
        if (!third.contains("IllegalStateException")) throw new AssertionError("no exception type:\n" + third);
        if (!third.contains("LaunchDiagnosticsSmokeTest")) throw new AssertionError("no stack trace:\n" + third);
        if (!third.contains("Game window resumed and service connected")) throw new AssertionError("failure lost the run-up:\n" + third);

        // A failure with no throwable still has to record what it can.
        LaunchDiagnostics.fail(context, "Stopped: the selected instance could not be loaded", null);
        String fourth = Files.readString(file, StandardCharsets.UTF_8);
        if (!fourth.contains("the selected instance could not be loaded")) throw new AssertionError(fourth);

        // The history is a breadcrumb, so it has to stay bounded however long the session runs.
        for (int i = 0; i < 60; i++) LaunchDiagnostics.mark(context, "stage " + i);
        String longRun = Files.readString(file, StandardCharsets.UTF_8);
        if (longRun.length() > 64 * 1024) throw new AssertionError("breadcrumb grew unbounded: " + longRun.length());
        if (!longRun.contains("stage 59")) throw new AssertionError("latest stage missing:\n" + longRun);
        int entries = 0;
        for (String line : longRun.split("\n")) if (line.startsWith("  ") && line.contains("stage ")) entries++;
        if (entries > 25) throw new AssertionError("history kept " + entries + " entries, cap is 24");

        // A failure attributed to a class with a huge message must not fill the file.
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 4000; i++) huge.append('x');
        LaunchDiagnostics.fail(context, "Stopped: starting Minecraft threw", new RuntimeException(huge.toString()));
        if (Files.readString(file, StandardCharsets.UTF_8).length() > 64 * 1024) throw new AssertionError("exception not truncated");

        // A storage failure must never crash the launch path.
        LaunchDiagnostics.mark(new Context(dir.resolve("missing").toFile()), "Game activity requested");
        LaunchDiagnostics.fail(new Context(dir.resolve("missing").toFile()), "Stopped: nope", null);

        Files.delete(file);
        Files.delete(dir);
        System.out.println("Launch diagnostics smoke test passed");
    }
}
