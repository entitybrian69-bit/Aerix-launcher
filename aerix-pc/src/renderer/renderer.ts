interface AerixBridge {
  getCapabilities(): Promise<any>;
  getSettings(): Promise<any>;
  saveSettings(patch: Record<string, unknown>): Promise<any>;
  getWallpapers(): Promise<Array<{ id: string; title: string; biome: string; assetFile: string; accentHex: string; animatedScene?: boolean }>>;
  getAssetDataUrl(relativePath: string): Promise<string | null>;
  windowMinimize(): Promise<void>;
  windowMaximizeToggle(): Promise<void>;
  windowClose(): Promise<void>;
  openExternal(url: string): Promise<void>;
  openDataDir(): Promise<string>;
  listAccounts(): Promise<any[]>;
  addOfflineAccount(username: string): Promise<any>;
  loginMicrosoft(): Promise<any>;
  loginAerixEmail(email: string, password: string): Promise<any>;
  loginAerixGoogle(): Promise<any>;
  removeAccount(accountId: string): Promise<any[]>;
  listInstances(): Promise<any[]>;
  createInstance(input: Record<string, unknown>): Promise<any>;
  updateInstance(instanceId: string, patch: Record<string, unknown>): Promise<any>;
  deleteInstance(instanceId: string): Promise<void>;
  openInstanceFolder(instanceId: string, sub?: string): Promise<void>;
  listContent(instanceId: string, kind: string): Promise<any[]>;
  importContentDialog(instanceId: string, kind: string): Promise<any[]>;
  toggleContent(instanceId: string, kind: string, fileName: string, enabled: boolean): Promise<void>;
  deleteContent(instanceId: string, kind: string, fileName: string): Promise<void>;
  fetchMinecraftVersions(includeSnapshots: boolean): Promise<Array<{ id: string; type: string }>>;
  fetchLoaderVersions(loader: string, mcVersion: string): Promise<string[]>;
  getAerixClientVersions(): Promise<string[]>;
  discoverJava(): Promise<any[]>;
  installJava(majorVersion: number): Promise<any>;
  searchDiscover(params: Record<string, unknown>): Promise<any[]>;
  fetchDiscoverVersions(platform: string, projectId: string, options?: Record<string, unknown>): Promise<any[]>;
  installDiscoverFile(params: Record<string, unknown>): Promise<any>;
  launchInstance(instanceId: string): Promise<void>;
  stopInstance(instanceId: string): Promise<boolean>;
  getRunningInstances(): Promise<string[]>;
  checkForUpdates(): Promise<any>;
  onLaunchProgress(cb: (ev: any) => void): () => void;
  onGameLog(cb: (ev: any) => void): () => void;
  onRunningStateChange(cb: (runningIds: string[]) => void): () => void;
}

const aerix: AerixBridge | undefined = (window as unknown as { aerix?: AerixBridge }).aerix;

const FALLBACK_MC_VERSIONS = [
  '1.21.11', '1.21.10', '1.21.8', '1.21.4', '1.21.1', '1.20.4', '1.20.1', '1.19.4', '1.18.2', '1.16.5',
].map((id) => ({ id, type: 'release' }));

const FALLBACK_AERIX_CLIENT_VERSIONS = [
  '1.21.11', '1.21.10', '1.21.8', '1.21.4', '1.21.1', '1.20.4', '1.20.1', '1.19.4', '1.18.2', '1.16.5',
];

const THEME_ACCENTS: Record<string, { hex: string; contrast: string }> = {
  'aurora-teal': { hex: '#8DEFE0', contrast: '#06201D' },
  'aerix-crimson': { hex: '#F43F5E', contrast: '#FFFFFF' },
  'ender-amethyst': { hex: '#C084FC', contrast: '#12081F' },
  'sunset-amber': { hex: '#FBBF24', contrast: '#1A1004' },
  'ocean-azure': { hex: '#38BDF8', contrast: '#041524' },
  'sakura-pink': { hex: '#F472B6', contrast: '#200716' },
};

const state = {
  capabilities: null as any,
  settings: null as any,
  accounts: [] as any[],
  instances: [] as any[],
  wallpapers: [] as Array<{ id: string; title: string; biome: string; assetFile: string; accentHex: string; animatedScene?: boolean }>,
  wallpaperUrls: new Map<string, string>(),
  mcVersions: [...FALLBACK_MC_VERSIONS] as Array<{ id: string; type: string }>,
  aerixClientVersions: [...FALLBACK_AERIX_CLIENT_VERSIONS] as string[],
  selectedClientVersion: '1.21.4',
  runningIds: new Set<string>(),
  inspectedInstanceId: null as string | null,
  inspectorTab: 'mods' as 'mods' | 'resourcepacks' | 'shaderpacks' | 'saves' | 'screenshots' | 'config',
  discoverPlatform: 'modrinth' as 'modrinth' | 'curseforge',
  discoverType: 'mod' as 'mod' | 'modpack' | 'resourcepack' | 'shader',
  discoverResults: [] as any[],
  logs: [] as Array<{ stream: string; line: string; timestamp: number }>,
  dollAzimuth: 26,
  currentSkinUrl: '',
};

function $(id: string): HTMLElement {
  const el = document.getElementById(id);
  if (!el) throw new Error(`Missing DOM element #${id}`);
  return el;
}

