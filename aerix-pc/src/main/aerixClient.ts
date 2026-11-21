import fs from 'node:fs';
import path from 'node:path';
import {
  mergeResourcePackOrder,
  parseMinecraftOptionsResourcePacks,
  setMinecraftOptionsResourcePacks,
} from './contentManager';
import { downloadFile, fetchJson, runWithConcurrency } from './http';
import { LaunchProgressEvent } from './types';
import { createZipBuffer } from './zip';

export const AERIX_CLIENT_PACK_NAME = 'Aerix-Client-Pack.zip';
export const AERIX_CLIENT_PACK_TOKEN = `file/${AERIX_CLIENT_PACK_NAME}`;
export const AERIX_PACK_STAMP_REL = path.join('config', '.aerix_pack_stamp');

export const AERIX_CLIENT_MOD_SLUGS = [
  'fabric-api',
  'sodium',
  'lithium',
  'ferrite-core',
  'immediatelyfast',
  'entityculling',
  'modernfix',
  'krypton',
  'modmenu',
  'appleskin',
  'logical-zoom',
  'wavey-capes',
];

export const AERIX_CLIENT_SUPPORTED_VERSIONS = [
  '1.21.11',
  '1.21.10',
  '1.21.8',
  '1.21.4',
  '1.21.1',
  '1.20.4',
  '1.20.1',
  '1.19.4',
  '1.18.2',
  '1.16.5',
];

export function getPackFormatForVersion(mcVersion: string): { format: number; minor: number } {
  if (mcVersion === '26.3') return { format: 97, minor: 1 };
  if (mcVersion === '26.2') return { format: 88, minor: 0 };
  if (mcVersion.startsWith('26.1')) return { format: 84, minor: 0 };
  if (mcVersion === '1.21.11') return { format: 75, minor: 0 };
  if (mcVersion === '1.21.9' || mcVersion === '1.21.10') return { format: 69, minor: 0 };
  if (mcVersion === '1.21.7' || mcVersion === '1.21.8') return { format: 64, minor: 0 };
  if (mcVersion === '1.21.6') return { format: 63, minor: 0 };
  if (mcVersion === '1.21.5') return { format: 55, minor: 0 };
  if (mcVersion === '1.21.4') return { format: 46, minor: 0 };
  if (mcVersion === '1.21.2' || mcVersion === '1.21.3') return { format: 42, minor: 0 };
  if (mcVersion === '1.21' || mcVersion === '1.21.1') return { format: 34, minor: 0 };
  if (mcVersion === '1.20.5' || mcVersion === '1.20.6') return { format: 32, minor: 0 };
  if (mcVersion === '1.20.3' || mcVersion === '1.20.4') return { format: 22, minor: 0 };
  if (mcVersion === '1.20.2') return { format: 18, minor: 0 };
  if (mcVersion === '1.20' || mcVersion === '1.20.1') return { format: 15, minor: 0 };
  if (mcVersion === '1.19.4') return { format: 13, minor: 0 };
  if (mcVersion.startsWith('1.19')) return { format: 9, minor: 0 };
  if (mcVersion.startsWith('1.18')) return { format: 8, minor: 0 };
  if (mcVersion.startsWith('1.17')) return { format: 7, minor: 0 };
  return { format: 6, minor: 0 };
}

export function buildAerixPackMcmeta(mcVersion: string): string {
  const { format, minor } = getPackFormatForVersion(mcVersion);
  const pack: Record<string, unknown> = {
    pack_format: format,
    description: 'Aerix Client PC — Crisp Dark UI & Essentials',
  };
  if (format >= 65) {
    pack.min_format = [format, minor];
    pack.max_format = [format, minor];
  } else if (format >= 16) {
    pack.supported_formats = { min_inclusive: 6, max_inclusive: format };
  }
  return JSON.stringify({ pack }, null, 2);
}

