interface AerixBridge {
  getCapabilities(): Promise<any>;
  getSettings(): Promise<any>;
  saveSettings(patch: Record<string, unknown>): Promise<any>;
  getWallpapers(): Promise<Array<{ id: string; title: string; biome: string; assetFile: string; accentHex: string }>>;
  getAssetDataUrl(relativePath: string): Promise<string | null>;
  windowMinimize(): Promise<void>;
  windowMaximizeToggle(): Promise<void>;
  windowClose(): Promise<void>;
  openExternal(url: string): Promise<void>;
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

const aerix = (window as unknown as { aerix: AerixBridge }).aerix;

const state = {
  capabilities: null as any,
  settings: null as any,
  accounts: [] as any[],
  instances: [] as any[],
  wallpapers: [] as Array<{ id: string; title: string; biome: string; assetFile: string; accentHex: string }>,
  wallpaperUrls: new Map<string, string>(),
  mcVersions: [] as Array<{ id: string; type: string }>,
  runningIds: new Set<string>(),
  inspectedInstanceId: null as string | null,
  inspectorTab: 'mods' as 'mods' | 'resourcepacks' | 'shaderpacks' | 'saves' | 'screenshots' | 'config',
  discoverPlatform: 'modrinth' as 'modrinth' | 'curseforge',
  discoverType: 'mod' as 'mod' | 'modpack' | 'resourcepack' | 'shader',
  discoverResults: [] as any[],
  logs: [] as Array<{ stream: string; line: string; timestamp: number }>,
};

function $(id: string): HTMLElement {
  const el = document.getElementById(id);
  if (!el) throw new Error(`Missing DOM element #${id}`);
  return el;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showToast(message: string, isError = false): void {
  const container = $('toast-container');
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

function switchView(viewId: string): void {
  document.querySelectorAll('.nav-btn').forEach((b) => {
    b.classList.toggle('active', b.getAttribute('data-view') === viewId);
  });
  document.querySelectorAll('.view').forEach((v) => {
    v.classList.toggle('active', v.id === `view-${viewId}`);
  });
}

async function applyThemeAndWallpaper(): Promise<void> {
  if (!state.settings) return;
  document.body.setAttribute('data-theme', state.settings.themePreset || 'aurora-teal');
  const backdrop = $('wallpaper-backdrop');
  backdrop.style.opacity = String(state.settings.wallpaperOpacity ?? 0.34);

  const wp = state.wallpapers.find((w) => w.id === state.settings.wallpaperId) ?? state.wallpapers[0];
  if (wp) {
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
    $('rail-account-name').textContent = 'No Account';
    $('rail-account-type').textContent = 'Click to sign in';
    return;
  }
  const headUrl = `https://mc-heads.net/avatar/${encodeURIComponent(acc.uuid)}/64`;
  avatar.innerHTML = `<img src="${headUrl}" alt="${escapeHtml(acc.username)}" />`;
  $('rail-account-name').textContent = acc.username;
  $('rail-account-type').textContent = acc.type;
}

function renderHome(): void {
  const inst = getSelectedInstance();
  const playBtn = $('home-play-btn') as HTMLButtonElement;
  const chips = $('home-instance-chips');

  if (!inst) {
    $('home-instance-badge').textContent = 'NO INSTANCE SELECTED';
    $('home-instance-title').textContent = 'Welcome to Aerix Launcher PC';
    $('home-instance-subtitle').textContent =
      'Create a Vanilla, Fabric, Forge, NeoForge, Quilt, or 1-click Aerix Client PC instance to begin.';
    chips.innerHTML = '';
    playBtn.textContent = 'CREATE INSTANCE';
    playBtn.classList.remove('stop-mode');
  } else {
    const running = state.runningIds.has(inst.id);
    $('home-instance-badge').textContent = inst.aerixClient
      ? 'AERIX CLIENT PC • FABRIC'
      : `${inst.loader.toUpperCase()} INSTANCE`;
    $('home-instance-title').textContent = inst.name;
    $('home-instance-subtitle').textContent = inst.aerixClient
      ? 'Bundled with Aerix-Client-Pack.zip and Sodium/Lithium performance stack.'
      : `Ready to launch Minecraft ${inst.mcVersion} with ${inst.loader}.`;
    const ram = inst.ramMb ?? state.settings?.defaultRamMb ?? 4096;
    const mins = Math.round((inst.playTimeSec || 0) / 60);
    chips.innerHTML = `
      <span class="chip">MC ${escapeHtml(inst.mcVersion)}</span>
      <span class="chip">${escapeHtml(inst.loader)}</span>
      <span class="chip">${ram} MB RAM</span>
      <span class="chip">${mins}m played</span>
    `;
    playBtn.textContent = running ? 'STOP GAME' : 'PLAY NOW';
    playBtn.classList.toggle('stop-mode', running);
  }

  const mini = $('home-instances-list');
  if (!state.instances.length) {
    mini.innerHTML = '<div class="muted">No instances yet. Click "Quick Aerix Client PC" or "New Instance".</div>';
  } else {
    mini.innerHTML = state.instances
      .slice(0, 6)
      .map(
        (item) => `
        <div class="mini-instance-card ${item.id === inst?.id ? 'selected' : ''}" data-select-inst="${escapeHtml(item.id)}">
          <strong>${escapeHtml(item.name)}</strong>
          <span class="muted">${escapeHtml(item.mcVersion)} • ${item.aerixClient ? 'Aerix Client' : escapeHtml(item.loader)}</span>
        </div>
      `
      )
      .join('');
    mini.querySelectorAll('[data-select-inst]').forEach((el) => {
      el.addEventListener('click', async () => {
        const id = el.getAttribute('data-select-inst');
        if (id) {
          state.settings = await aerix.saveSettings({ selectedInstanceId: id });
          renderHome();
          renderInstances();
        }
      });
    });
  }

  const acc = getSelectedAccount();
  const caps = state.capabilities;
  $('home-stats-list').innerHTML = `
    <div class="stat-row"><span>Active Account</span><strong>${acc ? escapeHtml(`${acc.username} (${acc.type})`) : 'Not signed in'}</strong></div>
    <div class="stat-row"><span>Platform</span><strong>${caps ? `${caps.platform} (${caps.arch})` : 'Desktop'}</strong></div>
    <div class="stat-row"><span>Default Memory</span><strong>${state.settings?.defaultRamMb || 4096} MB / ${caps?.totalMemoryMb || '?'} MB</strong></div>
    <div class="stat-row"><span>Running Processes</span><strong>${state.runningIds.size}</strong></div>
  `;
}

function renderInstances(): void {
  const filter = (($('instance-search-input') as HTMLInputElement).value || '').trim().toLowerCase();
  const selectedId = state.settings?.selectedInstanceId;
  const grid = $('instances-grid');
  const filtered = state.instances.filter(
    (i) => !filter || i.name.toLowerCase().includes(filter) || i.mcVersion.includes(filter) || i.loader.includes(filter)
  );

  if (!filtered.length) {
    grid.innerHTML = '<div class="glass-panel section-card"><p class="muted">No matching instances. Create one with "+ New Instance" or "Aerix Client".</p></div>';
  } else {
    grid.innerHTML = filtered
      .map((inst) => {
        const running = state.runningIds.has(inst.id);
        return `
          <div class="glass-panel instance-card ${inst.id === selectedId ? 'selected' : ''}">
            <div class="instance-card-top">
              <div>
                <div class="instance-title">${escapeHtml(inst.name)}</div>
                <div class="muted">Minecraft ${escapeHtml(inst.mcVersion)}</div>
              </div>
              <span class="badge ${inst.aerixClient ? 'badge-aerix' : ''}">${inst.aerixClient ? 'Aerix Client' : escapeHtml(inst.loader)}</span>
            </div>
            <div class="muted">${inst.ramMb ?? state.settings?.defaultRamMb} MB RAM • ${Math.round((inst.playTimeSec || 0) / 60)}m played</div>
            <div class="instance-actions">
              <button class="btn-primary btn-sm" data-inst-play="${escapeHtml(inst.id)}">${running ? 'Stop' : 'Play'}</button>
              <button class="btn-secondary btn-sm" data-inst-manage="${escapeHtml(inst.id)}">Manage</button>
              <button class="btn-ghost btn-sm" data-inst-select="${escapeHtml(inst.id)}">Select</button>
            </div>
          </div>
        `;
      })
      .join('');

    grid.querySelectorAll('[data-inst-play]').forEach((btn) =>
      btn.addEventListener('click', () => triggerLaunchOrStop(btn.getAttribute('data-inst-play')!))
    );
    grid.querySelectorAll('[data-inst-manage]').forEach((btn) =>
      btn.addEventListener('click', () => openInspector(btn.getAttribute('data-inst-manage')!))
    );
    grid.querySelectorAll('[data-inst-select]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        state.settings = await aerix.saveSettings({ selectedInstanceId: btn.getAttribute('data-inst-select') });
        renderHome();
        renderInstances();
      })
    );
  }

  // Sync Discover target dropdown
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
  $('inspector-panel' in window ? '' : 'instance-inspector').classList.remove('hidden');
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
  const contentToolbar = $('inspector-content-toolbar');
  contentToolbar.classList.toggle('hidden', tab === 'config' || tab === 'saves' || tab === 'screenshots');

  document.querySelectorAll('#inspector-tabs .tab-btn').forEach((b) => {
    b.classList.toggle('active', b.getAttribute('data-tab') === tab);
  });

  if (tab === 'config') {
    body.innerHTML = `
      <label class="field-label">Instance Name</label>
      <input id="insp-name" class="input" type="text" value="${escapeHtml(inst.name)}" />
      <label class="field-label">Memory Allocation (MB, leave empty for global default)</label>
      <input id="insp-ram" class="input" type="number" min="512" max="32768" value="${inst.ramMb ?? ''}" placeholder="Default (${state.settings?.defaultRamMb} MB)" />
      <label class="field-label">Custom JVM Arguments</label>
      <input id="insp-jvm" class="input" type="text" value="${escapeHtml(inst.jvmArgs || '')}" placeholder="Inherit global JVM args" />
      <label class="field-label">Custom Java Executable Path</label>
      <input id="insp-java" class="input" type="text" value="${escapeHtml(inst.javaPath || '')}" placeholder="Auto-managed Adoptium Java" />
      <label class="checkbox-row">
        <input id="insp-fullscreen" type="checkbox" ${inst.fullscreen ? 'checked' : ''} />
        <span>Launch in fullscreen</span>
      </label>
      <div class="auth-row" style="margin-top:10px; justify-content:space-between;">
        <button class="btn-danger btn-sm" id="insp-delete-btn">Delete Instance</button>
        <button class="btn-primary btn-sm" id="insp-save-btn">Save Changes</button>
      </div>
    `;
    $('insp-save-btn').addEventListener('click', async () => {
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
    });
    $('insp-delete-btn').addEventListener('click', async () => {
      if (!confirm(`Delete instance "${inst.name}" and all its worlds/mods?`)) return;
      await aerix.deleteInstance(inst.id);
      state.inspectedInstanceId = null;
      $('instance-inspector').classList.add('hidden');
      await refreshAllData();
      showToast(`Deleted "${inst.name}".`);
    });
    return;
  }

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
              ? `<button class="btn-secondary btn-sm" data-toggle-file="${escapeHtml(entry.fileName)}" data-enabled="${entry.enabled}">${entry.enabled ? 'Enabled' : 'Disabled'}</button>`
              : ''
          }
          <button class="btn-danger btn-sm" data-del-file="${escapeHtml(entry.fileName)}">Remove</button>
        </div>
      </div>
    `
    )
    .join('');

  body.querySelectorAll('[data-toggle-file]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      const fileName = btn.getAttribute('data-toggle-file')!;
      const cur = btn.getAttribute('data-enabled') === 'true';
      await aerix.toggleContent(inst.id, tab, fileName, !cur);
      await renderInspectorBody();
    })
  );

  body.querySelectorAll('[data-del-file]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      const fileName = btn.getAttribute('data-del-file')!;
      await aerix.deleteContent(inst.id, tab, fileName);
      await renderInspectorBody();
    })
  );
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
            <button class="btn-secondary btn-sm" data-acc-use="${escapeHtml(acc.id)}">${active ? 'Active' : 'Use'}</button>
            <button class="btn-danger btn-sm" data-acc-del="${escapeHtml(acc.id)}">Remove</button>
          </div>
        </div>
      `;
    })
    .join('');

  list.querySelectorAll('[data-acc-use]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      state.settings = await aerix.saveSettings({ selectedAccountId: btn.getAttribute('data-acc-use') });
      renderAccountChip();
      renderAccounts();
      renderHome();
    })
  );
  list.querySelectorAll('[data-acc-del]').forEach((btn) =>
    btn.addEventListener('click', async () => {
      state.accounts = await aerix.removeAccount(btn.getAttribute('data-acc-del')!);
      state.settings = await aerix.getSettings();
      renderAccountChip();
      renderAccounts();
      renderHome();
    })
  );
}

