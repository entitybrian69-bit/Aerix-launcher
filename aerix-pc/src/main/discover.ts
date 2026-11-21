import fs from 'node:fs';
import path from 'node:path';
import { setInstanceResourcePackEnabled } from './contentManager';
import { downloadFile, fetchJson, runWithConcurrency } from './http';
import { sanitizeFileName } from './paths';
import { LauncherStore } from './storage';
import {
  ContentKind,
  DiscoverPlatform,
  DiscoverProject,
  DiscoverProjectType,
  DiscoverSearchParams,
  DiscoverVersion,
  InstanceConfig,
  ModLoaderType,
} from './types';
import { extractZipFile, normalizeResourcePackZipBuffer } from './zip';

interface ModrinthSearchResponse {
  hits: Array<{
    project_id: string;
    slug: string;
    project_type: string;
    title: string;
    description: string;
    author: string;
    icon_url?: string;
    downloads: number;
    categories: string[];
    versions: string[];
  }>;
}

interface ModrinthVersionApi {
  id: string;
  name: string;
  version_number: string;
  game_versions: string[];
  loaders: string[];
  date_published: string;
  downloads: number;
  files: Array<{
    url: string;
    filename: string;
    size: number;
    primary: boolean;
    hashes?: { sha1?: string };
  }>;
}

interface CurseForgeSearchResponse {
  data: Array<{
    id: number;
    slug: string;
    classId?: number;
    name: string;
    summary: string;
    authors?: Array<{ name: string }>;
    logo?: { thumbnailUrl?: string; url?: string };
    downloadCount: number;
    categories?: Array<{ name: string }>;
    latestFilesIndexes?: Array<{ gameVersion: string; modLoader?: number }>;
    links?: { websiteUrl?: string };
  }>;
}

interface CurseForgeFilesResponse {
  data: Array<{
    id: number;
    displayName: string;
    fileName: string;
    fileDate: string;
    fileLength: number;
    downloadCount: number;
    downloadUrl?: string;
    gameVersions: string[];
    hashes?: Array<{ algo: number; value: string }>;
  }>;
}

const CF_MOD_LOADERS: Record<number, string> = {
  1: 'forge',
  4: 'fabric',
  5: 'quilt',
  6: 'neoforge',
};

const CF_CLASS_ID: Record<DiscoverProjectType, number> = {
  mod: 6,
  modpack: 4471,
  resourcepack: 12,
  shader: 6552,
};

const CF_MODLOADER_ID: Partial<Record<ModLoaderType, number>> = {
  forge: 1,
  fabric: 4,
  quilt: 5,
  neoforge: 6,
};

function getCurseForgeBase(apiKey?: string): { baseUrl: string; headers: Record<string, string> } {
  if (apiKey?.trim()) {
    return {
      baseUrl: 'https://api.curseforge.com/v1',
      headers: { 'x-api-key': apiKey.trim() },
    };
  }
  return {
    baseUrl: 'https://mod.mcimirror.top/curseforge/v1',
    headers: {},
  };
}

