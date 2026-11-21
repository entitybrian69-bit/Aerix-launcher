import { fetchJson } from './http';
import { UpdateCheckResult } from './types';

interface GitHubReleaseApi {
  tag_name: string;
  html_url: string;
  body?: string;
  assets?: Array<{
    name: string;
    browser_download_url: string;
  }>;
}

export function normalizeVersionTag(raw: string): string {
  return raw.trim().replace(/^pc-v/i, '').replace(/^v/i, '');
}

export function compareSemver(a: string, b: string): number {
  const pa = normalizeVersionTag(a).split('.').map((n) => parseInt(n, 10) || 0);
  const pb = normalizeVersionTag(b).split('.').map((n) => parseInt(n, 10) || 0);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const da = pa[i] ?? 0;
    const db = pb[i] ?? 0;
    if (da > db) return 1;
    if (da < db) return -1;
  }
  return 0;
}

export async function checkForLauncherUpdate(currentVersion: string): Promise<UpdateCheckResult> {
  const release = await fetchJson<GitHubReleaseApi>(
    'https://api.github.com/repos/entitybrian69-bit/Aerix-launcher/releases/latest'
  );
  const latestVersion = normalizeVersionTag(release.tag_name);
  const updateAvailable = compareSemver(latestVersion, currentVersion) > 0;
  const assets = release.assets || [];

  let matcher: (name: string) => boolean;
  if (process.platform === 'win32') {
    matcher = (n) => /aerix-pc.*\.exe$/i.test(n);
  } else if (process.platform === 'darwin') {
    matcher = (n) =>
      process.arch === 'arm64'
        ? /aerix-pc.*arm64.*\.dmg$/i.test(n)
        : /aerix-pc.*x64.*\.dmg$/i.test(n);
  } else {
    matcher = (n) => /aerix-pc.*\.appimage$/i.test(n);
  }

  const matched = assets.find((a) => matcher(a.name));
  return {
    currentVersion,
    latestVersion,
    updateAvailable,
    releaseUrl: release.html_url,
    releaseNotes: release.body || '',
    matchingAssetUrl: matched?.browser_download_url,
    matchingAssetName: matched?.name,
  };
}