function escapeHtml(text: string): string {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showToast(message: string, isError = false): void {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const el = document.createElement('div');
  el.className = `toast${isError ? ' error' : ''}`;
  el.textContent = message;
  container.appendChild(el);
  setTimeout(() => el.remove(), 4200);
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

function hexToRgbTuple(hex: string): { r: number; g: number; b: number } {
  const clean = hex.replace('#', '').trim();
  const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean.padEnd(6, '0').slice(0, 6);
  const num = parseInt(full, 16);
  if (Number.isNaN(num)) return { r: 141, g: 239, b: 224 };
  return { r: (num >> 16) & 255, g: (num >> 8) & 255, b: num & 255 };
}

function contrastForHex(hex: string): string {
  const { r, g, b } = hexToRgbTuple(hex);
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return lum > 0.52 ? '#061B1A' : '#FFFFFF';
}

function applyAccentColor(hex: string): void {
  const { r, g, b } = hexToRgbTuple(hex);
  const root = document.documentElement.style;
  root.setProperty('--accent', hex);
  root.setProperty('--accent-rgb', `${r}, ${g}, ${b}`);
  root.setProperty('--accent-contrast', contrastForHex(hex));
}

function switchView(viewId: string): void {
  document.querySelectorAll('.rail-btn[data-view]').forEach((b) => {
    b.classList.toggle('active', b.getAttribute('data-view') === viewId);
  });
  document.querySelectorAll('.view').forEach((v) => {
    v.classList.toggle('active', v.id === `view-${viewId}`);
  });
  if (viewId === 'client') {
    renderClientHub();
  } else if (viewId === 'discover' && !state.discoverResults.length && aerix) {
    void runDiscoverSearch();
  } else if (viewId === 'settings') {
    void renderSettings();
    if (aerix) void refreshJavaList();
  } else if (viewId === 'console') {
    renderConsole();
  }
}

/* ---------------- Animated Live Canvas Wallpaper Engine (AerixAnimations port) ---------------- */

interface SceneConfig {
  sky: [string, string, string];
  accent: string;
  effect: 'fireflies' | 'petals' | 'snow' | 'embers' | 'stars' | 'motes';
  aurora?: boolean;
  water?: string;
}

const ANIMATED_SCENES: Record<string, SceneConfig> = {
  anim_01_lush_cavern: { sky: ['#05231F', '#0B3B33', '#14614F'], accent: '#2DD4BF', effect: 'fireflies', water: '#1E6F5C' },
  anim_02_cherry_grove: { sky: ['#3A1B33', '#7C3F63', '#E8A0BE'], accent: '#F472B6', effect: 'petals' },
  anim_13_glacier: { sky: ['#04121F', '#0A3145', '#16607B'], accent: '#22D3EE', effect: 'snow', aurora: true, water: '#1E6377' },
  anim_05_nether_lava: { sky: ['#1A0705', '#4A130A', '#A33212'], accent: '#F97316', effect: 'embers' },
  anim_25_deep_dark: { sky: ['#02090D', '#062A31', '#0B4A52'], accent: '#06B6D4', effect: 'fireflies' },
  anim_08_moonlit_lake: { sky: ['#050A1B', '#0E1B3D', '#243A6B'], accent: '#818CF8', effect: 'stars', water: '#1B3462' },
  anim_03_sunset_river: { sky: ['#2B1440', '#8E3B2E', '#F0A44A'], accent: '#F59E0B', effect: 'motes', water: '#C97A3A' },
  anim_06_the_end: { sky: ['#06040F', '#150C2A', '#2F1B4E'], accent: '#A855F7', effect: 'stars' },
};

let canvasRaf = 0;
let activeSceneId: string | null = null;
const particles = Array.from({ length: 48 }, (_, i) => ({
  x: ((i * 73) % 100) / 100,
  y: ((i * 41) % 100) / 100,
  r: 1.5 + (i % 4) * 0.9,
  speed: 0.015 + (i % 7) * 0.006,
  phase: i * 0.7,
}));

function stopAnimatedWallpaper(): void {
  if (canvasRaf) {
    cancelAnimationFrame(canvasRaf);
    canvasRaf = 0;
  }
  activeSceneId = null;
  document.getElementById('wallpaper-canvas')?.classList.add('hidden');
}

function startAnimatedWallpaper(sceneId: string): void {
  const cfg = ANIMATED_SCENES[sceneId];
  const canvas = document.getElementById('wallpaper-canvas') as HTMLCanvasElement | null;
  if (!cfg || !canvas) {
    stopAnimatedWallpaper();
    return;
  }
  canvas.classList.remove('hidden');
  if (activeSceneId === sceneId && canvasRaf) return;
  if (canvasRaf) cancelAnimationFrame(canvasRaf);
  activeSceneId = sceneId;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let start = performance.now();
  const drawFrame = (now: number) => {
    if (activeSceneId !== sceneId) return;
    const w = (canvas.width = window.innerWidth);
    const h = (canvas.height = window.innerHeight);
    const t = (now - start) / 1000;

    // Sky gradient
    const skyGrad = ctx.createLinearGradient(0, 0, 0, h);
    skyGrad.addColorStop(0, cfg.sky[0]);
    skyGrad.addColorStop(0.55, cfg.sky[1]);
    skyGrad.addColorStop(1, cfg.sky[2]);
    ctx.fillStyle = skyGrad;
    ctx.fillRect(0, 0, w, h);

    // Aurora ribbons
    if (cfg.aurora) {
      for (let band = 0; band < 3; band++) {
        ctx.beginPath();
        const baseY = h * (0.22 + band * 0.08);
        ctx.moveTo(0, baseY);
        for (let x = 0; x <= w; x += 28) {
          const wave = Math.sin(x * 0.004 + t * 0.55 + band) * 34 + Math.cos(x * 0.009 - t * 0.35) * 16;
          ctx.lineTo(x, baseY + wave);
        }
        ctx.lineTo(w, baseY + 120);
        ctx.lineTo(0, baseY + 120);
        ctx.closePath();
        const aGrad = ctx.createLinearGradient(0, baseY - 30, 0, baseY + 120);
        aGrad.addColorStop(0, 'rgba(34, 211, 238, 0.24)');
        aGrad.addColorStop(1, 'transparent');
        ctx.fillStyle = aGrad;
        ctx.fill();
      }
    }

    // Distant mountain silhouettes
    for (let layer = 0; layer < 3; layer++) {
      ctx.beginPath();
      const base = h * (0.64 + layer * 0.09);
      ctx.moveTo(0, h);
      for (let x = 0; x <= w; x += 32) {
        const peak = Math.sin(x * 0.0035 + layer * 2.1) * 44 + Math.cos(x * 0.008 + layer) * 20;
        ctx.lineTo(x, base + peak);
      }
      ctx.lineTo(w, h);
      ctx.closePath();
      ctx.fillStyle = `rgba(6, 14, 20, ${0.28 + layer * 0.16})`;
      ctx.fill();
    }

    if (cfg.water) {
      ctx.fillStyle = cfg.water;
      ctx.globalAlpha = 0.28;
      ctx.fillRect(0, h * 0.8, w, h * 0.2);
      ctx.globalAlpha = 1;
    }

    // Particles
    const motionEnabled = document.body.getAttribute('data-motion') !== 'off';
    const time = motionEnabled ? t : 1.5;
    for (const p of particles) {
      let px = p.x * w;
      let py = p.y * h;
      if (cfg.effect === 'embers') {
        py = ((p.y - time * p.speed * 1.4) % 1 + 1) % 1 * h;
        px += Math.sin(time * 1.6 + p.phase) * 16;
      } else if (cfg.effect === 'petals' || cfg.effect === 'snow') {
        py = ((p.y + time * p.speed * 1.2) % 1) * h;
        px += Math.sin(time * 1.1 + p.phase) * 26;
      } else if (cfg.effect === 'fireflies' || cfg.effect === 'motes') {
        px += Math.sin(time * 0.9 + p.phase) * 28;
        py += Math.cos(time * 0.7 + p.phase) * 22;
      }
      const alpha = cfg.effect === 'stars'
        ? 0.35 + 0.55 * (0.5 + 0.5 * Math.sin(time * 2.1 + p.phase))
        : 0.45 + 0.4 * Math.sin(time * 1.7 + p.phase);
      ctx.beginPath();
      ctx.arc(px, py, p.r, 0, Math.PI * 2);
      ctx.fillStyle = cfg.effect === 'snow' || cfg.effect === 'stars' ? `rgba(255,255,255,${alpha})` : cfg.accent;
      ctx.globalAlpha = Math.max(0.12, Math.min(0.95, alpha));
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    if (motionEnabled) {
      canvasRaf = requestAnimationFrame(drawFrame);
    }
  };
  canvasRaf = requestAnimationFrame(drawFrame);
}

/* ---------------- 3D Minecraft Skin Voxel Renderer (CSS 3D + Drag Fling) ---------------- */

function createFallbackSkinDataUrl(): string {
  const c = document.createElement('canvas');
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext('2d');
  if (!ctx) return '';
  // Crisp procedural Steve / Aerix skin layout (64x64 standard UV)
  ctx.fillStyle = '#5f3b22'; ctx.fillRect(0, 0, 64, 64); // base
  // Head skin front/sides/top/bottom
  ctx.fillStyle = '#d8a684'; ctx.fillRect(8, 8, 8, 8); // front
  ctx.fillStyle = '#c89270'; ctx.fillRect(0, 8, 8, 8); ctx.fillRect(16, 8, 8, 8); ctx.fillRect(8, 16, 8, 8);
  ctx.fillStyle = '#3d2515'; ctx.fillRect(8, 0, 8, 8); ctx.fillRect(24, 8, 8, 8); // hair
  ctx.fillStyle = '#3d2515'; ctx.fillRect(8, 8, 8, 2); // bangs
  // Eyes & smile
  ctx.fillStyle = '#ffffff'; ctx.fillRect(9, 11, 2, 1); ctx.fillRect(13, 11, 2, 1);
  ctx.fillStyle = '#37d5c6'; ctx.fillRect(10, 11, 1, 1); ctx.fillRect(13, 11, 1, 1);
  ctx.fillStyle = '#9a6449'; ctx.fillRect(11, 13, 2, 1); ctx.fillRect(10, 14, 4, 1);
  // Torso (Aerix teal hoodie)
  ctx.fillStyle = '#18938b'; ctx.fillRect(20, 20, 8, 12); // front
  ctx.fillStyle = '#147a73'; ctx.fillRect(16, 20, 4, 12); ctx.fillRect(28, 20, 4, 12); ctx.fillRect(32, 20, 8, 12);
  ctx.fillStyle = '#8defe0'; ctx.fillRect(23, 23, 2, 5); // accent emblem
  // Arms
  ctx.fillStyle = '#1bb0a5'; ctx.fillRect(44, 20, 4, 8); ctx.fillRect(36, 52, 4, 8);
  ctx.fillStyle = '#d8a684'; ctx.fillRect(44, 28, 4, 4); ctx.fillRect(36, 60, 4, 4);
  ctx.fillStyle = '#14847b'; ctx.fillRect(40, 20, 4, 12); ctx.fillRect(48, 20, 4, 12); ctx.fillRect(52, 20, 4, 12);
  // Legs
  ctx.fillStyle = '#2b3b64'; ctx.fillRect(4, 20, 4, 12); ctx.fillRect(20, 52, 4, 12);
  ctx.fillStyle = '#202d4d'; ctx.fillRect(0, 20, 4, 12); ctx.fillRect(8, 20, 4, 12); ctx.fillRect(12, 20, 4, 12);
  return c.toDataURL('image/png');
}

let fallbackSkinCache = '';
function getFallbackSkinUrl(): string {
  if (!fallbackSkinCache) fallbackSkinCache = createFallbackSkinDataUrl();
  return fallbackSkinCache;
}

function buildCuboid(
  skinUrl: string,
  w: number,
  h: number,
  d: number,
  uv: { front: [number, number]; back: [number, number]; left: [number, number]; right: [number, number]; top: [number, number]; bottom: [number, number] },
  txW: number,
  txH: number,
  scale: number,
  x: number,
  y: number,
  z = 0
): HTMLElement {
  const part = document.createElement('div');
  part.className = 'mc-part';
  const pw = w * scale;
  const ph = h * scale;
  const pd = d * scale;
  part.style.width = `${pw}px`;
  part.style.height = `${ph}px`;
  part.style.transform = `translate3d(${x * scale - pw / 2}px, ${y * scale}px, ${z * scale}px)`;

  const texTotal = 64 * scale;
  const faceDefs: Array<{ fw: number; fh: number; u: number; v: number; uw: number; uh: number; tr: string }> = [
    { fw: pw, fh: ph, u: uv.front[0], v: uv.front[1], uw: w, uh: h, tr: `translateZ(${pd / 2}px)` },
    { fw: pw, fh: ph, u: uv.back[0], v: uv.back[1], uw: w, uh: h, tr: `rotateY(180deg) translateZ(${pd / 2}px)` },
    { fw: pd, fh: ph, u: uv.right[0], v: uv.right[1], uw: d, uh: h, tr: `rotateY(-90deg) translateZ(${pw / 2}px)` },
    { fw: pd, fh: ph, u: uv.left[0], v: uv.left[1], uw: d, uh: h, tr: `rotateY(90deg) translateZ(${pw / 2}px)` },
    { fw: pw, fh: pd, u: uv.top[0], v: uv.top[1], uw: w, uh: d, tr: `rotateX(90deg) translateZ(${ph / 2}px)` },
    { fw: pw, fh: pd, u: uv.bottom[0], v: uv.bottom[1], uw: w, uh: d, tr: `rotateX(-90deg) translateZ(${ph / 2}px)` },
  ];

  for (const f of faceDefs) {
    const face = document.createElement('div');
    face.className = 'mc-face';
    face.style.width = `${f.fw}px`;
    face.style.height = `${f.fh}px`;
    face.style.left = `${(pw - f.fw) / 2}px`;
    face.style.top = `${(ph - f.fh) / 2}px`;
    face.style.transform = f.tr;
    face.style.backgroundImage = `url("${skinUrl}")`;
    face.style.backgroundSize = `${(texTotal / f.uw) * f.uw}px ${texTotal}px`;
    face.style.backgroundPosition = `-${f.u * scale}px -${f.v * scale}px`;
    part.appendChild(face);
  }
  return part;
}

function renderSkinViewport(containerId: string, skinUrl: string): void {
  const host = document.getElementById(containerId);
  if (!host) return;
  host.innerHTML = '';

  const rig = document.createElement('div');
  rig.className = 'mc-character-rig';
  rig.style.transform = `rotateX(-8deg) rotateY(${state.dollAzimuth}deg)`;

  const breath = document.createElement('div');
  breath.className = 'mc-character-breath';
  const scale = 5.4;

  // Head (8x8x8)
  breath.appendChild(
    buildCuboid(skinUrl, 8, 8, 8, {
      front: [8, 8], back: [24, 8], right: [0, 8], left: [16, 8], top: [8, 0], bottom: [16, 0],
    }, 8, 8, scale, 0, -16)
  );
  // Torso (8x12x4)
  breath.appendChild(
    buildCuboid(skinUrl, 8, 12, 4, {
      front: [20, 20], back: [32, 20], right: [16, 20], left: [28, 20], top: [20, 16], bottom: [28, 16],
    }, 8, 12, scale, 0, -8)
  );
  // Right Arm (4x12x4)
  breath.appendChild(
    buildCuboid(skinUrl, 4, 12, 4, {
      front: [44, 20], back: [52, 20], right: [40, 20], left: [48, 20], top: [44, 16], bottom: [48, 16],
    }, 4, 12, scale, -6, -8)
  );
  // Left Arm (4x12x4)
  breath.appendChild(
    buildCuboid(skinUrl, 4, 12, 4, {
      front: [36, 52], back: [44, 52], right: [32, 52], left: [40, 52], top: [36, 48], bottom: [40, 48],
    }, 4, 12, scale, 6, -8)
  );
  // Right Leg (4x12x4)
  breath.appendChild(
    buildCuboid(skinUrl, 4, 12, 4, {
      front: [4, 20], back: [12, 20], right: [0, 20], left: [8, 20], top: [4, 16], bottom: [8, 16],
    }, 4, 12, scale, -2, 4)
  );
  // Left Leg (4x12x4)
  breath.appendChild(
    buildCuboid(skinUrl, 4, 12, 4, {
      front: [20, 52], back: [28, 52], right: [16, 52], left: [24, 52], top: [20, 48], bottom: [24, 48],
    }, 4, 12, scale, 2, 4)
  );

  rig.appendChild(breath);
  host.appendChild(rig);
}

function updateCharacterRigsRotation(): void {
  document.querySelectorAll('.mc-character-rig').forEach((el) => {
    (el as HTMLElement).style.transform = `rotateX(-8deg) rotateY(${state.dollAzimuth}deg)`;
  });
}

function updateCharacterSkins(): void {
  const acc = getSelectedAccount();
  const fallback = getFallbackSkinUrl();
  if (!acc) {
    state.currentSkinUrl = fallback;
    renderSkinViewport('premium-skin-3d', fallback);
    renderSkinViewport('clean-skin-3d', fallback);
    return;
  }

  // Render crisp procedural fallback immediately, then upgrade asynchronously if online skin loads
  if (!state.currentSkinUrl) {
    renderSkinViewport('premium-skin-3d', fallback);
    renderSkinViewport('clean-skin-3d', fallback);
  }
  const remoteUrl = `https://mc-heads.net/skin/${encodeURIComponent(acc.uuid)}`;
  if (state.currentSkinUrl === remoteUrl) return;

  const img = new Image();
  img.crossOrigin = 'anonymous';
  img.onload = () => {
    state.currentSkinUrl = remoteUrl;
    renderSkinViewport('premium-skin-3d', remoteUrl);
    renderSkinViewport('clean-skin-3d', remoteUrl);
  };
  img.onerror = () => {
    state.currentSkinUrl = fallback;
    renderSkinViewport('premium-skin-3d', fallback);
    renderSkinViewport('clean-skin-3d', fallback);
  };
  img.src = remoteUrl;
}

function bindSkinDragStage(stageId: string): void {
  const stage = document.getElementById(stageId);
  if (!stage) return;

  let dragging = false;
  let lastX = 0;
  let lastTime = 0;
  let velocity = 0;
  let flingRaf = 0;

  stage.addEventListener('pointerdown', (e) => {
    if ((e.target as HTMLElement).closest('button')) return;
    dragging = true;
    lastX = e.clientX;
    lastTime = performance.now();
    velocity = 0;
    if (flingRaf) cancelAnimationFrame(flingRaf);
    stage.setPointerCapture(e.pointerId);
  });

  stage.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const now = performance.now();
    const dx = e.clientX - lastX;
    const dt = Math.max(0.008, (now - lastTime) / 1000);
    state.dollAzimuth = (state.dollAzimuth + dx * 0.55) % 360;
    velocity = (dx * 0.55) / dt;
    lastX = e.clientX;
    lastTime = now;
    updateCharacterRigsRotation();
  });

  const endDrag = () => {
    if (!dragging) return;
    dragging = false;
    if (Math.abs(velocity) < 35) return;
    let prev = performance.now();
    const step = (now: number) => {
      const dt = Math.min(0.05, (now - prev) / 1000);
      prev = now;
      state.dollAzimuth = (state.dollAzimuth + velocity * dt) % 360;
      velocity *= Math.exp(-5 * dt);
      updateCharacterRigsRotation();
      if (Math.abs(velocity) > 15) {
        flingRaf = requestAnimationFrame(step);
      }
    };
    flingRaf = requestAnimationFrame(step);
  };

  stage.addEventListener('pointerup', endDrag);
  stage.addEventListener('pointercancel', endDrag);
}

/* ---------------- Theme & Wallpaper Application ---------------- */

async function applyThemeAndWallpaper(): Promise<void> {
  if (!state.settings) return;
  const preset = state.settings.themePreset || 'aurora-teal';
  document.body.setAttribute('data-theme', preset);
  document.body.setAttribute('data-motion', state.settings.themeMotion === false ? 'off' : 'on');

  const transparency = Math.max(30, Math.min(100, Number(state.settings.layoutTransparency ?? 100)));
  document.documentElement.style.setProperty('--glass-alpha', String(transparency / 100));

  const blurPx = Math.max(0, Math.min(16, Number(state.settings.wallpaperBlur ?? 2)));
  document.documentElement.style.setProperty('--wallpaper-blur', `${blurPx}px`);

  if (preset === 'custom' && state.settings.customAccentHex) {
    applyAccentColor(state.settings.customAccentHex);
  } else {
    const info = THEME_ACCENTS[preset] ?? THEME_ACCENTS['aurora-teal'];
    applyAccentColor(info.hex);
  }

  const backdrop = $('wallpaper-backdrop');
  backdrop.style.opacity = String(state.settings.wallpaperOpacity ?? 0.44);

  const wp = state.wallpapers.find((w) => w.id === state.settings.wallpaperId) ?? state.wallpapers[0];
  if (wp?.animatedScene) {
    backdrop.style.backgroundImage = 'none';
    startAnimatedWallpaper(wp.id);
  } else if (wp && aerix) {
    stopAnimatedWallpaper();
    let url = state.wallpaperUrls.get(wp.id);
    if (!url) {
      url = (await aerix.getAssetDataUrl(`wallpapers/${wp.assetFile}`)) || '';
      state.wallpaperUrls.set(wp.id, url);
    }
    if (url) {
      backdrop.style.backgroundImage = `url("${url}")`;
    }
  }
}

function getSelectedAccount(): any | null {
  if (!state.accounts.length) return null;
  return state.accounts.find((a) => a.id === state.settings?.selectedAccountId) ?? state.accounts[0];
}

function getSelectedInstance(): any | null {
  if (!state.instances.length) return null;
  return state.instances.find((i) => i.id === state.settings?.selectedInstanceId) ?? state.instances[0];
}

function renderAccountChip(): void {
  const acc = getSelectedAccount();
  const avatar = $('rail-account-avatar');
  if (!acc) {
    avatar.textContent = '?';
    $('rail-account-name').textContent = 'Account';
    $('rail-account-type').textContent = 'Sign in';
    $('tile-accounts-sub').textContent = 'Sign in or switch';
    $('premium-account-pill').textContent = '+ Add Account';
    $('clean-username').textContent = 'Aerix';
    $('clean-account-btn').textContent = 'ADD MINECRAFT ACCOUNT';
  } else {
    const headUrl = `https://mc-heads.net/avatar/${encodeURIComponent(acc.uuid)}/64`;
    avatar.innerHTML = `<img src="${headUrl}" alt="${escapeHtml(acc.username)}" />`;
    $('rail-account-name').textContent = acc.username;
    $('rail-account-type').textContent = acc.type;
    $('tile-accounts-sub').textContent = `${acc.username} (${acc.type})`;
    $('premium-account-pill').textContent = acc.username;
    $('clean-username').textContent = acc.username;
    $('clean-account-btn').textContent = 'DRAG TO ROTATE';
  }
  updateCharacterSkins();
}

function renderHome(): void {
  const uiStyle = state.settings?.uiStyle === 'clean' ? 'clean' : 'premium';
  $('style-pill-premium').classList.toggle('active', uiStyle === 'premium');
  $('style-pill-clean').classList.toggle('active', uiStyle === 'clean');
  $('home-premium-layout').classList.toggle('hidden', uiStyle !== 'premium');
  $('home-clean-layout').classList.toggle('hidden', uiStyle !== 'clean');

  const inst = getSelectedInstance();
  const chips = $('home-instance-chips');
  const running = inst ? state.runningIds.has(inst.id) : false;

  if (!inst) {
    $('home-instance-badge').textContent = 'ACTIVE WORLD';
    $('home-instance-title').textContent = 'Welcome to Aerix Launcher PC';
    $('home-instance-subtitle').textContent =
      'Create a Vanilla, Fabric, Forge, NeoForge, Quilt, or 1-click Aerix Client PC instance.';
    chips.innerHTML = '';
    $('home-play-label').textContent = 'CREATE INSTANCE';
    $('home-play-btn').classList.remove('stop-mode');
    $('premium-picker-name').textContent = 'No Instance';
    $('premium-picker-ver').textContent = 'Select or Create';
    $('premium-play-label').textContent = 'CREATE';
    $('premium-play-btn').classList.remove('stop-mode');
    $('clean-picker-name').textContent = 'Select instance';
    $('clean-picker-ver').textContent = 'Choose a Minecraft instance';
    $('clean-play-label').textContent = 'CREATE';
    $('clean-play-btn').classList.remove('stop-mode');
  } else {
    const loaderTag = inst.aerixClient ? 'Aerix Client • Fabric' : inst.loader.toUpperCase();
    $('home-instance-badge').textContent = inst.aerixClient ? 'AERIX CLIENT PC' : 'ACTIVE WORLD';
    $('home-instance-title').textContent = inst.name;
    $('home-instance-subtitle').textContent = `${loaderTag} · Minecraft ${inst.mcVersion}`;
    const ram = inst.ramMb ?? state.settings?.defaultRamMb ?? 4096;
    const mins = Math.round((inst.playTimeSec || 0) / 60);
    chips.innerHTML = `
      <span class="chip">MC ${escapeHtml(inst.mcVersion)}</span>
      <span class="chip">${escapeHtml(inst.loader)}</span>
      <span class="chip">${ram} MB</span>
      <span class="chip">${mins}m played</span>
    `;
    $('home-play-label').textContent = running ? 'STOP GAME' : 'PLAY';
    $('home-play-btn').classList.toggle('stop-mode', running);
    $('premium-picker-name').textContent = inst.name;
    $('premium-picker-ver').textContent = `${inst.aerixClient ? 'Aerix Client' : inst.loader} ${inst.mcVersion}`;
    $('premium-play-label').textContent = running ? 'STOP' : 'PLAY';
    $('premium-play-btn').classList.toggle('stop-mode', running);
    $('clean-picker-name').textContent = inst.name;
    $('clean-picker-ver').textContent = `${inst.aerixClient ? 'Aerix Client' : inst.loader} ${inst.mcVersion}`;
    $('clean-play-label').textContent = running ? 'STOP' : 'PLAY';
    $('clean-play-btn').classList.toggle('stop-mode', running);
  }

  $('tile-library-sub').textContent = `${state.instances.length} ${state.instances.length === 1 ? 'instance' : 'instances'}`;
  $('home-ready-sub').textContent = state.runningIds.size > 0
    ? `${state.runningIds.size} game running`
    : getSelectedAccount()
    ? 'Ready to launch'
    : 'Sign in an account to launch';

  // Dropdown menus for Premium & Clean instance selectors
  const dropdownHtml = state.instances.length
    ? state.instances
        .map(
          (i) => `
          <button type="button" class="dropdown-item" data-pick-inst="${escapeHtml(i.id)}">
            <strong>${escapeHtml(i.name)}</strong>
            <span>${escapeHtml(i.aerixClient ? 'Aerix Client' : i.loader)} · ${escapeHtml(i.mcVersion)}</span>
          </button>`
        )
        .join('') + `<button type="button" class="dropdown-item accent" data-open-library="1">Manage All Instances...</button>`
    : `<button type="button" class="dropdown-item accent" data-open-create="1">+ Create First Instance</button>`;

  $('premium-inst-dropdown').innerHTML = dropdownHtml;
  $('clean-inst-dropdown').innerHTML = dropdownHtml;

  // Recent worlds
  const mini = $('home-instances-list');
  if (!state.instances.length) {
    mini.innerHTML = '<div class="muted">No instances yet. Click "+ Create" on the rail or "Quick Aerix Client".</div>';
  } else {
    mini.innerHTML = state.instances
      .slice(0, 6)
      .map(
        (item) => `
        <div class="recent-world-row ${item.id === inst?.id ? 'selected' : ''}" data-select-inst="${escapeHtml(item.id)}">
          <div class="recent-row-badge">${escapeHtml((item.aerixClient ? 'AC' : item.loader.slice(0, 2)).toUpperCase())}</div>
          <div class="recent-row-info">
            <strong>${escapeHtml(item.name)}</strong>
            <span>${escapeHtml(item.aerixClient ? 'Aerix Client' : item.loader)} · ${escapeHtml(item.mcVersion)}</span>
          </div>
          <button type="button" class="recent-row-btn" data-quick-edit="${escapeHtml(item.id)}" title="Manage">&#9998;</button>
          <button type="button" class="recent-row-btn" data-quick-play="${escapeHtml(item.id)}" title="Play">&#9654;</button>
        </div>
      `
      )
      .join('');
  }

  const acc = getSelectedAccount();
  const caps = state.capabilities;
  $('home-stats-list').innerHTML = `
    <div class="stat-row"><span>Active Account</span><strong>${acc ? escapeHtml(`${acc.username} (${acc.type})`) : 'Not signed in'}</strong></div>
    <div class="stat-row"><span>Platform</span><strong>${caps ? `${caps.platform} (${caps.arch})` : 'Desktop'}</strong></div>
    <div class="stat-row"><span>Memory Pool</span><strong>${state.settings?.defaultRamMb || 4096} MB / ${caps?.totalMemoryMb || '?'} MB</strong></div>
    <div class="stat-row"><span>Running Games</span><strong>${state.runningIds.size}</strong></div>
  `;
}

function renderClientHub(): void {
  const host = $('client-version-pills');
  host.innerHTML = state.aerixClientVersions
    .map(
      (v) =>
        `<button type="button" class="client-ver-pill ${v === state.selectedClientVersion ? 'active' : ''}" data-client-ver="${escapeHtml(v)}">${escapeHtml(v)}</button>`
    )
    .join('');
  $('client-cta-label').textContent = `Create & Launch Aerix Client (${state.selectedClientVersion})`;
}

function renderInstances(): void {
  const filter = (($('instance-search-input') as HTMLInputElement).value || '').trim().toLowerCase();
  const selectedId = state.settings?.selectedInstanceId;
  const grid = $('instances-grid');
  const filtered = state.instances.filter(
    (i) => !filter || i.name.toLowerCase().includes(filter) || i.mcVersion.includes(filter) || i.loader.includes(filter)
  );

  if (!filtered.length) {
    grid.innerHTML = '<div class="liquid-glass section-card"><p class="muted">No matching instances. Create one with "+ New Instance" or "Aerix Client".</p></div>';
  } else {
    grid.innerHTML = filtered
      .map((inst) => {
        const running = state.runningIds.has(inst.id);
        return `
          <div class="liquid-glass instance-card ${inst.id === selectedId ? 'selected' : ''}">
            <div class="instance-card-top">
              <div>
                <div class="instance-title">${escapeHtml(inst.name)}</div>
                <div class="muted">Minecraft ${escapeHtml(inst.mcVersion)}</div>
              </div>
              <span class="badge ${inst.aerixClient ? 'badge-aerix' : ''}">${inst.aerixClient ? 'Aerix Client' : escapeHtml(inst.loader)}</span>
            </div>
            <div class="muted">${inst.ramMb ?? state.settings?.defaultRamMb ?? 4096} MB RAM • ${Math.round((inst.playTimeSec || 0) / 60)}m played</div>
            <div class="instance-actions">
              <button type="button" class="btn-primary btn-sm" data-inst-play="${escapeHtml(inst.id)}">${running ? 'Stop' : 'Play'}</button>
              <button type="button" class="btn-secondary btn-sm" data-inst-manage="${escapeHtml(inst.id)}">Manage</button>
              <button type="button" class="btn-ghost btn-sm" data-inst-select="${escapeHtml(inst.id)}">Select</button>
            </div>
          </div>
        `;
      })
      .join('');
  }

  const targetSel = $('discover-target-instance') as HTMLSelectElement;
  targetSel.innerHTML = state.instances.length
    ? state.instances
        .map(
          (i) =>
            `<option value="${escapeHtml(i.id)}" ${i.id === selectedId ? 'selected' : ''}>${escapeHtml(i.name)} (${escapeHtml(i.mcVersion)} ${escapeHtml(i.loader)})</option>`
        )
        .join('')
    : '<option value="">Create an instance (or install a modpack)</option>';
}

async function openInspector(instanceId: string): Promise<void> {
  state.inspectedInstanceId = instanceId;
  const inst = state.instances.find((i) => i.id === instanceId);
  if (!inst) return;
  $('instance-inspector').classList.remove('hidden');
  $('inspector-title').textContent = inst.name;
  $('inspector-loader-badge').textContent = `${inst.mcVersion} • ${inst.aerixClient ? 'Aerix Client' : inst.loader}`;
  $('inspector-play-btn').textContent = state.runningIds.has(inst.id) ? 'Stop' : 'Play';
  await renderInspectorBody();
}

async function renderInspectorBody(): Promise<void> {
  const inst = state.instances.find((i) => i.id === state.inspectedInstanceId);
  if (!inst) return;
  const body = $('inspector-body');
  const tab = state.inspectorTab;
  $('inspector-content-toolbar').classList.toggle('hidden', tab === 'config' || tab === 'saves' || tab === 'screenshots');

  document.querySelectorAll('#inspector-tabs .tab-btn').forEach((b) => {
    b.classList.toggle('active', b.getAttribute('data-tab') === tab);
  });

  if (tab === 'config') {
    body.innerHTML = `
      <label class="field-label">Instance Name</label>
      <input id="insp-name" class="input" type="text" value="${escapeHtml(inst.name)}" />
      <label class="field-label">Memory Allocation (MB, leave empty for global default)</label>
      <input id="insp-ram" class="input" type="number" min="512" max="32768" value="${inst.ramMb ?? ''}" placeholder="Default (${state.settings?.defaultRamMb ?? 4096} MB)" />
      <label class="field-label">Custom JVM Arguments</label>
      <input id="insp-jvm" class="input" type="text" value="${escapeHtml(inst.jvmArgs || '')}" placeholder="Inherit global JVM args" />
      <label class="field-label">Custom Java Executable Path</label>
      <input id="insp-java" class="input" type="text" value="${escapeHtml(inst.javaPath || '')}" placeholder="Auto-managed Adoptium Java" />
      <label class="checkbox-row">
        <input id="insp-fullscreen" type="checkbox" ${inst.fullscreen ? 'checked' : ''} />
        <span>Launch in fullscreen</span>
      </label>
      <div class="auth-row" style="margin-top:10px; justify-content:space-between;">
        <button type="button" class="btn-danger btn-sm" id="insp-delete-btn">Delete Instance</button>
        <button type="button" class="btn-primary btn-sm" id="insp-save-btn">Save Changes</button>
      </div>
    `;
    return;
  }

  if (!aerix) return;
  const entries = await aerix.listContent(inst.id, tab);
  if (!entries.length) {
    body.innerHTML = `<div class="muted" style="padding:12px;">No ${tab} installed yet.</div>`;
    return;
  }

  body.innerHTML = entries
    .map(
      (entry) => `
      <div class="content-row">
        <div>
          <div class="content-row-name">${escapeHtml(entry.name)}</div>
          <div class="content-row-meta">${escapeHtml(entry.fileName)} • ${formatBytes(entry.sizeBytes)}</div>
        </div>
        <div class="content-row-actions">
          ${
            tab === 'mods' || tab === 'resourcepacks'
              ? `<button type="button" class="btn-secondary btn-sm" data-toggle-file="${escapeHtml(entry.fileName)}" data-enabled="${entry.enabled}">${entry.enabled ? 'Enabled' : 'Disabled'}</button>`
              : ''
          }
          <button type="button" class="btn-danger btn-sm" data-del-file="${escapeHtml(entry.fileName)}">Remove</button>
        </div>
      </div>
    `
    )
    .join('');
}

function renderAccounts(): void {
  const list = $('accounts-list');
  const selectedId = state.settings?.selectedAccountId;
  if (!state.accounts.length) {
    list.innerHTML = '<div class="muted">No accounts added yet. Sign in on the right.</div>';
    return;
  }

  list.innerHTML = state.accounts
    .map((acc) => {
      const active = acc.id === selectedId || (!selectedId && acc === state.accounts[0]);
      return `
        <div class="account-row ${active ? 'active' : ''}">
          <div class="account-row-left">
            <div class="account-avatar">
              <img src="https://mc-heads.net/avatar/${encodeURIComponent(acc.uuid)}/64" alt="${escapeHtml(acc.username)}" />
            </div>
            <div>
              <strong>${escapeHtml(acc.username)}</strong>
              <div class="muted">${escapeHtml(acc.type.toUpperCase())}${acc.email ? ` • ${escapeHtml(acc.email)}` : ''}</div>
            </div>
          </div>
          <div class="auth-row">
            <button type="button" class="btn-secondary btn-sm" data-acc-use="${escapeHtml(acc.id)}">${active ? 'Active' : 'Use'}</button>
            <button type="button" class="btn-danger btn-sm" data-acc-del="${escapeHtml(acc.id)}">Remove</button>
          </div>
        </div>
      `;
    })
    .join('');
}

async function runDiscoverSearch(): Promise<void> {
  if (!aerix) return;
  const grid = $('discover-results');
  grid.innerHTML = '<div class="liquid-glass section-card"><p class="muted">Searching...</p></div>';
  try {
    const query = ($('discover-query-input') as HTMLInputElement).value.trim();
    const mcVersion = ($('discover-version-select') as HTMLSelectElement).value;
    const loader = ($('discover-loader-select') as HTMLSelectElement).value;
    const results = await aerix.searchDiscover({
      platform: state.discoverPlatform,
      projectType: state.discoverType,
      query,
      mcVersion: mcVersion || undefined,
      loader: loader || undefined,
      sort: query ? 'relevance' : 'downloads',
      limit: 24,
    });
    state.discoverResults = results;
    if (!results.length) {
      grid.innerHTML = '<div class="liquid-glass section-card"><p class="muted">No matching projects found.</p></div>';
      return;
    }

    grid.innerHTML = results
      .map(
        (p, idx) => `
        <div class="liquid-glass discover-card">
          <div class="discover-card-head">
            ${p.iconUrl ? `<img class="discover-icon" src="${escapeHtml(p.iconUrl)}" alt="" />` : '<div class="discover-icon"></div>'}
            <div>
              <div class="discover-title">${escapeHtml(p.title)}</div>
              <div class="muted">by ${escapeHtml(p.author)} • ${formatNumber(p.downloads)} downloads</div>
            </div>
          </div>
          <p class="discover-desc">${escapeHtml(p.description || '')}</p>
          <div class="discover-foot">
            <span class="badge">${escapeHtml(p.platform)}</span>
            <div class="auth-row">
              <button type="button" class="btn-ghost btn-sm" data-disc-web="${idx}">Page</button>
              <button type="button" class="btn-primary btn-sm" data-disc-install="${idx}">Install</button>
            </div>
          </div>
        </div>
      `
      )
      .join('');
  } catch (err: any) {
    grid.innerHTML = `<div class="liquid-glass section-card"><p class="muted">Search error: ${escapeHtml(err?.message || String(err))}</p></div>`;
  }
}

async function openDiscoverInstallModal(project: any): Promise<void> {
  if (!project || !aerix) return;
  const modal = $('modal-discover-install');
  modal.classList.remove('hidden');
  $('discover-modal-title').textContent = `Install ${project.title}`;
  $('discover-modal-desc').textContent = project.description || '';
  const list = $('discover-versions-list');
  list.innerHTML = '<div class="muted">Fetching compatible releases...</div>';

  const targetId = ($('discover-target-instance') as HTMLSelectElement).value;
  const targetInst = state.instances.find((i) => i.id === targetId);
  const mcVersion = ($('discover-version-select') as HTMLSelectElement).value || targetInst?.mcVersion || undefined;
  const loader = ($('discover-loader-select') as HTMLSelectElement).value || (targetInst && targetInst.loader !== 'vanilla' ? targetInst.loader : undefined);

  try {
    const versions = await aerix.fetchDiscoverVersions(project.platform, project.id, { mcVersion, loader });
    if (!versions.length) {
      list.innerHTML = '<div class="muted">No matching versions for the active MC/loader filter. Clear filters and retry.</div>';
      return;
    }
    list.innerHTML = versions
      .map(
        (v, idx) => `
        <div class="version-row">
          <div>
            <strong>${escapeHtml(v.name || v.versionNumber)}</strong>
            <div class="muted">${escapeHtml((v.gameVersions || []).slice(0, 4).join(', '))} • ${escapeHtml((v.loaders || []).join(', '))}</div>
          </div>
          <button type="button" class="btn-primary btn-sm" data-ver-pick="${idx}">Install</button>
        </div>
      `
      )
      .join('');

    list.querySelectorAll('[data-ver-pick]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        const ver = versions[Number(btn.getAttribute('data-ver-pick'))];
        (btn as HTMLButtonElement).disabled = true;
        (btn as HTMLButtonElement).textContent = 'Installing...';
        try {
          const res = await aerix.installDiscoverFile({
            projectTitle: project.title,
            projectType: project.projectType,
            version: ver,
            instanceId: targetId || undefined,
          });
          modal.classList.add('hidden');
          await refreshAllData();
          showToast(
            res.createdInstance
              ? `Created modpack instance "${res.createdInstance.name}"!`
              : `Installed ${project.title}!`
          );
        } catch (err: any) {
          showToast(err?.message || 'Install failed', true);
          (btn as HTMLButtonElement).disabled = false;
          (btn as HTMLButtonElement).textContent = 'Install';
        }
      })
    );
  } catch (err: any) {
    list.innerHTML = `<div class="muted">Failed to fetch versions: ${escapeHtml(err?.message || String(err))}</div>`;
  }
}