export async function searchDiscoverProjects(
  params: DiscoverSearchParams,
  curseforgeApiKey?: string
): Promise<DiscoverProject[]> {
  const limit = params.limit ?? 24;
  const offset = params.offset ?? 0;

  if (params.platform === 'modrinth') {
    const facets: string[][] = [[`project_type:${params.projectType}`]];
    if (params.mcVersion) {
      facets.push([`versions:${params.mcVersion}`]);
    }
    if (params.loader && params.loader !== 'vanilla' && (params.projectType === 'mod' || params.projectType === 'modpack')) {
      facets.push([`categories:${params.loader}`]);
    }
    const indexMap = {
      relevance: 'relevance',
      downloads: 'downloads',
      updated: 'updated',
      newest: 'newest',
    } as const;
    const qs = new URLSearchParams({
      query: params.query || '',
      facets: JSON.stringify(facets),
      index: indexMap[params.sort || 'downloads'],
      limit: String(limit),
      offset: String(offset),
    });
    const res = await fetchJson<ModrinthSearchResponse>(`https://api.modrinth.com/v2/search?${qs.toString()}`);
    return res.hits.map((h) => ({
      id: h.project_id,
      slug: h.slug,
      platform: 'modrinth',
      projectType: params.projectType,
      title: h.title,
      description: h.description,
      author: h.author,
      iconUrl: h.icon_url,
      downloads: h.downloads,
      categories: h.categories || [],
      mcVersions: h.versions || [],
      loaders: [],
      webUrl: `https://modrinth.com/${params.projectType}/${h.slug}`,
    }));
  }

  // CurseForge
  const { baseUrl, headers } = getCurseForgeBase(curseforgeApiKey);
  const sortField = params.sort === 'updated' ? '3' : params.sort === 'downloads' ? '6' : '2';
  const qs = new URLSearchParams({
    gameId: '432',
    classId: String(CF_CLASS_ID[params.projectType]),
    searchFilter: params.query || '',
    sortField,
    sortOrder: 'desc',
    pageSize: String(limit),
    index: String(offset),
  });
  if (params.mcVersion) {
    qs.set('gameVersion', params.mcVersion);
  }
  if (params.loader && CF_MODLOADER_ID[params.loader] && (params.projectType === 'mod' || params.projectType === 'modpack')) {
    qs.set('modLoaderType', String(CF_MODLOADER_ID[params.loader]));
  }
  const res = await fetchJson<CurseForgeSearchResponse>(`${baseUrl}/mods/search?${qs.toString()}`, { headers });
  return (res.data || []).map((m) => ({
    id: String(m.id),
    slug: m.slug,
    platform: 'curseforge',
    projectType: params.projectType,
    title: m.name,
    description: m.summary,
    author: m.authors?.[0]?.name || 'Unknown',
    iconUrl: m.logo?.thumbnailUrl || m.logo?.url,
    downloads: m.downloadCount || 0,
    categories: (m.categories || []).map((c) => c.name),
    mcVersions: Array.from(new Set((m.latestFilesIndexes || []).map((f) => f.gameVersion).filter(Boolean))),
    loaders: Array.from(
      new Set(
        (m.latestFilesIndexes || [])
          .map((f) => (f.modLoader ? CF_MOD_LOADERS[f.modLoader] : undefined))
          .filter((v): v is string => Boolean(v))
      )
    ),
    webUrl: m.links?.websiteUrl || `https://www.curseforge.com/minecraft/mc-mods/${m.slug}`,
  }));
}

export async function fetchDiscoverVersions(
  platform: DiscoverPlatform,
  projectId: string,
  options: { mcVersion?: string; loader?: ModLoaderType; curseforgeApiKey?: string } = {}
): Promise<DiscoverVersion[]> {
  if (platform === 'modrinth') {
    const qs = new URLSearchParams();
    if (options.mcVersion) {
      qs.set('game_versions', JSON.stringify([options.mcVersion]));
    }
    if (options.loader && options.loader !== 'vanilla') {
      qs.set('loaders', JSON.stringify([options.loader]));
    }
    const suffix = qs.toString() ? `?${qs.toString()}` : '';
    const rows = await fetchJson<ModrinthVersionApi[]>(
      `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version${suffix}`
    );
    return rows.slice(0, 40).map((v) => ({
      id: v.id,
      name: v.name,
      versionNumber: v.version_number,
      gameVersions: v.game_versions || [],
      loaders: v.loaders || [],
      publishedAt: v.date_published,
      downloads: v.downloads,
      files: v.files.map((f) => ({
        url: f.url,
        filename: f.filename,
        size: f.size,
        sha1: f.hashes?.sha1,
        primary: f.primary,
      })),
    }));
  }

  const { baseUrl, headers } = getCurseForgeBase(options.curseforgeApiKey);
  const qs = new URLSearchParams({ pageSize: '30' });
  if (options.mcVersion) qs.set('gameVersion', options.mcVersion);
  if (options.loader && CF_MODLOADER_ID[options.loader]) {
    qs.set('modLoaderType', String(CF_MODLOADER_ID[options.loader]));
  }
  const res = await fetchJson<CurseForgeFilesResponse>(
    `${baseUrl}/mods/${encodeURIComponent(projectId)}/files?${qs.toString()}`,
    { headers }
  );
  return (res.data || [])
    .filter((f) => Boolean(f.downloadUrl))
    .map((f) => ({
      id: String(f.id),
      name: f.displayName,
      versionNumber: f.fileName,
      gameVersions: f.gameVersions || [],
      loaders: [],
      publishedAt: f.fileDate,
      downloads: f.downloadCount || 0,
      files: [
        {
          url: f.downloadUrl as string,
          filename: f.fileName,
          size: f.fileLength,
          sha1: f.hashes?.find((h) => h.algo === 1)?.value,
          primary: true,
        },
      ],
    }));
}

