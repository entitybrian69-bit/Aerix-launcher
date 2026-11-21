import { execFile } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { downloadFile } from './http';
import { AerixPaths } from './paths';
import { JavaRuntimeInfo } from './types';
import { extractZipFile } from './zip';

const execFileAsync = promisify(execFile);

export function parseJavaMajorVersion(versionOutput: string): { major: number; versionString: string } | null {
  const match = versionOutput.match(/version\s+"([^"]+)"/);
  if (!match) return null;
  const raw = match[1];
  const parts = raw.split(/[._+-]/);
  const first = Number(parts[0]);
  if (!Number.isFinite(first)) return null;
  if (first === 1 && parts.length > 1) {
    const second = Number(parts[1]);
    if (Number.isFinite(second)) {
      return { major: second, versionString: raw };
    }
  }
  return { major: first, versionString: raw };
}

export async function probeJavaExecutable(exePath: string, source: 'managed' | 'system'): Promise<JavaRuntimeInfo | null> {
  try {
    if (!fs.existsSync(exePath)) return null;
    const { stdout, stderr } = await execFileAsync(exePath, ['-version'], { timeout: 8000 });
    const combined = `${stdout}\n${stderr}`;
    const parsed = parseJavaMajorVersion(combined);
    if (!parsed) return null;
    const arch = /64-Bit|aarch64|arm64/i.test(combined)
      ? /aarch64|arm64/i.test(combined) ? 'arm64' : 'x64'
      : process.arch;
    return {
      majorVersion: parsed.major,
      versionString: parsed.versionString,
      executablePath: exePath,
      source,
      arch,
    };
  } catch {
    return null;
  }
}

function getJavaBinaryName(): string {
  return process.platform === 'win32' ? 'java.exe' : 'java';
}

function findJavaBinaryInside(rootDir: string): string | null {
  const binName = getJavaBinaryName();
  const direct = [
    path.join(rootDir, 'bin', binName),
    path.join(rootDir, 'Contents', 'Home', 'bin', binName),
  ];
  for (const candidate of direct) {
    if (fs.existsSync(candidate)) return candidate;
  }
  try {
    const subdirs = fs.readdirSync(rootDir, { withFileTypes: true }).filter((d) => d.isDirectory());
    for (const sub of subdirs) {
      for (const candidate of [
        path.join(rootDir, sub.name, 'bin', binName),
        path.join(rootDir, sub.name, 'Contents', 'Home', 'bin', binName),
      ]) {
        if (fs.existsSync(candidate)) return candidate;
      }
    }
  } catch {
    // ignore
  }
  return null;
}

