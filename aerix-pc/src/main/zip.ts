import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

export function crc32(buf: Buffer): number {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

export interface ZipInputEntry {
  name: string;
  data: Buffer | string;
}

export interface ZipParsedEntry {
  name: string;
  compressionMethod: number;
  compressedSize: number;
  uncompressedSize: number;
  crc32: number;
  dataOffset: number;
  externalAttributes: number;
}

export function createZipBuffer(entries: ZipInputEntry[]): Buffer {
  const localChunks: Buffer[] = [];
  const centralChunks: Buffer[] = [];
  let offset = 0;

  for (const entry of entries) {
    const cleanName = entry.name.replace(/\\/g, '/').replace(/^\/+/, '');
    const nameBuf = Buffer.from(cleanName, 'utf8');
    const raw = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(entry.data, 'utf8');
    const crc = crc32(raw);
    const compressed = zlib.deflateRawSync(raw, { level: 6 });
    const useDeflate = compressed.length < raw.length;
    const method = useDeflate ? 8 : 0;
    const payload = useDeflate ? compressed : raw;

    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt16LE(0x0800, 6); // UTF-8 flag
    localHeader.writeUInt16LE(method, 8);
    localHeader.writeUInt16LE(0, 10);
    localHeader.writeUInt16LE(0, 12);
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(payload.length, 18);
    localHeader.writeUInt32LE(raw.length, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28);

    localChunks.push(localHeader, nameBuf, payload);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0x0800, 8);
    centralHeader.writeUInt16LE(method, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(payload.length, 20);
    centralHeader.writeUInt32LE(raw.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);

    centralChunks.push(centralHeader, nameBuf);
    offset += localHeader.length + nameBuf.length + payload.length;
  }

  const centralSize = centralChunks.reduce((sum, b) => sum + b.length, 0);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralSize, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([...localChunks, ...centralChunks, eocd]);
}

function findEocdOffset(buf: Buffer): number {
  const minOffset = Math.max(0, buf.length - 65557);
  for (let i = buf.length - 22; i >= minOffset; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      return i;
    }
  }
  throw new Error('Invalid ZIP archive: End of Central Directory not found');
}

export function parseZipEntries(buf: Buffer): ZipParsedEntry[] {
  const eocdOffset = findEocdOffset(buf);
  const totalEntries = buf.readUInt16LE(eocdOffset + 10);
  const centralDirOffset = buf.readUInt32LE(eocdOffset + 16);
  const entries: ZipParsedEntry[] = [];
  let ptr = centralDirOffset;

  for (let i = 0; i < totalEntries; i++) {
    if (ptr + 46 > buf.length || buf.readUInt32LE(ptr) !== 0x02014b50) {
      throw new Error('Invalid ZIP central directory header');
    }
    const compressionMethod = buf.readUInt16LE(ptr + 10);
    const entryCrc = buf.readUInt32LE(ptr + 16);
    const compressedSize = buf.readUInt32LE(ptr + 20);
    const uncompressedSize = buf.readUInt32LE(ptr + 24);
    const nameLen = buf.readUInt16LE(ptr + 28);
    const extraLen = buf.readUInt16LE(ptr + 30);
    const commentLen = buf.readUInt16LE(ptr + 32);
    const externalAttributes = buf.readUInt32LE(ptr + 38);
    const localHeaderOffset = buf.readUInt32LE(ptr + 42);
    const name = buf.toString('utf8', ptr + 46, ptr + 46 + nameLen);

    if (buf.readUInt32LE(localHeaderOffset) !== 0x04034b50) {
      throw new Error(`Invalid local header for ZIP entry: ${name}`);
    }
    const localNameLen = buf.readUInt16LE(localHeaderOffset + 26);
    const localExtraLen = buf.readUInt16LE(localHeaderOffset + 28);
    const dataOffset = localHeaderOffset + 30 + localNameLen + localExtraLen;

    entries.push({
      name,
      compressionMethod,
      compressedSize,
      uncompressedSize,
      crc32: entryCrc,
      dataOffset,
      externalAttributes,
    });

    ptr += 46 + nameLen + extraLen + commentLen;
  }

  return entries;
}

