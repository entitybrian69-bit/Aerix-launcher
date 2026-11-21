import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export interface AerixPaths {
  root: string;
  accountsFile: string;
  settingsFile: string;
  instancesDir: string;
  assetsDir: string;
  librariesDir: string;
  versionsDir: string;
  runtimesDir: string;
  skinsDir: string;
  logsDir: string;
  cacheDir: string;
}

export function getDefaultDataRoot(
  platform: NodeJS.Platform = process.platform,
  env: NodeJS.ProcessEnv = process.env,
  homeDir: string = os.homedir()
): string {
  const override = env.AERIX_PC_HOME?.trim();
  if (override) {
    return path.resolve(override);
  }
  if (platform === 'win32') {
    const appData = env.APPDATA?.trim() || path.join(homeDir, 'AppData', 'Roaming');
    return path.join(appData, 'AerixPC');
  }
  if (platform === 'darwin') {
    return path.join(homeDir, 'Library', 'Application Support', 'AerixPC');
  }
  const xdg = env.XDG_DATA_HOME?.trim() || path.join(homeDir, '.local', 'share');
  return path.join(xdg, 'AerixPC');
}

export function resolveAerixPaths(rootOverride?: string): AerixPaths {
  const root = rootOverride ? path.resolve(rootOverride) : getDefaultDataRoot();
  return {
    root,
    accountsFile: path.join(root, 'accounts.json'),
    settingsFile: path.join(root, 'settings.json'),
    instancesDir: path.join(root, 'instances'),
    assetsDir: path.join(root, 'assets'),
    librariesDir: path.join(root, 'libraries'),
    versionsDir: path.join(root, 'versions'),
    runtimesDir: path.join(root, 'runtimes'),
    skinsDir: path.join(root, 'skins'),
    logsDir: path.join(root, 'logs'),
    cacheDir: path.join(root, 'cache'),
  };
}

export function ensureAerixDirs(paths: AerixPaths): void {
  for (const dir of [
    paths.root,
    paths.instancesDir,
    paths.assetsDir,
    paths.librariesDir,
    paths.versionsDir,
    paths.runtimesDir,
    paths.skinsDir,
    paths.logsDir,
    paths.cacheDir,
  ]) {
    fs.mkdirSync(dir, { recursive: true });
  }
}

export function sanitizeSlug(raw: string, fallback = 'instance'): string {
  const cleaned = raw
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return cleaned || fallback;
}

export function sanitizeFileName(raw: string, fallback = 'file'): string {
  const base = path.basename(raw).replace(/[\\/:*?"<>|\u0000-\u001f]+/g, '_').trim();
  return base && base !== '.' && base !== '..' ? base : fallback;
}

export function getBundledAssetPath(relativePath: string): string {
  const resourcesPath = (process as NodeJS.Process & { resourcesPath?: string }).resourcesPath;
  const candidates = [
    typeof resourcesPath === 'string'
      ? path.join(resourcesPath, 'assets', relativePath)
      : '',
    path.resolve(__dirname, '../../assets', relativePath),
    path.resolve(__dirname, '../../../assets', relativePath),
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return candidates[0];
}
