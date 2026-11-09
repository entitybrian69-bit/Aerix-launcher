package aerix.client.mod;

import net.fabricmc.api.ClientModInitializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Lightweight, zero-dependency Fabric client mod for Aerix Client (1.16.5 - 1.21.4+):
 * 1. Cycles 12 animated 64x32 PNG cape frames onto the player's OpenGL DynamicTexture (~10.5 FPS)
 *    with hot-reload whenever the user switches capes inside the in-game Client menu.
 * 2. Hot-reloads WaveyCapes physics config (`config/waveycapes.json`) when `Waving Cape` is toggled.
 * 3. Hot-applies live client runtime options (`config/aerix_live.properties`, e.g. Fullbright gamma).
 */
public final class AerixClientMod implements ClientModInitializer {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // Intermediary reflective handles (verified identical across 1.16.5 -> 1.21.4)
    private Class<?> clsMinecraftClient;   // net.minecraft.class_310
    private Method mGetInstance;           // method_1551 ()Lclass_310;
    private Method mGetTextureManager;     // method_1531 ()Lclass_1060;
    private Field fPlayer;                 // field_1724 Lclass_746;
    private Field fRunDirectory;           // field_1697 Ljava/io/File;
    private Field fOptions;                // field_1690 Lclass_315;

    private Class<?> clsAbstractPlayer;    // net.minecraft.class_742
    private Method mGetCapeLegacy;         // method_3119 ()Lclass_2960; (1.16.5 - 1.20.1)
    private Method mGetSkinTextures;       // method_52814 ()Lclass_8685; (1.20.2 - 1.21.4)
    private Method mSkinCompCape;          // comp_1627 ()Lclass_2960;

    private Class<?> clsIdentifier;        // net.minecraft.class_2960
    private Class<?> clsAbstractTexture;   // net.minecraft.class_1044
    private Class<?> clsDynamicTexture;    // net.minecraft.class_1043 (NativeImageBackedTexture)
    private Method mDynUpload;             // method_4524 ()V
    private Method mDynGetImage;           // method_4525 ()Lclass_1011;

    private Class<?> clsTextureManager;    // net.minecraft.class_1060
    private Method mTexRegister;           // method_4616 (Lclass_2960;Lclass_1044;)V
    private Method mTexGet;                // method_4619 (Lclass_2960;)Lclass_1044;

    private Class<?> clsNativeImage;       // net.minecraft.class_1011
    private Method mNativeRead;            // method_4309 (Ljava/io/InputStream;)Lclass_1011;
    private Method mNativeCopyFrom;        // method_4317 (Lclass_1011;)V

    private boolean handlesResolved = false;
    private boolean modernRuntime = false;
    private ModernCapeBridge modernCapeBridge;
    private final ClientVisualOverrides visualOverrides = new ClientVisualOverrides();
    private final ClientAdaptiveDistance adaptiveDistance = new ClientAdaptiveDistance();

    // Cached cape animation frames (kept as raw PNG bytes + decoded NativeImage instances)
    private final List<byte[]> framePngBytes = new ArrayList<byte[]>();
    private final List<Object> frameNativeImages = new ArrayList<Object>();
    private long lastCapeMetaMtime = -1L;
    private long lastWaveyMtime = -1L;
    private long lastLivePropsMtime = -1L;
    private int frameIndex = 0;
    private int slowCounter = 0;

    // Trinkies wardrobe state: what the launcher says the player wears, the frame cadence the worn
    // item's own animation clip asks for, and the mtime of the file that last produced them.
    private volatile TrinkiesLoadout.Snapshot trinkiesSnapshot;
    private volatile int trinkiesFrameTicks = 1;
    private int trinkiesFrameDwell = 0;
    private int trinkiesPollCounter = 0;
    private java.lang.reflect.Method mGlfwGetKey;
    private java.lang.reflect.Method mWindowGetHandle;
    private Field fWindow;
    private long lastTrinkiesMtime = Long.MIN_VALUE;
    private volatile java.util.List<String> trinkiesWheel =
            java.util.Collections.nCopies(8, (String) null);
    private volatile String activeEmote;
    private volatile long emoteUntilMs;
    private int lastWheelKey = -1;
    private long lastWheelPressMs;

