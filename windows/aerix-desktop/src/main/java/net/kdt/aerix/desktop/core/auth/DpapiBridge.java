package net.kdt.aerix.desktop.core.auth;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Windows DPAPI (CryptProtectData / CryptUnprotectData) through the Foreign Function &amp; Memory API.
 *
 * Written in Java on purpose: {@code java.lang.foreign} does not exist before JDK 22, and this
 * project compiles against whatever JDK the builder has (17+). So the class is compiled once and
 * reaches the linker reflectively at run time:
 *
 * <ul>
 *   <li>JDK 22+ on Windows → real DPAPI calls; secrets are bound to the signed-in Windows user and
 *       cannot be decrypted by another account or another machine.</li>
 *   <li>Anything else → every method throws {@link UnsupportedOperationException}, and
 *       {@code SecureStore} transparently falls back to AES-GCM.</li>
 * </ul>
 *
 * DATA_BLOB on the C side is {DWORD cbData; BYTE *pbData;}: 16 bytes on x64, with 4 bytes of
 * padding after the length. L"wide string" descriptions are UTF-16LE.
 */
public final class DpapiBridge {
    private DpapiBridge() {}

    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;
    private static final long BLOB_SIZE = 16L;
    private static final long CB_DATA_OFFSET = 0L;
    private static final long PB_DATA_OFFSET = 8L;

    public static byte[] protect(byte[] input) {
        return crypt(true, input, "Aerix Launcher account data");
    }

    public static byte[] unprotect(byte[] input) {
        return crypt(false, input, null);
    }

    private static byte[] crypt(boolean encrypt, byte[] input, String description) {
        try {
            Class.forName("java.lang.foreign.Linker");
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("java.lang.foreign is unavailable on this JDK", e);
        }
        try {
            return encrypt ? (byte[]) FfmHolder.PROTECT.invokeExact(input, description)
                    : (byte[]) FfmHolder.UNPROTECT.invokeExact(input, (String) null);
        } catch (Throwable t) {
            throw new UnsupportedOperationException("DPAPI call failed: " + t, t);
        }
    }

    /** Holds the two method handles; touching this class is what requires java.lang.foreign. */
    private static final class FfmHolder {
        static final MethodHandle PROTECT;
        static final MethodHandle UNPROTECT;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DpapiBridge.class, MethodHandles.lookup());
                MethodType mt = MethodType.methodType(byte[].class, byte[].class, String.class);
                PROTECT = lookup.findStatic(DpapiBridge.class, "ffmProtect", mt);
                UNPROTECT = lookup.findStatic(DpapiBridge.class, "ffmUnprotect", mt);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    // ---- The actual FFI work, only ever reached on JDK 22+ ------------------------------------

    public static byte[] ffmProtect(byte[] data, String description) throws Throwable {
        return call(true, data, description);
    }

    public static byte[] ffmUnprotect(byte[] data, String description) throws Throwable {
        return call(false, data, null);
    }

    private static Object field(Class<?> c, String name) throws Exception {
        return c.getField(name).get(null);
    }

