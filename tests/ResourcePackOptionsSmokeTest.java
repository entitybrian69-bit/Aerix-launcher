package net.kdt.pojavlaunch.aerix.resourcepack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackOptionsSmokeTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        if (content != null) {
            zos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        zos.closeEntry();
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        check(entry != null, "Missing entry: " + name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        try (java.io.InputStream in = zip.getInputStream(entry)) {
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        // 1. Client pack never overrides user resource packs, even if previously appended at the end.
        check(
            "[\"vanilla\",\"file/Aerix-Client-Pack.zip\",\"file/UserPvP.zip\"]".equals(
                ResourcePackOptions.mergeClientPack(
                    "[\"vanilla\",\"file/UserPvP.zip\"]",
                    "file/Aerix-Client-Pack.zip",
                    true
                )
            ),
            "Client pack must sit below existing user packs"
        );
        check(
            "[\"vanilla\",\"file/Aerix-Client-Pack.zip\",\"file/UserPvP.zip\"]".equals(
                ResourcePackOptions.mergeClientPack(
                    "[\"vanilla\",\"file/UserPvP.zip\",\"file/Aerix-Client-Pack.zip\"]",
                    "file/Aerix-Client-Pack.zip",
                    true
                )
            ),
            "Legacy inverted client pack must be reordered below user packs"
        );
        check(
            "[\"vanilla\",\"fabric\",\"file/Aerix-Client-Pack.zip\",\"file/UserPvP.zip\"]".equals(
                ResourcePackOptions.mergeClientPack(
                    "[\"vanilla\",\"fabric\",\"file/UserPvP.zip\"]",
                    "file/Aerix-Client-Pack.zip",
                    true
                )
            ),
            "Client pack belongs after built-in packs and before user file/ packs"
        );
        check(
            "[\"vanilla\",\"file/Aerix-Client-Pack.zip\"]".equals(
                ResourcePackOptions.mergeClientPack("[]", "file/Aerix-Client-Pack.zip", true)
            ),
            "Empty [] from default options.txt must retain vanilla"
        );
        check(
            "[\"vanilla\",\"file/UserPvP.zip\"]".equals(
                ResourcePackOptions.mergeClientPack(
                    "[\"vanilla\",\"file/Aerix-Client-Pack.zip\",\"file/UserPvP.zip\"]",
                    "file/Aerix-Client-Pack.zip",
                    false
                )
            ),
            "Disabling client pack must remove only the client pack"
        );

        // 2. Enabling/disabling user packs in options.txt preserves existing lines and priority.
        List<String> initial = Arrays.asList(
            "renderDistance:8",
            "resourcePacks:[\"vanilla\",\"file/Aerix-Client-Pack.zip\"]",
            "fov:0.0"
        );
        List<String> enabled = ResourcePackOptions.setPackEnabled(initial, "CustomPack.zip", true);
        check(ResourcePackOptions.isPackEnabled(enabled, "CustomPack.zip"), "CustomPack must be active");
        check(
            "[\"vanilla\",\"file/Aerix-Client-Pack.zip\",\"file/CustomPack.zip\"]".equals(
                ResourcePackOptions.findRawValue(enabled)
            ),
            "Newly enabled pack must sit at top priority"
        );
        check(enabled.contains("renderDistance:8") && enabled.contains("fov:0.0"), "Other options must stay intact");

        List<String> disabled = ResourcePackOptions.setPackEnabled(enabled, "CustomPack.zip", false);
        check(!ResourcePackOptions.isPackEnabled(disabled, "CustomPack.zip"), "CustomPack must be inactive");

        List<String> fromEmpty = ResourcePackOptions.setPackEnabled(Collections.singletonList("resourcePacks:[]"), "FirstPack.zip", true);
        check(
            "[\"vanilla\",\"file/FirstPack.zip\"]".equals(ResourcePackOptions.findRawValue(fromEmpty)),
            "Enabling on empty [] must keep vanilla"
        );

        boolean rejectedMalformed = false;
        try {
            ResourcePackOptions.parseStrict("{not an array");
        } catch (IllegalArgumentException expected) {
            rejectedMalformed = true;
        }
        check(rejectedMalformed, "Malformed non-empty resourcePacks must be rejected by parseStrict");

        // 3. Nested single-root ZIP unwrapping vs valid root ZIP preservation.
        File dir = Files.createTempDirectory("aerix-rp-archive").toFile();
        try {
            File nested = new File(dir, "nested.zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(nested))) {
                addEntry(zos, "MyCoolPack/", null);
                addEntry(zos, "MyCoolPack/pack.mcmeta", "{\"pack\":{\"pack_format\":75,\"description\":\"Test\"}}");
                addEntry(zos, "MyCoolPack/assets/minecraft/textures/item/diamond_sword.png", "sword");
                addEntry(zos, "__MACOSX/MyCoolPack/._pack.mcmeta", "junk");
            }
            check(ResourcePackArchive.unwrapSingleRootInPlace(nested), "Nested single-root pack should unwrap");
            try (ZipFile zf = new ZipFile(nested)) {
                check(zf.getEntry("pack.mcmeta") != null, "Unwrapped ZIP must have root pack.mcmeta");
                check("sword".equals(readEntry(zf, "assets/minecraft/textures/item/diamond_sword.png")), "Asset preserved");
                check(zf.getEntry("__MACOSX/MyCoolPack/._pack.mcmeta") == null, "macOS metadata stripped");
            }

            File normal = new File(dir, "normal.zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(normal))) {
                addEntry(zos, "pack.mcmeta", "{\"pack\":{\"pack_format\":75,\"description\":\"Normal\"}}");
                addEntry(zos, "assets/minecraft/textures/block/stone.png", "stone");
            }
            byte[] before = Files.readAllBytes(normal.toPath());
            check(!ResourcePackArchive.unwrapSingleRootInPlace(normal), "Normal root pack must not be rewritten");
            check(Arrays.equals(before, Files.readAllBytes(normal.toPath())), "Normal ZIP bytes unchanged");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }

        System.out.println("Resource pack options & archive tests passed");
    }
}
