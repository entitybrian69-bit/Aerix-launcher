package net.kdt.aerix.desktop.core.auth

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * `CryptProtectData` / `CryptUnprotectData` from `crypt32.dll`, through the JDK's Foreign Function
 * & Memory API.
 *
 * This class is only ever touched through reflection from [Dpapi], so the launcher still loads and
 * runs on a JVM without `java.lang.foreign` (JDK 17-21) - there the AES-GCM fallback in
 * [SecureStore] is used and this class is never initialised.
 *
 * Two details are easy to get wrong and are handled here:
 *
 *   * `DATA_BLOB` is two words, `cbData` (DWORD) and `pbData` (pointer). On 64-bit Windows that is
 *     a 16-byte struct with 4 bytes of padding after `cbData`; laying it out as 8 bytes makes
 *     `CryptProtectData` read the pointer out of the wrong place and return `ERROR_INVALID_PARAMETER`
 *     instead of ciphertext.
 *   * The output blob is allocated by Windows with `LocalAlloc`, so it must be freed with
 *     `LocalFree`, and the copy taken before that is the only safe one. Skipping the free leaks a
 *     block on every token write.
 *
 * Note on native access: from JDK 22 the FFM API warns (and later releases may refuse) unless the
 * JVM runs with `--enable-native-access`. [Dpapi] therefore treats any failure here as "DPAPI is
 * not usable" and falls back, rather than letting a Windows user lose the ability to stay signed in.
 */
internal object DpapiBridge {

    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    /** `DATA_BLOB` on 64-bit: DWORD cbData + 4 padding, then a pointer. */
    private val blobLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cbData"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.ADDRESS.withName("pbData"),
    )

    private val handleProtect: MethodHandle by lazy { handle("CryptProtectData") }
    private val handleUnprotect: MethodHandle by lazy { handle("CryptUnprotectData") }
    private val handleLocalFree: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            SymbolLookup.libraryLookup("kernel32", Arena.global()).find("LocalFree").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
    }

    private fun handle(name: String): MethodHandle {
        val linker = Linker.nativeLinker()
        val symbol = SymbolLookup.libraryLookup("crypt32", Arena.global())
            .find(name)
            .orElseThrow { IllegalStateException("crypt32!$name not found") }
        // BOOL CryptProtectData(DATA_BLOB*, LPCWSTR, DATA_BLOB*, PVOID, CRYPTPROTECT_PROMPTSTRUCT*, DWORD, DATA_BLOB*)
        return linker.downcallHandle(
            symbol,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT, // BOOL
                ValueLayout.ADDRESS,  // pDataIn
                ValueLayout.ADDRESS,  // szDataDescr
                ValueLayout.ADDRESS,  // pOptionalEntropy
                ValueLayout.ADDRESS,  // pvReserved
                ValueLayout.ADDRESS,  // pPromptStruct
                ValueLayout.JAVA_INT, // dwFlags
                ValueLayout.ADDRESS,  // pDataOut
            ),
        )
    }

    @JvmStatic
    fun protect(data: ByteArray): ByteArray = run(handleProtect, data)

    @JvmStatic
    fun unprotect(data: ByteArray): ByteArray = run(handleUnprotect, data)

    private fun run(handle: MethodHandle, data: ByteArray): ByteArray = Arena.ofConfined().use { arena ->
        val inBlob = arena.allocate(blobLayout)
        val inBytes = arena.allocate(data.size.toLong())
        MemorySegment.copy(data, 0, inBytes, ValueLayout.JAVA_BYTE, 0, data.size)
        inBlob.set(ValueLayout.JAVA_INT, 0, data.size)
        inBlob.set(ValueLayout.ADDRESS, 8, inBytes)

        val outBlob = arena.allocate(blobLayout)
        val ok = handle.invokeExact(
            inBlob,
            MemorySegment.NULL,
            MemorySegment.NULL,
            MemorySegment.NULL,
            MemorySegment.NULL,
            CRYPTPROTECT_UI_FORBIDDEN,
            outBlob,
        ) as Int
        if (ok == 0) {
            throw IllegalStateException("CryptProtectData failed (error ${lastError()})")
        }
        val size = outBlob.get(ValueLayout.JAVA_INT, 0)
        val pointer = outBlob.get(ValueLayout.ADDRESS, 8)
        try {
            if (size <= 0 || pointer.address() == 0L) throw IllegalStateException("empty DPAPI result")
            val out = ByteArray(size)
            MemorySegment.copy(pointer.reinterpret(size.toLong()), ValueLayout.JAVA_BYTE, 0, out, 0, size)
            out
        } finally {
            // Windows allocated this with LocalAlloc; freeing it is not optional.
            runCatching { handleLocalFree.invokeExact(pointer) as MemorySegment }
        }
    }

    private fun lastError(): Int = runCatching {
        val getLastError = Linker.nativeLinker().downcallHandle(
            SymbolLookup.libraryLookup("kernel32", Arena.global()).find("GetLastError").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT),
        )
        getLastError.invokeExact() as Int
    }.getOrDefault(-1)
}
