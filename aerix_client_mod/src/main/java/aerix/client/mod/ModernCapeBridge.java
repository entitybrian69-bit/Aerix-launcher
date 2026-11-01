package aerix.client.mod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Adapts animated local capes to the PlayerSkin asset API used by newer clients. */
final class ModernCapeBridge {
    private final ClassLoader loader;
    private final boolean named;
    private boolean resolved;
    private boolean disabled;
    private long retryAfter;
    private long lastFailureLog;
    private Field playerField, skinLookupField;
    private Method playerInfo, infoSkin, getManager, texGet, texRegister, texRelease;
    private Method imageRead, imageCopy, dynPixels, dynUpload, skinBody, skinModel, skinSecure;
    private Constructor<?> textureConstructor, skinConstructor;
    private Object capeId, capeAsset, activeInfo, activeManager, activeTexture;
    private Supplier<?> installedLookup, originalLookup;
    private final List<byte[]> frameBytes = new ArrayList<byte[]>();
    private final List<Object> frameImages = new ArrayList<Object>();
    private long frameStamp = -1L;
    private int frameIndex;

    ModernCapeBridge(ClassLoader loader, boolean named) {
        this.loader = loader;
        this.named = named;
    }

    void tick(Object minecraft, File runDir) {
        long now = System.currentTimeMillis();
        if (disabled || now < retryAfter) return;
        try {
            if (!resolved) resolve();
            Object player = playerField.get(minecraft);
            if (player == null) { detach(); return; }
            Object info = playerInfo.invoke(player);
            Object manager = getManager.invoke(minecraft);
            if (info == null || manager == null) { detach(); return; }
            if ((activeInfo != null && activeInfo != info) ||
                    (activeManager != null && activeManager != manager)) detach();
            reloadFrames(runDir);
            if (frameImages.isEmpty()) { detach(); return; }

            infoSkin.invoke(info);
            Object lookup = skinLookupField.get(info);
            if (!(lookup instanceof Supplier)) return;
            if (activeTexture == null || activeManager != manager || texGet.invoke(manager, capeId) != activeTexture) {
                Object ownedImage = imageRead.invoke(null, new ByteArrayInputStream(frameBytes.get(frameIndex)));
                Object texture;
                try {
                    texture = textureConstructor.newInstance((Supplier<String>) () -> "aerix_cape", ownedImage);
                } catch (Throwable failure) {
                    close(ownedImage);
                    throw failure;
                }
                try {
                    texRegister.invoke(manager, capeId, texture);
                } catch (Throwable failure) {
                    close(texture);
                    throw failure;
                }
                activeManager = manager;
                activeTexture = texture;
            } else {
                frameIndex = (frameIndex + 1) % frameImages.size();
                Object pixels = dynPixels.invoke(activeTexture);
                imageCopy.invoke(pixels, frameImages.get(frameIndex));
                dynUpload.invoke(activeTexture);
            }
            if (info != activeInfo || lookup != installedLookup) {
                originalLookup = (Supplier<?>) lookup;
                installedLookup = new CapeLookup(originalLookup);
                skinLookupField.set(info, installedLookup);
                activeInfo = info;
            }
        } catch (Throwable error) {
            if (!resolved) disabled = true;
            else retryAfter = now + 2000L;
            detach();
            if (disabled || now - lastFailureLog >= 30000L) {
                System.err.println("Aerix Client: cape animation unavailable: " + error);
                lastFailureLog = now;
            }
        }
    }

