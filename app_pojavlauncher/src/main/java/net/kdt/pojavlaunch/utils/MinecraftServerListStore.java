package net.kdt.pojavlaunch.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Reads and updates the standard compressed Minecraft servers.dat NBT file. */
public final class MinecraftServerListStore {
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_COLLECTION_LENGTH = 1_000_000;
    private static final int MAX_NESTING_DEPTH = 64;

    private MinecraftServerListStore() {
    }

    public static final class ServerEntry {
        public String name;
        public String address;
        private final NbtCompound nbt;

        public ServerEntry(String name, String address) {
            this(name, address, defaultServerTag(name, address));
        }

        public ServerEntry copy() {
            return new ServerEntry(name, address, new NbtCompound(nbt.values));
        }

        private ServerEntry(String name, String address, NbtCompound nbt) {
            this.name = name;
            this.address = address;
            this.nbt = nbt;
        }

        private NbtTag toTag() {
            nbt.put("name", stringTag(name));
            nbt.put("ip", stringTag(address));
            if (!nbt.contains("hideAddress")) nbt.put("hideAddress", byteTag((byte) 0));
            if (!nbt.contains("acceptTextures")) nbt.put("acceptTextures", byteTag((byte) 1));
            return new NbtTag(TAG_COMPOUND, nbt);
        }
    }

    public static List<ServerEntry> load(File serversFile) throws IOException {
        NbtDocument document = readDocument(serversFile);
        NbtTag servers = document.root.values.get("servers");
        ArrayList<ServerEntry> entries = new ArrayList<>();
        if (servers == null) return entries;
        if (servers.type != TAG_LIST) throw new IOException("Minecraft servers.dat has an invalid servers tag");
        NbtList serverList = (NbtList) servers.value;
        if (serverList.elementType != TAG_COMPOUND && !serverList.items.isEmpty()) {
            throw new IOException("Minecraft servers.dat servers list is not a compound list");
        }
        for (NbtTag item : serverList.items) {
            if (item.type != TAG_COMPOUND) continue;
            NbtCompound compound = (NbtCompound) item.value;
            String name = getString(compound, "name");
            String address = getString(compound, "ip");
            if (address.isEmpty()) continue;
            entries.add(new ServerEntry(name.isEmpty() ? address : name, address, compound));
        }
        return entries;
    }

    public static void save(File serversFile, List<ServerEntry> entries) throws IOException {
        if (serversFile == null) throw new IOException("No Minecraft server-list path was provided");
        NbtDocument document = readDocument(serversFile);
        ArrayList<NbtTag> serverTags = new ArrayList<>(entries.size());
        for (ServerEntry entry : entries) {
            if (entry == null) continue;
            serverTags.add(entry.toTag());
        }
        document.root.values.put("servers", new NbtTag(TAG_LIST,
                new NbtList(TAG_COMPOUND, serverTags)));
        writeDocumentAtomically(serversFile, document);
    }

    private static NbtDocument readDocument(File file) throws IOException {
        if (file == null) return new NbtDocument("", new NbtCompound());
        if (!file.exists()) {
            File backup = new File(file.getPath() + ".aerix.bak");
            if (backup.isFile()) file = backup;
            else return new NbtDocument("", new NbtCompound());
        }
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) {
            throw new IOException("Minecraft servers.dat is not a regular file or exceeds the size limit");
        }