async function runDiscoverSearch(): Promise<void> {
  const grid = $('discover-results');
  grid.innerHTML = '<div class="glass-panel section-card"><p class="muted">Searching...</p></div>';
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
      grid.innerHTML = '<div class="glass-panel section-card"><p class="muted">No matching projects found.</p></div>';
      return;
    }

    grid.innerHTML = results
      .map(
        (p, idx) => `
        <div class="glass-panel discover-card">
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
              <button class="btn-ghost btn-sm" data-disc-web="${idx}">Page</button>
              <button class="btn-primary btn-sm" data-disc-install="${idx}">Install</button>
            </div>
          </div>
        </div>
      `
      )
      .join('');

    grid.querySelectorAll('[data-disc-web]').forEach((btn) =>
      btn.addEventListener('click', () => {
        const p = state.discoverResults[Number(btn.getAttribute('data-disc-web'))];
        if (p?.webUrl) aerix.openExternal(p.webUrl);
      })
    );
    grid.querySelectorAll('[data-disc-install]').forEach((btn) =>
      btn.addEventListener('click', () => openDiscoverInstallModal(state.discoverResults[Number(btn.getAttribute('data-disc-install'))]))
    );
  } catch (err: any) {
    grid.innerHTML = `<div class="glass-panel section-card"><p class="muted">Search error: ${escapeHtml(err?.message || String(err))}</p></div>`;
  }
}

