import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { AerixPaths, ensureAerixDirs, sanitizeSlug } from './paths';
import {
  Account,
  InstanceConfig,
  LauncherSettings,
  ModLoaderType,
  WallpaperPreset,
} from './types';

export const WALLPAPER_PRESETS: WallpaperPreset[] = [
  // Animated live scenes (matching mobile AerixAnimations.kt)
  { id: 'anim_01_lush_cavern', title: 'Lush Cavern Glow', biome: 'Live Canvas • Fireflies & Cavern Light', assetFile: '', accentHex: '#2DD4BF', animatedScene: true },
  { id: 'anim_02_cherry_grove', title: 'Cherry Grove Drift', biome: 'Live Canvas • Falling Petals', assetFile: '', accentHex: '#F472B6', animatedScene: true },
  { id: 'anim_13_glacier', title: 'Glacier Aurora', biome: 'Live Canvas • Northern Lights & Snow', assetFile: '', accentHex: '#22D3EE', animatedScene: true },
  { id: 'anim_05_nether_lava', title: 'Nether Emberfall', biome: 'Live Canvas • Rising Embers', assetFile: '', accentHex: '#F97316', animatedScene: true },
  { id: 'anim_25_deep_dark', title: 'Sculk Abyss', biome: 'Live Canvas • Sculk Pulse & Motes', assetFile: '', accentHex: '#06B6D4', animatedScene: true },
  { id: 'anim_08_moonlit_lake', title: 'Moonlit Lake', biome: 'Live Canvas • Starlight & Ripples', assetFile: '', accentHex: '#818CF8', animatedScene: true },
  { id: 'anim_03_sunset_river', title: 'Golden Sunset', biome: 'Live Canvas • Sunset & Warm Motes', assetFile: '', accentHex: '#F59E0B', animatedScene: true },
  { id: 'anim_06_the_end', title: 'The End Void', biome: 'Live Canvas • End Void & Portal Glow', assetFile: '', accentHex: '#A855F7', animatedScene: true },

  // All 26 photographic shader wallpapers from mobile AerixThemeManager
  { id: 'wp_01_lush_caves', title: 'Lush Glowberry Cave', biome: 'Lush Caves • Complementary Shaders', assetFile: 'wp_01_lush_caves.webp', accentHex: '#8DEFE0' },
  { id: 'wp_02_cherry_blossom', title: 'Cherry Blossom Grove', biome: 'Cherry Grove • BSL Shaders', assetFile: 'wp_02_cherry_blossom.webp', accentHex: '#F472B6' },
  { id: 'wp_03_sunset_river', title: 'Golden Sunset River', biome: 'River Valley • SEUS Renewed', assetFile: 'wp_03_sunset_river.webp', accentHex: '#F59E0B' },
  { id: 'wp_04_crystal_river', title: 'Crystal River Forest', biome: 'Birch & Oak River • Complementary', assetFile: 'wp_04_crystal_river.webp', accentHex: '#06B6D4' },
  { id: 'wp_05_nether_fortress', title: 'Nether Lava Cavern', biome: 'Nether Wastes • RTX Shaders', assetFile: 'wp_05_nether_fortress.webp', accentHex: '#F97316' },
  { id: 'wp_06_the_end', title: 'The End Obsidian Pillars', biome: 'The End • Sildur Vibrant', assetFile: 'wp_06_the_end.webp', accentHex: '#A855F7' },
  { id: 'wp_07_ocean_arch', title: 'Sunlight Ocean Arch', biome: 'Warm Ocean • Complementary', assetFile: 'wp_07_ocean_arch.webp', accentHex: '#38BDF8' },
  { id: 'wp_08_moonlit_lake', title: 'Moonlit Birch Lake', biome: 'Night Forest • Nostalgia', assetFile: 'wp_08_moonlit_lake.webp', accentHex: '#818CF8' },
  { id: 'wp_09_flower_meadow', title: 'Sunrise Flower Meadow', biome: 'Flower Forest • BSL Shaders', assetFile: 'wp_09_flower_meadow.webp', accentHex: '#EC4899' },
  { id: 'wp_10_spruce_mist', title: 'Spruce Lake Morning Mist', biome: 'Old Growth Taiga • Bliss', assetFile: 'wp_10_spruce_mist.webp', accentHex: '#84CC16' },
  { id: 'wp_11_cherry_bee', title: 'Honeybee Cherry Canopy', biome: 'Cherry Canopy • Complementary', assetFile: 'wp_11_cherry_bee.webp', accentHex: '#FBBF24' },
  { id: 'wp_12_snowy_valley', title: 'Snowy Peaks Valley', biome: 'Jagged Peaks • Distant Horizons', assetFile: 'wp_12_snowy_valley.webp', accentHex: '#60A5FA' },
  { id: 'wp_13_frozen_glacier', title: 'Frozen Glacier Ridge', biome: 'Frozen Peaks • Complementary', assetFile: 'wp_13_frozen_glacier.webp', accentHex: '#22D3EE' },
  { id: 'wp_14_cozy_village', title: 'Cozy Plains Village', biome: 'Plains Village • SEUS PTGI', assetFile: 'wp_14_cozy_village.webp', accentHex: '#10B981' },
  { id: 'wp_15_end_portal', title: 'End Portal Starfield', biome: 'Stronghold • Void Shaders', assetFile: 'wp_15_end_portal.webp', accentHex: '#14B8A6' },
  { id: 'wp_16_ocean_monument', title: 'Deep Ocean Monument', biome: 'Deep Ocean • Prismarine', assetFile: 'wp_16_ocean_monument.webp', accentHex: '#3B82F6' },
  { id: 'wp_17_night_clouds', title: 'Starry Night River', biome: 'Plains Night • Solas Shaders', assetFile: 'wp_17_night_clouds.webp', accentHex: '#8B5CF6' },
  { id: 'wp_18_crater_harbor', title: 'Cherry Crater Village', biome: 'Cherry Basin • Vanilla+', assetFile: 'wp_18_crater_harbor.webp', accentHex: '#FB7185' },
  { id: 'wp_19_sinkhole_falls', title: 'Lush Sinkhole Waterfall', biome: 'Dripstone & Lush • Iris', assetFile: 'wp_19_sinkhole_falls.webp', accentHex: '#2DD4BF' },
  { id: 'wp_20_sakura_sunbeams', title: 'Sakura Sunbeams', biome: 'Cherry Glade • Complementary', assetFile: 'wp_20_sakura_sunbeams.webp', accentHex: '#E879F9' },
  { id: 'wp_21_crimson_forest', title: 'Crimson Forest Embers', biome: 'Nether Crimson Forest', assetFile: 'wp_21_crimson_forest.webp', accentHex: '#E11D48' },
  { id: 'wp_22_amethyst_geode', title: 'Amethyst Geode Lake', biome: 'Amethyst Geode • Crystal', assetFile: 'wp_22_amethyst_geode.webp', accentHex: '#A855F7' },
  { id: 'wp_23_bamboo_dawn', title: 'Bamboo Sunrise', biome: 'Bamboo Jungle • Sunrise', assetFile: 'wp_23_bamboo_dawn.webp', accentHex: '#84CC16' },
  { id: 'wp_24_badlands_canyon', title: 'Badlands Canyon River', biome: 'Badlands Mesa • Canyon', assetFile: 'wp_24_badlands_canyon.webp', accentHex: '#F97316' },
  { id: 'wp_25_deep_dark', title: 'Deep Dark Sculk City', biome: 'Deep Dark • Sculk Shaders', assetFile: 'wp_25_deep_dark.webp', accentHex: '#06B6D4' },
  { id: 'aerix_twilight', title: 'Aerix Twilight', biome: 'Cozy Voxel Village at Dusk', assetFile: 'aerix_twilight.jpg', accentHex: '#8DEFE0' },
];

