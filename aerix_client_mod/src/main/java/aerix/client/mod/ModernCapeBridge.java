package aerix.client.mod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Adapts launcher-served animated capes to the skin-asset API the newer clients use.
 *
 * A player without a Mojang cape has no cape texture to animate, so this bridge replaces the
 * {@code PlayerSkin} the game builds for the local player with one whose cape asset points at a
 * texture id this mod owns, then feeds that texture the launcher's animated frames. Members are
 * looked up by several names at once (Mojang-mapped first, intermediary second), the record's
 * components are matched by name rather than by position, and a failure is retried and reported
 * through {@link #status()} instead of swallowed - a single renamed accessor is what used to make
 * capes silently never appear at all.
 */
final class ModernCapeBridge {
    private final ClassLoader loader;
    private final boolean named;
    private boolean resolved;
    private long retryAfter;
    private long lastFailureLog;
    private String failure = "";
    private int attempts;
    private Field playerField, skinLookupField;
    private Method playerInfo, infoSkin, getManager, texGet, texRegister, texRelease;
    private Method imageRead, imageCopy, dynPixels, dynUpload;
    private Constructor<?> textureConstructor, skinConstructor;
    private RecordComponent[] skinComponents;
    private Method[] skinAccessors;
    private int capeComponent = -1;
    private Object capeId, capeAsset, activeInfo, activeManager, activeTexture;
    /** Why the skin itself could not be rebuilt, once the handles were already good. */
    private String skinFailure;
    private Supplier<?> installedLookup, originalLookup;
    private final List<byte[]> frameBytes = new ArrayList<byte[]>();
    private final List<Object> frameImages = new ArrayList<Object>();
    private long frameStamp = -1L;
    private int frameIndex;

    ModernCapeBridge(ClassLoader loader, boolean named) {
        this.loader = loader;
        this.named = named;
    }

    /** One line for the launcher's Client menu: what the cape path is doing right now. */
    String status() {
        if (!resolved) return "not available on this version (" + failure + ")";
        if (frameImages.isEmpty()) return "no cape frames in config/aerix_cape_frames yet";
        if (activeTexture == null) return "waiting for the player to load in";
        // A skin that cannot be rebuilt looks exactly like a cape that is not wanted, so the reason
        // is carried into the sentence rather than left in a log nobody opens.
        if (skinFailure != null)
            return "frames live, skin override failed: " + skinFailure;
        return "animating " + frameImages.size() + " frames";
    }

    void tick(Object minecraft, File runDir) {
        long now = System.currentTimeMillis();
        if (now < retryAfter) return;
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
            // A missing handle is retried instead of given up on: the class it lives on may not be
            // loaded yet this early in the launch, and quitting forever is what hid capes.
            if (!resolved) {
                attempts++;
                retryAfter = now + Math.min(30_000L, 1_500L * attempts);
            } else {
                retryAfter = now + 2_000L;
            }
            failure = brief(error);
            detach();
            if (now - lastFailureLog >= 30_000L) {
                lastFailureLog = now;
                System.err.println("Aerix Client: cape animation unavailable: " + error);
            }
        }
    }

    private void resolve() throws Exception {
        Class<?> mc = load("net.minecraft.client.Minecraft", "net.minecraft.class_310");
        Class<?> player = load("net.minecraft.client.player.AbstractClientPlayer", "net.minecraft.class_742");
        Class<?> info = load("net.minecraft.client.multiplayer.PlayerInfo", "net.minecraft.class_640");
        Class<?> skin = load("net.minecraft.world.entity.player.PlayerSkin", "net.minecraft.class_8685");
        Class<?> identifier = load("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation",
                "net.minecraft.class_2960");
        Class<?> asset = load("net.minecraft.core.ClientAsset$ResourceTexture",
                "net.minecraft.class_12079$class_10726");
        Class<?> manager = load("net.minecraft.client.renderer.texture.TextureManager", "net.minecraft.class_1060");
        Class<?> abstractTexture = load("net.minecraft.client.renderer.texture.AbstractTexture", "net.minecraft.class_1044");
        Class<?> dynamic = load("net.minecraft.client.renderer.texture.DynamicTexture", "net.minecraft.class_1043");
        Class<?> image = load("com.mojang.blaze3d.platform.NativeImage", "net.minecraft.class_1011");
        playerField = field(mc, "player", "field_1724");
        skinLookupField = field(info, "skinLookup", "field_45607");
        playerInfo = noArgMethod(player, "getPlayerInfo", "method_3123");
        infoSkin = noArgMethod(info, "getSkin", "method_52810");
        getManager = noArgMethod(mc, "getTextureManager", "method_1531");
        texGet = methodWithArgs(manager, new Class<?>[] { identifier }, "getTexture", "method_4619");
        texRegister = methodWithArgs(manager, new Class<?>[] { identifier, abstractTexture }, "register", "method_4616");
        texRelease = methodWithArgs(manager, new Class<?>[] { identifier }, "release", "method_4615");
        imageRead = methodWithArgs(image, new Class<?>[] { InputStream.class }, "read", "method_4309");
        imageCopy = methodWithArgs(image, new Class<?>[] { image }, "copyFrom", "method_4317");
        dynPixels = noArgMethod(dynamic, "getPixels", "method_4525");
        dynUpload = noArgMethod(dynamic, "upload", "method_4524");
        resolveSkinShape(skin);
        textureConstructor = dynamic.getConstructor(Supplier.class, image);
        Object local;
        try {
            Method fromPath = methodWithArgs(identifier, new Class<?>[] { String.class, String.class },
                    "fromNamespaceAndPath", "method_60655");
            local = fromPath.invoke(null, "aerix", "local_cape");
        } catch (NoSuchMethodException noFactory) {
            local = identifier.getConstructor(String.class, String.class).newInstance("aerix", "local_cape");
        }
        capeId = local;
        capeAsset = asset.getConstructor(identifier, identifier).newInstance(capeId, capeId);
        resolved = true;
    }

    /**
     * Resolves the {@code PlayerSkin} record's components so the cape slot - and only the cape slot -
     * is the one replaced. Records declare their components in canonical-constructor order, which is
     * the one thing that cannot drift between versions.
     */
    private void resolveSkinShape(Class<?> skin) throws Exception {
        if (!skin.isRecord()) {
            resolvePositionalSkin(skin);
            return;
        }
        RecordComponent[] components = skin.getRecordComponents();
        if (components.length < 3) throw new NoSuchMethodException("skin record has too few components");
        List<Method> accessors = new ArrayList<Method>();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Method accessor = component.getAccessor();
            if (accessor == null) throw new NoSuchMethodException("accessor for " + component.getName());
            accessor.setAccessible(true);
            accessors.add(accessor);
            parameterTypes[i] = component.getType();
            if (component.getName().toLowerCase(Locale.ROOT).contains("cape")) capeComponent = i;
        }
        if (capeComponent < 0) throw new NoSuchFieldException("no cape component in " + skin.getSimpleName());
        skinConstructor = skin.getConstructor(parameterTypes);
        skinComponents = components;
        skinAccessors = accessors.toArray(new Method[0]);
    }

    /**
     * Pre-record skin classes keep the order this bridge has always used - body, cape, elytra - with
     * our texture in both cape slots, because the elytra sheet shares it.
     */
    private void resolvePositionalSkin(Class<?> skin) throws Exception {
        Method body = noArgMethod(skin, "body", "comp_1626");
        Method model = noArgMethod(skin, "model", "comp_1629");
        Method secure = noArgMethod(skin, "secure", "comp_1630");
        Class<?> textureType = body.getReturnType();
        skinConstructor = skin.getConstructor(textureType, textureType, textureType,
                model.getReturnType(), secure.getReturnType());
        skinAccessors = new Method[] { body, model, secure };
        skinComponents = null;
        capeComponent = -1;
    }

    /** @return the same skin, with its cape asset pointing at the launcher-served texture. */
    private Object withLocalCape(Object original) throws Exception {
        if (original == null) return null;
        if (skinComponents != null) {
            Object[] arguments = new Object[skinAccessors.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = i == capeComponent ? capeAsset : skinAccessors[i].invoke(original);
            }
            return skinConstructor.newInstance(arguments);
        }
        Object body = skinAccessors[0].invoke(original);
        Object model = skinAccessors[1].invoke(original);
        Object secure = skinAccessors[2].invoke(original);
        return skinConstructor.newInstance(body, capeAsset, capeAsset, model, secure);
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
                Object skin = withLocalCape(original);
                previous = original; result = skin;
                return skin;
            } catch (Throwable failure) {
                skinFailure = brief(failure);
                return original;
            }
        }
    }

    private Class<?> load(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (int i = 0; i < names.length; i++) {
            try {
                return Class.forName(names[i], false, loader);
            } catch (ClassNotFoundException missing) {
                last = missing;
            }
        }
        throw last;
    }

    private static Field field(Class<?> type, String... names) throws Exception {
        Exception last = null;
        for (int i = 0; i < names.length; i++) {
            try {
                Field found = type.getDeclaredField(names[i]);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException missing) {
                last = missing;
            }
        }
        throw last;
    }

    /** @return the first named no-argument method the type declares, walking its supertypes. */
    private static Method noArgMethod(Class<?> type, String... names) throws Exception {
        NoSuchMethodException last = null;
        for (int i = 0; i < names.length; i++) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                try {
                    Method found = current.getDeclaredMethod(names[i]);
                    if (found.getParameterTypes().length != 0) break;
                    found.setAccessible(true);
                    return found;
                } catch (NoSuchMethodException missing) {
                    last = missing;
                    current = current.getSuperclass();
                }
            }
        }
        throw last == null ? new NoSuchMethodException(names[0]) : last;
    }

    private static Method methodWithArgs(Class<?> type, Class<?>[] arguments, String... names) throws Exception {
        NoSuchMethodException last = null;
        for (int i = 0; i < names.length; i++) {
            try {
                Method found = type.getMethod(names[i], arguments);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException missing) {
                last = missing;
            }
        }
        throw last;
    }

    private static String brief(Throwable error) {
        if (error == null) return "";
        String message = error.getMessage();
        String text = error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() <= 160 ? text : text.substring(0, 160);
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
