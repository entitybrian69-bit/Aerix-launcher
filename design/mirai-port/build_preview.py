#!/usr/bin/env python3
"""Builds a live HTML preview of the Mirai shell now in the Android sources.

The palette table is read straight out of res/values/mirai_colors.xml so the
preview can never drift from what the app actually paints.
"""
import re
import os
import json

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = '../../app_pojavlauncher/src/main/res'
COLORS = os.path.join(ROOT, RES, 'values/mirai_colors.xml')

PALETTES = ['embermire', 'velvetrose', 'mistwave', 'glacier',
            'verdantfield', 'urbanash', 'verdantdawn']
LABELS = {
    'embermire': 'Embermire', 'velvetrose': 'Velvet Rose', 'mistwave': 'Mistwave',
    'glacier': 'Glacier', 'verdantfield': 'Verdant Field', 'urbanash': 'Urban Ash',
    'verdantdawn': 'Verdant Dawn',
}

# css variable name -> colour role suffix used in mirai_colors.xml
ROLES = {
    'primary': 'primary', 'onPrimary': 'onprimary',
    'primaryContainer': 'primarycontainer', 'onPrimaryContainer': 'onprimarycontainer',
    'secondary': 'secondary', 'onSecondary': 'onsecondary',
    'secondaryContainer': 'secondarycontainer', 'onSecondaryContainer': 'onsecondarycontainer',
    'tertiary': 'tertiary', 'onTertiary': 'ontertiary',
    'tertiaryContainer': 'tertiarycontainer', 'onTertiaryContainer': 'ontertiarycontainer',
    'error': 'error', 'onError': 'onerror',
    'errorContainer': 'errorcontainer', 'onErrorContainer': 'onerrorcontainer',
    'surface': 'surface', 'onSurface': 'onsurface',
    'surfaceVariant': 'surfacevariant', 'onSurfaceVariant': 'onsurfacevariant',
    'outline': 'outline', 'outlineVariant': 'outlinevariant',
    'surfaceContainerLowest': 'surfacecontainerlowest',
    'surfaceContainerLow': 'surfacecontainerlow',
    'surfaceContainer': 'surfacecontainer',
    'surfaceContainerHigh': 'surfacecontainerhigh',
    'surfaceContainerHighest': 'surfacecontainerhighest',
}


def load_palettes():
    text = open(COLORS).read()
    found = dict(re.findall(r'<color name="mirai_(\w+)">([^<]+)</color>', text))
    out = {}
    for palette in PALETTES:
        entry = {}
        for css, role in ROLES.items():
            key = palette + '_' + role
            if key in found:
                value = found[key]
                entry[css] = value if len(value) == 7 else '#' + value[-6:]
        out[palette] = entry
    return out


HTML = r'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Aerix — Mirai shell preview</title>
<style>
:root {
  --page: #0D0F14;
  --panel: rgba(23,43,58,.62);
  --panelRaised: rgba(34,59,78,.69);
  --border: rgba(230,250,255,.47);
  --borderSoft: rgba(230,250,255,.28);
  --accent: #8DEFE0;
  --onAccent: #06201D;
  --auroraCyan: #37DCEC;
  --auroraViolet: #7A71F5;
  --text: #F1F6FA;
  --text2: #A8B9C5;
  --text3: #7D909D;
}
* { box-sizing: border-box; }
body {
  margin: 0; background: #05070a; color: #eee;
  font-family: Inter, "Segoe UI", Roboto, system-ui, sans-serif;
  display: flex; flex-direction: column; align-items: center;
  min-height: 100vh; padding: 22px 14px 40px;
}
h1 { font-size: 17px; font-weight: 600; margin: 0 0 2px; }
p.sub { margin: 0 0 16px; color: #7d8590; font-size: 12.5px; }

.device {
  position: relative; width: min(96vw, 1200px); aspect-ratio: 16 / 9;
  border-radius: 20px; overflow: hidden;
  background: var(--page);
  box-shadow: 0 36px 84px rgba(0,0,0,.7), 0 0 0 1px rgba(255,255,255,.06);
}
/* Mirai paints one cached aurora pass: cyan to violet, barely there. */
.aurora {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(55,220,236,.10) 0%, rgba(0,0,0,0) 52%, rgba(122,113,245,.09) 100%);
}
.shell { position: absolute; inset: 0; display: flex; }
.bodywrap { flex: 1; display: flex; min-height: 0; }