export function getRecommendedRamMb(): number {
  const totalMb = Math.round(os.totalmem() / (1024 * 1024));
  if (totalMb >= 16384) return 4096;
  if (totalMb >= 8192) return 3072;
  return 2048;
}

export function createDefaultSettings(): LauncherSettings {
  return {
    selectedInstanceId: null,
    selectedAccountId: null,
    defaultRamMb: getRecommendedRamMb(),
    defaultJavaPath: '',
    defaultJvmArgs: '-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M',
    defaultWidth: 1280,
    defaultHeight: 720,
    closeOnLaunch: 'minimize',
    showSnapshots: false,
    themePreset: 'aurora-teal',
    customAccentHex: '#8DEFE0',
    uiStyle: 'premium',
    themeMotion: true,
    layoutTransparency: 100,
    wallpaperBlur: 2,
    wallpaperId: 'wp_01_lush_caves',
    wallpaperOpacity: 0.44,
    curseforgeApiKey: '',
  };
}

function readJsonSafe<T>(filePath: string, fallback: T): T {
  try {
    if (!fs.existsSync(filePath)) return fallback;
    return JSON.parse(fs.readFileSync(filePath, 'utf8')) as T;
  } catch {
    return fallback;
  }
}

function writeJsonAtomic(filePath: string, value: unknown): void {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tmp = `${filePath}.tmp.${process.pid}`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2), 'utf8');
  fs.renameSync(tmp, filePath);
}

export class LauncherStore {
  constructor(public readonly paths: AerixPaths) {
    ensureAerixDirs(this.paths);
  }

  getSettings(): LauncherSettings {
    const defaults = createDefaultSettings();
    const loaded = readJsonSafe<Partial<LauncherSettings>>(this.paths.settingsFile, {});
    return { ...defaults, ...loaded };
  }

