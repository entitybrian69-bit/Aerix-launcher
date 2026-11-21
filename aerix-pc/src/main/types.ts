export type AccountType = 'microsoft' | 'aerix' | 'offline';

export interface Account {
  id: string;
  type: AccountType;
  username: string;
  uuid: string;
  accessToken: string;
  refreshToken?: string;
  expiresAt?: number;
  email?: string;
  avatarUrl?: string;
  authServerUrl?: string;
  createdAt: number;
  lastUsedAt: number;
}

export type ModLoaderType = 'vanilla' | 'fabric' | 'quilt' | 'forge' | 'neoforge';

export interface InstanceConfig {
  id: string;
  name: string;
  mcVersion: string;
  loader: ModLoaderType;
  loaderVersion?: string;
  aerixClient?: boolean;
  icon?: string;
  ramMb?: number;
  javaPath?: string;
  jvmArgs?: string;
  resolutionWidth?: number;
  resolutionHeight?: number;
  fullscreen?: boolean;
  createdAt: number;
  lastPlayedAt?: number;
  playTimeSec: number;
  resolvedVersionId?: string;
}

export type ThemePresetId =
  | 'aurora-teal'
  | 'aerix-crimson'
  | 'ender-amethyst'
  | 'sunset-amber'
  | 'ocean-azure'
  | 'sakura-pink'
  | 'custom';

export type LauncherUiStyle = 'premium' | 'clean';

export interface WallpaperPreset {
  id: string;
  title: string;
  biome: string;
  assetFile: string;
  accentHex: string;
  animatedScene?: boolean;
}

export interface LauncherSettings {
  selectedInstanceId: string | null;
  selectedAccountId: string | null;
  defaultRamMb: number;
  defaultJavaPath: string;
  defaultJvmArgs: string;
  defaultWidth: number;
  defaultHeight: number;
  closeOnLaunch: 'keep' | 'minimize' | 'hide';
  showSnapshots: boolean;
  themePreset: ThemePresetId;
  customAccentHex?: string;
  uiStyle?: LauncherUiStyle;
  themeMotion?: boolean;
  layoutTransparency?: number;
  wallpaperBlur?: number;
  wallpaperId: string;
  wallpaperOpacity: number;
  curseforgeApiKey: string;
}

export interface JavaRuntimeInfo {
  majorVersion: number;
  versionString: string;
  executablePath: string;
  source: 'managed' | 'system';
  arch: string;
}

export interface MinecraftVersionSummary {
  id: string;
  type: 'release' | 'snapshot' | 'old_beta' | 'old_alpha';
  url: string;
  releaseTime: string;
}

export interface LaunchProgressEvent {
  stage: 'preparing' | 'java' | 'minecraft' | 'loader' | 'aerix-client' | 'assets' | 'launching' | 'running' | 'exited' | 'error';
  message: string;
  percent: number;
  instanceId?: string;
}

export interface GameLogEvent {
  instanceId: string;
  stream: 'stdout' | 'stderr' | 'launcher';
  line: string;
  timestamp: number;
}

export type ContentKind = 'mods' | 'resourcepacks' | 'shaderpacks' | 'saves' | 'screenshots';

export interface ContentEntry {
  name: string;
  fileName: string;
  fullPath: string;
  enabled: boolean;
  sizeBytes: number;
  modifiedAt: number;
  isDirectory: boolean;
}

export type DiscoverPlatform = 'modrinth' | 'curseforge';
export type DiscoverProjectType = 'mod' | 'modpack' | 'resourcepack' | 'shader';

export interface DiscoverSearchParams {
  platform: DiscoverPlatform;
  projectType: DiscoverProjectType;
  query: string;
  mcVersion?: string;
  loader?: ModLoaderType;
  sort?: 'relevance' | 'downloads' | 'updated' | 'newest';
  offset?: number;
  limit?: number;
}

export interface DiscoverProject {
  id: string;
  slug: string;
  platform: DiscoverPlatform;
  projectType: DiscoverProjectType;
  title: string;
  description: string;
  author: string;
  iconUrl?: string;
  downloads: number;
  categories: string[];
  mcVersions: string[];
  loaders: string[];
  webUrl: string;
}

export interface DiscoverVersionFile {
  url: string;
  filename: string;
  size: number;
  sha1?: string;
  primary: boolean;
}

export interface DiscoverVersion {
  id: string;
  name: string;
  versionNumber: string;
  gameVersions: string[];
  loaders: string[];
  publishedAt: string;
  downloads: number;
  files: DiscoverVersionFile[];
}

export interface UpdateCheckResult {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  releaseUrl: string;
  releaseNotes: string;
  matchingAssetUrl?: string;
  matchingAssetName?: string;
}

export interface SystemCapabilities {
  platform: NodeJS.Platform;
  arch: string;
  totalMemoryMb: number;
  recommendedRamMb: number;
  dataDir: string;
  appVersion: string;
}