export function ensureAerixClientPack(instanceDir: string, mcVersion: string): void {
  const resourcePacksDir = path.join(instanceDir, 'resourcepacks');
  fs.mkdirSync(resourcePacksDir, { recursive: true });
  const packPath = path.join(resourcePacksDir, AERIX_CLIENT_PACK_NAME);
  const stampPath = path.join(instanceDir, AERIX_PACK_STAMP_REL);
  fs.mkdirSync(path.dirname(stampPath), { recursive: true });

  const stamp = `${mcVersion}:pc-v1`;
  const existingStamp = fs.existsSync(stampPath) ? fs.readFileSync(stampPath, 'utf8').trim() : '';

  if (!fs.existsSync(packPath) || existingStamp !== stamp) {
    const zipBuffer = createZipBuffer([
      { name: 'pack.mcmeta', data: buildAerixPackMcmeta(mcVersion) },
      {
        name: 'assets/minecraft/texts/splashes.txt',
        data: [
          'Aerix Client PC!',
          'Smooth frames, sharp visuals!',
          'Powered by Fabric & Aerix!',
          'Cross-platform ready!',
        ].join('\n'),
      },
    ]);
    fs.writeFileSync(packPath, zipBuffer);
    fs.writeFileSync(stampPath, stamp, 'utf8');
  }

  // Ensure Aerix-Client-Pack.zip is enabled in options.txt strictly BELOW user resource packs
  const optionsPath = path.join(instanceDir, 'options.txt');
  const existingOptions = fs.existsSync(optionsPath) ? fs.readFileSync(optionsPath, 'utf8') : '';
  const currentPacks = parseMinecraftOptionsResourcePacks(existingOptions);
  const merged = mergeResourcePackOrder(currentPacks, AERIX_CLIENT_PACK_TOKEN, {
    enabled: true,
    belowUserPacks: true,
  });
  if (!merged.includes('vanilla')) {
    merged.unshift('vanilla');
  }
  fs.writeFileSync(optionsPath, setMinecraftOptionsResourcePacks(existingOptions, merged), 'utf8');
}

interface ModrinthVersionRow {
  id: string;
  version_number: string;
  game_versions: string[];
  loaders: string[];
  files: Array<{
    url: string;
    filename: string;
    primary: boolean;
    hashes?: { sha1?: string };
  }>;
}

export async function syncAerixClientMods(
  instanceDir: string,
  mcVersion: string,
  onProgress?: (ev: LaunchProgressEvent) => void,
  instanceId?: string
): Promise<string[]> {
  const modsDir = path.join(instanceDir, 'mods');
  fs.mkdirSync(modsDir, { recursive: true });

  const markerPath = path.join(instanceDir, 'config', '.aerix_client_mods_version');
  const existingMarker = fs.existsSync(markerPath) ? fs.readFileSync(markerPath, 'utf8').trim() : '';
  if (existingMarker === mcVersion && fs.readdirSync(modsDir).some((f) => f.endsWith('.jar'))) {
    return [];
  }

  const installed: string[] = [];
  let done = 0;

  await runWithConcurrency(AERIX_CLIENT_MOD_SLUGS, 4, async (slug) => {
    try {
      const params = new URLSearchParams({
        loaders: JSON.stringify(['fabric']),
        game_versions: JSON.stringify([mcVersion]),
      });
      const versions = await fetchJson<ModrinthVersionRow[]>(
        `https://api.modrinth.com/v2/project/${encodeURIComponent(slug)}/version?${params.toString()}`
      );
      const chosen = versions[0];
      if (!chosen) return;
      const file = chosen.files.find((f) => f.primary) ?? chosen.files[0];
      if (!file) return;

      const dest = path.join(modsDir, file.filename);
      await downloadFile(file.url, dest, { expectedSha1: file.hashes?.sha1 });
      installed.push(file.filename);
    } catch {
      // Optional performance/QoL mod unavailable for this MC version; skip cleanly
    } finally {
      done++;
      const pct = Math.round((done / AERIX_CLIENT_MOD_SLUGS.length) * 100);
      onProgress?.({
        stage: 'aerix-client',
        message: `Syncing Aerix Client PC mods (${done}/${AERIX_CLIENT_MOD_SLUGS.length})...`,
        percent: pct,
        instanceId,
      });
    }
  });

  if (installed.length > 0) {
    fs.mkdirSync(path.dirname(markerPath), { recursive: true });
    fs.writeFileSync(markerPath, mcVersion, 'utf8');
  }
  return installed;
}