  saveSettings(patch: Partial<LauncherSettings>): LauncherSettings {
    const next = { ...this.getSettings(), ...patch };
    writeJsonAtomic(this.paths.settingsFile, next);
    return next;
  }

  listAccounts(): Account[] {
    const items = readJsonSafe<Account[]>(this.paths.accountsFile, []);
    return Array.isArray(items) ? items : [];
  }

  saveAccounts(accounts: Account[]): void {
    writeJsonAtomic(this.paths.accountsFile, accounts);
  }

  upsertAccount(account: Account): Account[] {
    const accounts = this.listAccounts();
    const idx = accounts.findIndex(
      (a) => a.id === account.id || (a.type === account.type && a.uuid === account.uuid)
    );
    if (idx >= 0) {
      accounts[idx] = account;
    } else {
      accounts.push(account);
    }
    this.saveAccounts(accounts);
    const settings = this.getSettings();
    if (!settings.selectedAccountId) {
      this.saveSettings({ selectedAccountId: account.id });
    }
    return accounts;
  }

  removeAccount(accountId: string): Account[] {
    const accounts = this.listAccounts().filter((a) => a.id !== accountId);
    this.saveAccounts(accounts);
    const settings = this.getSettings();
    if (settings.selectedAccountId === accountId) {
      this.saveSettings({ selectedAccountId: accounts[0]?.id ?? null });
    }
    return accounts;
  }

  getInstanceDir(instanceId: string): string {
    return path.join(this.paths.instancesDir, sanitizeSlug(instanceId));
  }

  getInstanceMetaFile(instanceId: string): string {
    return path.join(this.getInstanceDir(instanceId), 'aerix_instance.json');
  }

  listInstances(): InstanceConfig[] {
    ensureAerixDirs(this.paths);
    const entries = fs.readdirSync(this.paths.instancesDir, { withFileTypes: true });
    const list: InstanceConfig[] = [];
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const metaPath = path.join(this.paths.instancesDir, entry.name, 'aerix_instance.json');
      if (!fs.existsSync(metaPath)) continue;
      const item = readJsonSafe<InstanceConfig | null>(metaPath, null);
      if (item && item.id) {
        list.push(item);
      }
    }
    return list.sort((a, b) => (b.lastPlayedAt ?? b.createdAt) - (a.lastPlayedAt ?? a.createdAt));
  }

  getInstance(instanceId: string): InstanceConfig | null {
    const metaPath = this.getInstanceMetaFile(instanceId);
    if (!fs.existsSync(metaPath)) return null;
    return readJsonSafe<InstanceConfig | null>(metaPath, null);
  }

  createInstance(input: {
    name: string;
    mcVersion: string;
    loader: ModLoaderType;
    loaderVersion?: string;
    aerixClient?: boolean;
    ramMb?: number;
    icon?: string;
  }): InstanceConfig {
    const baseSlug = sanitizeSlug(input.name, 'instance');
    let id = baseSlug;
    let counter = 2;
    while (fs.existsSync(this.getInstanceDir(id))) {
      id = `${baseSlug}-${counter++}`;
    }

    const instanceDir = this.getInstanceDir(id);
    for (const sub of ['mods', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots', 'config', 'logs']) {
      fs.mkdirSync(path.join(instanceDir, sub), { recursive: true });
    }

    const config: InstanceConfig = {
      id,
      name: input.name.trim() || id,
      mcVersion: input.mcVersion.trim(),
      loader: input.aerixClient ? 'fabric' : input.loader,
      loaderVersion: input.loaderVersion?.trim() || undefined,
      aerixClient: Boolean(input.aerixClient),
      icon: input.icon,
      ramMb: input.ramMb,
      createdAt: Date.now(),
      playTimeSec: 0,
    };

    writeJsonAtomic(this.getInstanceMetaFile(id), config);
    const settings = this.getSettings();
    if (!settings.selectedInstanceId) {
      this.saveSettings({ selectedInstanceId: id });
    }
    return config;
  }

  updateInstance(instanceId: string, patch: Partial<InstanceConfig>): InstanceConfig {
    const existing = this.getInstance(instanceId);
    if (!existing) {
      throw new Error(`Instance not found: ${instanceId}`);
    }
    const updated: InstanceConfig = {
      ...existing,
      ...patch,
      id: existing.id,
      createdAt: existing.createdAt,
    };
    writeJsonAtomic(this.getInstanceMetaFile(instanceId), updated);
    return updated;
  }

  deleteInstance(instanceId: string): void {
    const dir = this.getInstanceDir(instanceId);
    if (fs.existsSync(dir)) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
    const settings = this.getSettings();
    if (settings.selectedInstanceId === instanceId) {
      const remaining = this.listInstances();
      this.saveSettings({ selectedInstanceId: remaining[0]?.id ?? null });
    }
  }
}
