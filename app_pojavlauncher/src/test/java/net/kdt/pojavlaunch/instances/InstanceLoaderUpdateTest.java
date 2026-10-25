package net.kdt.pojavlaunch.instances;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

public class InstanceLoaderUpdateTest {
    static class RecordingInstance extends Instance {
        int writes;
        boolean fail;
        @Override public void write() throws IOException {
            if (fail) throw new IOException("disk error");
            writes++;
        }
    }
    @Test public void loaderUpdatePreservesIdentityAndSettings() throws Exception {
        RecordingInstance target = new RecordingInstance();
        target.mInstanceRoot = new File("existing-modpack");
        target.name = "Fabulously Optimized";
        target.versionId = "fabric-loader-old-1.18.2";
        target.renderer = "mobileglues";
        target.jvmArgs = "-Xmx2G";
        target.selectedRuntime = "Java17";
        target.controlLayout = "custom.json";
        target.sharedData = false;
        File root = target.mInstanceRoot;
        InstanceLoaderUpdate.version(target, "fabric-loader-new-1.18.2");
        assertEquals("fabric-loader-new-1.18.2", target.versionId);
        assertEquals(1, target.writes);
        assertSame(root, target.mInstanceRoot);
        assertEquals("Fabulously Optimized", target.name);
        assertEquals("mobileglues", target.renderer);
        assertEquals("-Xmx2G", target.jvmArgs);
        assertEquals("Java17", target.selectedRuntime);
        assertEquals("custom.json", target.controlLayout);
        assertFalse(target.sharedData);
    }
    @Test public void writeFailureRestoresPreviousVersion() throws Exception {
        RecordingInstance target = new RecordingInstance(); target.versionId = "original"; target.fail = true;
        try { InstanceLoaderUpdate.version(target, "updated"); fail("Must report write failure"); }
        catch (IOException expected) { assertEquals("original", target.versionId); }
    }
    @Test public void emptyVersionCannotEraseInstalledLoader() throws Exception {
        RecordingInstance target = new RecordingInstance(); target.versionId = "original";
        try { InstanceLoaderUpdate.version(target, ""); fail("Must reject missing version"); }
        catch (IOException expected) { assertEquals("original", target.versionId); assertEquals(0, target.writes); }
    }
}
