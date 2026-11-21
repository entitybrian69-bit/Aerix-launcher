import { ChildProcess, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { ensureAerixClientPack, syncAerixClientMods } from './aerixClient';
import { AERIX_AUTH_SERVER_URL, ensureFreshAccount } from './auth';
import { ensureJavaForVersion } from './javaManager';
import { evaluateMojangRules, prepareGameVersion, ResolvedLaunchVersion, RuleEntry } from './minecraftInstaller';
import { getBundledAssetPath } from './paths';
import { LauncherStore } from './storage';
import { Account, GameLogEvent, InstanceConfig, LaunchProgressEvent } from './types';

function expandArgEntries(
  items: Array<string | { rules?: RuleEntry[]; value: string | string[] }>,
  vars: Record<string, string>,
  features: Record<string, boolean>
): string[] {
  const out: string[] = [];
  for (const item of items) {
    if (typeof item === 'string') {
      out.push(substituteVars(item, vars));
    } else if (evaluateMojangRules(item.rules, process.platform, process.arch, features)) {
      const values = Array.isArray(item.value) ? item.value : [item.value];
      for (const v of values) {
        out.push(substituteVars(v, vars));
      }
    }
  }
  return out;
}

function substituteVars(input: string, vars: Record<string, string>): string {
  return input.replace(/\$\{([a-zA-Z0-9_]+)\}/g, (full, key: string) =>
    key in vars ? vars[key] : full
  );
}

export function buildJvmAndGameArgs(params: {
  resolved: ResolvedLaunchVersion;
  instance: InstanceConfig;
  instanceDir: string;
  assetsDir: string;
  librariesDir: string;
  account: Account;
  ramMb: number;
  width: number;
  height: number;
  customJvmArgs: string;
  authlibInjectorPath?: string;
}): string[] {
  const classpath = [...params.resolved.libraries, params.resolved.clientJarPath].join(path.delimiter);
  const vars: Record<string, string> = {
    auth_player_name: params.account.username,
    version_name: params.resolved.versionId,
    game_directory: params.instanceDir,
    assets_root: params.assetsDir,
    assets_index_name: params.resolved.assetIndexId,
    auth_uuid: params.account.uuid,
    auth_access_token: params.account.accessToken || '0',
    clientid: '00000000402b5328',
    auth_xuid: '0',
    user_type: params.account.type === 'offline' ? 'legacy' : 'msa',
    version_type: 'Aerix PC 2.4.5',
    resolution_width: String(params.width),
    resolution_height: String(params.height),
    natives_directory: params.resolved.nativesDir,
    launcher_name: 'AerixLauncherPC',
    launcher_version: '2.4.5',
    classpath,
    classpath_separator: path.delimiter,
    library_directory: params.librariesDir,
  };

  const features: Record<string, boolean> = {
    has_custom_resolution: true,
    is_demo_user: false,
  };

  const jvm: string[] = [];
  if (params.account.type === 'aerix' && params.authlibInjectorPath && fs.existsSync(params.authlibInjectorPath)) {
    jvm.push(`-javaagent:${params.authlibInjectorPath}=${params.account.authServerUrl || AERIX_AUTH_SERVER_URL}`);
  }
  jvm.push(`-Xmx${params.ramMb}M`, `-Xms${Math.min(1024, params.ramMb)}M`);
  if (process.platform === 'darwin') {
    jvm.push('-XstartOnFirstThread');
  }
  if (params.customJvmArgs.trim()) {
    jvm.push(...params.customJvmArgs.trim().split(/\s+/));
  }
  if (params.resolved.loggingArg) {
    jvm.push(params.resolved.loggingArg);
  }

  if (params.resolved.jvmArgs.length > 0) {
    jvm.push(...expandArgEntries(params.resolved.jvmArgs, vars, features));
  } else {
    jvm.push(`-Djava.library.path=${params.resolved.nativesDir}`, '-cp', classpath);
  }

  jvm.push(params.resolved.mainClass);

  if (params.resolved.gameArgs.length > 0) {
    jvm.push(...expandArgEntries(params.resolved.gameArgs, vars, features));
  } else if (params.resolved.minecraftArguments) {
    const legacy = params.resolved.minecraftArguments.split(/\s+/).map((tok) => substituteVars(tok, vars));
    jvm.push(...legacy);
  }

  if (params.instance.fullscreen) {
    jvm.push('--fullscreen');
  }
  return jvm;
}

export class GameProcessManager {
  private running = new Map<string, ChildProcess>();

  isRunning(instanceId: string): boolean {
    return this.running.has(instanceId);
  }

  getRunningInstanceIds(): string[] {
    return Array.from(this.running.keys());
  }

  stopInstance(instanceId: string): boolean {
    const child = this.running.get(instanceId);
    if (!child) return false;
    child.kill('SIGTERM');
    setTimeout(() => {
      if (this.running.get(instanceId) === child && !child.killed) {
        child.kill('SIGKILL');
      }
    }, 5000);
    return true;
  }

  async launchInstance(
    store: LauncherStore,
    instanceId: string,
    callbacks: {
      onProgress: (ev: LaunchProgressEvent) => void;
      onLog: (ev: GameLogEvent) => void;
      onStateChange?: (runningIds: string[]) => void;
    }
  ): Promise<void> {
    if (this.running.has(instanceId)) {
      throw new Error('This instance is already running.');
    }

    const instance = store.getInstance(instanceId);
    if (!instance) {
      throw new Error(`Instance not found: ${instanceId}`);
    }

    const settings = store.getSettings();
    const accounts = store.listAccounts();
    const selectedAccount = accounts.find((a) => a.id === settings.selectedAccountId) ?? accounts[0];
    if (!selectedAccount) {
      throw new Error('Please add or select an account before launching.');
    }

    const logLauncher = (line: string) =>
      callbacks.onLog({ instanceId, stream: 'launcher', line, timestamp: Date.now() });

    callbacks.onProgress({ stage: 'preparing', message: 'Refreshing account session...', percent: 4, instanceId });
    const freshAccount = await ensureFreshAccount(selectedAccount);
    store.upsertAccount(freshAccount);

    const instanceDir = store.getInstanceDir(instance.id);
    if (instance.aerixClient) {
      callbacks.onProgress({ stage: 'aerix-client', message: 'Preparing Aerix Client PC pack & mods...', percent: 8, instanceId });
      ensureAerixClientPack(instanceDir, instance.mcVersion);
      await syncAerixClientMods(instanceDir, instance.mcVersion, callbacks.onProgress, instanceId);
    }

    const resolved = await prepareGameVersion(store.paths, {
      mcVersion: instance.mcVersion,
      loader: instance.loader,
      loaderVersion: instance.loaderVersion,
      instanceId,
      onProgress: callbacks.onProgress,
    });

    const java = await ensureJavaForVersion(
      store.paths,
      resolved.javaMajor,
      instance.javaPath || settings.defaultJavaPath,
      (msg) => callbacks.onProgress({ stage: 'java', message: msg, percent: 92, instanceId })
    );
    logLauncher(`Using Java ${java.versionString} (${java.executablePath})`);

    const authlibPath = getBundledAssetPath('authlib-injector.jar');
    const ramMb = instance.ramMb ?? settings.defaultRamMb;
    const width = instance.resolutionWidth ?? settings.defaultWidth;
    const height = instance.resolutionHeight ?? settings.defaultHeight;
    const customJvmArgs = instance.jvmArgs ?? settings.defaultJvmArgs;

    const args = buildJvmAndGameArgs({
      resolved,
      instance,
      instanceDir,
      assetsDir: store.paths.assetsDir,
      librariesDir: store.paths.librariesDir,
      account: freshAccount,
      ramMb,
      width,
      height,
      customJvmArgs,
      authlibInjectorPath: authlibPath,
    });

    callbacks.onProgress({ stage: 'launching', message: `Starting ${instance.name}...`, percent: 98, instanceId });
    logLauncher(`Launching ${instance.name} (${resolved.versionId}) with ${ramMb} MB RAM`);

    const startedAt = Date.now();
    store.updateInstance(instanceId, {
      lastPlayedAt: startedAt,
      resolvedVersionId: resolved.versionId,
    });

    const child = spawn(java.executablePath, args, {
      cwd: instanceDir,
      env: { ...process.env },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    this.running.set(instanceId, child);
    callbacks.onStateChange?.(this.getRunningInstanceIds());
    callbacks.onProgress({ stage: 'running', message: `${instance.name} is running`, percent: 100, instanceId });

    const pump = (stream: 'stdout' | 'stderr', chunk: Buffer) => {
      const text = chunk.toString('utf8');
      for (const line of text.split(/\r?\n/)) {
        if (line.length > 0) {
          callbacks.onLog({ instanceId, stream, line, timestamp: Date.now() });
        }
      }
    };

    child.stdout?.on('data', (d: Buffer) => pump('stdout', d));
    child.stderr?.on('data', (d: Buffer) => pump('stderr', d));

    child.on('error', (err) => {
      this.running.delete(instanceId);
      callbacks.onStateChange?.(this.getRunningInstanceIds());
      callbacks.onLog({ instanceId, stream: 'stderr', line: `Process error: ${err.message}`, timestamp: Date.now() });
      callbacks.onProgress({ stage: 'error', message: err.message, percent: 0, instanceId });
    });

    child.on('close', (code, signal) => {
      this.running.delete(instanceId);
      callbacks.onStateChange?.(this.getRunningInstanceIds());
      const elapsedSec = Math.max(0, Math.round((Date.now() - startedAt) / 1000));
      const latest = store.getInstance(instanceId);
      if (latest) {
        store.updateInstance(instanceId, {
          playTimeSec: (latest.playTimeSec || 0) + elapsedSec,
        });
      }
      const msg = `Game exited (code=${code ?? 'null'}, signal=${signal ?? 'none'})`;
      logLauncher(msg);
      callbacks.onProgress({
        stage: code === 0 ? 'exited' : 'error',
        message: code === 0 ? `${instance.name} closed cleanly.` : `${instance.name} exited with code ${code}.`,
        percent: 0,
        instanceId,
      });
    });
  }
}
