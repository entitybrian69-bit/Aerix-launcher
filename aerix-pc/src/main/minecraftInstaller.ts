import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { promisify } from 'node:util';
import { downloadFile, fetchJson, runWithConcurrency } from './http';
import { ensureJavaForVersion } from './javaManager';
import { AerixPaths } from './paths';
import { LaunchProgressEvent, MinecraftVersionSummary, ModLoaderType } from './types';
import { extractZipFile } from './zip';

const execFileAsync = promisify(execFile);

export const MOJANG_MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';

export interface RuleEntry {
  action: 'allow' | 'disallow';
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
}

export interface MojangLibrary {
  name: string;
  url?: string;
  rules?: RuleEntry[];
  natives?: Record<string, string>;
  extract?: { exclude?: string[] };
  downloads?: {
    artifact?: { path: string; sha1?: string; size?: number; url: string };
    classifiers?: Record<string, { path: string; sha1?: string; size?: number; url: string }>;
  };
}

export interface VersionJson {
  id: string;
  inheritsFrom?: string;
  mainClass: string;
  type?: string;
  minecraftArguments?: string;
  arguments?: {
    game?: Array<string | { rules?: RuleEntry[]; value: string | string[] }>;
    jvm?: Array<string | { rules?: RuleEntry[]; value: string | string[] }>;
  };
  assetIndex?: { id: string; sha1?: string; size?: number; url: string };
  assets?: string;
  javaVersion?: { component?: string; majorVersion: number };
  downloads?: {
    client?: { sha1?: string; size?: number; url: string };
  };
  libraries: MojangLibrary[];
  logging?: {
    client?: {
      argument: string;
      file: { id: string; sha1?: string; url: string };
    };
  };
}

export function getMojangOsName(platform: NodeJS.Platform = process.platform): 'windows' | 'osx' | 'linux' {
  if (platform === 'win32') return 'windows';
  if (platform === 'darwin') return 'osx';
  return 'linux';
}

export function evaluateMojangRules(
  rules: RuleEntry[] | undefined,
  platform: NodeJS.Platform = process.platform,
  arch: string = process.arch,
  features: Record<string, boolean> = {}
): boolean {
  if (!rules || rules.length === 0) return true;
  const currentOs = getMojangOsName(platform);
  const currentArch: 'arm64' | 'x86_64' | 'x86' =
    arch === 'arm64' ? 'arm64' : arch === 'ia32' ? 'x86' : 'x86_64';
  let allowed = false;

  for (const rule of rules) {
    let matches = true;
    if (rule.os) {
      if (rule.os.name && rule.os.name !== currentOs) matches = false;
      if (rule.os.arch) {
        const expected = rule.os.arch.toLowerCase();
        if (expected === 'x86' && currentArch !== 'x86') matches = false;
        if ((expected === 'arm64' || expected === 'aarch64') && currentArch !== 'arm64') matches = false;
      }
    }
    if (rule.features) {
      for (const [k, v] of Object.entries(rule.features)) {
        if (Boolean(features[k]) !== Boolean(v)) {
          matches = false;
          break;
        }
      }
    }
    if (matches) {
      allowed = rule.action === 'allow';
    }
  }
  return allowed;
}

export function mavenCoordinateToPath(coord: string): string {
  const [withoutExt, explicitExt] = coord.split('@');
  const ext = explicitExt || 'jar';
  const parts = withoutExt.split(':');
  const group = parts[0].replace(/\./g, '/');
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3] ? `-${parts[3]}` : '';
  return `${group}/${artifact}/${version}/${artifact}-${version}${classifier}.${ext}`;
}

export function mavenCoordinateToKey(coord: string): string {
  const [withoutExt] = coord.split('@');
  const parts = withoutExt.split(':');
  return `${parts[0]}:${parts[1]}${parts[3] ? `:${parts[3]}` : ''}`;
}

export async function fetchMinecraftVersions(includeSnapshots = false): Promise<MinecraftVersionSummary[]> {
  const data = await fetchJson<{ versions: MinecraftVersionSummary[] }>(MOJANG_MANIFEST_URL);
  return includeSnapshots
    ? data.versions
    : data.versions.filter((v) => v.type === 'release');
}

