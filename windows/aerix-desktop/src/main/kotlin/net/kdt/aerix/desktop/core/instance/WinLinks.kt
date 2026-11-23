package net.kdt.aerix.desktop.core.instance

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path

/**
 * Directory junctions and their removal, called straight into the Win32 API.
 *
 * The launcher used to run
 *
 *     cmd /c mklink /J <target> <source>
 *
 * to share `versions`, `libraries` and `assets` between instances. `cmd.exe` re-parses the command
 * line, so any character it treats specially is honoured *even inside an argument*: a path with a
 * `&` in it (an instance may legitimately be called `Minecraft & mods`) becomes two commands. That
 * is a command-injection hole with the player's own folder names as the input, and the same applied
 * to `cmd /c rmdir` when removing a link.
 *
 * Nothing here goes through a shell, so there is no command line to parse at all.
 *
 * * A junction is a reparse point of type `IO_REPARSE_TAG_MOUNT_POINT`. Creating one needs only
 *   `CreateFileW` with `FILE_FLAG_OPEN_REPARSE_POINT` and `FSCTL_SET_REPARSE_POINT` - the same thing
 *   `mklink /J` does, and like it, it needs no administrator rights.
 * * `RemoveDirectoryW` is what `rmdir` ultimately calls, and for a junction it removes the link
 *   only, never the shared folder it points at.
 *
 * Reached only through reflection (see [InstanceLinks]), so the class is never loaded on platforms
 * whose JVM has no `java.lang.foreign` - there a symbolic link is used instead.
 */
internal object WinLinks {

    private const val FILE_ATTRIBUTE_DIRECTORY = 0x10
    private const val FILE_ATTRIBUTE_REPARSE_POINT = 0x400
    private const val FILE_SHARE_READ = 0x1
    private const val FILE_SHARE_WRITE = 0x2
    private const val FILE_SHARE_DELETE = 0x4
    private const val OPEN_EXISTING = 3
    private const val FILE_FLAG_BACKUP_SEMANTICS = 0x02000000
    private const val FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000
    private const val FSCTL_SET_REPARSE_POINT = 0x000900A4
    private const val FSCTL_DELETE_REPARSE_POINT = 0x000900AC
    private const val IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003L

    private val kernel32: SymbolLookup by lazy { SymbolLookup.libraryLookup("kernel32", Arena.global()) }

    private fun handle(name: String, vararg args: ValueLayout): MethodHandle {
        val symbol = kernel32.find(name).orElseThrow { IllegalStateException("kernel32!$name not found") }
        return Linker.nativeLinker().downcallHandle(symbol, FunctionDescriptor.of(ValueLayout.JAVA_INT, *args))
    }