function renderConsole(): void {
  const filter = (($('console-filter-input') as HTMLInputElement).value || '').toLowerCase();
  const out = $('console-output');
  const filtered = filter ? state.logs.filter((l) => l.line.toLowerCase().includes(filter)) : state.logs;
  out.innerHTML = filtered
    .slice(-500)
    .map(
      (l) =>
        `<div class="log-line log-${escapeHtml(l.stream)}">[${new Date(l.timestamp).toLocaleTimeString()}] [${l.stream.toUpperCase()}] ${escapeHtml(l.line)}</div>`
    )
    .join('');
  out.scrollTop = out.scrollHeight;
}

async function renderSettings(): Promise<void> {
  if (!state.settings) return;
  document.querySelectorAll('.swatch-btn').forEach((b) => {
    b.classList.toggle('active', b.getAttribute('data-theme') === state.settings.themePreset);
  });

  const colorHex = state.settings.customAccentHex || THEME_ACCENTS[state.settings.themePreset]?.hex || '#8DEFE0';
  ($('setting-custom-color') as HTMLInputElement).value = colorHex;
  $('setting-custom-color-hex').textContent = colorHex.toUpperCase();
  ($('setting-theme-motion') as HTMLInputElement).checked = state.settings.themeMotion !== false;

  const ramSlider = $('setting-ram-slider') as HTMLInputElement;
  if (state.capabilities?.totalMemoryMb) {
    ramSlider.max = String(Math.max(4096, Math.min(32768, state.capabilities.totalMemoryMb)));
  }
  ramSlider.value = String(state.settings.defaultRamMb);
  $('setting-ram-val').textContent = `${state.settings.defaultRamMb} MB`;
  ($('setting-jvm-args') as HTMLInputElement).value = state.settings.defaultJvmArgs || '';
  ($('setting-width') as HTMLInputElement).value = String(state.settings.defaultWidth || 1280);
  ($('setting-height') as HTMLInputElement).value = String(state.settings.defaultHeight || 720);
  ($('setting-close-behavior') as HTMLSelectElement).value = state.settings.closeOnLaunch || 'minimize';
  ($('setting-show-snapshots') as HTMLInputElement).checked = Boolean(state.settings.showSnapshots);
  ($('setting-cf-key') as HTMLInputElement).value = state.settings.curseforgeApiKey || '';
  $('setting-data-dir').textContent = state.capabilities?.dataDir || '';

  const opPct = Math.round((state.settings.wallpaperOpacity ?? 0.44) * 100);
  ($('setting-wallpaper-opacity') as HTMLInputElement).value = String(opPct);
  $('wallpaper-opacity-val').textContent = `${opPct}%`;

  const blurPx = Number(state.settings.wallpaperBlur ?? 2);
  ($('setting-wallpaper-blur') as HTMLInputElement).value = String(blurPx);
  $('wallpaper-blur-val').textContent = `${blurPx}px`;

  const glassPct = Number(state.settings.layoutTransparency ?? 100);
  ($('setting-layout-transparency') as HTMLInputElement).value = String(glassPct);
  $('layout-transparency-val').textContent = `${glassPct}%`;

  const wpGrid = $('wallpaper-grid');
  wpGrid.innerHTML = '';
  for (const wp of state.wallpapers) {
    const tile = document.createElement('button');
    tile.type = 'button';
    tile.className = `wallpaper-tile ${wp.id === state.settings.wallpaperId ? 'active' : ''}`;
    tile.setAttribute('data-wp-id', wp.id);
    if (wp.animatedScene) {
      const cfg = ANIMATED_SCENES[wp.id];
      if (cfg) {
        tile.style.background = `linear-gradient(135deg, ${cfg.sky[0]}, ${cfg.sky[2]})`;
      }
      tile.innerHTML = `<span class="wallpaper-tile-tag">LIVE</span><span>${escapeHtml(wp.title)}</span>`;
    } else {
      tile.innerHTML = `<span>${escapeHtml(wp.title)}</span>`;
      if (aerix) {
        let url = state.wallpaperUrls.get(wp.id);
        if (!url) {
          url = (await aerix.getAssetDataUrl(`wallpapers/${wp.assetFile}`)) || '';
          state.wallpaperUrls.set(wp.id, url);
        }
        if (url) tile.style.backgroundImage = `linear-gradient(180deg, transparent 20%, rgba(0,0,0,0.68)), url("${url}")`;
      }
    }
    wpGrid.appendChild(tile);
  }
}

