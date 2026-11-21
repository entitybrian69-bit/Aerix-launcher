import fs from 'node:fs';
import path from 'node:path';
import { sanitizeFileName } from './paths';
import { ContentEntry, ContentKind } from './types';
import { normalizeResourcePackZipBuffer } from './zip';

const AERIX_CLIENT_PACK_TOKEN = 'file/Aerix-Client-Pack.zip';

export function parseMinecraftOptionsResourcePacks(optionsText: string): string[] {
  const line = optionsText
    .split(/\r?\n/)
    .find((l) => l.startsWith('resourcePacks:'));
  if (!line) return [];
  const rawValue = line.slice('resourcePacks:'.length).trim();
  if (!rawValue.startsWith('[') || !rawValue.endsWith(']')) return [];
  try {
    const parsed = JSON.parse(rawValue);
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    const inner = rawValue.slice(1, -1).trim();
    if (!inner) return [];
    return inner
      .split(',')
      .map((item) => item.trim().replace(/^"(.*)"$/, '$1'))
      .filter(Boolean);
  }
}

export function setMinecraftOptionsResourcePacks(optionsText: string, packs: string[]): string {
  const encoded = JSON.stringify(packs);
  const newLine = `resourcePacks:${encoded}`;
  const lines = optionsText ? optionsText.split(/\r?\n/) : [];
  let replaced = false;
  const out = lines.map((line) => {
    if (line.startsWith('resourcePacks:')) {
      replaced = true;
      return newLine;
    }
    return line;
  });
  if (!replaced) {
    out.push(newLine);
  }
  return out.filter((l, idx) => idx < out.length - 1 || l !== '').join('\n') + '\n';
}

export function mergeResourcePackOrder(
  existing: string[],
  packToken: string,
  options: {
    enabled: boolean;
    belowUserPacks?: boolean;
  }
): string[] {
  const deduped: string[] = [];
  for (const item of existing) {
    if (item === packToken) continue;
    if (!deduped.includes(item)) deduped.push(item);
  }
  if (!options.enabled) {
    return deduped;
  }
  if (options.belowUserPacks) {
    // Minecraft applies packs from bottom to top of list: earlier entries have LOWER priority.
    // Place Aerix-Client-Pack.zip immediately after "vanilla" (before user "file/..." packs)
    // so user resource packs always win.
    const vanillaIdx = deduped.indexOf('vanilla');
    const insertAt = vanillaIdx >= 0 ? vanillaIdx + 1 : 0;
    deduped.splice(insertAt, 0, packToken);
    return deduped;
  }
  deduped.push(packToken);
  return deduped;
}

export function readInstanceResourcePacks(instanceDir: string): string[] {
  const optionsPath = path.join(instanceDir, 'options.txt');
  if (!fs.existsSync(optionsPath)) return [];
  return parseMinecraftOptionsResourcePacks(fs.readFileSync(optionsPath, 'utf8'));
}

export function setInstanceResourcePackEnabled(
  instanceDir: string,
  fileName: string,
  enabled: boolean
): string[] {
  const optionsPath = path.join(instanceDir, 'options.txt');
  const existingText = fs.existsSync(optionsPath) ? fs.readFileSync(optionsPath, 'utf8') : '';
  const current = parseMinecraftOptionsResourcePacks(existingText);
  const token = `file/${fileName}`;
  const merged = mergeResourcePackOrder(current, token, {
    enabled,
    belowUserPacks: token === AERIX_CLIENT_PACK_TOKEN,
  });
  if (!merged.includes('vanilla') && merged.length > 0) {
    merged.unshift('vanilla');
  }
  fs.writeFileSync(optionsPath, setMinecraftOptionsResourcePacks(existingText, merged), 'utf8');
  return merged;
}