    private val createFileW: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            kernel32.find("CreateFileW").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.ADDRESS, // HANDLE
                ValueLayout.ADDRESS, // lpFileName
                ValueLayout.JAVA_INT, // dwDesiredAccess
                ValueLayout.JAVA_INT, // dwShareMode
                ValueLayout.ADDRESS, // lpSecurityAttributes
                ValueLayout.JAVA_INT, // dwCreationDisposition
                ValueLayout.JAVA_INT, // dwFlagsAndAttributes
                ValueLayout.ADDRESS, // hTemplateFile
            ),
        )
    }

    private val closeHandle: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            kernel32.find("CloseHandle").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        )
    }

    private val deviceIoControl: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            kernel32.find("DeviceIoControl").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT, // BOOL
                ValueLayout.ADDRESS, // hDevice
                ValueLayout.JAVA_INT, // dwIoControlCode
                ValueLayout.ADDRESS, // lpInBuffer
                ValueLayout.JAVA_INT, // nInBufferSize
                ValueLayout.ADDRESS, // lpOutBuffer
                ValueLayout.JAVA_INT, // nOutBufferSize
                ValueLayout.ADDRESS, // lpBytesReturned
                ValueLayout.ADDRESS, // lpOverlapped
            ),
        )
    }

    private val removeDirectoryW: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            kernel32.find("RemoveDirectoryW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
        )
    }

    private val moveFileExW: MethodHandle by lazy {
        Linker.nativeLinker().downcallHandle(
            kernel32.find("MoveFileExW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        )
    }

    /** `true` when this JVM can call into Win32 at all (JDK 22+ on a Windows host). */
    val supported: Boolean by lazy {
        runCatching {
            Class.forName("java.lang.foreign.Linker")
            createFileW != null
        }.getOrDefault(false)
    }

    /** Creates a directory junction at [link] pointing to the existing directory [target]. */
    @JvmStatic
    fun createJunction(link: Path, target: Path): Boolean = runCatching {
        Arena.ofConfined().use { arena ->
            val linkName = wideString(arena, link.toAbsolutePath().toString())
            val targetPath = wideString(arena, target.toAbsolutePath().toString())
            val printed = target.toAbsolutePath().toString().toByteArray(Charsets.UTF_16LE)

            // REPARSE_DATA_BUFFER for a mount point:
            //   ULONG  ReparseTag
            //   USHORT ReparseDataLength
            //   USHORT Reserved
            //   USHORT SubstituteNameOffset, SubstituteNameLength
            //   USHORT PrintNameOffset, PrintNameLength
            //   WCHAR  PathBuffer[]            -- "\??\C:\...\target" then the printed path
            val substitute = "\\??\\".toByteArray(Charsets.UTF_16LE) + printed
            val pathBuffer = substitute + ByteArray(2) /* NUL */ + printed + ByteArray(2) /* NUL */
            val header = 8 // tag + length + reserved
            val offsets = 8 // four USHORTs
            val dataLength = offsets + pathBuffer.size
            val total = header + dataLength
            val buffer = arena.allocate(total.toLong(), 8)
            buffer.set(ValueLayout.JAVA_INT, 0, IO_REPARSE_TAG_MOUNT_POINT.toInt())
            buffer.set(ValueLayout.JAVA_SHORT, 4, dataLength.toShort())
            buffer.set(ValueLayout.JAVA_SHORT, 6, 0)
            buffer.set(ValueLayout.JAVA_SHORT, 8, 0) // SubstituteNameOffset
            buffer.set(ValueLayout.JAVA_SHORT, 10, substitute.size.toShort())
            buffer.set(ValueLayout.JAVA_SHORT, 12, substitute.size.toShort()) // PrintNameOffset
            buffer.set(ValueLayout.JAVA_SHORT, 14, printed.size.toShort())
            MemorySegment.copy(pathBuffer, 0, buffer, ValueLayout.JAVA_BYTE, 16, pathBuffer.size)

            val handle = openForReparse(arena, linkName, write = true) ?: return@use false
            try {
                val returned = arena.allocate(ValueLayout.JAVA_INT)
                val ok = deviceIoControl.invokeExact(
                    handle, FSCTL_SET_REPARSE_POINT, buffer, total, MemorySegment.NULL, 0, returned, MemorySegment.NULL,
                ) as Int
                ok != 0
            } finally {
                closeHandle.invokeExact(handle) as Int
            }
        }
    }.getOrDefault(false)

    /** Removes the junction at [link]; the directory it pointed to is untouched. */
    @JvmStatic
    fun removeJunction(link: Path): Boolean = runCatching {
        Arena.ofConfined().use { arena ->
            val linkName = wideString(arena, link.toAbsolutePath().toString())
            // Deleting the reparse point through the handle is the reliable path; RemoveDirectoryW
            // is the documented shortcut, so try it first and fall back.
            if ((removeDirectoryW.invokeExact(linkName) as Int) != 0) return@use true
            val handle = openForReparse(arena, linkName, write = true) ?: return@use false
            try {
                val buffer = arena.allocate(8L, 8)
                buffer.set(ValueLayout.JAVA_INT, 0, IO_REPARSE_TAG_MOUNT_POINT.toInt())
                buffer.set(ValueLayout.JAVA_SHORT, 4, 0)
                buffer.set(ValueLayout.JAVA_SHORT, 6, 0)
                val returned = arena.allocate(ValueLayout.JAVA_INT)
                val ok = deviceIoControl.invokeExact(
                    handle, FSCTL_DELETE_REPARSE_POINT, buffer, 8, MemorySegment.NULL, 0, returned, MemorySegment.NULL,
                ) as Int
                ok != 0
            } finally {
                closeHandle.invokeExact(handle) as Int
            }
        }
    }.getOrDefault(false)

    /** `Files.move` is used for the plain-directory case, so no shell `rmdir` is needed there. */
    @JvmStatic
    fun moveDirectory(from: Path, to: Path): Boolean = runCatching {
        Arena.ofConfined().use { arena ->
            val a = wideString(arena, from.toAbsolutePath().toString())
            val b = wideString(arena, to.toAbsolutePath().toString())
            // MOVEFILE_COPY_ALLOWED | MOVEFILE_REPLACE_EXISTING
            (moveFileExW.invokeExact(a, b, 0x2 or 0x1) as Int) != 0
        }
    }.getOrDefault(false)

    private fun openForReparse(arena: Arena, name: MemorySegment, write: Boolean): MemorySegment? {
        val access = if (write) 0x40000000 else 0x80000000.toInt() // GENERIC_WRITE / GENERIC_READ
        val handle = createFileW.invokeExact(
            name,
            access,
            FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE,
            MemorySegment.NULL,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_DIRECTORY or FILE_ATTRIBUTE_REPARSE_POINT or
                FILE_FLAG_BACKUP_SEMANTICS or FILE_FLAG_OPEN_REPARSE_POINT,
            MemorySegment.NULL,
        ) as MemorySegment
        return if (handle.address() == -1L || handle.address() == 0L) null else handle
    }

    private fun wideString(arena: Arena, s: String): MemorySegment {
        val bytes = s.toByteArray(Charsets.UTF_16LE)
        val segment = arena.allocate(bytes.size.toLong() + 2)
        MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.size)
        return segment
    }
}