        try (PushbackInputStream raw = new PushbackInputStream(
                new BufferedInputStream(new FileInputStream(file)), 2)) {
            int first = raw.read();
            int second = raw.read();
            if (second >= 0) raw.unread(second);
            if (first >= 0) raw.unread(first);
            InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(raw) : raw;
            try (DataInputStream input = new DataInputStream(
                    new BoundedInputStream(decoded, MAX_FILE_BYTES))) {
                int rootType = input.readUnsignedByte();
                if (rootType != TAG_COMPOUND) throw new IOException("Minecraft servers.dat root is not a compound");
                String rootName = input.readUTF();
                return new NbtDocument(rootName, readCompound(input, 0));
            }
        } catch (EOFException e) {
            throw new IOException("Minecraft servers.dat is truncated", e);
        }
    }

    private static void writeDocumentAtomically(File destination, NbtDocument document) throws IOException {
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create the Minecraft game directory");
        }
        File temp = new File(parent, destination.getName() + ".aerix.tmp");
        File backup = new File(parent, destination.getName() + ".aerix.bak");
        if (temp.exists() && !temp.delete()) throw new IOException("Could not clear a previous server-list temp file");
        if (backup.exists() && !backup.delete()) throw new IOException("Could not clear a previous server-list backup");

        try (FileOutputStream fileOutput = new FileOutputStream(temp);
             GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(fileOutput));
             DataOutputStream output = new DataOutputStream(gzip)) {
            output.writeByte(TAG_COMPOUND);
            output.writeUTF(document.rootName);
            writeCompound(output, document.root, 0);
            output.writeByte(TAG_END);
            output.flush();
            gzip.finish();
            output.flush();
            fileOutput.getFD().sync();
        } catch (IOException e) {
            boolean ignored = temp.delete();
            throw e;
        }

        boolean backedUp = false;
        if (destination.exists()) {
            if (!destination.renameTo(backup)) {
                boolean ignored = temp.delete();
                throw new IOException("Could not preserve the previous Minecraft server list");
            }
            backedUp = true;
        }
        if (!temp.renameTo(destination)) {
            if (backedUp && !backup.renameTo(destination)) {
                throw new IOException("Could not restore the previous Minecraft server list after a failed update");
            }
            boolean ignored = temp.delete();
            throw new IOException("Could not replace Minecraft servers.dat");
        }
        if (backedUp && !backup.delete()) {
            // The new list is already safely in place; a stale backup can be cleaned up later.
        }
    }

    private static NbtCompound readCompound(DataInputStream input, int depth) throws IOException {
        checkDepth(depth);
        NbtCompound compound = new NbtCompound();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == TAG_END) return compound;
            String name = input.readUTF();
            compound.put(name, readPayload(input, type, depth + 1));
        }
    }

    private static NbtTag readPayload(DataInputStream input, int type, int depth) throws IOException {
        checkDepth(depth);
        switch (type) {
            case TAG_BYTE: return new NbtTag(type, input.readByte());
            case TAG_SHORT: return new NbtTag(type, input.readShort());
            case TAG_INT: return new NbtTag(type, input.readInt());
            case TAG_LONG: return new NbtTag(type, input.readLong());
            case TAG_FLOAT: return new NbtTag(type, input.readFloat());
            case TAG_DOUBLE: return new NbtTag(type, input.readDouble());
            case TAG_BYTE_ARRAY: {
                int length = checkedLength(input.readInt());
                byte[] data = new byte[length];
                input.readFully(data);
                return new NbtTag(type, data);
            }
            case TAG_STRING: return new NbtTag(type, input.readUTF());
            case TAG_LIST: {
                int elementType = input.readUnsignedByte();
                int length = checkedLength(input.readInt());
                if (elementType == TAG_END && length != 0) throw new IOException("Invalid NBT list element type");
                ArrayList<NbtTag> items = new ArrayList<>(length);
                for (int i = 0; i < length; i++) items.add(readPayload(input, elementType, depth + 1));
                return new NbtTag(type, new NbtList(elementType, items));
            }
            case TAG_COMPOUND: return new NbtTag(type, readCompound(input, depth + 1));
            case TAG_INT_ARRAY: {
                int length = checkedLength(input.readInt());
                int[] data = new int[length];
                for (int i = 0; i < length; i++) data[i] = input.readInt();
                return new NbtTag(type, data);
            }
            case TAG_LONG_ARRAY: {
                int length = checkedLength(input.readInt());
                long[] data = new long[length];
                for (int i = 0; i < length; i++) data[i] = input.readLong();
                return new NbtTag(type, data);
            }
            default: throw new IOException("Unsupported NBT tag type " + type + " in servers.dat");
        }
    }

    private static void writeCompound(DataOutputStream output, NbtCompound compound, int depth) throws IOException {
        checkDepth(depth);
        for (Map.Entry<String, NbtTag> entry : compound.values.entrySet()) {
            output.writeByte(entry.getValue().type);
            output.writeUTF(entry.getKey());
            writePayload(output, entry.getValue(), depth + 1);
        }
    }

    private static void writePayload(DataOutputStream output, NbtTag tag, int depth) throws IOException {
        checkDepth(depth);
        switch (tag.type) {
            case TAG_BYTE: output.writeByte((Byte) tag.value); return;
            case TAG_SHORT: output.writeShort((Short) tag.value); return;
            case TAG_INT: output.writeInt((Integer) tag.value); return;
            case TAG_LONG: output.writeLong((Long) tag.value); return;
            case TAG_FLOAT: output.writeFloat((Float) tag.value); return;
            case TAG_DOUBLE: output.writeDouble((Double) tag.value); return;
            case TAG_BYTE_ARRAY: {
                byte[] data = (byte[]) tag.value;
                output.writeInt(data.length);
                output.write(data);
                return;
            }
            case TAG_STRING: output.writeUTF((String) tag.value); return;
            case TAG_LIST: {
                NbtList list = (NbtList) tag.value;
                output.writeByte(list.elementType);
                output.writeInt(list.items.size());
                for (NbtTag item : list.items) writePayload(output, item, depth + 1);
                return;
            }
            case TAG_COMPOUND:
                writeCompound(output, (NbtCompound) tag.value, depth + 1);
                output.writeByte(TAG_END);
                return;
            case TAG_INT_ARRAY: {
                int[] data = (int[]) tag.value;
                output.writeInt(data.length);
                for (int value : data) output.writeInt(value);
                return;
            }
            case TAG_LONG_ARRAY: {
                long[] data = (long[]) tag.value;
                output.writeInt(data.length);
                for (long value : data) output.writeLong(value);
                return;
            }
            default: throw new IOException("Unsupported NBT tag type " + tag.type + " in servers.dat");
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > MAX_COLLECTION_LENGTH) throw new IOException("Invalid NBT collection length");
        return length;
    }

    private static void checkDepth(int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) throw new IOException("Minecraft servers.dat is nested too deeply");
    }

    private static String getString(NbtCompound compound, String name) {
        NbtTag tag = compound.values.get(name);
        return tag != null && tag.type == TAG_STRING ? (String) tag.value : "";
    }

    private static NbtCompound defaultServerTag(String name, String address) {
        NbtCompound compound = new NbtCompound();
        compound.put("name", stringTag(name));
        compound.put("ip", stringTag(address));
        compound.put("hideAddress", byteTag((byte) 0));
        compound.put("acceptTextures", byteTag((byte) 1));
        return compound;
    }

    private static NbtTag stringTag(String value) { return new NbtTag(TAG_STRING, value == null ? "" : value); }
    private static NbtTag byteTag(byte value) { return new NbtTag(TAG_BYTE, value); }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long bytesRead;

        BoundedInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) account(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) account(count);
            return count;
        }

        private void account(int count) throws IOException {
            bytesRead += count;
            if (bytesRead > maxBytes) throw new IOException("Minecraft servers.dat exceeds the decompressed size limit");
        }
    }

    private static final class NbtDocument {
        final String rootName;
        final NbtCompound root;
        NbtDocument(String rootName, NbtCompound root) {
            this.rootName = rootName;
            this.root = root;
        }
    }

    private static final class NbtCompound {
        final LinkedHashMap<String, NbtTag> values = new LinkedHashMap<>();
        NbtCompound() {
        }
        NbtCompound(Map<String, NbtTag> values) {
            this.values.putAll(values);
        }
        void put(String key, NbtTag value) { values.put(key, value); }
        boolean contains(String key) { return values.containsKey(key); }
    }

    private static final class NbtList {
        final int elementType;
        final List<NbtTag> items;
        NbtList(int elementType, List<NbtTag> items) {
            this.elementType = elementType;
            this.items = items;
        }
    }

    private static final class NbtTag {
        final int type;
        final Object value;
        NbtTag(int type, Object value) {
            this.type = type;
            this.value = value;
        }
    }
}