    private void resolve() throws Exception {
        Class<?> mc = load("net.minecraft.client.Minecraft", "net.minecraft.class_310");
        Class<?> player = load("net.minecraft.client.player.AbstractClientPlayer", "net.minecraft.class_742");
        Class<?> info = load("net.minecraft.client.multiplayer.PlayerInfo", "net.minecraft.class_640");
        Class<?> skin = load("net.minecraft.world.entity.player.PlayerSkin", "net.minecraft.class_8685");
        Class<?> identifier = load("net.minecraft.resources.Identifier", "net.minecraft.class_2960");
        Class<?> asset = load("net.minecraft.core.ClientAsset$ResourceTexture", "net.minecraft.class_12079$class_10726");
        Class<?> manager = load("net.minecraft.client.renderer.texture.TextureManager", "net.minecraft.class_1060");
        Class<?> abstractTexture = load("net.minecraft.client.renderer.texture.AbstractTexture", "net.minecraft.class_1044");
        Class<?> dynamic = load("net.minecraft.client.renderer.texture.DynamicTexture", "net.minecraft.class_1043");
        Class<?> image = load("com.mojang.blaze3d.platform.NativeImage", "net.minecraft.class_1011");
        playerField = field(mc, named ? "player" : "field_1724");
        skinLookupField = field(info, named ? "skinLookup" : "field_45607");
        playerInfo = method(player, named ? "getPlayerInfo" : "method_3123");
        infoSkin = method(info, named ? "getSkin" : "method_52810");
        getManager = method(mc, named ? "getTextureManager" : "method_1531");
        texGet = method(manager, named ? "getTexture" : "method_4619", identifier);
        texRegister = method(manager, named ? "register" : "method_4616", identifier, abstractTexture);
        texRelease = method(manager, named ? "release" : "method_4615", identifier);
        imageRead = method(image, named ? "read" : "method_4309", InputStream.class);
        imageCopy = method(image, named ? "copyFrom" : "method_4317", image);
        dynPixels = method(dynamic, named ? "getPixels" : "method_4525");
        dynUpload = method(dynamic, named ? "upload" : "method_4524");
        skinBody = method(skin, named ? "body" : "comp_1626");
        skinModel = method(skin, named ? "model" : "comp_1629");
        skinSecure = method(skin, named ? "secure" : "comp_1630");
        Class<?> assetType = skinBody.getReturnType();
        skinConstructor = skin.getConstructor(assetType, assetType, assetType, skinModel.getReturnType(), boolean.class);
        textureConstructor = dynamic.getConstructor(Supplier.class, image);
        capeId = method(identifier, named ? "fromNamespaceAndPath" : "method_60655", String.class, String.class)
                .invoke(null, "aerix", "local_cape");
        capeAsset = asset.getConstructor(identifier, identifier).newInstance(capeId, capeId);
        resolved = true;
    }

    private void reloadFrames(File runDir) throws Throwable {
        File dir = new File(runDir, "config/aerix_cape_frames");
        File meta = new File(dir, "meta.properties");
        File first = new File(dir, "frame_00.png");
        long stamp = meta.isFile() ? meta.lastModified() : first.isFile() ? first.lastModified() : -1L;
        if (stamp <= 0 || stamp == frameStamp) return;
        List<byte[]> bytes = new ArrayList<byte[]>();
        List<Object> images = new ArrayList<Object>();
        try {
            for (int i = 0; i < 16; i++) {
                File file = new File(dir, String.format(Locale.ROOT, "frame_%02d.png", i));
                if (!file.isFile()) break;
                byte[] frame = read(file);
                Object decoded = imageRead.invoke(null, new ByteArrayInputStream(frame));
                if (decoded == null) throw new IllegalStateException("Cape frame could not be decoded");
                bytes.add(frame);
                images.add(decoded);
            }
            if (images.isEmpty()) return;
        } catch (Throwable error) {
            for (Object image : images) close(image);
            throw error;
        }
        for (Object image : frameImages) close(image);
        frameBytes.clear(); frameBytes.addAll(bytes);
        frameImages.clear(); frameImages.addAll(images);
        frameIndex = 0;
        frameStamp = stamp;
    }

    private void detach() {
        try {
            if (activeInfo != null && skinLookupField.get(activeInfo) == installedLookup)
                skinLookupField.set(activeInfo, originalLookup);
        } catch (Throwable ignored) { }
        try {
            if (activeManager != null && activeTexture != null && texGet.invoke(activeManager, capeId) == activeTexture)
                texRelease.invoke(activeManager, capeId);
        } catch (Throwable ignored) { }
        activeInfo = null; activeManager = null; activeTexture = null;
        installedLookup = null; originalLookup = null;
    }

    private final class CapeLookup implements Supplier<Object> {
        private final Supplier<?> delegate;
        private Object previous, result;
        CapeLookup(Supplier<?> delegate) { this.delegate = delegate; }
        @Override public Object get() {
            Object original = delegate.get();
            if (original == null) return null;
            if (original == previous && result != null) return result;
            try {
                Object skin = skinConstructor.newInstance(skinBody.invoke(original), capeAsset, capeAsset,
                        skinModel.invoke(original), skinSecure.invoke(original));
                previous = original; result = skin;
                return skin;
            } catch (Throwable ignored) { return original; }
        }
    }

    private Class<?> load(String mojang, String intermediary) throws ClassNotFoundException {
        return Class.forName(named ? mojang : intermediary, false, loader);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method m = type.getDeclaredMethod(name, args); m.setAccessible(true); return m;
    }
    private static void close(Object value) {
        if (value instanceof AutoCloseable) try { ((AutoCloseable) value).close(); } catch (Exception ignored) { }
    }
    private static byte[] read(File file) throws Exception {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