interface MrpackIndex {
  name: string;
  versionId?: string;
  dependencies: Record<string, string>;
  files: Array<{
    path: string;
    downloads: string[];
    hashes?: { sha1?: string };
    env?: { client?: string };
  }>;
}

function inferLoaderFromMrpack(deps: Record<string, string>): { loader: ModLoaderType; loaderVersion?: string } {
  if (deps['fabric-loader']) return { loader: 'fabric', loaderVersion: deps['fabric-loader'] };
  if (deps['quilt-loader']) return { loader: 'quilt', loaderVersion: deps['quilt-loader'] };
  if (deps.neoforge) return { loader: 'neoforge', loaderVersion: deps.neoforge };
  if (deps.forge) return { loader: 'forge', loaderVersion: deps.forge };
  return { loader: 'vanilla' };
}

export async function installDiscoverFile(
  store: LauncherStore,
  params: {
    projectTitle: string;
    projectType: DiscoverProjectType;
    version: DiscoverVersion;
    instanceId?: string;
  }
): Promise<{ installedPath: string; createdInstance?: InstanceConfig }> {
  const file = params.version.files.find((f) => f.primary) ?? params.version.files[0];
  if (!file) {
    throw new Error('Selected version has no downloadable file.');
  }

  // Modrinth .mrpack creates a fresh managed instance!
  if (params.projectType === 'modpack' && file.filename.toLowerCase().endsWith('.mrpack')) {
    const tempPack = path.join(store.paths.cacheDir, sanitizeFileName(file.filename));
    await downloadFile(file.url, tempPack, { expectedSha1: file.sha1 });
    const tempExtract = path.join(store.paths.cacheDir, `mrpack-${Date.now()}`);
    extractZipFile(tempPack, tempExtract);
    const indexPath = path.join(tempExtract, 'modrinth.index.json');
    if (!fs.existsSync(indexPath)) {
      throw new Error('Invalid .mrpack archive: missing modrinth.index.json');
    }
    const index = JSON.parse(fs.readFileSync(indexPath, 'utf8')) as MrpackIndex;
    const mcVersion = index.dependencies?.minecraft || params.version.gameVersions[0] || '1.21.1';
    const { loader, loaderVersion } = inferLoaderFromMrpack(index.dependencies || {});
    const instance = store.createInstance({
      name: index.name || params.projectTitle,
      mcVersion,
      loader,
      loaderVersion,
    });
    const instDir = store.getInstanceDir(instance.id);

    const files = (index.files || []).filter((f) => f.env?.client !== 'unsupported' && f.downloads?.length);
    await runWithConcurrency(files, 6, async (entry) => {
      const target = path.resolve(instDir, entry.path);
      if (!target.startsWith(path.resolve(instDir) + path.sep)) return;
      await downloadFile(entry.downloads[0], target, { expectedSha1: entry.hashes?.sha1 });
    });

    for (const overrideName of ['overrides', 'client-overrides']) {
      const overrideDir = path.join(tempExtract, overrideName);
      if (fs.existsSync(overrideDir)) {
        fs.cpSync(overrideDir, instDir, { recursive: true, force: true });
      }
    }

    fs.rmSync(tempExtract, { recursive: true, force: true });
    store.saveSettings({ selectedInstanceId: instance.id });
    return { installedPath: instDir, createdInstance: instance };
  }

  if (!params.instanceId) {
    throw new Error('Please choose a target instance first.');
  }
  const instDir = store.getInstanceDir(params.instanceId);
  if (!fs.existsSync(instDir)) {
    throw new Error(`Target instance not found: ${params.instanceId}`);
  }

  const kindMap: Record<DiscoverProjectType, ContentKind> = {
    mod: 'mods',
    modpack: 'mods',
    resourcepack: 'resourcepacks',
    shader: 'shaderpacks',
  };
  const kind = kindMap[params.projectType];
  const fileName = sanitizeFileName(file.filename);
  const destPath = path.join(instDir, kind, fileName);

  await downloadFile(file.url, destPath, { expectedSha1: file.sha1 });

  if (kind === 'resourcepacks' && fileName.toLowerCase().endsWith('.zip')) {
    const buf = fs.readFileSync(destPath);
    const normalized = normalizeResourcePackZipBuffer(buf);
    if (normalized.changed) {
      fs.writeFileSync(destPath, normalized.buffer);
    }
    setInstanceResourcePackEnabled(instDir, fileName, true);
  }

  return { installedPath: destPath };
}