export function listInstanceContent(instanceDir: string, kind: ContentKind): ContentEntry[] {
  const targetDir = path.join(instanceDir, kind);
  if (!fs.existsSync(targetDir)) return [];

  const activePacks =
    kind === 'resourcepacks' ? new Set(readInstanceResourcePacks(instanceDir)) : null;

  const entries: ContentEntry[] = [];
  for (const dirent of fs.readdirSync(targetDir, { withFileTypes: true })) {
    const fullPath = path.join(targetDir, dirent.name);
    const stat = fs.statSync(fullPath);

    if (kind === 'mods') {
      if (dirent.isDirectory()) continue;
      if (!dirent.name.endsWith('.jar') && !dirent.name.endsWith('.jar.disabled')) continue;
      const enabled = dirent.name.endsWith('.jar');
      const displayName = dirent.name.replace(/\.jar(\.disabled)?$/i, '');
      entries.push({
        name: displayName,
        fileName: dirent.name,
        fullPath,
        enabled,
        sizeBytes: stat.size,
        modifiedAt: stat.mtimeMs,
        isDirectory: false,
      });
    } else if (kind === 'resourcepacks') {
      if (!dirent.isDirectory() && !dirent.name.endsWith('.zip')) continue;
      const token = `file/${dirent.name}`;
      entries.push({
        name: dirent.name.replace(/\.zip$/i, ''),
        fileName: dirent.name,
        fullPath,
        enabled: activePacks ? activePacks.has(token) : false,
        sizeBytes: stat.size,
        modifiedAt: stat.mtimeMs,
        isDirectory: dirent.isDirectory(),
      });
    } else if (kind === 'shaderpacks') {
      if (!dirent.isDirectory() && !dirent.name.endsWith('.zip')) continue;
      entries.push({
        name: dirent.name.replace(/\.zip$/i, ''),
        fileName: dirent.name,
        fullPath,
        enabled: true,
        sizeBytes: stat.size,
        modifiedAt: stat.mtimeMs,
        isDirectory: dirent.isDirectory(),
      });
    } else if (kind === 'saves') {
      if (!dirent.isDirectory()) continue;
      entries.push({
        name: dirent.name,
        fileName: dirent.name,
        fullPath,
        enabled: true,
        sizeBytes: stat.size,
        modifiedAt: stat.mtimeMs,
        isDirectory: true,
      });
    } else if (kind === 'screenshots') {
      if (dirent.isDirectory() || !/\.(png|jpg|jpeg|webp)$/i.test(dirent.name)) continue;
      entries.push({
        name: dirent.name,
        fileName: dirent.name,
        fullPath,
        enabled: true,
        sizeBytes: stat.size,
        modifiedAt: stat.mtimeMs,
        isDirectory: false,
      });
    }
  }

  return entries.sort((a, b) => a.name.localeCompare(b.name));
}

export function importContentFile(
  instanceDir: string,
  kind: ContentKind,
  sourcePath: string
): ContentEntry {
  const destDir = path.join(instanceDir, kind);
  fs.mkdirSync(destDir, { recursive: true });
  const fileName = sanitizeFileName(path.basename(sourcePath));
  const destPath = path.join(destDir, fileName);

  if (kind === 'resourcepacks' && fileName.toLowerCase().endsWith('.zip')) {
    const raw = fs.readFileSync(sourcePath);
    const normalized = normalizeResourcePackZipBuffer(raw);
    fs.writeFileSync(destPath, normalized.buffer);
    setInstanceResourcePackEnabled(instanceDir, fileName, true);
  } else {
    fs.copyFileSync(sourcePath, destPath);
  }

  const stat = fs.statSync(destPath);
  return {
    name: fileName.replace(/\.(jar|zip)(\.disabled)?$/i, ''),
    fileName,
    fullPath: destPath,
    enabled: true,
    sizeBytes: stat.size,
    modifiedAt: stat.mtimeMs,
    isDirectory: stat.isDirectory(),
  };
}

export function toggleContentEntry(
  instanceDir: string,
  kind: ContentKind,
  fileName: string,
  enabled: boolean
): void {
  if (kind === 'mods') {
    const modsDir = path.join(instanceDir, 'mods');
    const currentPath = path.join(modsDir, fileName);
    if (!fs.existsSync(currentPath)) return;
    const baseJar = fileName.replace(/\.disabled$/i, '');
    const targetName = enabled ? baseJar : `${baseJar}.disabled`;
    const targetPath = path.join(modsDir, targetName);
    if (currentPath !== targetPath) {
      fs.renameSync(currentPath, targetPath);
    }
    return;
  }

  if (kind === 'resourcepacks') {
    setInstanceResourcePackEnabled(instanceDir, fileName, enabled);
  }
}

export function deleteContentEntry(
  instanceDir: string,
  kind: ContentKind,
  fileName: string
): void {
  const target = path.join(instanceDir, kind, path.basename(fileName));
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true });
  }
  if (kind === 'resourcepacks') {
    setInstanceResourcePackEnabled(instanceDir, fileName, false);
  }
}