export async function fetchLoaderVersions(loader: ModLoaderType, mcVersion: string): Promise<string[]> {
  if (loader === 'vanilla') return [];
  if (loader === 'fabric') {
    const rows = await fetchJson<Array<{ loader: { version: string; stable?: boolean } }>>(
      `https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(mcVersion)}`
    );
    return rows.map((r) => r.loader.version);
  }
  if (loader === 'quilt') {
    const rows = await fetchJson<Array<{ loader: { version: string } }>>(
      `https://meta.quiltmc.org/v3/versions/loader/${encodeURIComponent(mcVersion)}`
    );
    return rows.map((r) => r.loader.version);
  }
  if (loader === 'neoforge') {
    const data = await fetchJson<{ versions: string[] }>(
      'https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge'
    );
    const parts = mcVersion.split('.');
    if (parts[0] === '1' && parts.length >= 2) {
      const prefix = `${parts[1]}.${parts[2] || '0'}.`;
      return data.versions.filter((v) => v.startsWith(prefix)).reverse();
    }
    return data.versions.slice(-20).reverse();
  }
  if (loader === 'forge') {
    const promo = await fetchJson<{ promos: Record<string, string> }>(
      'https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json'
    );
    const out: string[] = [];
    const rec = promo.promos[`${mcVersion}-recommended`];
    const latest = promo.promos[`${mcVersion}-latest`];
    if (rec) out.push(rec);
    if (latest && latest !== rec) out.push(latest);
    return out;
  }
  return [];
}

function readLocalVersionJson(paths: AerixPaths, versionId: string): VersionJson | null {
  const filePath = path.join(paths.versionsDir, versionId, `${versionId}.json`);
  if (!fs.existsSync(filePath)) return null;
  return JSON.parse(fs.readFileSync(filePath, 'utf8')) as VersionJson;
}

function writeLocalVersionJson(paths: AerixPaths, versionId: string, json: VersionJson): void {
  const dir = path.join(paths.versionsDir, versionId);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, `${versionId}.json`), JSON.stringify(json, null, 2), 'utf8');
}

async function ensureVanillaVersionJson(paths: AerixPaths, mcVersion: string): Promise<VersionJson> {
  const cached = readLocalVersionJson(paths, mcVersion);
  if (cached) return cached;

  const manifest = await fetchJson<{ versions: MinecraftVersionSummary[] }>(MOJANG_MANIFEST_URL);
  const entry = manifest.versions.find((v) => v.id === mcVersion);
  if (!entry) {
    throw new Error(`Minecraft version "${mcVersion}" was not found in Mojang manifest.`);
  }
  const versionJson = await fetchJson<VersionJson>(entry.url);
  writeLocalVersionJson(paths, mcVersion, versionJson);
  return versionJson;
}

function ensureLauncherProfilesStub(paths: AerixPaths): void {
  const profilesPath = path.join(paths.root, 'launcher_profiles.json');
  if (!fs.existsSync(profilesPath)) {
    fs.writeFileSync(
      profilesPath,
      JSON.stringify({ profiles: {}, settings: {}, version: 3 }, null, 2),
      'utf8'
    );
  }
}

export interface ResolvedLaunchVersion {
  versionId: string;
  mainClass: string;
  javaMajor: number;
  clientJarPath: string;
  nativesDir: string;
  libraries: string[];
  assetIndexId: string;
  minecraftArguments?: string;
  gameArgs: Array<string | { rules?: RuleEntry[]; value: string | string[] }>;
  jvmArgs: Array<string | { rules?: RuleEntry[]; value: string | string[] }>;
  loggingArg?: string;
}

function mergeVersionChain(child: VersionJson, parent?: VersionJson): VersionJson {
  if (!parent) return child;
  const childLibKeys = new Set(child.libraries.map((l) => mavenCoordinateToKey(l.name)));
  const mergedLibs = [
    ...child.libraries,
    ...parent.libraries.filter((l) => !childLibKeys.has(mavenCoordinateToKey(l.name))),
  ];
  return {
    ...parent,
    ...child,
    mainClass: child.mainClass || parent.mainClass,
    javaVersion: child.javaVersion ?? parent.javaVersion,
    assetIndex: child.assetIndex ?? parent.assetIndex,
    assets: child.assets ?? parent.assets,
    downloads: child.downloads ?? parent.downloads,
    logging: child.logging ?? parent.logging,
    libraries: mergedLibs,
    minecraftArguments: child.minecraftArguments ?? parent.minecraftArguments,
    arguments: {
      game: [...(parent.arguments?.game ?? []), ...(child.arguments?.game ?? [])],
      jvm: [...(parent.arguments?.jvm ?? []), ...(child.arguments?.jvm ?? [])],
    },
  };
}