export function readZipEntryBuffer(buf: Buffer, entry: ZipParsedEntry): Buffer {
  const slice = buf.subarray(entry.dataOffset, entry.dataOffset + entry.compressedSize);
  if (entry.compressionMethod === 0) {
    return Buffer.from(slice);
  }
  if (entry.compressionMethod === 8) {
    return zlib.inflateRawSync(slice);
  }
  throw new Error(`Unsupported ZIP compression method ${entry.compressionMethod} for ${entry.name}`);
}

export function safeResolveZipTarget(destDir: string, entryName: string): string | null {
  const normalized = entryName.replace(/\\/g, '/');
  if (!normalized || normalized.endsWith('/')) {
    return null;
  }
  const destRoot = path.resolve(destDir);
  const target = path.resolve(destRoot, normalized);
  if (target !== destRoot && !target.startsWith(destRoot + path.sep)) {
    throw new Error(`Unsafe ZIP entry path traversal blocked: ${entryName}`);
  }
  return target;
}

export function extractZipFile(
  zipPath: string,
  destDir: string,
  filter?: (entryName: string) => boolean
): string[] {
  const buf = fs.readFileSync(zipPath);
  const entries = parseZipEntries(buf);
  const extracted: string[] = [];
  fs.mkdirSync(destDir, { recursive: true });

  for (const entry of entries) {
    const cleanName = entry.name.replace(/\\/g, '/');
    if (filter && !filter(cleanName)) continue;
    const target = safeResolveZipTarget(destDir, cleanName);
    if (!target) continue;
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const data = readZipEntryBuffer(buf, entry);
    fs.writeFileSync(target, data);
    if ((entry.externalAttributes >>> 16) & 0o111) {
      try {
        fs.chmodSync(target, 0o755);
      } catch {
        // Ignore on filesystems without POSIX permissions
      }
    }
    extracted.push(target);
  }
  return extracted;
}

/**
 * Unwraps a resource pack ZIP that was archived inside a single top-level folder
 * (e.g. `MyPack/pack.mcmeta`, `__MACOSX/...`) so Minecraft recognizes `pack.mcmeta` at ZIP root.
 */
export function normalizeResourcePackZipBuffer(zipBuffer: Buffer): {
  changed: boolean;
  buffer: Buffer;
} {
  const entries = parseZipEntries(zipBuffer);
  const fileEntries = entries.filter((e) => {
    const clean = e.name.replace(/\\/g, '/');
    return clean && !clean.endsWith('/') && !clean.startsWith('__MACOSX/');
  });
  if (fileEntries.length === 0) {
    return { changed: false, buffer: zipBuffer };
  }
  const hasRootMcmeta = fileEntries.some((e) => e.name.replace(/\\/g, '/') === 'pack.mcmeta');
  if (hasRootMcmeta) {
    return { changed: false, buffer: zipBuffer };
  }
  const nestedMcmetas = fileEntries
    .map((e) => e.name.replace(/\\/g, '/'))
    .filter((n) => n.endsWith('/pack.mcmeta'));
  if (nestedMcmetas.length !== 1) {
    return { changed: false, buffer: zipBuffer };
  }
  const prefix = nestedMcmetas[0].slice(0, -'pack.mcmeta'.length);
  const allMatchPrefix = fileEntries.every((e) => e.name.replace(/\\/g, '/').startsWith(prefix));
  if (!allMatchPrefix) {
    return { changed: false, buffer: zipBuffer };
  }

  const rewritten: ZipInputEntry[] = fileEntries.map((entry) => ({
    name: entry.name.replace(/\\/g, '/').slice(prefix.length),
    data: readZipEntryBuffer(zipBuffer, entry),
  }));
  return { changed: true, buffer: createZipBuffer(rewritten) };
}