/* ---------- the rail: a floating glass capsule -------------------------- */
.rail {
  width: 68px; flex: none; padding: 10px 4px; display: flex; flex-direction: column;
}
.capsule {
  flex: 1; border-radius: 50px; padding: 10px 0;
  background: rgba(43,80,99,.94);
  border: 1px solid var(--borderSoft);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  box-shadow: 0 6px 24px rgba(0,6,16,.4);
}
.mark {
  width: 38px; height: 38px; border-radius: 12px; margin-bottom: 4px;
  background: rgba(141,239,224,.16); border: 1px solid var(--borderSoft);
  display: grid; place-items: center; cursor: pointer;
}
.mark svg { width: 24px; height: 24px; }
.slot {
  width: 44px; height: 42px; border-radius: 12px; cursor: pointer;
  background: var(--panel); border: 1px solid var(--borderSoft);
  display: grid; place-items: center; transition: background .14s, border-color .14s;
}
.slot svg { width: 22px; height: 22px; stroke: var(--text2); fill: none;
  stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
.slot:hover { background: var(--panelRaised); }
.slot.on { background: var(--accent); border-color: var(--accent); }
.slot.on svg { stroke: var(--onAccent); }
.divider { width: 28px; height: 1px; background: var(--borderSoft); margin: 2px 0; }

/* ---------- page furniture --------------------------------------------- */
.page { flex: 1; min-width: 0; padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.panel {
  background: var(--panel); border: 1px solid var(--borderSoft); border-radius: 18px;
  padding: 16px; backdrop-filter: blur(14px); position: relative; overflow: hidden;
}
.panel::before {
  content: ''; position: absolute; inset: 0 0 auto 0; height: 40%;
  background: linear-gradient(180deg, rgba(255,255,255,.07), rgba(255,255,255,0));
  pointer-events: none;
}
.row { display: flex; gap: 10px; }
.col { display: flex; flex-direction: column; gap: 10px; min-height: 0; }
.grow { flex: 1; min-height: 0; }
.label { font-size: 11px; color: var(--text2); letter-spacing: .04em; text-transform: uppercase; font-weight: 700; }
.title { font-size: clamp(15px, 1.7vw, 26px); font-weight: 700; color: var(--text); }
.sub2 { font-size: 12px; color: var(--text2); }
.muted { font-size: 11px; color: var(--text3); }
.playwrap { position: relative; display: grid; place-items: center; }
.halo {
  position: absolute; width: 150px; height: 150px; border-radius: 50%;
  background: conic-gradient(from 0deg, rgba(141,239,224,0), rgba(141,239,224,.55), rgba(141,239,224,0) 70%);
  -webkit-mask: radial-gradient(circle, transparent 46px, #000 47px, #000 62px, transparent 63px);
          mask: radial-gradient(circle, transparent 46px, #000 47px, #000 62px, transparent 63px);
  animation: spin 9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.play {
  width: 96px; height: 96px; border-radius: 50%; border: 1px solid var(--borderSoft);
  background: var(--panel); display: grid; place-items: center; cursor: pointer;
  position: relative;
}
.play svg { width: 34px; height: 34px; fill: var(--text); }
.tile {
  flex: 1; background: var(--panel); border: 1px solid var(--borderSoft);
  border-radius: 18px; padding: 0 16px; display: flex; align-items: center; gap: 12px;
  cursor: pointer; height: 78px;
}
.tile svg { width: 26px; height: 26px; stroke: var(--text2); fill: none;
  stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; flex: none; }
.tool {
  width: 100%; height: 40px; border-radius: 12px; background: var(--panel);
  border: 1px solid var(--borderSoft); color: var(--text); font-size: 12px;
  display: flex; align-items: center; gap: 10px; padding: 0 14px; cursor: pointer;
}
.tool svg { width: 18px; height: 18px; stroke: var(--text2); fill: none; stroke-width: 1.9; }
.pillrow { display: flex; gap: 8px; }
.chip {
  height: 26px; padding: 0 12px; border-radius: 50px; background: var(--panel);
  border: 1px solid var(--borderSoft); color: var(--text2); font-size: 11px;
  display: inline-flex; align-items: center; gap: 6px;
}
.chip.on { background: var(--accent); color: var(--onAccent); border-color: var(--accent); }
.list { display: flex; flex-direction: column; gap: 4px; }
.item {
  display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: 12px;
  background: rgba(23,43,58,.34); color: var(--text); font-size: 13px;
}
.item.on { background: rgba(141,239,224,.8); color: var(--onAccent); font-weight: 600; }
.item .box {
  width: 28px; height: 28px; border-radius: 8px; flex: none;
  background: rgba(0,0,0,.2); display: grid; place-items: center;
}
.item .box svg { width: 18px; height: 18px; stroke: var(--text2); fill: none; stroke-width: 1.9; }
.swatches { display: grid; grid-template-columns: repeat(7, 1fr); gap: 8px; }
.swatch { border: 2px solid transparent; border-radius: 12px; overflow: hidden; cursor: pointer; background: none; padding: 0; }
.swatch.on { border-color: var(--text); }
.swatch .strip { height: 26px; display: flex; }
.swatch .strip i { flex: 1; }
.swatch .name { font-size: 8.5px; padding: 4px 2px; color: var(--text3); text-align: center; }
.walltiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.walltile {
  aspect-ratio: 16/10; border-radius: 14px; position: relative; overflow: hidden; cursor: pointer;
  border: 2px solid var(--borderSoft); display: grid; place-items: center; color: var(--text2); font-size: 11px;
}
.walltile.on { border-color: var(--accent); }
.walltile i { position: absolute; inset: 0; }

.tabs { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; margin-top: 18px; }
.tabs button {
  border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.05);
  color: #d8d8e0; border-radius: 999px; padding: 8px 15px; font-size: 12.5px; cursor: pointer;
}
.tabs button.on { background: #fff; color: #111; border-color: #fff; }
.palettes { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; margin-top: 10px; }
.palettes button {
  border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.05);
  color: #d8d8e0; border-radius: 999px; padding: 6px 13px; font-size: 12px; cursor: pointer;
}
.palettes button.on { border-color: #fff; color: #fff; }
</style>
</head>
<body>

<h1>Aerix — Mirai shell</h1>
<p class="sub">Rebuilt on Mirai's own glass system · no top bar · floating rail capsule · aurora pass · landscape</p>

<div class="device">
  <div class="aurora"></div>
  <div class="shell">
    <div class="bodywrap">
      <nav class="rail">
        <div class="capsule" id="capsule"></div>
      </nav>
      <main class="page" id="page"></main>
    </div>
  </div>
</div>

<div class="tabs" id="tabs"></div>
<div class="palettes" id="palettes"></div>

<script>
const ICONS = {
  mark: '<path d="M32 9 L53 51 H43 L32 30 L21 51 H11 Z" fill="#D7E2EA" stroke="none"/><path d="M32 22 L38 35 H26 Z" fill="#0B1420" stroke="none"/>',
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9.5 21v-6h5v6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  public: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c2.6 3 2.6 15 0 18-2.6-3-2.6-15 0-18z"/>',
  paint: '<path d="M19 11.5 12.5 5 5 12.5V19h6.5z"/><path d="M5 19c-1.7 0-3 1.3-3 3h6c0-1.7-1.3-3-3-3z"/><circle cx="8.5" cy="8.5" r="1"/>',
  settings: '<circle cx="12" cy="12" r="3.2"/><path d="M19.4 14a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7.5 19l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3 13.6H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 7l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 10 3.1V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1.3z"/>',
  person: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/>',
  play: '<path d="M6 4l14 8-14 8z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  gamepad: '<rect x="2" y="6" width="20" height="12" rx="5"/><path d="M8 10h8M11 7v6"/><circle cx="16.5" cy="11.5" r="1"/><circle cx="7.5" cy="13.5" r="1"/>',
  download: '<path d="M12 3v12"/><path d="m7 11 5 5 5-5"/><path d="M4 21h16"/>',
  arrow: '<path d="m9 6 6 6-6 6"/>',
  box: '<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="m3 8 9 5 9-5"/><path d="M12 13v8"/>',
  java: '<path d="M8 17c-2.5 0-4-1.6-4-4 0-2.6 2-4.5 5-4.5"/><path d="M9 5s4 2.5 4 6c0 0 2 1 2 3"/><path d="M13 19c0-3 3-5 6-5"/><path d="M10 21c0-2 1.5-3.5 3.5-3.5"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/>',
  check: '<path d="m4 12 5 5L20 6"/>',
};
const svg = n => `<svg viewBox="0 0 24 24">${ICONS[n]}</svg>`;

const DESTINATIONS = [
  { key: 'home',      icon: 'home',      label: 'Home',       title: 'Home' },
  { key: 'new',       icon: 'plus',      label: 'New Instance', title: 'New instance' },
  { key: 'library',   icon: 'dashboard', label: 'Library',    title: 'Library' },
  { key: 'discover',  icon: 'public',    label: 'Discover',   title: 'Discover' },
  { key: 'wallpaper', icon: 'paint',     label: 'Wallpapers', title: 'Wallpapers' },
  { key: 'settings',  icon: 'settings',  label: 'Settings',   title: 'Settings' },
];

const PAGES = {
  home: `
    <div class="row" style="flex:1.3;min-height:0">
      <div class="panel" style="flex:1.55;display:flex;flex-direction:column">
        <div class="label">Instances</div>
        <div class="title" style="margin-top:6px">Fabric Loader 1.20.4</div>
        <div class="sub2" style="margin-top:4px">Java 21 · 4 GB · 3 installed profiles</div>
        <div style="flex:1"></div>
        <div class="pillrow">
          <span class="chip on">${svg('check')}Ready</span>
          <span class="chip">Vulkan</span>
          <span class="chip">Latest release</span>
        </div>
        <div class="row" style="margin-top:12px">
          <div class="tool" style="flex:1;justify-content:center">${svg('plus')}Create instance</div>
          <div class="tool" style="flex:1;justify-content:center">${svg('dashboard')}Manage</div>
        </div>
      </div>
      <div class="col" style="flex:.85">
        <div class="panel grow" style="display:grid;place-items:center">
          <div class="playwrap">
            <div class="halo"></div>
            <div class="play">${svg('play')}</div>
          </div>
          <div class="title" style="font-size:15px;margin-top:12px">Play</div>
        </div>
        <div class="panel" style="height:74px;display:flex;align-items:center;gap:12px">
          ${svg('download')}
          <div style="flex:1">
            <div style="font-size:13px;font-weight:700">Tasks</div>
            <div class="sub2">Nothing running</div>
          </div>
          ${svg('arrow')}
        </div>
      </div>
    </div>
    <div class="row" style="height:78px">
      <div class="tile">${svg('plus')}<div><div style="font-size:13px;font-weight:700">Create instance</div><div class="muted">A fresh game profile</div></div></div>
      <div class="tile">${svg('search')}<div><div style="font-size:13px;font-weight:700">Explore content</div><div class="muted">Modpacks, mods and more</div></div></div>
      <div class="tile">${svg('dashboard')}<div><div style="font-size:13px;font-weight:700">Your library</div><div class="muted">3 installed profiles</div></div></div>
      <div class="tile">${svg('folder')}<div><div style="font-size:13px;font-weight:700">File space</div><div class="muted">Worlds, logs and exports</div></div></div>
    </div>
    <div class="row" style="flex:1;min-height:0">
      <div class="panel" style="flex:1.7">
        <div class="label">Recent</div>
        <div class="list" style="margin-top:10px">
          <div class="item on"><span class="box">${svg('box')}</span>Survival world</div>
          <div class="item"><span class="box">${svg('box')}</span>Create modpack</div>
          <div class="item"><span class="box">${svg('box')}</span>Vanilla testing</div>
        </div>
      </div>
      <div class="panel" style="flex:.72">
        <div class="label">Quick tools</div>
        <div class="col" style="margin-top:10px;gap:8px">
          <div class="tool">${svg('gamepad')}Controls</div>
          <div class="tool">${svg('java')}Install jar</div>
          <div class="tool">${svg('download')}Share logs</div>
        </div>
      </div>
    </div>`,
  new: `
    <div class="row" style="flex:1;min-height:0">
      <div class="panel col grow">
        <div class="label">Vanilla</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Latest release</b><br><span class="muted">1.20.4 · stable</span></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Snapshot</b><br><span class="muted">24w14a · experimental</span></div></div>
      </div>
      <div class="panel col grow">
        <div class="label">Modded</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Fabric</b><br><span class="muted">Loader 0.15.11</span></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Forge</b><br><span class="muted">1.20.4 · 49.0.19</span></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>NeoForge</b><br><span class="muted">1.20.4 · 20.4.80</span></div></div>
        <div class="item"><span class="box">${svg('download')}</span><div style="flex:1"><b>OptiFine</b><br><span class="muted">HD U I7</span></div></div>
      </div>
    </div>`,
  library: `
    <div class="panel col grow">
      <div class="label">Instances</div>
      <div class="list" style="margin-top:10px">
        <div class="item on"><span class="box">${svg('box')}</span><div style="flex:1"><b>Survival world</b><br><span class="muted">Fabric Loader 1.20.4</span></div>${svg('edit')}${svg('trash')}</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Create modpack</b><br><span class="muted">Forge 1.20.1</span></div>${svg('edit')}${svg('trash')}</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Vanilla testing</b><br><span class="muted">1.21</span></div>${svg('edit')}${svg('trash')}</div>
      </div>
      <div style="flex:1"></div>
      <div class="sub2">Storage · 2.4 GB used of 12 GB</div>
    </div>`,
  discover: `
    <div class="panel" style="display:flex;gap:12px;align-items:center">
      ${svg('search')}<span class="sub2" style="flex:1">Search mods, modpacks and resource packs</span>
      <div class="chip on">${svg('search')}Search</div>
    </div>
    <div class="row grow">
      <div class="panel col grow">
        <div class="label">Popular on 1.20.4</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Sodium</b><br><span class="muted">Performance · 4.1 M downloads</span></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Iris Shaders</b><br><span class="muted">Graphics · 3.2 M downloads</span></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>JEI</b><br><span class="muted">Utility · 9.8 M downloads</span></div></div>
      </div>
      <div class="panel col" style="flex:.8">
        <div class="label">Sources</div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>Modrinth</b></div></div>
        <div class="item"><span class="box">${svg('box')}</span><div style="flex:1"><b>CurseForge</b></div></div>
        <div class="item"><span class="box">${svg('download')}</span><div style="flex:1"><b>Install from file</b></div></div>
      </div>
    </div>`,
  wallpaper: `
    <div class="panel col grow">
      <div class="label">Wallpapers</div>
      <div class="sub2">The launcher backdrop, or a picture from the device.</div>
      <div class="row" style="margin-top:8px">
        <div class="tool" style="flex:1;justify-content:center">Pick from storage</div>
        <div class="tool" style="flex:1;justify-content:center">Use launcher backdrop</div>
      </div>
      <div class="walltiles" style="margin-top:8px">
        <div class="walltile on"><i style="background:linear-gradient(135deg,rgba(55,220,236,.18),rgba(122,113,245,.16)),var(--page)"></i><span style="position:relative">Aurora</span></div>
        <div class="walltile"><i style="background:linear-gradient(200deg,rgba(141,239,224,.16),var(--page))"></i><span style="position:relative">Dusk</span></div>
        <div class="walltile"><i style="background:linear-gradient(160deg,rgba(243,169,217,.16),var(--page))"></i><span style="position:relative">Meadow</span></div>
      </div>
      <div style="flex:1"></div>
    </div>`,
  settings: `
    <div class="row grow">
      <div class="panel col grow">
        <div class="label">Appearance</div>
        <div class="swatches" id="swatches"></div>
        <div class="item"><div style="flex:1"><b>Keep the launcher sideways</b><br><span class="muted">Hold the screen in landscape</span></div><span class="chip on">On</span></div>
        <div class="item"><div style="flex:1"><b>Backdrop opacity</b><br><span class="muted">How much of the wallpaper shows through</span></div><span class="chip">70%</span></div>
      </div>
      <div class="panel col" style="flex:.9">
        <div class="label">Sections</div>
        <div class="item"><div style="flex:1"><b>Video</b><br><span class="muted">Resolution, renderer, performance</span></div>${svg('arrow')}</div>
        <div class="item"><div style="flex:1"><b>Control</b><br><span class="muted">Buttons, gestures, mouse</span></div>${svg('arrow')}</div>
        <div class="item"><div style="flex:1"><b>Java</b><br><span class="muted">Runtimes and arguments</span></div>${svg('arrow')}</div>
        <div class="item"><div style="flex:1"><b>Miscellaneous</b></div>${svg('arrow')}</div>
        <div class="item"><div style="flex:1"><b>Experimental</b></div>${svg('arrow')}</div>
      </div>
    </div>`,
};

const PALETTES = __PALETTES__;

let current = 'home';
let palette = 'embermire';

function applyPalette(key) {
  palette = key;
  const p = PALETTES[key];
  // The palette tints the page underneath the glass, the glass itself stays put.
  const base = p.surfaceContainer || '#291D19';
  document.documentElement.style.setProperty('--page', base);
  document.querySelectorAll('#palettes button').forEach(b => b.classList.toggle('on', b.dataset.key === key));
  if (current === 'settings') renderSwatches();
}

function renderSwatches() {
  const host = document.getElementById('swatches');
  if (!host) return;
  host.innerHTML = Object.keys(PALETTES).map(key => {
    const p = PALETTES[key];
    return `<button class="swatch ${key === palette ? 'on' : ''}" data-palette="${key}">
      <span class="strip">
        <i style="background:${p.primary}"></i><i style="background:${p.secondary}"></i>
        <i style="background:${p.tertiary}"></i><i style="background:${p.surfaceContainer}"></i>
      </span><span class="name">${key.replace(/([A-Z])/g, ' $1')}</span></button>`;
  }).join('');
  host.querySelectorAll('button').forEach(b => b.onclick = () => applyPalette(b.dataset.palette));
}

function renderRail() {
  document.getElementById('capsule').innerHTML =
    `<div class="mark" title="Aerix"><svg viewBox="0 0 64 64">${ICONS.mark}</svg></div>` +
    DESTINATIONS.map(d => `<div class="slot ${d.key === current ? 'on' : ''}" data-key="${d.key}" title="${d.label}">${svg(d.icon)}</div>`).join('') +
    `<div class="divider"></div>` +
    `<div class="slot ${current === 'accounts' ? 'on' : ''}" data-key="accounts" title="Accounts">${svg('person')}</div>`;
  document.getElementById('capsule').querySelectorAll('.slot').forEach(s => s.onclick = () => show(s.dataset.key));
}

function show(key) {
  current = key;
  const d = DESTINATIONS.find(x => x.key === key);
  document.getElementById('page').innerHTML = PAGES[key] || PAGES.home;
  renderRail();
  if (key === 'settings') renderSwatches();
  document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('on', b.dataset.key === key));
}

document.getElementById('tabs').innerHTML = DESTINATIONS.map(d => `<button data-key="${d.key}">${d.title}</button>`).join('');
document.querySelectorAll('#tabs button').forEach(b => b.onclick = () => show(b.dataset.key));
document.getElementById('palettes').innerHTML = Object.keys(PALETTES).map(k => `<button data-key="${k}">${PALETTES[k].__label}</button>`).join('');
document.querySelectorAll('#palettes button').forEach(b => b.onclick = () => applyPalette(b.dataset.key));

applyPalette('embermire');
show('home');
</script>
</body>
</html>
'''


def main():
    palettes = load_palettes()
    for key, entry in palettes.items():
        entry['__label'] = LABELS[key]
    html = HTML.replace('__PALETTES__', json.dumps(palettes, indent=2))
    out = os.path.join(ROOT, 'preview', 'index.html')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as f:
        f.write(html)
    print('wrote', out, len(html), 'bytes')
    for key in PALETTES:
        print('  ', key, len(palettes[key]), 'roles')


if __name__ == '__main__':
    main()
