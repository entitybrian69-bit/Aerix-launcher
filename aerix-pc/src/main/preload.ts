import { contextBridge, ipcRenderer } from 'electron';
import {
  Account,
  ContentEntry,
  ContentKind,
  DiscoverPlatform,
  DiscoverProject,
  DiscoverSearchParams,
  DiscoverVersion,
  GameLogEvent,
  InstanceConfig,
  JavaRuntimeInfo,
  LauncherSettings,
  LaunchProgressEvent,
  MinecraftVersionSummary,
  ModLoaderType,
  SystemCapabilities,
  UpdateCheckResult,
  WallpaperPreset,
} from './types';

export interface AerixBridgeApi {
  getCapabilities(): Promise<SystemCapabilities>;
  getSettings(): Promise<LauncherSettings>;
  saveSettings(patch: Partial<LauncherSettings>): Promise<LauncherSettings>;
  getWallpapers(): Promise<WallpaperPreset[]>;
  getAssetDataUrl(relativePath: string): Promise<string | null>;

  // Window controls
  windowMinimize(): Promise<void>;
  windowMaximizeToggle(): Promise<void>;
  windowClose(): Promise<void>;
  openExternal(url: string): Promise<void>;

  // Accounts
  listAccounts(): Promise<Account[]>;
  addOfflineAccount(username: string): Promise<Account>;
  loginMicrosoft(): Promise<Account>;
  loginAerixEmail(email: string, password: string): Promise<Account>;
  loginAerixGoogle(): Promise<Account>;
  removeAccount(accountId: string): Promise<Account[]>;

  // Instances
  listInstances(): Promise<InstanceConfig[]>;
  createInstance(input: {
    name: string;
    mcVersion: string;
    loader: ModLoaderType;
    loaderVersion?: string;
    aerixClient?: boolean;
    ramMb?: number;
  }): Promise<InstanceConfig>;
  updateInstance(instanceId: string, patch: Partial<InstanceConfig>): Promise<InstanceConfig>;
  deleteInstance(instanceId: string): Promise<void>;
  openInstanceFolder(instanceId: string, sub?: string): Promise<void>;

  // Content management
  listContent(instanceId: string, kind: ContentKind): Promise<ContentEntry[]>;
  importContentDialog(instanceId: string, kind: ContentKind): Promise<ContentEntry[]>;
  toggleContent(instanceId: string, kind: ContentKind, fileName: string, enabled: boolean): Promise<void>;
  deleteContent(instanceId: string, kind: ContentKind, fileName: string): Promise<void>;

  // Minecraft & Loaders
  fetchMinecraftVersions(includeSnapshots: boolean): Promise<MinecraftVersionSummary[]>;
  fetchLoaderVersions(loader: ModLoaderType, mcVersion: string): Promise<string[]>;
  getAerixClientVersions(): Promise<string[]>;

  // Java
  discoverJava(): Promise<JavaRuntimeInfo[]>;
  installJava(majorVersion: number): Promise<JavaRuntimeInfo>;

  // Discover
  searchDiscover(params: DiscoverSearchParams): Promise<DiscoverProject[]>;
  fetchDiscoverVersions(
    platform: DiscoverPlatform,
    projectId: string,
    options?: { mcVersion?: string; loader?: ModLoaderType }
  ): Promise<DiscoverVersion[]>;
  installDiscoverFile(params: {
    projectTitle: string;
    projectType: DiscoverSearchParams['projectType'];
    version: DiscoverVersion;
    instanceId?: string;
  }): Promise<{ installedPath: string; createdInstance?: InstanceConfig }>;

  // Launch & Process
  launchInstance(instanceId: string): Promise<void>;
  stopInstance(instanceId: string): Promise<boolean>;
  getRunningInstances(): Promise<string[]>;

  // Updates
  checkForUpdates(): Promise<UpdateCheckResult>;

  // Events
  onLaunchProgress(cb: (ev: LaunchProgressEvent) => void): () => void;
  onGameLog(cb: (ev: GameLogEvent) => void): () => void;
  onRunningStateChange(cb: (runningIds: string[]) => void): () => void;
}