async function openDiscoverInstallModal(project: any): Promise<void> {
  if (!project) return;
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
          <button class="btn-primary btn-sm" data-ver-pick="${idx}">Install</button>
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
  const filtered = filter
    ? state.logs.filter((l) => l.line.toLowerCase().includes(filter))
    : state.logs;
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

  const opPct = Math.round((state.settings.wallpaperOpacity ?? 0.34) * 100);
  ($('setting-wallpaper-opacity') as HTMLInputElement).value = String(opPct);
  $('wallpaper-opacity-val').textContent = `${opPct}%`;

  const wpGrid = $('wallpaper-grid');
  wpGrid.innerHTML = '';
  for (const wp of state.wallpapers) {
    let url = state.wallpaperUrls.get(wp.id);
    if (!url) {
      url = (await aerix.getAssetDataUrl(`wallpapers/${wp.assetFile}`)) || '';
      state.wallpaperUrls.set(wp.id, url);
    }
    const tile = document.createElement('button');
    tile.className = `wallpaper-tile ${wp.id === state.settings.wallpaperId ? 'active' : ''}`;
    tile.textContent = wp.title;
    if (url) tile.style.backgroundImage = `url("${url}")`;
    tile.addEventListener('click', async () => {
      state.settings = await aerix.saveSettings({ wallpaperId: wp.id });
      await applyThemeAndWallpaper();
      await renderSettings();
    });
    wpGrid.appendChild(tile);
  }
}

