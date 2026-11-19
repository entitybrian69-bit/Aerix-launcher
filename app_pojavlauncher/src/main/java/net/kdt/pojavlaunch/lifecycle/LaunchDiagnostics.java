package net.kdt.pojavlaunch.lifecycle;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Small, private launch breadcrumb. Never records account data, tokens or JVM arguments.
 *
 * <p>Written as a history rather than a single line. The failure this exists for is "the game kicks
 * me back to the home screen", where the player cannot reach the log because the game is already
 * gone - the last thing written before the kick is often the only evidence there is. A single
 * overwritten line lost that; the last few stages plus the reason and the exception survive.
 */
public final class LaunchDiagnostics {
    public static final String FILE_NAME = "aerix-launch-status.txt";

    /** Enough to see the run-up to a failure without turning this into a log file. */
    private static final int MAX_ENTRIES = 24;
    private static final int MAX_EXCEPTION_CHARS = 4000;

    private LaunchDiagnostics() {}

    /** Remember a stage. Replaces the file with the history so far plus this entry. */
    public static void mark(Context context, String stage) {
        append(context, stage, null);
    }

    /**
     * Remember why a launch stopped, with the throwable when there is one.
     *
     * @param stage what was being attempted, in the same words as the other marks
     * @param error the failure, or null when the launcher stopped on purpose (a missing instance,
     *              an unavailable renderer) - those are decisions, not crashes, and the stage says
     *              which decision it was.
     */
    public static void fail(Context context, String stage, Throwable error) {
        append(context, stage, error);
    }

    private static synchronized void append(Context context, String stage, Throwable error) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            String previous = read(file);
            StringBuilder text = new StringBuilder("Aerix launch status\n");
            text.append("Time (UTC milliseconds): ").append(System.currentTimeMillis()).append("\n");
            text.append("Last stage: ").append(stage).append("\n");
            if (error != null) {
                text.append("Failed with: ").append(error.getClass().getName()).append("\n");
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                String stack = trace.toString();
                if (stack.length() > MAX_EXCEPTION_CHARS) {
                    stack = stack.substring(0, MAX_EXCEPTION_CHARS) + "\n… truncated\n";
                }
                text.append("Stack trace:\n").append(stack);
            }
            text.append("History (oldest first):\n");
            for (String line : history(previous)) text.append("  ").append(line).append("\n");
            text.append("  ").append(System.currentTimeMillis()).append("  ").append(stage);
            if (error != null) text.append("  FAILED: ").append(error.getClass().getSimpleName());
            text.append("\n");
            text.append("This is a startup breadcrumb, not a crash diagnosis.\n");
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(text.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w("AerixLaunch", "Could not save launch status", e);
        }
    }

    private static String read(File file) {
        try {
            if (!file.isFile()) return "";
            byte[] bytes = new byte[(int) Math.min(file.length(), 64 * 1024)];
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                int read = input.read(bytes);
                if (read <= 0) return "";
                return new String(bytes, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The stage lines of a previous report, dropped when there is no previous report.
     *
     * A malformed or absent previous file simply starts a new history: a diagnostic must never be
     * the reason a launch path throws.
     */
    private static java.util.List<String> history(String previous) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (previous.isEmpty()) return lines;
        boolean inHistory = false;
        for (String line : previous.split("\n")) {
            if (line.startsWith("History (oldest first):")) {
                inHistory = true;
                continue;
            }
            if (line.startsWith("This is a startup breadcrumb")) {
                inHistory = false;
                continue;
            }
            if (inHistory && line.startsWith("  ")) lines.add(line.substring(2));
        }
        while (lines.size() >= MAX_ENTRIES) lines.remove(0);
        return lines;
    }
}
