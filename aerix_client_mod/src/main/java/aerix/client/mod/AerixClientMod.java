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

    // Cached cape animation frames (kept as raw PNG bytes + decoded NativeImage instances)
    private final List<byte[]> framePngBytes = new ArrayList<byte[]>();
    private final List<Object> frameNativeImages = new ArrayList<Object>();
    private long lastCapeMetaMtime = -1L;
    private long lastWaveyMtime = -1L;
    private long lastLivePropsMtime = -1L;
    private int frameIndex = 0;
    private int slowCounter = 0;

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
        while (true) {
            try {
                Thread.sleep(95L);
                if (!handlesResolved) {
                    if (!resolveHandles()) {
                        Thread.sleep(500L);
                        continue;
                    }
                }
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

    private boolean resolveHandles() {
        try {
            ClassLoader cl = AerixClientMod.class.getClassLoader();
            clsMinecraftClient = Class.forName("net.minecraft.class_310", false, cl);
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
        slowCounter++;
        if (slowCounter >= 8) {
            slowCounter = 0;
            reloadCapeFramesIfNeeded(runDir);
            syncWaveyCapesIfNeeded(runDir);
            syncLiveOptionsIfNeeded(mc, runDir);
        } else if (frameNativeImages.isEmpty()) {
            reloadCapeFramesIfNeeded(runDir);
        }

        if (frameNativeImages.isEmpty() || fPlayer == null) return;
        Object player = fPlayer.get(mc);
        if (player == null) return;

        Object capeId = resolvePlayerCapeIdentifier(player);
        if (capeId == null) return;

        Object texMgr = mGetTextureManager.invoke(mc);
        if (texMgr == null) return;

        frameIndex = (frameIndex + 1) % frameNativeImages.size();
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
            String name = String.format("frame_%02d.png", Integer.valueOf(i));
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

            setEnumField(cfg, "windMode", wavesOn ? "WAVES" : "NONE");
            setEnumField(cfg, "capeStyle", wavesOn ? "SMOOTH" : "BLOCKY");
            setEnumField(cfg, "capeMovement", wavesOn ? "BASIC_SIMULATION_3D" : "VANILLA");
            // Retry until the mod has actually initialised; the animator starts on another thread.
            lastWaveyMtime = mtime;
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumField(Object target, String fieldName, String enumConst) {
        try {
            Field f = target.getClass().getField(fieldName);
            Class<?> t = f.getType();
            if (t.isEnum()) {
                Object val = Enum.valueOf((Class<Enum>) t, enumConst);
                f.set(target, val);
            }
        } catch (Throwable ignored) {
        }
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