async function refreshJavaList(): Promise<void> {
  if (!aerix || typeof aerix.discoverJava !== 'function') return;
  const list = $('java-runtimes-list');
  list.innerHTML = '<div class="muted">Scanning Java runtimes...</div>';
  try {
    const runtimes = await aerix.discoverJava();
    if (!runtimes.length) {
      list.innerHTML = '<div class="muted">No Java runtimes found yet — Aerix will auto-download Temurin when you launch, or click Install above.</div>';
      return;
    }
    list.innerHTML = runtimes
      .map(
        (r) => `
      <div class="java-item">
        <strong>Java ${r.majorVersion} (${escapeHtml(r.versionString)})</strong>
        <span class="muted">${r.source} • ${escapeHtml(r.executablePath)}</span>
      </div>
    `
      )
      .join('');
  } catch (err: any) {
    list.innerHTML = `<div class="muted">Failed to scan Java: ${escapeHtml(err?.message || String(err))}</div>`;
  }
}

async function openCreateInstanceModal(presetAerixClient = false): Promise<void> {
  const modal = $('modal-new-instance');
  modal.classList.remove('hidden');
  const aerixCheck = $('new-inst-aerix') as HTMLInputElement;
  const loaderSel = $('new-inst-loader') as HTMLSelectElement;
  const nameInput = $('new-inst-name') as HTMLInputElement;

  aerixCheck.checked = presetAerixClient;
  loaderSel.disabled = presetAerixClient;
  if (presetAerixClient) {
    loaderSel.value = 'fabric';
    nameInput.value = 'Aerix Client PC';
  } else {
    nameInput.value = '';
  }

  const mcSel = $('new-inst-mc') as HTMLSelectElement;
  mcSel.innerHTML = state.mcVersions
    .map((v) => `<option value="${escapeHtml(v.id)}">${escapeHtml(v.id)}</option>`)
    .join('');

  if (aerix) {
    try {
      const versions = presetAerixClient
        ? (await aerix.getAerixClientVersions()).map((id) => ({ id, type: 'release' }))
        : await Promise.race([
            aerix.fetchMinecraftVersions(Boolean(state.settings?.showSnapshots)),
            new Promise<Array<{ id: string; type: string }>>((_, rej) => setTimeout(() => rej(new Error('timeout')), 6000)),
          ]);
      if (versions.length) {
        state.mcVersions = versions;
        mcSel.innerHTML = versions
          .map((v) => `<option value="${escapeHtml(v.id)}">${escapeHtml(v.id)}</option>`)
          .join('');
      }
    } catch {
      // Keep cached/fallback list
    }
  }
  await refreshCreateModalLoaders();
}

