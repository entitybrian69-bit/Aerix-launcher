import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { AERIX_CLIENT_SUPPORTED_VERSIONS } from './aerixClient';
import {
  buildAerixGooglePkceUrl,
  buildMicrosoftAuthorizeUrl,
  createOfflineAccount,
  exchangeAerixPkceCode,
  loginAerixWithEmail,
  loginMicrosoftWithAuthCode,
  MSA_REDIRECT_URI,
} from './auth';
import {
  deleteContentEntry,
  importContentFile,
  listInstanceContent,
  toggleContentEntry,
} from './contentManager';
import { fetchDiscoverVersions, installDiscoverFile, searchDiscoverProjects } from './discover';
import { discoverJavaRuntimes, installManagedJavaRuntime } from './javaManager';
import { GameProcessManager } from './launcherProcess';
import { fetchLoaderVersions, fetchMinecraftVersions } from './minecraftInstaller';
import { getBundledAssetPath, resolveAerixPaths } from './paths';
import { getRecommendedRamMb, LauncherStore, WALLPAPER_PRESETS } from './storage';
import {
  ContentKind,
  DiscoverPlatform,
  DiscoverProjectType,
  DiscoverSearchParams,
  DiscoverVersion,
  InstanceConfig,
  LauncherSettings,
  ModLoaderType,
} from './types';
import { checkForLauncherUpdate } from './updater';

let mainWindow: BrowserWindow | null = null;
const paths = resolveAerixPaths();
const store = new LauncherStore(paths);
const processManager = new GameProcessManager();

function sendEvent<T>(channel: string, payload: T): void {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}

function createMainWindow(): void {
  const iconPath = getBundledAssetPath('aerix-logo.png');
  mainWindow = new BrowserWindow({
    width: 1240,
    height: 800,
    minWidth: 980,
    minHeight: 640,
    backgroundColor: '#07131d',
    frame: false,
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'hidden',
    icon: fs.existsSync(iconPath) ? iconPath : undefined,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

async function openMicrosoftLoginModal(): Promise<string> {
  return new Promise((resolve, reject) => {
    const authWin = new BrowserWindow({
      width: 520,
      height: 680,
      parent: mainWindow ?? undefined,
      modal: Boolean(mainWindow),
      title: 'Sign in with Microsoft — Aerix Launcher PC',
      backgroundColor: '#0d1520',
      webPreferences: {
        nodeIntegration: false,
        contextIsolation: true,
      },
    });

    let settled = false;
    const inspectUrl = (rawUrl: string) => {
      if (settled || !rawUrl.startsWith(MSA_REDIRECT_URI)) return;
      const parsed = new URL(rawUrl);
      const code = parsed.searchParams.get('code');
      const error = parsed.searchParams.get('error_description') || parsed.searchParams.get('error');
      if (code) {
        settled = true;
        authWin.close();
        resolve(code);
      } else if (error) {
        settled = true;
        authWin.close();
        reject(new Error(error));
      }
    };

    authWin.webContents.on('will-redirect', (_ev, url) => inspectUrl(url));
    authWin.webContents.on('did-navigate', (_ev, url) => inspectUrl(url));
    authWin.on('closed', () => {
      if (!settled) {
        settled = true;
        reject(new Error('Microsoft sign-in cancelled.'));
      }
    });

    authWin.loadURL(buildMicrosoftAuthorizeUrl());
  });
}

async function openAerixGoogleOAuth(): Promise<{ code: string; codeVerifier: string }> {
  const { url, codeVerifier } = buildAerixGooglePkceUrl();
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      if (!req.url?.startsWith('/aerix-oauth')) {
        res.writeHead(404);
        res.end('Not found');
        return;
      }
      const parsed = new URL(req.url, 'http://127.0.0.1:43821');
      const code = parsed.searchParams.get('code');
      const error = parsed.searchParams.get('error_description') || parsed.searchParams.get('error');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(
        '<!doctype html><html><body style="background:#07131d;color:#e6f7f4;font-family:sans-serif;display:grid;place-items:center;height:100vh;"><div><h2>Aerix Account Connected</h2><p>You can close this browser tab and return to Aerix Launcher PC.</p></div></body></html>'
      );
      clearTimeout(timer);
      server.close();
      if (code) {
        resolve({ code, codeVerifier });
      } else {
        reject(new Error(error || 'Aerix Google sign-in did not return an authorization code.'));
      }
    });

    const timer = setTimeout(() => {
      server.close();
      reject(new Error('Aerix Google sign-in timed out.'));
    }, 180000);

    server.listen(43821, '127.0.0.1', () => {
      shell.openExternal(url).catch((err) => {
        clearTimeout(timer);
        server.close();
        reject(err);
      });
    });
  });
}

