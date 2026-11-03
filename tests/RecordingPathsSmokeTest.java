import net.kdt.pojavlaunch.recording.RecordingPaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RecordingPathsSmokeTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory("recorder-paths").toFile();
        File first = new File(base, "first"); File second = new File(base, "second");
        check(first.mkdir() && second.mkdir());
        check(new File(first, "mojo_instance.json").createNewFile());
        check(new File(second, "mojo_instance.json").createNewFile());
        File a = RecordingPaths.directory(first, true), b = RecordingPaths.directory(second, true);
        check(a.isDirectory() && b.isDirectory() && !a.getCanonicalFile().equals(b.getCanonicalFile()));
        check(a.getCanonicalFile().getParentFile().equals(first.getCanonicalFile()));
        check(RecordingPaths.directory(first, false).equals(a));
        File missing = new File(base, "missing"); check(missing.mkdir());
        try { RecordingPaths.directory(missing, true); throw new AssertionError("unregistered profile accepted"); }
        catch (IOException expected) { }
        File linkRoot = new File(base, "linked"); check(linkRoot.mkdir());
        check(new File(linkRoot, "mojo_instance.json").createNewFile());
        Files.createSymbolicLink(new File(linkRoot, "recordings").toPath(), first.toPath());
        try { RecordingPaths.directory(linkRoot, true); throw new AssertionError("symlink escape accepted"); }
        catch (IOException expected) { }
        System.out.println("Recorder instance-path smoke tests passed");
    }
}