async function refreshCreateModalLoaders(): Promise<void> {
  const mcVersion = ($('new-inst-mc') as HTMLSelectElement).value;
  const loader = ($('new-inst-loader') as HTMLSelectElement).value;
  const verSel = $('new-inst-loader-ver') as HTMLSelectElement;
  if (!mcVersion || loader === 'vanilla' || !aerix) {
    verSel.innerHTML = '<option value="">Latest compatible</option>';
    return;
  }
  verSel.innerHTML = '<option value="">Loading...</option>';
  try {
    const loaders = await aerix.fetchLoaderVersions(loader, mcVersion);
    verSel.innerHTML = loaders.length
      ? loaders.slice(0, 20).map((l) => `<option value="${escapeHtml(l)}">${escapeHtml(l)}</option>`).join('')
      : '<option value="">Latest compatible</option>';
  } catch {
    verSel.innerHTML = '<option value="">Latest compatible</option>';
  }
}

async function triggerLaunchOrStop(instanceId?: string): Promise<void> {
  const inst = instanceId
    ? state.instances.find((i) => i.id === instanceId)
    : getSelectedInstance();
  if (!inst) {
    await openCreateInstanceModal(false);
    return;
  }
  if (!aerix) {
    showToast('Launcher backend bridge is unavailable.', true);
    return;
  }
  if (state.runningIds.has(inst.id)) {
    await aerix.stopInstance(inst.id);
    showToast(`Stopping ${inst.name}...`);
    return;
  }
  try {
    $('launch-progress-panel').classList.remove('hidden');
    await aerix.launchInstance(inst.id);
    await refreshAllData();
  } catch (err: any) {
    showToast(err?.message || 'Failed to launch game', true);
    $('launch-progress-panel').classList.add('hidden');
  }
}