const api: AerixBridgeApi = {
  getCapabilities: () => ipcRenderer.invoke('aerix:getCapabilities'),
  getSettings: () => ipcRenderer.invoke('aerix:getSettings'),
  saveSettings: (patch) => ipcRenderer.invoke('aerix:saveSettings', patch),
  getWallpapers: () => ipcRenderer.invoke('aerix:getWallpapers'),
  getAssetDataUrl: (relativePath) => ipcRenderer.invoke('aerix:getAssetDataUrl', relativePath),

  windowMinimize: () => ipcRenderer.invoke('aerix:windowMinimize'),
  windowMaximizeToggle: () => ipcRenderer.invoke('aerix:windowMaximizeToggle'),
  windowClose: () => ipcRenderer.invoke('aerix:windowClose'),
  openExternal: (url) => ipcRenderer.invoke('aerix:openExternal', url),

  listAccounts: () => ipcRenderer.invoke('aerix:listAccounts'),
  addOfflineAccount: (username) => ipcRenderer.invoke('aerix:addOfflineAccount', username),
  loginMicrosoft: () => ipcRenderer.invoke('aerix:loginMicrosoft'),
  loginAerixEmail: (email, password) => ipcRenderer.invoke('aerix:loginAerixEmail', email, password),
  loginAerixGoogle: () => ipcRenderer.invoke('aerix:loginAerixGoogle'),
  removeAccount: (accountId) => ipcRenderer.invoke('aerix:removeAccount', accountId),

  listInstances: () => ipcRenderer.invoke('aerix:listInstances'),
  createInstance: (input) => ipcRenderer.invoke('aerix:createInstance', input),
  updateInstance: (instanceId, patch) => ipcRenderer.invoke('aerix:updateInstance', instanceId, patch),
  deleteInstance: (instanceId) => ipcRenderer.invoke('aerix:deleteInstance', instanceId),
  openInstanceFolder: (instanceId, sub) => ipcRenderer.invoke('aerix:openInstanceFolder', instanceId, sub),

  listContent: (instanceId, kind) => ipcRenderer.invoke('aerix:listContent', instanceId, kind),
  importContentDialog: (instanceId, kind) => ipcRenderer.invoke('aerix:importContentDialog', instanceId, kind),
  toggleContent: (instanceId, kind, fileName, enabled) =>
    ipcRenderer.invoke('aerix:toggleContent', instanceId, kind, fileName, enabled),
  deleteContent: (instanceId, kind, fileName) =>
    ipcRenderer.invoke('aerix:deleteContent', instanceId, kind, fileName),

  fetchMinecraftVersions: (includeSnapshots) =>
    ipcRenderer.invoke('aerix:fetchMinecraftVersions', includeSnapshots),
  fetchLoaderVersions: (loader, mcVersion) =>
    ipcRenderer.invoke('aerix:fetchLoaderVersions', loader, mcVersion),
  getAerixClientVersions: () => ipcRenderer.invoke('aerix:getAerixClientVersions'),

  discoverJava: () => ipcRenderer.invoke('aerix:discoverJava'),
  installJava: (majorVersion) => ipcRenderer.invoke('aerix:installJava', majorVersion),

  searchDiscover: (params) => ipcRenderer.invoke('aerix:searchDiscover', params),
  fetchDiscoverVersions: (platform, projectId, options) =>
    ipcRenderer.invoke('aerix:fetchDiscoverVersions', platform, projectId, options),
  installDiscoverFile: (params) => ipcRenderer.invoke('aerix:installDiscoverFile', params),

  launchInstance: (instanceId) => ipcRenderer.invoke('aerix:launchInstance', instanceId),
  stopInstance: (instanceId) => ipcRenderer.invoke('aerix:stopInstance', instanceId),
  getRunningInstances: () => ipcRenderer.invoke('aerix:getRunningInstances'),

  checkForUpdates: () => ipcRenderer.invoke('aerix:checkForUpdates'),

  onLaunchProgress: (cb) => {
    const listener = (_: unknown, ev: LaunchProgressEvent) => cb(ev);
    ipcRenderer.on('aerix:launchProgress', listener);
    return () => ipcRenderer.removeListener('aerix:launchProgress', listener);
  },
  onGameLog: (cb) => {
    const listener = (_: unknown, ev: GameLogEvent) => cb(ev);
    ipcRenderer.on('aerix:gameLog', listener);
    return () => ipcRenderer.removeListener('aerix:gameLog', listener);
  },
  onRunningStateChange: (cb) => {
    const listener = (_: unknown, runningIds: string[]) => cb(runningIds);
    ipcRenderer.on('aerix:runningState', listener);
    return () => ipcRenderer.removeListener('aerix:runningState', listener);
  },
};

contextBridge.exposeInMainWorld('aerix', api);
