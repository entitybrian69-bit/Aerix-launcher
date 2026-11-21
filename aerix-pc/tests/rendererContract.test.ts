import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import vm from 'node:vm';
import { WALLPAPER_PRESETS } from '../src/main/storage';

const pcRoot = path.resolve(__dirname, '../..');

test('every DOM ID referenced in renderer.ts exists in index.html', () => {
  const html = fs.readFileSync(path.join(pcRoot, 'src/renderer/index.html'), 'utf8');
  const ts = fs.readFileSync(path.join(pcRoot, 'src/renderer/renderer.ts'), 'utf8');
  const htmlIds = new Set([...html.matchAll(/id="([^"]+)"/g)].map((m) => m[1]));
  const referenced = [...ts.matchAll(/\$\(\s*'([^']+)'\s*\)/g)].map((m) => m[1]);
  const missing = referenced.filter((id) => !htmlIds.has(id) && !id.startsWith('insp-'));
  assert.deepEqual(missing, []);
});

test('all photographic wallpapers in WALLPAPER_PRESETS exist in aerix-pc/assets/wallpapers', () => {
  const photos = WALLPAPER_PRESETS.filter((w) => !w.animatedScene);
  assert.equal(photos.length, 26);
  for (const wp of photos) {
    const full = path.join(pcRoot, 'assets/wallpapers', wp.assetFile);
    assert.equal(fs.existsSync(full), true, `Missing wallpaper asset: ${wp.assetFile}`);
  }
});

test('wireEvents binds synchronously before async IPC and buttons respond immediately even if network fails', async () => {
  const html = fs.readFileSync(path.join(pcRoot, 'src/renderer/index.html'), 'utf8');
  const rendererJs = fs.readFileSync(path.join(pcRoot, 'dist/renderer/renderer.js'), 'utf8');

  // Minimal DOM mock covering every element in index.html + classList + events
  class MockElement {
    id: string;
    tagName: string;
    attributes = new Map<string, string>();
    listeners = new Map<string, Array<(ev: any) => any>>();
    style: Record<string, string> = {};
    className = '';
    value = '';
    checked = false;
    disabled = false;
    textContent = '';
    scrollTop = 0;
    scrollHeight = 0;
    children: MockElement[] = [];
    classList = {
      add: (...cls: string[]) => {
        const set = new Set(this.className.split(/\s+/).filter(Boolean));
        cls.forEach((c) => set.add(c));
        this.className = [...set].join(' ');
      },
      remove: (...cls: string[]) => {
        const set = new Set(this.className.split(/\s+/).filter(Boolean));
        cls.forEach((c) => set.delete(c));
        this.className = [...set].join(' ');
      },
      toggle: (c: string, force?: boolean) => {
        const has = this.classList.contains(c);
        const next = force === undefined ? !has : Boolean(force);
        if (next) this.classList.add(c);
        else this.classList.remove(c);
        return next;
      },
      contains: (c: string) => this.className.split(/\s+/).filter(Boolean).includes(c),
    };
    private _innerHTML = '';
    constructor(tag: string, id = '', cls = '') {
      this.tagName = tag.toUpperCase();
      this.id = id;
      this.className = cls;
    }
    set innerHTML(v: string) {
      this._innerHTML = v;
    }
    get innerHTML(): string {
      return this._innerHTML;
    }
    setAttribute(k: string, v: string) {
      this.attributes.set(k, v);
    }
    getAttribute(k: string): string | null {
      return this.attributes.get(k) ?? null;
    }
    addEventListener(type: string, cb: (ev: any) => any) {
      const list = this.listeners.get(type) ?? [];
      list.push(cb);
      this.listeners.set(type, list);
    }
    appendChild(child: MockElement) {
      this.children.push(child);
      return child;
    }
    remove() {}
    closest() {
      return null;
    }
    querySelectorAll() {
      return [];
    }
    setPointerCapture() {}
    click() {
      const ev = { target: this, stopPropagation() {} };
      for (const cb of this.listeners.get('click') ?? []) cb(ev);
    }
  }

  const elementsById = new Map<string, MockElement>();
  const allElements: MockElement[] = [];
  for (const match of html.matchAll(/<([a-zA-Z0-9-]+)([^>]*)>/g)) {
    const [, tag, attrs] = match;
    const id = attrs.match(/id="([^"]+)"/)?.[1] ?? '';
    const cls = attrs.match(/class="([^"]+)"/)?.[1] ?? '';
    const el = new MockElement(tag, id, cls);
    for (const attr of attrs.matchAll(/(data-[a-z-]+)="([^"]*)"/g)) {
      el.setAttribute(attr[1], attr[2]);
    }
    if (id) elementsById.set(id, el);
    allElements.push(el);
  }

  const docListeners = new Map<string, Array<(ev: any) => any>>();
  const winListeners = new Map<string, Array<(ev: any) => any>>();
  const body = new MockElement('body');
  const rootStyle = new Map<string, string>();
  let minimized = false;
  let savedStyle: unknown = null;

  const mockDocument = {
    readyState: 'complete',
    body,
    documentElement: {
      style: {
        setProperty: (k: string, v: string) => rootStyle.set(k, v),
      },
    },
    getElementById: (id: string) => elementsById.get(id) ?? null,
    createElement: (tag: string) => {
      const el = new MockElement(tag);
      if (tag === 'canvas') {
        (el as any).getContext = () => ({
          fillStyle: '',
          fillRect() {},
          createLinearGradient: () => ({ addColorStop() {} }),
          beginPath() {},
          moveTo() {},
          lineTo() {},
          closePath() {},
          fill() {},
          arc() {},
          toDataURL: () => 'data:image/png;base64,AA==',
        });
        (el as any).toDataURL = () => 'data:image/png;base64,AA==';
      }
      return el;
    },
    querySelectorAll: (selector: string) => {
      if (selector === '.rail-btn[data-view]') {
        return allElements.filter((e) => e.classList.contains('rail-btn') && e.getAttribute('data-view'));
      }
      if (selector === '.view') {
        return allElements.filter((e) => e.classList.contains('view'));
      }
      if (selector === '[data-ui-style]') {
        return allElements.filter((e) => e.getAttribute('data-ui-style'));
      }
      if (selector === '[data-close-modal]') {
        return allElements.filter((e) => e.getAttribute('data-close-modal'));
      }
      if (selector === '.swatch-btn') {
        return allElements.filter((e) => e.classList.contains('swatch-btn'));
      }
      if (selector === '[data-install-java]') {
        return allElements.filter((e) => e.getAttribute('data-install-java'));
      }
      if (selector.includes('.seg-btn')) {
        return allElements.filter((e) => e.classList.contains('seg-btn'));
      }
      if (selector.includes('.tab-btn')) {
        return allElements.filter((e) => e.classList.contains('tab-btn'));
      }
      return [];
    },
    addEventListener: (type: string, cb: (ev: any) => any) => {
      const list = docListeners.get(type) ?? [];
      list.push(cb);
      docListeners.set(type, list);
    },
  };

  const mockWindow: any = {
    innerWidth: 1280,
    innerHeight: 800,
    addEventListener: (type: string, cb: (ev: any) => any) => {
      const list = winListeners.get(type) ?? [];
      list.push(cb);
      winListeners.set(type, list);
    },
    aerix: {
      getCapabilities: async () => ({ platform: 'win32', arch: 'x64', totalMemoryMb: 16384, dataDir: 'C:\\AerixPC', appVersion: '2.4.5' }),
      getSettings: async () => ({ selectedInstanceId: null, selectedAccountId: null, defaultRamMb: 4096, themePreset: 'aurora-teal', wallpaperId: 'anim_01_lush_cavern', wallpaperOpacity: 0.44, uiStyle: 'premium' }),
      saveSettings: async (patch: any) => {
        savedStyle = patch.uiStyle ?? savedStyle;
        return { selectedInstanceId: null, selectedAccountId: null, defaultRamMb: 4096, themePreset: 'aurora-teal', wallpaperId: 'anim_01_lush_cavern', wallpaperOpacity: 0.44, ...patch };
      },
      getWallpapers: async () => WALLPAPER_PRESETS,
      getAssetDataUrl: async () => null,
      windowMinimize: async () => { minimized = true; },
      windowMaximizeToggle: async () => {},
      windowClose: async () => {},
      openExternal: async () => {},
      openDataDir: async () => '',
      listAccounts: async () => [],
      listInstances: async () => [],
      getRunningInstances: async () => [],
      getAerixClientVersions: async () => ['1.21.4', '1.16.5'],
      fetchMinecraftVersions: async () => { throw new Error('offline'); },
      fetchLoaderVersions: async () => ['0.16.10'],
      discoverJava: async () => [],
      searchDiscover: async () => [],
      onLaunchProgress: () => () => {},
      onGameLog: () => () => {},
      onRunningStateChange: () => () => {},
    },
  };

  class MockImage {
    src = '';
    crossOrigin = '';
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
  }

  vm.runInNewContext(rendererJs, {
    window: mockWindow,
    document: mockDocument,
    Image: MockImage,
    navigator: { clipboard: { writeText: async () => {} } },
    performance: { now: () => 0 },
    requestAnimationFrame: () => 1,
    cancelAnimationFrame: () => {},
    setTimeout,
    clearTimeout,
    console,
  });

  // Immediately after script loads (even before async IPC settles), buttons MUST work!
  elementsById.get('win-min')!.click();
  assert.equal(minimized, true);

  // Clicking nav rail buttons switches active views immediately
  for (const viewId of ['client', 'instances', 'discover', 'accounts', 'console', 'settings', 'home']) {
    const railBtn = allElements.find((e) => e.classList.contains('rail-btn') && e.getAttribute('data-view') === viewId)!;
    railBtn.click();
    assert.equal(elementsById.get(`view-${viewId}`)!.classList.contains('active'), true, `view-${viewId} should become active`);
  }

  // Clicking "+ Create" opens the New Instance modal immediately, and close button closes it
  elementsById.get('rail-create-btn')!.click();
  assert.equal(elementsById.get('modal-new-instance')!.classList.contains('hidden'), false);
  const closeCreateBtn = allElements.find((e) => e.getAttribute('data-close-modal') === 'modal-new-instance')!;
  closeCreateBtn.click();
  assert.equal(elementsById.get('modal-new-instance')!.classList.contains('hidden'), true);

  // Clicking Quick Tiles switches views immediately
  elementsById.get('tile-discover')!.click();
  assert.equal(elementsById.get('view-discover')!.classList.contains('active'), true);
  elementsById.get('tile-client')!.click();
  assert.equal(elementsById.get('view-client')!.classList.contains('active'), true);
  elementsById.get('rail-brand-home')!.click();
  assert.equal(elementsById.get('view-home')!.classList.contains('active'), true);

  // Clicking UI Style pill switches to Clean mode
  elementsById.get('style-pill-clean')!.click();
  await new Promise((r) => setTimeout(r, 20));
  assert.equal(savedStyle, 'clean');
  assert.equal(elementsById.get('home-clean-layout')!.classList.contains('hidden'), false);
});