async function refreshAllData(): Promise<void> {
  if (!aerix) return;
  const [caps, settings, accounts, instances, wallpapers, runningIds] = await Promise.all([
    aerix.getCapabilities(),
    aerix.getSettings(),
    aerix.listAccounts(),
    aerix.listInstances(),
    aerix.getWallpapers(),
    aerix.getRunningInstances(),
  ]);
  state.capabilities = caps;
  state.settings = settings;
  state.accounts = accounts;
  state.instances = instances;
  state.wallpapers = wallpapers;
  state.runningIds = new Set(runningIds);
  $('app-version-pill').textContent = `v${caps.appVersion}`;
  await applyThemeAndWallpaper();
  renderAccountChip();
  renderHome();
  renderClientHub();
  renderInstances();
  renderAccounts();
}

function closeInstancePickers(): void {
  document.getElementById('premium-inst-dropdown')?.classList.add('hidden');
  document.getElementById('clean-inst-dropdown')?.classList.add('hidden');
}

function wireEvents(): void {
  // Window controls
  $('win-min').addEventListener('click', () => aerix?.windowMinimize());
  $('win-max').addEventListener('click', () => aerix?.windowMaximizeToggle());
  $('win-close').addEventListener('click', () => aerix?.windowClose());

  // Left Capsule Navigation Rail
  $('rail-brand-home').addEventListener('click', () => switchView('home'));
  $('rail-create-btn').addEventListener('click', () => void openCreateInstanceModal(false));
  document.querySelectorAll('.rail-btn[data-view]').forEach((btn) =>
    btn.addEventListener('click', () => {
      const view = btn.getAttribute('data-view');
      if (view) switchView(view);
    })
  );

  // Right Capsule Utility Rail
  $('util-settings').addEventListener('click', () => switchView('settings'));
  $('rail-account-chip').addEventListener('click', () => switchView('accounts'));
  $('util-client').addEventListener('click', () => switchView('client'));
  $('util-instances').addEventListener('click', () => switchView('instances'));
  $('util-discover').addEventListener('click', () => switchView('discover'));
  $('util-folder').addEventListener('click', () => aerix?.openDataDir());
  $('util-logs').addEventListener('click', () => switchView('console'));

  // Home UI Style Switcher (Premium vs Clean)
  document.querySelectorAll('[data-ui-style]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      const style = btn.getAttribute('data-ui-style') === 'clean' ? 'clean' : 'premium';
      if (aerix) {
        state.settings = await aerix.saveSettings({ uiStyle: style });
      } else {
        state.settings = { ...(state.settings || {}), uiStyle: style };
      }
      renderHome();
    })
  );

  // Home buttons & Quick Tiles & Quick Tools
  $('home-play-btn').addEventListener('click', () => void triggerLaunchOrStop());
  $('premium-play-btn').addEventListener('click', () => void triggerLaunchOrStop());
  $('clean-play-btn').addEventListener('click', () => void triggerLaunchOrStop());
  $('home-quick-aerix-btn').addEventListener('click', () => void openCreateInstanceModal(true));
  $('home-edit-inst-btn').addEventListener('click', () => {
    const inst = getSelectedInstance();
    if (!inst) {
      void openCreateInstanceModal(false);
    } else {
      switchView('instances');
      void openInspector(inst.id);
    }
  });
  $('home-view-all-instances').addEventListener('click', () => switchView('instances'));
  $('home-open-settings').addEventListener('click', () => switchView('settings'));
  $('premium-account-pill').addEventListener('click', () => switchView('accounts'));
  $('clean-account-btn').addEventListener('click', () => {
    if (!getSelectedAccount()) switchView('accounts');
  });

  $('tile-client').addEventListener('click', () => switchView('client'));
  $('tile-discover').addEventListener('click', () => switchView('discover'));
  $('tile-library').addEventListener('click', () => switchView('instances'));
  $('tile-accounts').addEventListener('click', () => switchView('accounts'));

  $('qt-discover').addEventListener('click', () => switchView('discover'));
  $('qt-content').addEventListener('click', () => {
    const inst = getSelectedInstance();
    switchView('instances');
    if (inst) void openInspector(inst.id);
  });
  $('qt-folder').addEventListener('click', () => {
    const inst = getSelectedInstance();
    if (inst && aerix) void aerix.openInstanceFolder(inst.id);
    else void aerix?.openDataDir();
  });
  $('qt-logs').addEventListener('click', () => switchView('console'));
  $('qt-settings').addEventListener('click', () => switchView('settings'));

  // Instance Selector Dropdowns (Premium & Clean)
  $('premium-inst-picker-btn').addEventListener('click', (e) => {
    e.stopPropagation();
    $('premium-inst-dropdown').classList.toggle('hidden');
  });
  $('clean-inst-picker-btn').addEventListener('click', (e) => {
    e.stopPropagation();
    $('clean-inst-dropdown').classList.toggle('hidden');
  });
  document.addEventListener('click', (e) => {
    const target = e.target as HTMLElement;
    if (!target.closest('.picker-anchor')) {
      closeInstancePickers();
    }
  });

  // 3D Skin Stage Drag Handlers
  bindSkinDragStage('premium-skin-stage');
  bindSkinDragStage('clean-skin-stage');

  // Aerix Client Hub
  $('btn-install-aerix-client').addEventListener('click', async () => {
    if (!aerix) return;
    const ver = state.selectedClientVersion || '1.21.4';
    const existing = state.instances.find((i) => i.aerixClient && i.mcVersion === ver);
    if (existing) {
      state.settings = await aerix.saveSettings({ selectedInstanceId: existing.id });
      await triggerLaunchOrStop(existing.id);
      return;
    }
    const created = await aerix.createInstance({
      name: `Aerix Client ${ver}`,
      mcVersion: ver,
      loader: 'fabric',
      aerixClient: true,
    });
    await refreshAllData();
    showToast(`Created Aerix Client ${ver}! Starting launch...`);
    await triggerLaunchOrStop(created.id);
  });

  // Instances & Inspector
  $('instance-search-input').addEventListener('input', renderInstances);
  $('btn-new-instance').addEventListener('click', () => void openCreateInstanceModal(false));
  $('btn-new-aerix-client').addEventListener('click', () => void openCreateInstanceModal(true));
  $('inspector-close-btn').addEventListener('click', () => $('instance-inspector').classList.add('hidden'));
  $('inspector-play-btn').addEventListener('click', () => {
    if (state.inspectedInstanceId) void triggerLaunchOrStop(state.inspectedInstanceId);
  });
  $('inspector-folder-btn').addEventListener('click', () => {
    if (state.inspectedInstanceId && aerix) void aerix.openInstanceFolder(state.inspectedInstanceId);
  });
  $('inspector-import-btn').addEventListener('click', async () => {
    if (!state.inspectedInstanceId || !aerix) return;
    await aerix.importContentDialog(state.inspectedInstanceId, state.inspectorTab);
    await renderInspectorBody();
  });
  $('inspector-discover-btn').addEventListener('click', () => switchView('discover'));
  document.querySelectorAll('#inspector-tabs .tab-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      state.inspectorTab = btn.getAttribute('data-tab') as any;
      void renderInspectorBody();
    })
  );

  // Create Instance Modal
  $('new-inst-aerix').addEventListener('change', () => {
    const checked = ($('new-inst-aerix') as HTMLInputElement).checked;
    void openCreateInstanceModal(checked);
  });
  $('new-inst-mc').addEventListener('change', () => void refreshCreateModalLoaders());
  $('new-inst-loader').addEventListener('change', () => void refreshCreateModalLoaders());
  $('btn-confirm-create-instance').addEventListener('click', async () => {
    if (!aerix) return;
    const aerixClient = ($('new-inst-aerix') as HTMLInputElement).checked;
    const mcVersion = ($('new-inst-mc') as HTMLSelectElement).value || '1.21.4';
    const loader = ($('new-inst-loader') as HTMLSelectElement).value;
    const loaderVersion = ($('new-inst-loader-ver') as HTMLSelectElement).value;
    const name = ($('new-inst-name') as HTMLInputElement).value.trim() || `${aerixClient ? 'Aerix Client' : loader} ${mcVersion}`;
    await aerix.createInstance({ name, mcVersion, loader, loaderVersion: loaderVersion || undefined, aerixClient });
    $('modal-new-instance').classList.add('hidden');
    await refreshAllData();
    showToast(`Created instance "${name}".`);
  });

  document.querySelectorAll('[data-close-modal]').forEach((btn) =>
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-close-modal')!;
      $(id).classList.add('hidden');
    })
  );

  // Discover
  document.querySelectorAll('#discover-platform-seg .seg-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      document.querySelectorAll('#discover-platform-seg .seg-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.discoverPlatform = btn.getAttribute('data-platform') as any;
      void runDiscoverSearch();
    })
  );
  document.querySelectorAll('#discover-type-seg .seg-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      document.querySelectorAll('#discover-type-seg .seg-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.discoverType = btn.getAttribute('data-type') as any;
      void runDiscoverSearch();
    })
  );
  $('discover-search-btn').addEventListener('click', () => void runDiscoverSearch());
  $('discover-query-input').addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') void runDiscoverSearch();
  });

  // Accounts
  $('btn-add-offline').addEventListener('click', async () => {
    if (!aerix) return;
    try {
      const name = ($('offline-username-input') as HTMLInputElement).value;
      const acc = await aerix.addOfflineAccount(name);
      ($('offline-username-input') as HTMLInputElement).value = '';
      await refreshAllData();
      showToast(`Added offline profile ${acc.username}.`);
    } catch (err: any) {
      showToast(err?.message || 'Invalid username', true);
    }
  });
  $('btn-login-microsoft').addEventListener('click', async () => {
    if (!aerix) return;
    try {
      const acc = await aerix.loginMicrosoft();
      await refreshAllData();
      showToast(`Signed in as ${acc.username} (Microsoft).`);
    } catch (err: any) {
      showToast(err?.message || 'Microsoft sign-in failed', true);
    }
  });
  $('btn-login-aerix-email').addEventListener('click', async () => {
    if (!aerix) return;
    try {
      const email = ($('aerix-email-input') as HTMLInputElement).value;
      const pass = ($('aerix-pass-input') as HTMLInputElement).value;
      const acc = await aerix.loginAerixEmail(email, pass);
      ($('aerix-pass-input') as HTMLInputElement).value = '';
      await refreshAllData();
      showToast(`Signed in to Aerix as ${acc.username}.`);
    } catch (err: any) {
      showToast(err?.message || 'Aerix sign-in failed', true);
    }
  });
  $('btn-login-aerix-google').addEventListener('click', async () => {
    if (!aerix) return;
    try {
      const acc = await aerix.loginAerixGoogle();
      await refreshAllData();
      showToast(`Signed in to Aerix as ${acc.username}.`);
    } catch (err: any) {
      showToast(err?.message || 'Google sign-in failed', true);
    }
  });

  // Console
  $('console-filter-input').addEventListener('input', renderConsole);
  $('console-clear-btn').addEventListener('click', () => {
    state.logs = [];
    renderConsole();
  });
  $('console-copy-btn').addEventListener('click', async () => {
    const text = state.logs.map((l) => `[${l.stream}] ${l.line}`).join('\n');
    await navigator.clipboard.writeText(text);
    showToast('Console log copied to clipboard.');
  });

  // Settings
  document.querySelectorAll('.swatch-btn').forEach((btn) =>
    btn.addEventListener('click', async () => {
      const themePreset = btn.getAttribute('data-theme') || 'aurora-teal';
      const customAccentHex = THEME_ACCENTS[themePreset]?.hex || '#8DEFE0';
      if (aerix) {
        state.settings = await aerix.saveSettings({ themePreset, customAccentHex });
      }
      await applyThemeAndWallpaper();
      await renderSettings();
    })
  );
  $('setting-custom-color').addEventListener('input', async () => {
    const hex = ($('setting-custom-color') as HTMLInputElement).value;
    $('setting-custom-color-hex').textContent = hex.toUpperCase();
    applyAccentColor(hex);
    if (aerix) {
      state.settings = await aerix.saveSettings({ themePreset: 'custom', customAccentHex: hex });
    }
  });
  $('setting-theme-motion').addEventListener('change', async () => {
    const enabled = ($('setting-theme-motion') as HTMLInputElement).checked;
    if (aerix) {
      state.settings = await aerix.saveSettings({ themeMotion: enabled });
    }
    await applyThemeAndWallpaper();
  });
  $('setting-ram-slider').addEventListener('input', () => {
    $('setting-ram-val').textContent = `${($('setting-ram-slider') as HTMLInputElement).value} MB`;
  });
  $('setting-wallpaper-opacity').addEventListener('input', () => {
    const pct = Number(($('setting-wallpaper-opacity') as HTMLInputElement).value);
    $('wallpaper-opacity-val').textContent = `${pct}%`;
    $('wallpaper-backdrop').style.opacity = String(pct / 100);
  });
  $('setting-wallpaper-blur').addEventListener('input', () => {
    const px = Number(($('setting-wallpaper-blur') as HTMLInputElement).value);
    $('wallpaper-blur-val').textContent = `${px}px`;
    document.documentElement.style.setProperty('--wallpaper-blur', `${px}px`);
  });
  $('setting-layout-transparency').addEventListener('input', () => {
    const pct = Number(($('setting-layout-transparency') as HTMLInputElement).value);
    $('layout-transparency-val').textContent = `${pct}%`;
    document.documentElement.style.setProperty('--glass-alpha', String(pct / 100));
  });
  $('btn-save-settings').addEventListener('click', async () => {
    if (!aerix) return;
    state.settings = await aerix.saveSettings({
      defaultRamMb: Number(($('setting-ram-slider') as HTMLInputElement).value),
      defaultJvmArgs: ($('setting-jvm-args') as HTMLInputElement).value,
      defaultWidth: Number(($('setting-width') as HTMLInputElement).value) || 1280,
      defaultHeight: Number(($('setting-height') as HTMLInputElement).value) || 720,
      closeOnLaunch: ($('setting-close-behavior') as HTMLSelectElement).value,
      showSnapshots: ($('setting-show-snapshots') as HTMLInputElement).checked,
      curseforgeApiKey: ($('setting-cf-key') as HTMLInputElement).value.trim(),
      wallpaperOpacity: Number(($('setting-wallpaper-opacity') as HTMLInputElement).value) / 100,
      wallpaperBlur: Number(($('setting-wallpaper-blur') as HTMLInputElement).value),
      layoutTransparency: Number(($('setting-layout-transparency') as HTMLInputElement).value),
      themeMotion: ($('setting-theme-motion') as HTMLInputElement).checked,
    });
    renderHome();
    showToast('Settings saved.');
  });
  $('btn-refresh-java').addEventListener('click', () => void refreshJavaList());
  document.querySelectorAll('[data-install-java]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      if (!aerix) return;
      const major = Number(btn.getAttribute('data-install-java'));
      showToast(`Installing Temurin Java ${major}...`);
      try {
        await aerix.installJava(major);
        await refreshJavaList();
        showToast(`Java ${major} installed!`);
      } catch (err: any) {
        showToast(err?.message || `Failed to install Java ${major}`, true);
      }
    })
  );
  $('btn-check-updates').addEventListener('click', async () => {
    if (!aerix) return;
    try {
      const res = await aerix.checkForUpdates();
      if (res.updateAvailable) {
        showToast(`Update v${res.latestVersion} is available! Opening release page...`);
        void aerix.openExternal(res.matchingAssetUrl || res.releaseUrl);
      } else {
        showToast(`Aerix Launcher PC v${res.currentVersion} is up to date.`);
      }
    } catch (err: any) {
      showToast(err?.message || 'Update check failed', true);
    }
  });

  // Delegated handlers for all dynamically rendered lists/cards (always responsive!)
  document.addEventListener('click', async (e) => {
    const target = e.target as HTMLElement;

    const pickInst = target.closest('[data-pick-inst]');
    if (pickInst && aerix) {
      const id = pickInst.getAttribute('data-pick-inst');
      closeInstancePickers();
      if (id) {
        state.settings = await aerix.saveSettings({ selectedInstanceId: id });
        renderHome();
        renderInstances();
      }
      return;
    }
    if (target.closest('[data-open-library]')) {
      closeInstancePickers();
      switchView('instances');
      return;
    }
    if (target.closest('[data-open-create]')) {
      closeInstancePickers();
      void openCreateInstanceModal(false);
      return;
    }

    const quickPlay = target.closest('[data-quick-play]');
    if (quickPlay) {
      e.stopPropagation();
      const id = quickPlay.getAttribute('data-quick-play')!;
      void triggerLaunchOrStop(id);
      return;
    }
    const quickEdit = target.closest('[data-quick-edit]');
    if (quickEdit) {
      e.stopPropagation();
      const id = quickEdit.getAttribute('data-quick-edit')!;
      switchView('instances');
      void openInspector(id);
      return;
    }
    const selectInst = target.closest('[data-select-inst]');
    if (selectInst && aerix) {
      const id = selectInst.getAttribute('data-select-inst');
      if (id) {
        state.settings = await aerix.saveSettings({ selectedInstanceId: id });
        renderHome();
        renderInstances();
      }
      return;
    }

    const clientVer = target.closest('[data-client-ver]');
    if (clientVer) {
      state.selectedClientVersion = clientVer.getAttribute('data-client-ver') || '1.21.4';
      renderClientHub();
      return;
    }

    const instPlay = target.closest('[data-inst-play]');
    if (instPlay) {
      void triggerLaunchOrStop(instPlay.getAttribute('data-inst-play')!);
      return;
    }
    const instManage = target.closest('[data-inst-manage]');
    if (instManage) {
      void openInspector(instManage.getAttribute('data-inst-manage')!);
      return;
    }
    const instSelect = target.closest('[data-inst-select]');
    if (instSelect && aerix) {
      state.settings = await aerix.saveSettings({ selectedInstanceId: instSelect.getAttribute('data-inst-select') });
      renderHome();
      renderInstances();
      return;
    }

    if (target.id === 'insp-save-btn' && aerix && state.inspectedInstanceId) {
      const inst = state.instances.find((i) => i.id === state.inspectedInstanceId);
      if (!inst) return;
      const name = ($('insp-name') as HTMLInputElement).value.trim() || inst.name;
      const ramRaw = ($('insp-ram') as HTMLInputElement).value.trim();
      const ramMb = ramRaw ? Number(ramRaw) : undefined;
      const jvmArgs = ($('insp-jvm') as HTMLInputElement).value.trim() || undefined;
      const javaPath = ($('insp-java') as HTMLInputElement).value.trim() || undefined;
      const fullscreen = ($('insp-fullscreen') as HTMLInputElement).checked;
      await aerix.updateInstance(inst.id, { name, ramMb, jvmArgs, javaPath, fullscreen });
      await refreshAllData();
      await openInspector(inst.id);
      showToast('Instance settings saved.');
      return;
    }
    if (target.id === 'insp-delete-btn' && aerix && state.inspectedInstanceId) {
      const inst = state.instances.find((i) => i.id === state.inspectedInstanceId);
      if (!inst || !confirm(`Delete instance "${inst.name}" and all its worlds/mods?`)) return;
      await aerix.deleteInstance(inst.id);
      state.inspectedInstanceId = null;
      $('instance-inspector').classList.add('hidden');
      await refreshAllData();
      showToast(`Deleted "${inst.name}".`);
      return;
    }

    const toggleFile = target.closest('[data-toggle-file]');
    if (toggleFile && aerix && state.inspectedInstanceId) {
      const fileName = toggleFile.getAttribute('data-toggle-file')!;
      const cur = toggleFile.getAttribute('data-enabled') === 'true';
      await aerix.toggleContent(state.inspectedInstanceId, state.inspectorTab, fileName, !cur);
      await renderInspectorBody();
      return;
    }
    const delFile = target.closest('[data-del-file]');
    if (delFile && aerix && state.inspectedInstanceId) {
      await aerix.deleteContent(state.inspectedInstanceId, state.inspectorTab, delFile.getAttribute('data-del-file')!);
      await renderInspectorBody();
      return;
    }

    const accUse = target.closest('[data-acc-use]');
    if (accUse && aerix) {
      state.settings = await aerix.saveSettings({ selectedAccountId: accUse.getAttribute('data-acc-use') });
      renderAccountChip();
      renderAccounts();
      renderHome();
      return;
    }
    const accDel = target.closest('[data-acc-del]');
    if (accDel && aerix) {
      state.accounts = await aerix.removeAccount(accDel.getAttribute('data-acc-del')!);
      state.settings = await aerix.getSettings();
      renderAccountChip();
      renderAccounts();
      renderHome();
      return;
    }

    const discWeb = target.closest('[data-disc-web]');
    if (discWeb && aerix) {
      const p = state.discoverResults[Number(discWeb.getAttribute('data-disc-web'))];
      if (p?.webUrl) void aerix.openExternal(p.webUrl);
      return;
    }
    const discInst = target.closest('[data-disc-install]');
    if (discInst) {
      const p = state.discoverResults[Number(discInst.getAttribute('data-disc-install'))];
      void openDiscoverInstallModal(p);
      return;
    }

    const wpTile = target.closest('[data-wp-id]');
    if (wpTile && aerix) {
      const wpId = wpTile.getAttribute('data-wp-id')!;
      const wp = state.wallpapers.find((w) => w.id === wpId);
      const patch: Record<string, unknown> = { wallpaperId: wpId };
      if (wp?.accentHex) {
        patch.themePreset = 'custom';
        patch.customAccentHex = wp.accentHex;
      }
      state.settings = await aerix.saveSettings(patch);
      await applyThemeAndWallpaper();
      await renderSettings();
    }
  });

  // Live IPC streams
  if (aerix) {
    aerix.onLaunchProgress((ev) => {
      const panel = $('launch-progress-panel');
      $('global-status-pill').textContent = ev.message;
      $('home-ready-sub').textContent = ev.message;
      if (ev.stage === 'exited' || ev.stage === 'error') {
        if (ev.stage === 'error') showToast(ev.message, true);
        setTimeout(() => panel.classList.add('hidden'), 1800);
        return;
      }
      panel.classList.remove('hidden');
      $('launch-progress-text').textContent = ev.message;
      $('launch-progress-pct').textContent = `${ev.percent}%`;
      $('launch-progress-fill').style.width = `${ev.percent}%`;
    });

    aerix.onGameLog((ev) => {
      state.logs.push(ev);
      if (state.logs.length > 2000) state.logs.shift();
      if ($('view-console').classList.contains('active')) {
        renderConsole();
      }
    });

    aerix.onRunningStateChange((ids) => {
      state.runningIds = new Set(ids);
      renderHome();
      renderInstances();
    });
  }
}