function registerIpcHandlers(): void {
  ipcMain.handle('aerix:getCapabilities', () => ({
    platform: process.platform,
    arch: process.arch,
    totalMemoryMb: Math.round(os.totalmem() / (1024 * 1024)),
    recommendedRamMb: getRecommendedRamMb(),
    dataDir: paths.root,
    appVersion: app.getVersion(),
  }));

  ipcMain.handle('aerix:getSettings', () => store.getSettings());
  ipcMain.handle('aerix:saveSettings', (_e, patch: Partial<LauncherSettings>) => store.saveSettings(patch));
  ipcMain.handle('aerix:getWallpapers', () => WALLPAPER_PRESETS);

  ipcMain.handle('aerix:getAssetDataUrl', (_e, relativePath: string) => {
    const clean = relativePath.replace(/^\/+/, '').replace(/\.\./g, '');
    const full = getBundledAssetPath(clean);
    if (!fs.existsSync(full)) return null;
    const ext = path.extname(full).toLowerCase();
    const mime =
      ext === '.webp'
        ? 'image/webp'
        : ext === '.jpg' || ext === '.jpeg'
        ? 'image/jpeg'
        : 'image/png';
    return `data:${mime};base64,${fs.readFileSync(full).toString('base64')}`;
  });

  ipcMain.handle('aerix:windowMinimize', () => mainWindow?.minimize());
  ipcMain.handle('aerix:windowMaximizeToggle', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.handle('aerix:windowClose', () => mainWindow?.close());
  ipcMain.handle('aerix:openExternal', (_e, url: string) => shell.openExternal(url));
  ipcMain.handle('aerix:openDataDir', () => {
    fs.mkdirSync(paths.root, { recursive: true });
    return shell.openPath(paths.root);
  });

  // Accounts
  ipcMain.handle('aerix:listAccounts', () => store.listAccounts());
  ipcMain.handle('aerix:addOfflineAccount', (_e, username: string) => {
    const account = createOfflineAccount(username);
    store.upsertAccount(account);
    store.saveSettings({ selectedAccountId: account.id });
    return account;
  });
  ipcMain.handle('aerix:loginMicrosoft', async () => {
    const code = await openMicrosoftLoginModal();
    const account = await loginMicrosoftWithAuthCode(code);
    store.upsertAccount(account);
    store.saveSettings({ selectedAccountId: account.id });
    return account;
  });
  ipcMain.handle('aerix:loginAerixEmail', async (_e, email: string, password: string) => {
    const account = await loginAerixWithEmail(email, password);
    store.upsertAccount(account);
    store.saveSettings({ selectedAccountId: account.id });
    return account;
  });
  ipcMain.handle('aerix:loginAerixGoogle', async () => {
    const { code, codeVerifier } = await openAerixGoogleOAuth();
    const account = await exchangeAerixPkceCode(code, codeVerifier);
    store.upsertAccount(account);
    store.saveSettings({ selectedAccountId: account.id });
    return account;
  });
  ipcMain.handle('aerix:removeAccount', (_e, accountId: string) => store.removeAccount(accountId));

  // Instances
  ipcMain.handle('aerix:listInstances', () => store.listInstances());
  ipcMain.handle(
    'aerix:createInstance',
    (
      _e,
      input: {
        name: string;
        mcVersion: string;
        loader: ModLoaderType;
        loaderVersion?: string;
        aerixClient?: boolean;
        ramMb?: number;
      }
    ) => {
      const created = store.createInstance(input);
      store.saveSettings({ selectedInstanceId: created.id });
      return created;
    }
  );
  ipcMain.handle('aerix:updateInstance', (_e, instanceId: string, patch: Partial<InstanceConfig>) =>
    store.updateInstance(instanceId, patch)
  );
  ipcMain.handle('aerix:deleteInstance', (_e, instanceId: string) => store.deleteInstance(instanceId));
  ipcMain.handle('aerix:openInstanceFolder', (_e, instanceId: string, sub?: string) => {
    const base = store.getInstanceDir(instanceId);
    const target = sub ? path.join(base, sub) : base;
    fs.mkdirSync(target, { recursive: true });
    return shell.openPath(target);
  });

  // Content
  ipcMain.handle('aerix:listContent', (_e, instanceId: string, kind: ContentKind) =>
    listInstanceContent(store.getInstanceDir(instanceId), kind)
  );
  ipcMain.handle('aerix:importContentDialog', async (_e, instanceId: string, kind: ContentKind) => {
    const filters =
      kind === 'mods'
        ? [{ name: 'Minecraft Mods', extensions: ['jar'] }]
        : [{ name: 'ZIP Archives', extensions: ['zip'] }];
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ['openFile', 'multiSelections'],
      filters,
    });
    if (result.canceled || result.filePaths.length === 0) return [];
    const instDir = store.getInstanceDir(instanceId);
    return result.filePaths.map((p) => importContentFile(instDir, kind, p));
  });
  ipcMain.handle(
    'aerix:toggleContent',
    (_e, instanceId: string, kind: ContentKind, fileName: string, enabled: boolean) =>
      toggleContentEntry(store.getInstanceDir(instanceId), kind, fileName, enabled)
  );
  ipcMain.handle('aerix:deleteContent', (_e, instanceId: string, kind: ContentKind, fileName: string) =>
    deleteContentEntry(store.getInstanceDir(instanceId), kind, fileName)
  );

  // Versions
  ipcMain.handle('aerix:fetchMinecraftVersions', (_e, includeSnapshots: boolean) =>
    fetchMinecraftVersions(includeSnapshots)
  );
  ipcMain.handle('aerix:fetchLoaderVersions', (_e, loader: ModLoaderType, mcVersion: string) =>
    fetchLoaderVersions(loader, mcVersion)
  );
  ipcMain.handle('aerix:getAerixClientVersions', () => AERIX_CLIENT_SUPPORTED_VERSIONS);

  // Java
  ipcMain.handle('aerix:discoverJava', () => discoverJavaRuntimes(paths));
  ipcMain.handle('aerix:installJava', (_e, majorVersion: number) =>
    installManagedJavaRuntime(paths, majorVersion, (msg) =>
      sendEvent('aerix:launchProgress', { stage: 'java', message: msg, percent: 50 })
    )
  );

  // Discover
  ipcMain.handle('aerix:searchDiscover', (_e, params: DiscoverSearchParams) => {
    const settings = store.getSettings();
    return searchDiscoverProjects(params, settings.curseforgeApiKey);
  });
  ipcMain.handle(
    'aerix:fetchDiscoverVersions',
    (
      _e,
      platform: DiscoverPlatform,
      projectId: string,
      options?: { mcVersion?: string; loader?: ModLoaderType }
    ) => {
      const settings = store.getSettings();
      return fetchDiscoverVersions(platform, projectId, {
        ...options,
        curseforgeApiKey: settings.curseforgeApiKey,
      });
    }
  );
  ipcMain.handle(
    'aerix:installDiscoverFile',
    (
      _e,
      params: {
        projectTitle: string;
        projectType: DiscoverProjectType;
        version: DiscoverVersion;
        instanceId?: string;
      }
    ) => installDiscoverFile(store, params)
  );

  // Launch & Process
  ipcMain.handle('aerix:launchInstance', async (_e, instanceId: string) => {
    const settings = store.getSettings();
    await processManager.launchInstance(store, instanceId, {
      onProgress: (ev) => {
        sendEvent('aerix:launchProgress', ev);
        if (ev.stage === 'running' && mainWindow) {
          if (settings.closeOnLaunch === 'minimize') mainWindow.minimize();
          else if (settings.closeOnLaunch === 'hide') mainWindow.hide();
        } else if ((ev.stage === 'exited' || ev.stage === 'error') && mainWindow && settings.closeOnLaunch === 'hide') {
          mainWindow.show();
        }
      },
      onLog: (ev) => sendEvent('aerix:gameLog', ev),
      onStateChange: (runningIds) => sendEvent('aerix:runningState', runningIds),
    });
  });
  ipcMain.handle('aerix:stopInstance', (_e, instanceId: string) => processManager.stopInstance(instanceId));
  ipcMain.handle('aerix:getRunningInstances', () => processManager.getRunningInstanceIds());

  // Updates
  ipcMain.handle('aerix:checkForUpdates', () => checkForLauncherUpdate(app.getVersion()));
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    registerIpcHandlers();
    createMainWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });
}