    private static Object staticCall(Class<?> c, String name, Class<?>[] params, Object... args) throws Exception {
        Method m = c.getMethod(name, params);
        try {
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static byte[] call(boolean encrypt, byte[] input, String description) throws Throwable {
        Class<?> linkerC = Class.forName("java.lang.foreign.Linker");
        Class<?> arenaC = Class.forName("java.lang.foreign.Arena");
        Class<?> layoutC = Class.forName("java.lang.foreign.MemoryLayout");
        Class<?> valueLayoutC = Class.forName("java.lang.foreign.ValueLayout");
        Class<?> segmentC = Class.forName("java.lang.foreign.MemorySegment");
        Class<?> lookupC = Class.forName("java.lang.foreign.SymbolLookup");
        Class<?> fdC = Class.forName("java.lang.foreign.FunctionDescriptor");
        Class<?> groupLayoutC = Class.forName("java.lang.foreign.GroupLayout");
        Class<?> structLayoutC = Class.forName("java.lang.foreign.StructLayout");
        Class<?> paddingC = Class.forName("java.lang.foreign.PaddingLayout");
        Class<?> memoryLayoutI = Class.forName("java.lang.foreign.MemoryLayout");
        Class<?> segmentI = Class.forName("java.lang.foreign.MemorySegment");

        Object linker = staticCall(linkerC, "nativeLinker", new Class<?>[]{});
        Object arena = staticCall(arenaC, "ofShared", new Class<?>[]{});
        try {
            Object lookup = staticCall(lookupC, "libraryLookup", new Class<?>[]{String.class, Class.forName("java.lang.foreign.Arena")},
                    "crypt32", arena);
            Object protectSym = lookupC.getMethod("find", String.class).invoke(lookup, "CryptProtectData");
            Object unprotectSym = lookupC.getMethod("find", String.class).invoke(lookup, "CryptUnprotectData");
            Object sym = encrypt ? protectSym : unprotectSym;

            Object cInt = field(valueLayoutC, "JAVA_INT");
            Object addr = field(valueLayoutC, "ADDRESS");
            Object memoryLayoutArray = java.lang.reflect.Array.newInstance(memoryLayoutI, 2);
            Array.set(memoryLayoutArray, 0, cInt);
            Array.set(memoryLayoutArray, 1, addr);
            Object blobStruct = staticCall(layoutC, "structLayout", new Class<?>[]{Class.forName("[Ljava.lang.foreign.MemoryLayout;")},
                    (Object) memoryLayoutArray);

            // FunctionDescriptor.of(ValueLayout.JAVA_INT, MemoryLayout.ADDRESS ×5) — all five
            // parameters are pointers (two blobs, two wide strings, one DWORD FLAGS passed as int)
            Method fdOf = fdC.getMethod("of", Class.forName("java.lang.foreign.MemoryLayout"),
                    Class.forName("[Ljava.lang.foreign.MemoryLayout;"));
            Object layoutArray = java.lang.reflect.Array.newInstance(memoryLayoutI, 5);
            Array.set(layoutArray, 0, addr);  // DATA_BLOB *pDataIn
            Array.set(layoutArray, 1, addr);  // LPCWSTR     szDataDescr
            Array.set(layoutArray, 2, addr);  // DATA_BLOB  *pOptionalEntropy
            Array.set(layoutArray, 3, addr);  // PVOID       pvReserved
            Array.set(layoutArray, 4, cInt);  // DWORD       dwFlags
            Object fd = fdOf.invoke(null, cInt, layoutArray);

            Object handle = linkerC.getMethod("downcallHandle", segmentI, fdC).invoke(linker, sym, fd);
            MethodHandle mh = (MethodHandle) handle;

            // input blob
            Object inSeg = arenaC.getMethod("allocate", long.class).invoke(arena, BLOB_SIZE);
            Object bytesSeg = arenaC.getMethod("allocate", long.class).invoke(arena, (long) Math.max(input.length, 1));
            segmentC.getMethod("copyFrom", segmentC).invoke(bytesSeg, segmentC.getMethod("ofArray", byte[].class).invoke(null, input));
            setInt(segmentC, inSeg, CB_DATA_OFFSET, input.length);
            setPointer(segmentC, inSeg, PB_DATA_OFFSET, bytesSeg);

            Object outSeg = arenaC.getMethod("allocate", long.class).invoke(arena, BLOB_SIZE);
            Object descrSeg = MemorySegmentNull.allocWideString(arena, segmentC, description);
            Object emptySeg = MemorySegmentNull.NULL();

            @SuppressWarnings("unchecked")
            int ok = (Integer) ((MethodHandle) handle).invokeWithArguments(
                    inSeg, descrSeg, emptySeg, emptySeg, CRYPTPROTECT_UI_FORBIDDEN);
            if (ok == 0) throw new IllegalStateException("CryptProtectData/CryptUnprotectData returned FALSE");

            int outLen = getInt(segmentC, outSeg, CB_DATA_OFFSET);
            Object outPtr = segmentC.getMethod("reinterpret", long.class).invoke(getPointer(segmentC, outSeg, PB_DATA_OFFSET), (long) outLen);
            byte[] result = (byte[]) segmentC.getMethod("toArray", Class.forName("java.lang.foreign.ValueLayout$OfByte"))
                    .invoke(outPtr, field(valueLayoutC, "JAVA_BYTE"));
            // LocalFree the DPAPI output
            try {
                Object kernel = staticCall(lookupC, "libraryLookup", new Class<?>[]{String.class, Class.forName("java.lang.foreign.Arena")},
                        "kernel32", arena);
                Object freeSym = lookupC.getMethod("find", String.class).invoke(kernel, "LocalFree");
                Object freeFd = fdC.getMethod("of", Class.forName("java.lang.foreign.MemoryLayout"),
                        Class.forName("[Ljava.lang.foreign.MemoryLayout;")).invoke(null, addr,
                        java.lang.reflect.Array.newInstance(memoryLayoutI, 1).also { arr -> Array.set(arr, 0, addr) });
                Object freeHandle = linkerC.getMethod("downcallHandle", segmentI, fdC).invoke(linker, freeSym, freeFd);
                ((MethodHandle) freeHandle).invokeWithArguments(outPtr);
            } catch (Throwable ignored) {
                // LocalFree failing leaks 100 bytes until the process exits; never worth failing a login
            }
            return result;
        } finally {
            arenaC.getMethod("close").invoke(arena);
        }
    }

    private static void setInt(Class<?> segmentC, Object segment, long offset, int value) throws Exception {
        Class<?> valueLayoutC = Class.forName("java.lang.foreign.ValueLayout");
        Object cInt = valueLayoutC.getField("JAVA_INT").get(null);
        Method set = segmentC.getMethod("set", Class.forName("java.lang.foreign.ValueLayout$OfInt"), long.class, int.class);
        set.invoke(segment, cInt, offset, value);
    }

    private static int getInt(Class<?> segmentC, Object segment, long offset) throws Exception {
        Object valueLayoutC = Class.forName("java.lang.foreign.ValueLayout");
        Object ofInt = Class.forName("java.lang.foreign.ValueLayout$OfInt");
        Object cInt = valueLayoutC.getField("JAVA_INT").get(null);
        Method get = segmentC.getMethod("get", Class.forName("java.lang.foreign.ValueLayout$OfInt"), long.class);
        return (Integer) get.invoke(segment, cInt, offset);
    }

    private static void setPointer(Class<?> segmentC, Object segment, long offset, Object pointer) throws Exception {
        Class<?> valueLayoutC = Class.forName("java.lang.foreign.ValueLayout");
        Object addr = valueLayoutC.getField("ADDRESS").get(null);
        Method set = segmentC.getMethod("set", Class.forName("java.lang.foreign.ValueLayout$OfAddress"), long.class, Class.forName("java.lang.foreign.MemorySegment"));
        set.invoke(segment, addr, offset, pointer);
    }

    private static Object getPointer(Class<?> segmentC, Object segment, long offset) throws Exception {
        Class<?> valueLayoutC = Class.forName("java.lang.foreign.ValueLayout");
        Object addr = valueLayoutC.getField("ADDRESS").get(null);
        Method get = segmentC.getMethod("get", Class.forName("java.lang.foreign.ValueLayout$OfAddress"), long.class);
        return get.invoke(segment, addr, offset);
    }

    /** Tiny helper so the NULL constant and wide strings read clearly. */
    private static final class MemorySegmentNull {
        static Object NULL() throws Exception {
            return Class.forName("java.lang.foreign.MemorySegment").getField("NULL").get(null);
        }

        static Object allocWideString(Object arena, Class<?> segmentC, String value) throws Exception {
            if (value == null) return NULL();
            byte[] utf16 = value.getBytes(StandardCharsets.UTF_16LE);
            Object seg = arena.getClass().getMethod("allocate", long.class).invoke(arena, (long) utf16.length + 2L);
            segmentC.getMethod("copyFrom", segmentC).invoke(seg,
                    segmentC.getMethod("ofArray", byte[].class).invoke(null, utf16));
            return seg;
        }
    }
}