function loadVersionsNonBlocking(): void {
  if (!aerix) return;
  void aerix.getAerixClientVersions().then((vers) => {
    if (Array.isArray(vers) && vers.length) {
      state.aerixClientVersions = vers;
      renderClientHub();
    }
  }).catch(() => {});

  void Promise.race([
    aerix.fetchMinecraftVersions(false),
    new Promise<Array<{ id: string; type: string }>>((_, rej) => setTimeout(() => rej(new Error('timeout')), 8000)),
  ])
    .then((versions) => {
      if (Array.isArray(versions) && versions.length) {
        state.mcVersions = versions;
        const mcFilter = document.getElementById('discover-version-select') as HTMLSelectElement | null;
        if (mcFilter) {
          mcFilter.innerHTML =
            '<option value="">Any MC Version</option>' +
            versions.slice(0, 35).map((v) => `<option value="${escapeHtml(v.id)}">${escapeHtml(v.id)}</option>`).join('');
        }
      }
    })
    .catch(() => {
      // Offline mode fallback already populated
    });
}

async function init(): Promise<void> {
  // CRITICAL: Wire all UI buttons & controls SYNCHRONOUSLY FIRST before any async/IPC/network calls
  wireEvents();
  updateCharacterSkins();
  renderClientHub();

  if (!aerix) {
    showToast('Preload IPC bridge failed to initialize.', true);
    $('global-status-pill').textContent = 'Bridge Error';
    return;
  }

  try {
    const logoUrl = await aerix.getAssetDataUrl('aerix-logo.png');
    if (logoUrl) {
      ($('titlebar-logo') as HTMLImageElement).src = logoUrl;
    }
  } catch {
    // Non-fatal
  }

  await refreshAllData();
  loadVersionsNonBlocking();
}

function boot(): void {
  init().catch((err) => showToast(err?.message || String(err), true));
}

if (document.readyState === 'loading') {
  window.addEventListener('DOMContentLoaded', boot);
} else {
  boot();
}