export async function prepareGameVersion(
  paths: AerixPaths,
  options: {
    mcVersion: string;
    loader: ModLoaderType;
    loaderVersion?: string;
    instanceId: string;
    onProgress?: (ev: LaunchProgressEvent) => void;
  }
): Promise<ResolvedLaunchVersion> {
  const report = (stage: LaunchProgressEvent['stage'], message: string, percent: number) =>
    options.onProgress?.({ stage, message, percent, instanceId: options.instanceId });

  report('minecraft', `Resolving Minecraft ${options.mcVersion}...`, 10);
  const vanillaJson = await ensureVanillaVersionJson(paths, options.mcVersion);

  let targetVersionId = options.mcVersion;
  let activeJson: VersionJson = vanillaJson;

  if (options.loader === 'fabric' || options.loader === 'quilt') {
    const loaderVer =
      options.loaderVersion ||
      (await fetchLoaderVersions(options.loader, options.mcVersion))[0];
    if (!loaderVer) {
      throw new Error(`No ${options.loader} build available for Minecraft ${options.mcVersion}.`);
    }
    report('loader', `Resolving ${options.loader} ${loaderVer}...`, 18);
    targetVersionId = `${options.loader}-loader-${loaderVer}-${options.mcVersion}`;
    let loaderJson = readLocalVersionJson(paths, targetVersionId);
    if (!loaderJson) {
      const profileUrl =
        options.loader === 'fabric'
          ? `https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(options.mcVersion)}/${encodeURIComponent(loaderVer)}/profile/json`
          : `https://meta.quiltmc.org/v3/versions/loader/${encodeURIComponent(options.mcVersion)}/${encodeURIComponent(loaderVer)}/profile/json`;
      loaderJson = await fetchJson<VersionJson>(profileUrl);
      loaderJson.id = targetVersionId;
      writeLocalVersionJson(paths, targetVersionId, loaderJson);
    }
    activeJson = mergeVersionChain(loaderJson, vanillaJson);
  } else if (options.loader === 'forge' || options.loader === 'neoforge') {
    const loaderVer =
      options.loaderVersion ||
      (await fetchLoaderVersions(options.loader, options.mcVersion))[0];
    if (!loaderVer) {
      throw new Error(`No ${options.loader} build available for Minecraft ${options.mcVersion}.`);
    }

    const expectedPrefix =
      options.loader === 'neoforge'
        ? `neoforge-${loaderVer}`
        : `${options.mcVersion}-forge-${loaderVer}`;
    let installedJson = readLocalVersionJson(paths, expectedPrefix);

    if (!installedJson) {
      report('loader', `Downloading ${options.loader} ${loaderVer} installer...`, 20);
      ensureLauncherProfilesStub(paths);
      const installerUrl =
        options.loader === 'neoforge'
          ? `https://maven.neoforged.net/releases/net/neoforged/neoforge/${loaderVer}/neoforge-${loaderVer}-installer.jar`
          : `https://maven.minecraftforge.net/net/minecraftforge/forge/${options.mcVersion}-${loaderVer}/forge-${options.mcVersion}-${loaderVer}-installer.jar`;
      const installerPath = path.join(paths.cacheDir, `${expectedPrefix}-installer.jar`);
      await downloadFile(installerUrl, installerPath);

      const java = await ensureJavaForVersion(
        paths,
        vanillaJson.javaVersion?.majorVersion ?? 17,
        undefined,
        (msg) => report('java', msg, 24)
      );
      report('loader', `Running ${options.loader} headless installer...`, 28);
      await execFileAsync(
        java.executablePath,
        ['-jar', installerPath, '--installClient', paths.root],
        { timeout: 300000 }
      );
      installedJson = readLocalVersionJson(paths, expectedPrefix);
      if (!installedJson) {
        const candidates = fs
          .readdirSync(paths.versionsDir)
          .filter((name) => name.includes(loaderVer));
        if (candidates.length > 0) {
          installedJson = readLocalVersionJson(paths, candidates[0]);
        }
      }
      if (!installedJson) {
        throw new Error(`${options.loader} installer finished but no version profile JSON was created.`);
      }
    }
    targetVersionId = installedJson.id;
    activeJson = mergeVersionChain(installedJson, vanillaJson);
  }

  // Ensure vanilla client JAR
  const clientJarPath = path.join(paths.versionsDir, options.mcVersion, `${options.mcVersion}.jar`);
  if (vanillaJson.downloads?.client?.url) {
    report('minecraft', `Downloading Minecraft ${options.mcVersion} client...`, 32);
    await downloadFile(vanillaJson.downloads.client.url, clientJarPath, {
      expectedSha1: vanillaJson.downloads.client.sha1,
    });
  }

  // Libraries & natives
  const nativesDir = path.join(paths.versionsDir, targetVersionId, 'natives');
  fs.mkdirSync(nativesDir, { recursive: true });
  const mojangOs = getMojangOsName();
  const classpathLibs: string[] = [];
  const libDownloads: Array<{ url: string; dest: string; sha1?: string; extractNatives?: boolean }> = [];

  for (const lib of activeJson.libraries) {
    if (!evaluateMojangRules(lib.rules)) continue;

    if (lib.downloads?.artifact) {
      const dest = path.join(paths.librariesDir, lib.downloads.artifact.path);
      classpathLibs.push(dest);
      libDownloads.push({
        url: lib.downloads.artifact.url,
        dest,
        sha1: lib.downloads.artifact.sha1,
      });
    } else if (!lib.natives) {
      const rel = mavenCoordinateToPath(lib.name);
      const base = (lib.url || 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
      const dest = path.join(paths.librariesDir, rel);
      classpathLibs.push(dest);
      if (!fs.existsSync(dest)) {
        libDownloads.push({ url: `${base}${rel}`, dest });
      }
    }

    if (lib.natives && lib.natives[mojangOs] && lib.downloads?.classifiers) {
      const archToken = process.arch === 'arm64' ? 'arm64' : '64';
      const classifierKey = lib.natives[mojangOs].replace('${arch}', archToken);
      const classifier = lib.downloads.classifiers[classifierKey];
      if (classifier) {
        const nativeJarPath = path.join(paths.librariesDir, classifier.path);
        libDownloads.push({
          url: classifier.url,
          dest: nativeJarPath,
          sha1: classifier.sha1,
          extractNatives: true,
        });
      }
    }
  }

  let completedLibs = 0;
  await runWithConcurrency(libDownloads, 8, async (item) => {
    await downloadFile(item.url, item.dest, { expectedSha1: item.sha1 });
    if (item.extractNatives) {
      extractZipFile(item.dest, nativesDir, (entryName) => !entryName.startsWith('META-INF/'));
    }
    completedLibs++;
    const pct = 35 + Math.round((completedLibs / Math.max(1, libDownloads.length)) * 25);
    report('minecraft', `Verifying libraries (${completedLibs}/${libDownloads.length})...`, pct);
  });

  // Assets
  const assetIndexId = activeJson.assetIndex?.id || activeJson.assets || options.mcVersion;
  if (activeJson.assetIndex) {
    const indexPath = path.join(paths.assetsDir, 'indexes', `${assetIndexId}.json`);
    await downloadFile(activeJson.assetIndex.url, indexPath, {
      expectedSha1: activeJson.assetIndex.sha1,
    });
    const indexData = JSON.parse(fs.readFileSync(indexPath, 'utf8')) as {
      objects: Record<string, { hash: string; size: number }>;
    };
    const entries = Object.values(indexData.objects || {});
    let doneAssets = 0;
    await runWithConcurrency(entries, 12, async (obj) => {
      const prefix = obj.hash.slice(0, 2);
      const dest = path.join(paths.assetsDir, 'objects', prefix, obj.hash);
      if (!fs.existsSync(dest)) {
        await downloadFile(`https://resources.download.minecraft.net/${prefix}/${obj.hash}`, dest, {
          expectedSha1: obj.hash,
        });
      }
      doneAssets++;
      if (doneAssets % 40 === 0 || doneAssets === entries.length) {
        const pct = 60 + Math.round((doneAssets / Math.max(1, entries.length)) * 30);
        report('assets', `Syncing game assets (${doneAssets}/${entries.length})...`, pct);
      }
    });
  }

  // Optional log4j2 XML config
  let loggingArg: string | undefined;
  if (activeJson.logging?.client?.file?.url) {
    const logCfgPath = path.join(paths.assetsDir, 'log_configs', activeJson.logging.client.file.id);
    await downloadFile(activeJson.logging.client.file.url, logCfgPath, {
      expectedSha1: activeJson.logging.client.file.sha1,
    });
    loggingArg = activeJson.logging.client.argument.replace('${path}', logCfgPath);
  }

  return {
    versionId: targetVersionId,
    mainClass: activeJson.mainClass,
    javaMajor: activeJson.javaVersion?.majorVersion ?? 21,
    clientJarPath,
    nativesDir,
    libraries: Array.from(new Set(classpathLibs)),
    assetIndexId,
    minecraftArguments: activeJson.minecraftArguments,
    gameArgs: activeJson.arguments?.game ?? [],
    jvmArgs: activeJson.arguments?.jvm ?? [],
    loggingArg,
  };
}