    @Override
    public void onInitializeClient() {
        if (!STARTED.compareAndSet(false, true)) return;
        ClientTelemetry.start();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "Aerix-Client-Animator");
        worker.setDaemon(true);
        worker.start();
    }

    private void runLoop() {
        final AtomicBoolean renderPending = new AtomicBoolean(false);
        long nextResolveAttempt = 0L;
        while (true) {
            try {
                Thread.sleep(95L);
                // Texture APIs change between Minecraft versions. Do not let a missing texture
                // handle prevent the independent WaveyCapes setting from updating.
                long now = System.currentTimeMillis();
                if (!handlesResolved && now >= nextResolveAttempt) {
                    resolveHandles();
                    nextResolveAttempt = now + 2000L;
                }
                // The wardrobe is polled on this daemon thread, ahead of both the handle resolution
                // above and the render-thread handoff below: answering equipped.json is file IO, so it
                // must not stall a frame, and a client whose texture bridge cannot resolve still has to
                // read what the player is wearing. Two stat() calls every ~1.5 s is cheaper than a
                // file watcher and keeps the loadout live if the launcher rewrites it mid-session.
                if (++trinkiesPollCounter >= 16) {
                    trinkiesPollCounter = 0;
                    refreshTrinkiesLoadout(resolveRunDir(safeClientInstance()));
                }
                if (mGetInstance == null) continue;
                final Object mc = mGetInstance.invoke(null);
                if (!(mc instanceof Executor)) continue;
                if (!renderPending.compareAndSet(false, true)) continue;

                ((Executor) mc).execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            onRenderThreadTick(mc);
                        } catch (Throwable ignored) {
                        } finally {
                            renderPending.set(false);
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    /** The client instance when it can be reached at all; null simply means "use the run directory". */
    private Object safeClientInstance() {
        try {
            return mGetInstance == null ? null : mGetInstance.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean resolveHandles() {
        try {
            ClassLoader cl = AerixClientMod.class.getClassLoader();
            try {
                clsMinecraftClient = Class.forName("net.minecraft.class_310", false, cl);
            } catch (ClassNotFoundException oldNamesUnavailable) {
                clsMinecraftClient = Class.forName("net.minecraft.client.Minecraft", false, cl);
                mGetInstance = clsMinecraftClient.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                fRunDirectory = findFieldSafe(clsMinecraftClient, "gameDirectory");
                modernRuntime = true;
                handlesResolved = true;
                return true;
            }
            mGetInstance = clsMinecraftClient.getDeclaredMethod("method_1551");
            mGetInstance.setAccessible(true);
            mGetTextureManager = clsMinecraftClient.getDeclaredMethod("method_1531");
            mGetTextureManager.setAccessible(true);
            fPlayer = findFieldSafe(clsMinecraftClient, "field_1724");
            fRunDirectory = findFieldSafe(clsMinecraftClient, "field_1697");
            fOptions = findFieldSafe(clsMinecraftClient, "field_1690");

            clsAbstractPlayer = Class.forName("net.minecraft.class_742", false, cl);
            mGetCapeLegacy = findMethodSafe(clsAbstractPlayer, "method_3119");
            mGetSkinTextures = findMethodSafe(clsAbstractPlayer, "method_52814");
            if (mGetSkinTextures != null) {
                Class<?> clsSkinTex = mGetSkinTextures.getReturnType();
                mSkinCompCape = findMethodSafe(clsSkinTex, "comp_1627");
                if (mSkinCompCape == null) {
                    mSkinCompCape = findMethodSafe(clsSkinTex, "capeTexture");
                }
            }

            clsIdentifier = Class.forName("net.minecraft.class_2960", false, cl);
            clsAbstractTexture = Class.forName("net.minecraft.class_1044", false, cl);
            clsDynamicTexture = Class.forName("net.minecraft.class_1043", false, cl);
            mDynUpload = clsDynamicTexture.getDeclaredMethod("method_4524");
            mDynUpload.setAccessible(true);
            mDynGetImage = clsDynamicTexture.getDeclaredMethod("method_4525");
            mDynGetImage.setAccessible(true);

            clsTextureManager = Class.forName("net.minecraft.class_1060", false, cl);
            mTexRegister = clsTextureManager.getDeclaredMethod("method_4616", clsIdentifier, clsAbstractTexture);
            mTexRegister.setAccessible(true);
            mTexGet = findMethodSafe(clsTextureManager, "method_4619", clsIdentifier);

            clsNativeImage = Class.forName("net.minecraft.class_1011", false, cl);
            mNativeRead = clsNativeImage.getDeclaredMethod("method_4309", InputStream.class);
            mNativeRead.setAccessible(true);
            mNativeCopyFrom = clsNativeImage.getDeclaredMethod("method_4317", clsNativeImage);
            mNativeCopyFrom.setAccessible(true);

            handlesResolved = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void onRenderThreadTick(Object mc) throws Exception {
        File runDir = resolveRunDir(mc);
        // Read on the client thread, which is the thread GLFW owns.
        pumpEmoteWheel(mc);
        if (!modernRuntime) visualOverrides.tick(mc, runDir);
        adaptiveDistance.tick(mc, runDir);
        boolean assetSkin = modernRuntime || (handlesResolved && mSkinCompCape != null &&
                clsIdentifier != null && !clsIdentifier.isAssignableFrom(mSkinCompCape.getReturnType()));
        if (assetSkin) {
            if (++slowCounter >= 8) { slowCounter = 0; syncWaveyCapesIfNeeded(runDir); }
            if (modernCapeBridge == null) modernCapeBridge = new ModernCapeBridge(AerixClientMod.class.getClassLoader(), modernRuntime);
            modernCapeBridge.tick(mc, runDir);
            return;
        }
        slowCounter++;
        if (slowCounter >= 8) {
            slowCounter = 0;
            syncWaveyCapesIfNeeded(runDir);
            syncLiveOptionsIfNeeded(mc, runDir);
            if (handlesResolved) reloadCapeFramesIfNeeded(runDir);
        } else if (handlesResolved && frameNativeImages.isEmpty()) {
            reloadCapeFramesIfNeeded(runDir);
        }

        if (!handlesResolved || frameNativeImages.isEmpty() || fPlayer == null) return;
        Object player = fPlayer.get(mc);
        if (player == null) return;

        Object capeId = resolvePlayerCapeIdentifier(player);
        if (capeId == null) return;

        Object texMgr = mGetTextureManager.invoke(mc);
        if (texMgr == null) return;

        // The equipped item's idle clip decides how long a full cycle takes, so the frame step is
        // paced by the asset instead of by a constant in this mod.
        if (++trinkiesFrameDwell >= effectiveFrameTicks()) {
            trinkiesFrameDwell = 0;
            frameIndex = (frameIndex + 1) % frameNativeImages.size();
        }
        Object srcImage = frameNativeImages.get(frameIndex);

        Object existingTex = mTexGet != null ? mTexGet.invoke(texMgr, capeId) : null;
        if (existingTex != null && clsDynamicTexture.isInstance(existingTex)) {
            Object dstImage = mDynGetImage.invoke(existingTex);
            if (dstImage != null) {
                mNativeCopyFrom.invoke(dstImage, srcImage);
                mDynUpload.invoke(existingTex);
                return;
            }
        }

        // First attachment (replaces static skin-server texture with live NativeImageBackedTexture)
        byte[] seedPng = framePngBytes.get(frameIndex);
        Object ownedImg = mNativeRead.invoke(null, new ByteArrayInputStream(seedPng));
        Object dynTex = instantiateDynamicTexture(ownedImg);
        if (dynTex != null) {
            mTexRegister.invoke(texMgr, capeId, dynTex);
        }
    }

    private Object instantiateDynamicTexture(Object nativeImg) {
        try {
            for (Constructor<?> c : clsDynamicTexture.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0].isAssignableFrom(clsNativeImage)) {
                    return c.newInstance(nativeImg);
                }
            }
            for (Constructor<?> c : clsDynamicTexture.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && p[0].isAssignableFrom(clsNativeImage) && p[1] == boolean.class) {
                    return c.newInstance(nativeImg, Boolean.TRUE);
                }
                if (p.length == 2 && p[0] == Supplier.class && p[1].isAssignableFrom(clsNativeImage)) {
                    Supplier<String> label = new Supplier<String>() {
                        @Override
                        public String get() {
                            return "aerix_cape";
                        }
                    };
                    return c.newInstance(label, nativeImg);
                }
                if (p.length == 3 && p[0] == Supplier.class && p[1].isAssignableFrom(clsNativeImage) && p[2] == boolean.class) {
                    Supplier<String> label = new Supplier<String>() {
                        @Override
                        public String get() {
                            return "aerix_cape";
                        }
                    };
                    return c.newInstance(label, nativeImg, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object resolvePlayerCapeIdentifier(Object player) {
        try {
            if (mGetCapeLegacy != null) {
                return mGetCapeLegacy.invoke(player);
            }
            if (mGetSkinTextures != null && mSkinCompCape != null) {
                Object skinTex = mGetSkinTextures.invoke(player);
                if (skinTex != null) {
                    return mSkinCompCape.invoke(skinTex);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Re-read {@code trinkies/equipped.json} when it changed, take the animation cadence from what
     * the player is actually wearing, and leave a receipt for the launcher.
     *
     * Every step degrades to "no Trinkies state", because a malformed cosmetic file must never reach
     * the render path: the cape animator and the live client options are independent of the wardrobe.
     */
    private void refreshTrinkiesLoadout(File runDir) {
        try {
            File trinkiesDir = new File(runDir, "trinkies");
            File equipped = new File(trinkiesDir, TrinkiesLoadout.FILE_NAME);
            long mtime = equipped.isFile() ? equipped.lastModified() : -1L;
            if (mtime == lastTrinkiesMtime && trinkiesSnapshot != null) return;
            lastTrinkiesMtime = mtime;

            TrinkiesLoadout.Snapshot snapshot = TrinkiesLoadout.read(equipped, TrinkiesLoadout.catalogFromJar());
            trinkiesSnapshot = snapshot;
            trinkiesFrameTicks = 1;
            trinkiesFrameDwell = 0;
            if (!snapshot.isPresent()) {
                return;
            }

            // A back or aura item owns the visible cycle on the cape; anything else is skin-only and
            // leaves the animator at one tick per frame.
            TrinkiesLoadout.Entry animated = snapshot.entryFor("back");
            if (animated == null) animated = snapshot.entryFor("aura");
            if (animated != null) {
                trinkiesFrameTicks = TrinkiesLoadout.frameTicksFromAnimation(
                        TrinkiesLoadout.readJarText(animated.animationsJsonPath()),
                        Math.max(1, framePngBytes.size()), 95L);
            }

            File meta = new File(new File(runDir, "config"), "aerix_cape_frames/meta.properties");
            boolean matchesBake = TrinkiesLoadout.bakeMatchesLoadout(
                    meta.isFile() ? readTextFile(meta) : null, snapshot);
            if (!matchesBake) {
                // The deployed frames were baked for a different wardrobe, so pacing them to the new
                // item would describe art that is not on disk yet. Keep the neutral cadence and let
                // the launcher ask for a restart.
                trinkiesFrameTicks = 1;
            }
            trinkiesWheel = snapshot.emoteWheel();
            TrinkiesLoadout.writeApplied(trinkiesDir, TrinkiesLoadout.buildAppliedJson(
                    snapshot, trinkiesFrameTicks, matchesBake, activeEmote, System.currentTimeMillis()));
        } catch (Throwable ignored) {
            // Cosmetic state is optional here, the frame loop is not.
        }
    }

    /**
     * One tick per frame while a wheel emote is playing, otherwise the worn item's own idle cadence.
     * That is the whole in-game effect of an emote: a flourish on the cape, at the item's own speed,
     * that returns itself to idle when the clip's length has passed.
     */
    private int effectiveFrameTicks() {
        long until = emoteUntilMs;
        if (until != 0L && System.currentTimeMillis() < until) {
            return 1;
        }
        int ticks = trinkiesFrameTicks;
        return ticks < 1 ? 1 : ticks;
    }

    /**
     * The keybound half of the wardrobe: the eight {@code emote_wheel} slots the launcher writes,
     * bound to the number keys, each of which plays that item's own {@code play} clip.
     *
     * Keys are polled through LWJGL rather than registered as a Fabric keybind, because a keybind
     * requires the input-screen plumbing that changed shape across the supported versions while
     * {@code glfwGetKey} has not. No window handle, no LWJGL, or an unbound slot all just mean the
     * wheel is silent; nothing here can take the rest of the client down.
     */
    private void pumpEmoteWheel(Object mc) {
        try {
            java.util.List<String> wheel = trinkiesWheel;
            boolean wheelIsEmpty = true;
            for (int i = 0; i < wheel.size(); i++) {
                if (wheel.get(i) != null) { wheelIsEmpty = false; break; }
            }
            if (wheelIsEmpty) return;

            long window = resolveWindowHandle(mc);
            if (window == 0L) return;
            int pressed = -1;
            for (int slot = 0; slot < 8 && pressed < 0; slot++) {
                if (wheel.get(slot) == null) continue;
                if (glfwKey(window, glfwKey1() + slot)) pressed = slot;
            }
            long now = System.currentTimeMillis();
            if (pressed >= 0 && pressed != lastWheelKey) {
                lastWheelPressMs = now;
                activeEmote = wheel.get(pressed);
                String clip = TrinkiesLoadout.readJarText(emoteClipPath(activeEmote));
                emoteUntilMs = now + TrinkiesLoadout.playClipMillis(clip, 900L);
                swingArm(mc);
            } else if (pressed < 0 && lastWheelKey >= 0) {
                lastWheelKey = pressed;
            }
            if (pressed >= 0 && pressed == lastWheelKey && now - lastWheelPressMs < 250L) return;
            lastWheelKey = pressed;
            if (now > emoteUntilMs) activeEmote = null;
        } catch (Throwable ignored) {
            // Emotes are the most optional thing in this mod.
        }
    }

    /** A wheel slot may hold any catalog item, so it is resolved by id rather than through a slot. */
    private String emoteClipPath(String id) {
        TrinkiesLoadout.Snapshot snapshot = trinkiesSnapshot;
        TrinkiesLoadout.Entry entry = snapshot == null ? null : snapshot.entryById(id);
        return entry == null ? null : entry.animationsJsonPath();
    }

    /** Reflective {@code GLFW.glfwGetKey(window, key)}; the constant and the method have been stable. */
    private boolean glfwKey(long window, int key) {
        try {
            if (mGlfwGetKey == null) {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, getClass().getClassLoader());
                mGlfwGetKey = glfw.getMethod("glfwGetKey", long.class, int.class);
            }
            Object state = mGlfwGetKey.invoke(null, Long.valueOf(window), Integer.valueOf(key));
            return state instanceof Number && ((Number) state).intValue() == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int glfwKey1() {
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW", false, getClass().getClassLoader());
            return glfw.getField("GLFW_KEY_1").getInt(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private long resolveWindowHandle(Object mc) {
        try {

            if (fWindow == null) {
                Field windowField = null;
                for (Field candidate : mc.getClass().getDeclaredFields()) {
                    if (!candidate.getType().getName().contains("Window")) continue;
                    windowField = candidate;
                    break;
                }
                if (windowField == null) return 0L;
                windowField.setAccessible(true);
                fWindow = windowField;
            }
            Object window = fWindow.get(mc);
            if (window == null) return 0L;
            if (mWindowGetHandle == null) {
                // getHandle() on a Mojmap runtime, its intermediary id on an obfuscated one.
                Method candidate = findMethodSafe(window.getClass(), "getHandle");
                if (candidate == null) candidate = findMethodSafe(window.getClass(), "method_1783");
                if (candidate == null) return 0L;
                mWindowGetHandle = candidate;
            }
            Object handle = mWindowGetHandle.invoke(window);
            return handle instanceof Long ? ((Long) handle).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /**
     * The client's own arm swing, found by shape rather than by name: a one-argument method taking
     * an enum, named {@code swingHand} on a Mojmap runtime or under its intermediary id elsewhere.
     * If this version exposes no such method, the emote still plays on the cape and says nothing more.
     */
    private void swingArm(Object mc) {
        try {
            if (fPlayer == null) return;
            Object player = fPlayer.get(mc);
            if (player == null) return;
            for (Method candidate : player.getClass().getMethods()) {
                String name = candidate.getName();
                if (!name.equals("swingHand") && !name.startsWith("method_606")) continue;
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 1 || !params[0].isEnum()) continue;
                Object hand = params[0].getEnumConstants()[0];
                if (hand == null) continue;
                candidate.setAccessible(true);
                candidate.invoke(player, hand);
                return;
            }
        } catch (Throwable ignored) {
            // A missing swing is not worth a stack trace in the log.
        }
    }

    /** Reuses the mod's own byte reader, which already copes with a missing or truncated file. */
    private static String readTextFile(File file) {
        try {
            return new String(readAllBytes(file), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void reloadCapeFramesIfNeeded(File runDir) {
        File dir = new File(runDir, "config/aerix_cape_frames");
        File meta = new File(dir, "meta.properties");
        File f0 = new File(dir, "frame_00.png");
        long mtime = meta.isFile() ? meta.lastModified() : (f0.isFile() ? f0.lastModified() : -1L);
        if (mtime <= 0L || mtime == lastCapeMetaMtime) return;
        lastCapeMetaMtime = mtime;

        List<byte[]> loadedBytes = new ArrayList<byte[]>();
        List<Object> loadedImages = new ArrayList<Object>();
        for (int i = 0; i < 16; i++) {
            String name = String.format(java.util.Locale.ROOT, "frame_%02d.png", Integer.valueOf(i));
            File f = new File(dir, name);
            if (!f.isFile()) break;
            try {
                byte[] bytes = readAllBytes(f);
                Object img = mNativeRead.invoke(null, new ByteArrayInputStream(bytes));
                if (img != null) {
                    loadedBytes.add(bytes);
                    loadedImages.add(img);
                }
            } catch (Throwable ignored) {
            }
        }
        if (loadedImages.isEmpty()) return;

        for (Object old : frameNativeImages) {
            if (old instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) old).close();
                } catch (Throwable ignored) {
                }
            }
        }
        framePngBytes.clear();
        frameNativeImages.clear();
        framePngBytes.addAll(loadedBytes);
        frameNativeImages.addAll(loadedImages);
        frameIndex = 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void syncWaveyCapesIfNeeded(File runDir) {
        File cfgFile = new File(runDir, "config/waveycapes.json");
        if (!cfgFile.isFile()) return;
        long mtime = cfgFile.lastModified();
        if (mtime == lastWaveyMtime) return;
        try {
            String json = new String(readAllBytes(cfgFile), StandardCharsets.UTF_8);
            boolean wavesOn = json.contains("\"WAVES\"");
            Class<?> modBase = Class.forName("dev.tr7zw.waveycapes.versionless.ModBase", false, AerixClientMod.class.getClassLoader());
            Field fCfg = modBase.getDeclaredField("config");
            fCfg.setAccessible(true);
            Object cfg = fCfg.get(null);
            if (cfg == null) return;

            boolean wind = setEnumField(cfg, "windMode", wavesOn ? "WAVES" : "NONE");
            boolean style = setEnumField(cfg, "capeStyle", wavesOn ? "SMOOTH" : "BLOCKY");
            boolean movement = setEnumField(cfg, "capeMovement", wavesOn ? "BASIC_SIMULATION_3D" : "VANILLA");
            // Retry until the mod has actually initialised and its fields have been updated.
            if (wind && style && movement) lastWaveyMtime = mtime;
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean setEnumField(Object target, String fieldName, String enumConst) {
        try {
            Field f = target.getClass().getField(fieldName);
            Class<?> t = f.getType();
            if (t.isEnum()) {
                Object val = Enum.valueOf((Class<Enum>) t, enumConst);
                f.set(target, val);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void syncLiveOptionsIfNeeded(Object mc, File runDir) {
        File liveFile = new File(runDir, "config/aerix_live.properties");
        if (!liveFile.isFile() || fOptions == null) return;
        long mtime = liveFile.lastModified();
        if (mtime == lastLivePropsMtime) return;
        lastLivePropsMtime = mtime;

        try {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(liveFile);
            try {
                props.load(fis);
            } finally {
                fis.close();
            }
            Object opts = fOptions.get(mc);
            if (opts == null) return;
            String gammaStr = props.getProperty("gamma");
            if (gammaStr != null) {
                double g = Double.parseDouble(gammaStr.trim());
                Field fGamma = findFieldSafe(opts.getClass(), "field_1840");
                if (fGamma != null && fGamma.getType() == double.class) {
                    fGamma.setDouble(opts, g);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private File resolveRunDir(Object mc) {
        try {
            if (fRunDirectory != null) {
                Object d = fRunDirectory.get(mc);
                if (d instanceof File) return (File) d;
            }
        } catch (Throwable ignored) {
        }
        return new File(".");
    }

    private static Field findFieldSafe(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethodSafe(Class<?> cls, String name, Class<?>... params) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Method m = cur.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static byte[] readAllBytes(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(256L, file.length()));
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }
}