async function refreshJavaList(): Promise<void> {
  const list = $('java-runtimes-list');
  list.innerHTML = '<div class="muted">Scanning Java runtimes...</div>';
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
  mcSel.innerHTML = '<option>Loading versions...</option>';
  const versions = presetAerixClient
    ? (await aerix.getAerixClientVersions()).map((id) => ({ id, type: 'release' }))
    : await aerix.fetchMinecraftVersions(Boolean(state.settings?.showSnapshots));
  state.mcVersions = versions;
  mcSel.innerHTML = versions.map((v) => `<option value="${escapeHtml(v.id)}">${escapeHtml(v.id)}</option>`).join('');
  await refreshCreateModalLoaders();
}

async function refreshCreateModalLoaders(): Promise<void> {
  const mcVersion = ($('new-inst-mc') as HTMLSelectElement).value;
  const loader = ($('new-inst-loader') as HTMLSelectElement).value;
  const verSel = $('new-inst-loader-ver') as HTMLSelectElement;
  if (!mcVersion || loader === 'vanilla') {
    verSel.innerHTML = '<option value="">Not required</option>';
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
  renderInstances();
  renderAccounts();
}

function wireEvents(): void {
  $('win-min').addEventListener('click', () => aerix.windowMinimize());
  $('win-max').addEventListener('click', () => aerix.windowMaximizeToggle());
  $('win-close').addEventListener('click', () => aerix.windowClose());

  document.querySelectorAll('.nav-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      const view = btn.getAttribute('data-view')!;
      switchView(view);
      if (view === 'discover' && !state.discoverResults.length) {
        runDiscoverSearch();
      }
      if (view === 'settings') {
        renderSettings();
        refreshJavaList();
      }
    })
  );

  $('rail-account-chip').addEventListener('click', () => switchView('accounts'));
  $('home-view-all-instances').addEventListener('click', () => switchView('instances'));
  $('home-open-settings').addEventListener('click', () => {
    switchView('settings');
    renderSettings();
    refreshJavaList();
  });
  $('home-play-btn').addEventListener('click', () => triggerLaunchOrStop());
  $('home-quick-aerix-btn').addEventListener('click', () => openCreateInstanceModal(true));

  $('instance-search-input').addEventListener('input', renderInstances);
  $('btn-new-instance').addEventListener('click', () => openCreateInstanceModal(false));
  $('btn-new-aerix-client').addEventListener('click', () => openCreateInstanceModal(true));

  $('inspector-close-btn').addEventListener('click', () => $('instance-inspector').classList.add('hidden'));
  $('inspector-play-btn').addEventListener('click', () => {
    if (state.inspectedInstanceId) triggerLaunchOrStop(state.inspectedInstanceId);
  });
  $('inspector-folder-btn').addEventListener('click', () => {
    if (state.inspectedInstanceId) aerix.openInstanceFolder(state.inspectedInstanceId);
  });
  $('inspector-import-btn').addEventListener('click', async () => {
    if (!state.inspectedInstanceId) return;
    await aerix.importContentDialog(state.inspectedInstanceId, state.inspectorTab);
    await renderInspectorBody();
  });
  $('inspector-discover-btn').addEventListener('click', () => {
    switchView('discover');
    runDiscoverSearch();
  });
  document.querySelectorAll('#inspector-tabs .tab-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      state.inspectorTab = btn.getAttribute('data-tab') as any;
      renderInspectorBody();
    })
  );

  // Create instance modal
  $('new-inst-aerix').addEventListener('change', async () => {
    const checked = ($('new-inst-aerix') as HTMLInputElement).checked;
    const loaderSel = $('new-inst-loader') as HTMLSelectElement;
    loaderSel.disabled = checked;
    if (checked) loaderSel.value = 'fabric';
    await openCreateInstanceModal(checked);
  });
  $('new-inst-mc').addEventListener('change', refreshCreateModalLoaders);
  $('new-inst-loader').addEventListener('change', refreshCreateModalLoaders);
  $('btn-confirm-create-instance').addEventListener('click', async () => {
    const aerixClient = ($('new-inst-aerix') as HTMLInputElement).checked;
    const mcVersion = ($('new-inst-mc') as HTMLSelectElement).value;
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
      runDiscoverSearch();
    })
  );
  document.querySelectorAll('#discover-type-seg .seg-btn').forEach((btn) =>
    btn.addEventListener('click', () => {
      document.querySelectorAll('#discover-type-seg .seg-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.discoverType = btn.getAttribute('data-type') as any;
      runDiscoverSearch();
    })
  );
  $('discover-search-btn').addEventListener('click', runDiscoverSearch);
  $('discover-query-input').addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') runDiscoverSearch();
  });

  // Accounts
  $('btn-add-offline').addEventListener('click', async () => {
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
    try {
      const acc = await aerix.loginMicrosoft();
      await refreshAllData();
      showToast(`Signed in as ${acc.username} (Microsoft).`);
    } catch (err: any) {
      showToast(err?.message || 'Microsoft sign-in failed', true);
    }
  });
  $('btn-login-aerix-email').addEventListener('click', async () => {
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
      state.settings = await aerix.saveSettings({ themePreset: btn.getAttribute('data-theme') });
      await applyThemeAndWallpaper();
      await renderSettings();
    })
  );
  $('setting-ram-slider').addEventListener('input', () => {
    $('setting-ram-val').textContent = `${($('setting-ram-slider') as HTMLInputElement).value} MB`;
  });
  $('setting-wallpaper-opacity').addEventListener('input', async () => {
    const pct = Number(($('setting-wallpaper-opacity') as HTMLInputElement).value);
    $('wallpaper-opacity-val').textContent = `${pct}%`;
    $('wallpaper-backdrop').style.opacity = String(pct / 100);
  });
  $('btn-save-settings').addEventListener('click', async () => {
    state.settings = await aerix.saveSettings({
      defaultRamMb: Number(($('setting-ram-slider') as HTMLInputElement).value),
      defaultJvmArgs: ($('setting-jvm-args') as HTMLInputElement).value,
      defaultWidth: Number(($('setting-width') as HTMLInputElement).value) || 1280,
      defaultHeight: Number(($('setting-height') as HTMLInputElement).value) || 720,
      closeOnLaunch: ($('setting-close-behavior') as HTMLSelectElement).value,
      showSnapshots: ($('setting-show-snapshots') as HTMLInputElement).checked,
      curseforgeApiKey: ($('setting-cf-key') as HTMLInputElement).value.trim(),
      wallpaperOpacity: Number(($('setting-wallpaper-opacity') as HTMLInputElement).value) / 100,
    });
    renderHome();
    showToast('Settings saved.');
  });
  $('btn-refresh-java').addEventListener('click', refreshJavaList);
  document.querySelectorAll('[data-install-java]').forEach((btn) =>
    btn.addEventListener('click', async () => {
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
    try {
      const res = await aerix.checkForUpdates();
      if (res.updateAvailable) {
        showToast(`Update v${res.latestVersion} is available! Opening release page...`);
        aerix.openExternal(res.matchingAssetUrl || res.releaseUrl);
      } else {
        showToast(`Aerix Launcher PC v${res.currentVersion} is up to date.`);
      }
    } catch (err: any) {
      showToast(err?.message || 'Update check failed', true);
    }
  });

  // Live IPC streams
  aerix.onLaunchProgress((ev) => {
    const panel = $('launch-progress-panel');
    $('global-status-pill').textContent = ev.message;
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

async function init(): Promise<void> {
  const logoUrl = await aerix.getAssetDataUrl('aerix-logo.png');
  if (logoUrl) {
    ($('titlebar-logo') as HTMLImageElement).src = logoUrl;
  }
  await refreshAllData();
  try {
    const versions = await aerix.fetchMinecraftVersions(false);
    state.mcVersions = versions;
    const mcFilter = $('discover-version-select') as HTMLSelectElement;
    mcFilter.innerHTML =
      '<option value="">Any MC Version</option>' +
      versions
        .slice(0, 35)
        .map((v) => `<option value="${escapeHtml(v.id)}">${escapeHtml(v.id)}</option>`)
        .join('');
  } catch {
    // Offline mode fallback
  }
  wireEvents();
}

window.addEventListener('DOMContentLoaded', () => {
  init().catch((err) => showToast(err?.message || String(err), true));
});
