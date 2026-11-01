package net.kdt.pojavlaunch.utils.jre;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ModJavaRequirementsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File game() throws IOException { File root = temp.newFolder(); assertTrue(new File(root, "mods").mkdir()); return root; }
    private void jar(File root, String name, String... entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(new File(new File(root, "mods"), name)))) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new ZipEntry(entries[i])); out.write(entries[i+1].getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
    }
    @Test public void java17MixinUpgradesMinecraft116Java8Requirement() throws Exception {
        File root = game();
        jar(root, "biomesunseen.jar", "fabric.mod.json", "{\"mixins\":[\"biomesunseen.mixins.json\"]}",
            "biomesunseen.mixins.json", "{\"compatibilityLevel\":\"JAVA_17\"}");
        assertEquals(17, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void legacyModsDoNotForceNewerJava() throws Exception {
        File root = game(); jar(root, "legacy.jar", "fabric.mod.json", "{\"mixins\":[\"old.json\"]}", "old.json", "{\"compatibilityLevel\":\"JAVA_8\"}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void vanillaMinimumIsNeverDowngraded() throws Exception {
        assertEquals(21, ModJavaRequirements.minimumJava(game(), 21));
    }
    @Test public void disabledJarsAreIgnored() throws Exception {
        File root = game(); jar(root, "disabled.jar.disabled", "fabric.mod.json", "{\"depends\":{\"java\":\">=21\"}}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void explicitJavaDependencyIsRespected() throws Exception {
        File root = game(); jar(root, "modern.jar", "fabric.mod.json", "{\"depends\":{\"java\":\">= 17\"}}");
        assertEquals(17, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void alternativeJavaDependenciesChooseLowestAllowedMajor() throws Exception {
        File root = game(); jar(root, "alternatives.jar", "fabric.mod.json", "{\"depends\":{\"java\":[\"1.8.x\",\">=17\"]}}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void upperBoundDoesNotBecomeMinimum() throws Exception {
        File root = game(); jar(root, "bounds.jar", "fabric.mod.json", "{\"depends\":{\"java\":\">=8 <17\"}}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void serverOnlyMixinDoesNotAffectClient() throws Exception {
        File root = game(); jar(root, "server-mixin.jar", "fabric.mod.json", "{\"mixins\":[{\"config\":\"server.json\",\"environment\":\"server\"}]}", "server.json", "{\"compatibilityLevel\":\"JAVA_25\"}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void manifestDeclaredMixinIsRespected() throws Exception {
        File root = game(); jar(root, "manifest.jar", "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMixinConfigs: declared.json\r\n\r\n", "declared.json", "{\"compatibilityLevel\":\"JAVA_21\"}");
        assertEquals(21, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void unreferencedMixinDoesNotForceUnnecessaryRuntime() throws Exception {
        File root = game(); jar(root, "unused.jar", "fabric.mod.json", "{}", "unused.mixins.json", "{\"compatibilityLevel\":\"JAVA_25\"}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void brokenJarDoesNotCrashPreflight() throws Exception {
        File root = game(); try (FileOutputStream out = new FileOutputStream(new File(root, "mods/broken.jar"))) { out.write(1); }
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void replacingModInvalidatesCachedRequirement() throws Exception {
        File root = game(); jar(root, "changed.jar", "fabric.mod.json", "{}");
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
        jar(root, "changed.jar", "fabric.mod.json", "{\"depends\":{\"java\":\">=17\"}}");
        assertEquals(17, ModJavaRequirements.minimumJava(root, 8));
    }
    @Test public void oversizedMetadataIsBounded() throws Exception {
        File root = game(); char[] huge = new char[600000]; Arrays.fill(huge, ' ');
        jar(root, "huge.jar", "fabric.mod.json", "{\"mixins\":[\"huge.json\"]}", "huge.json", new String(huge));
        assertEquals(8, ModJavaRequirements.minimumJava(root, 8));
    }
}
