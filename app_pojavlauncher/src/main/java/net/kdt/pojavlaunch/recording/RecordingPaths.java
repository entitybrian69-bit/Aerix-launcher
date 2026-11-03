package net.kdt.pojavlaunch.recording;

import java.io.File;
import java.io.IOException;

/** Keeps recordings in the running instance's own folder, never in shared game data. */
public final class RecordingPaths {
    private RecordingPaths() {}

    public static File directory(File instanceRoot, boolean create) throws IOException {
        if (instanceRoot == null) throw new IOException("Missing instance directory");
        File root = instanceRoot.getCanonicalFile();
        if (!root.isDirectory() || !new File(root, "mojo_instance.json").isFile())
            throw new IOException("The running instance no longer exists");
        File dir = new File(root, "recordings");
        // Reject an existing symlink that would redirect output outside this instance.
        if (!dir.getCanonicalFile().getParentFile().equals(root))
            throw new IOException("Invalid recording directory");
        if (create && !dir.isDirectory() && !dir.mkdirs())
            throw new IOException("Cannot create this instance's recordings folder");
        if (dir.exists() && !dir.isDirectory()) throw new IOException("Recording path is not a folder");
        return dir;
    }
}