export async function discoverJavaRuntimes(paths: AerixPaths): Promise<JavaRuntimeInfo[]> {
  const candidates: Array<{ exe: string; source: 'managed' | 'system' }> = [];
  const binName = getJavaBinaryName();

  // 1. Managed runtimes
  if (fs.existsSync(paths.runtimesDir)) {
    for (const entry of fs.readdirSync(paths.runtimesDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const found = findJavaBinaryInside(path.join(paths.runtimesDir, entry.name));
      if (found) candidates.push({ exe: found, source: 'managed' });
    }
  }

  // 2. System runtimes
  if (process.env.JAVA_HOME) {
    candidates.push({ exe: path.join(process.env.JAVA_HOME, 'bin', binName), source: 'system' });
  }
  const pathEnv = process.env.PATH || '';
  for (const dir of pathEnv.split(path.delimiter).filter(Boolean)) {
    candidates.push({ exe: path.join(dir, binName), source: 'system' });
  }

  if (process.platform === 'win32') {
    for (const base of [
      'C:\\Program Files\\Eclipse Adoptium',
      'C:\\Program Files\\Java',
      'C:\\Program Files\\Microsoft',
      'C:\\Program Files\\Zulu',
    ]) {
      if (fs.existsSync(base)) {
        for (const entry of fs.readdirSync(base, { withFileTypes: true })) {
          if (entry.isDirectory()) {
            candidates.push({ exe: path.join(base, entry.name, 'bin', binName), source: 'system' });
          }
        }
      }
    }
  } else if (process.platform === 'darwin') {
    const jvmBase = '/Library/Java/JavaVirtualMachines';
    if (fs.existsSync(jvmBase)) {
      for (const entry of fs.readdirSync(jvmBase, { withFileTypes: true })) {
        candidates.push({ exe: path.join(jvmBase, entry.name, 'Contents', 'Home', 'bin', binName), source: 'system' });
      }
    }
  } else {
    for (const base of ['/usr/lib/jvm', path.join(os.homedir(), '.sdkman', 'candidates', 'java')]) {
      if (fs.existsSync(base)) {
        for (const entry of fs.readdirSync(base, { withFileTypes: true })) {
          if (entry.isDirectory()) {
            candidates.push({ exe: path.join(base, entry.name, 'bin', binName), source: 'system' });
          }
        }
      }
    }
  }

  const seen = new Set<string>();
  const results: JavaRuntimeInfo[] = [];
  for (const candidate of candidates) {
    const resolved = path.resolve(candidate.exe);
    if (seen.has(resolved)) continue;
    seen.add(resolved);
    const info = await probeJavaExecutable(resolved, candidate.source);
    if (info) results.push(info);
  }
  return results.sort((a, b) => b.majorVersion - a.majorVersion);
}

function getAdoptiumOs(): string {
  if (process.platform === 'win32') return 'windows';
  if (process.platform === 'darwin') return 'mac';
  return 'linux';
}

function getAdoptiumArch(): string {
  if (process.arch === 'arm64') return 'aarch64';
  return 'x64';
}

export async function installManagedJavaRuntime(
  paths: AerixPaths,
  majorVersion: number,
  onStatus?: (message: string) => void
): Promise<JavaRuntimeInfo> {
  const osName = getAdoptiumOs();
  const arch = getAdoptiumArch();
  const targetDir = path.join(paths.runtimesDir, `temurin-${majorVersion}-${arch}`);
  const existing = findJavaBinaryInside(targetDir);
  if (existing) {
    const probed = await probeJavaExecutable(existing, 'managed');
    if (probed) return probed;
  }

  const ext = process.platform === 'win32' ? 'zip' : 'tar.gz';
  const url = `https://api.adoptium.net/v3/binary/latest/${majorVersion}/ga/${osName}/${arch}/jre/hotspot/normal/eclipse`;
  const archivePath = path.join(paths.cacheDir, `temurin-${majorVersion}-${osName}-${arch}.${ext}`);

  onStatus?.(`Downloading Eclipse Temurin Java ${majorVersion}...`);
  await downloadFile(url, archivePath, {
    onProgress: (downloaded, total) => {
      if (total > 0) {
        const pct = Math.min(99, Math.round((downloaded / total) * 100));
        onStatus?.(`Downloading Java ${majorVersion} (${pct}%)...`);
      }
    },
  });

  if (fs.existsSync(targetDir)) {
    fs.rmSync(targetDir, { recursive: true, force: true });
  }
  fs.mkdirSync(targetDir, { recursive: true });

  onStatus?.(`Extracting Java ${majorVersion} runtime...`);
  if (ext === 'zip') {
    extractZipFile(archivePath, targetDir);
  } else {
    await execFileAsync('tar', ['-xzf', archivePath, '-C', targetDir], { timeout: 120000 });
  }

  try {
    fs.unlinkSync(archivePath);
  } catch {
    // ignore cleanup error
  }

  const bin = findJavaBinaryInside(targetDir);
  if (!bin) {
    throw new Error(`Downloaded Java ${majorVersion} archive did not contain a java executable.`);
  }
  const info = await probeJavaExecutable(bin, 'managed');
  if (!info) {
    throw new Error(`Installed Java ${majorVersion} failed verification.`);
  }
  return info;
}

export async function ensureJavaForVersion(
  paths: AerixPaths,
  requiredMajor: number,
  preferredJavaPath?: string,
  onStatus?: (message: string) => void
): Promise<JavaRuntimeInfo> {
  if (preferredJavaPath?.trim()) {
    const custom = await probeJavaExecutable(preferredJavaPath.trim(), 'system');
    if (custom) return custom;
  }

  const runtimes = await discoverJavaRuntimes(paths);
  const exact = runtimes.find((r) => r.majorVersion === requiredMajor);
  if (exact) return exact;

  // Allow compatible newer Java only for 17->21+ edge cases if user already has one,
  // otherwise prefer exact Adoptium JRE install for maximum mod & Forge compatibility.
  return installManagedJavaRuntime(paths, requiredMajor, onStatus);
}
