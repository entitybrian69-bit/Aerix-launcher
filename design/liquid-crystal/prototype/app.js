// Aerix Liquid Crystal - interactive prototype shell.

import { ICONS } from './icons.js';
import { SCREENS, TABS } from './screens.js';

const TINT = {
  cyan: '#6ef0ff', mint: '#7cffcb', violet: '#a78bfa', azure: '#60a5fa',
  rose: '#ff8ab0', amber: '#ffc77a', lime: '#baff8d', white: '#e8f4ff',
};
const tint = (n) => TINT[n] || TINT.cyan;

const hex2rgb = (h) => {
  const v = h.replace('#', '');
  return [parseInt(v.slice(0, 2), 16), parseInt(v.slice(2, 4), 16), parseInt(v.slice(4, 6), 16)];
};
const rgba = (h, a) => { const [r, g, b] = hex2rgb(h); return `rgba(${r},${g},${b},${a})`; };
const mix = (h, a) => { const [r, g, b] = hex2rgb(h); return `rgba(${r},${g},${b},${a})`; };

const svg = (name, size) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ` +
  `stroke-linecap="round" stroke-linejoin="round" width="${size || '100%'}" ` +
  `height="${size || '100%'}">${ICONS[name] || ''}</svg>`;
const ico = (name, size) => `<span class="ico">${svg(name, size)}</span>`;

function orb(name, tintName, size, strong) {
  const c = tint(tintName);
  const bg = strong
    ? `linear-gradient(150deg, ${mix(c, .34)}, ${mix(c, .10)})`
    : `linear-gradient(150deg, rgba(255,255,255,.20), rgba(255,255,255,.06))`;
  const shadow = strong
    ? `inset 0 0 0 1px ${mix(c, .55)}, 0 0 26px ${mix(c, .30)}`
    : `inset 0 0 0 1px rgba(255,255,255,.30)`;
  return `<div class="orb" style="width:${size}px;height:${size}px;background:${bg};` +
         `box-shadow:${shadow};color:${c}">${ico(name, size * 0.46)}</div>`;
}

function chip(label, tintName, iconName) {
  const c = tint(tintName);
  return `<span class="chip glass" style="color:${c}">` +
         (iconName ? ico(iconName, 20) : '') + `${label}</span>`;
}

function bar(value, tintName) {
  const c = tint(tintName);
  return `<div class="bar"><i style="width:${value * 100}%;` +
         `background:linear-gradient(90deg,${mix(c, .6)},${mix('#7cffcb', .95)})"></i>` +
         `<u style="left:${value * 100}%;box-shadow:0 0 18px ${mix(c, .8)},0 0 0 2px ${mix(c, .7)}"></u></div>`;
}

const toggle = (on) =>
  `<div class="toggle ${on ? 'on' : ''}" data-toggle>${on ? '' : ''}<i></i></div>`;

// --------------------------------------------------------------- blocks ---
function block(b) {
  switch (b.t) {
    case 'section': return `<div class="section-label" style="height:38px;margin-bottom:8px">` +
      (b.icon ? ico(b.icon, 22) : '') + `<span>${b.label}</span>` +
      (b.right ? `<em>${b.right}</em>` : '') + `</div>`;

    case 'gap': return `<div style="height:${b.h}px"></div>`;

    case 'row': return rowBlock(b);

    case 'tiles': return `<div class="grid" style="grid-template-columns:repeat(${b.cols},1fr)">` +
      b.items.map((i) => tileBlock(i, b.h)).join('') + `</div>`;

    case 'slider': return sliderBlock(b);

    case 'note': return `<div class="note glass" style="height:${b.h}px;background:` +
      `linear-gradient(158deg,${mix(tint(b.tint), .13)},rgba(255,255,255,.05))">` +
      orb(b.icon, b.tint, 52) + `<p>${b.text}</p></div>`;

    case 'search': return `<div class="search glass" style="height:84px">` +
      ico('search', 24) + `<span>${b.placeholder}</span>` +
      `<div class="btn glass">${ico('sliders-horizontal', 24)}</div></div>`;

    case 'segmented': return `<div class="segmented glass" style="height:${b.h || 72}px">` +
      b.items.map((label, i) => `<button class="${i === b.active ? 'on' : ''}" data-seg="${i}">${label}</button>`).join('') +
      `</div>`;

    case 'button': {
      const inner = `<span>${b.icon ? ico(b.icon, b.accent ? 30 : 28) : ''}${b.label}</span>`;
      return `<div class="${b.accent ? 'btn-primary' : 'btn-ghost glass'}" style="height:${b.h || 96}px">${inner}</div>`;
    }

    case 'custom': return custom(b);
    default: return '';
  }
}

function rowBlock(b) {
  const h = b.h || 92;
  const trail =
    b.control === 'chevron' ? `<div class="trail">${ico('chevron-right', 28)}</div>` :
    b.control === 'value' ? `<div class="trail"><span class="val">${b.value}</span>${ico('chevron-right', 26)}</div>` :
    b.control === 'text' ? `<div class="trail"><span class="val" style="color:${tint(b.tint)}">${b.value}</span></div>` :
    (b.control === 'toggle_on' || b.control === 'toggle_off')
      ? `<div class="trail">${toggle(b.control === 'toggle_on')}</div>` : '';
  return `<div class="row glass ${b.sub ? '' : 'plain'}" style="height:${h}px">` +
    orb(b.icon, b.tint, 62) +
    `<div class="txt"><b>${b.title}</b>${b.sub ? `<small>${b.sub}</small>` : ''}</div>` +
    trail + `</div>`;
}

function tileBlock(i, h) {
  const on = i.control === 'toggle_on';
  const ctrl = (i.control === 'toggle_on' || i.control === 'toggle_off')
    ? toggle(i.control === 'toggle_on') : '';
  return `<div class="tile glass ${on ? 'on' : ''}" style="height:${h}px">` +
    orb(i.icon, i.tint, 56, on) + `<b>${i.title}</b>${ctrl}</div>`;
}

function sliderBlock(b) {
  return `<div class="slider-row glass" style="height:${b.h || 100}px">` +
    orb(b.icon, b.tint, 56) +
    `<div class="txt"><b>${b.title}</b><small>${b.valueText}</small></div>` +
    `<div class="bar-wrap" style="width:250px">${bar(b.value, b.tint)}</div></div>`;
}

// -------------------------------------------------------------- custom ---
function custom(b) {
  switch (b.id) {
    case 'hero': return `
      <div class="hero glass" style="height:${b.h}px">
        ${orb('box', 'mint', 104, true)}
        <div class="txt">
          <h2>Aerix Survival</h2>
          <p>Minecraft 1.21.4 · Fabric 0.16.9 · 18 mods</p>
          <div class="chips">
            ${chip('RELEASE', 'mint')}${chip('FABRIC', 'violet')}${chip('18 MODS', 'azure')}
          </div>
        </div>
        ${orb('chevron-right', 'white', 64)}
      </div>`;

    case 'console': return `
      <div class="console glass" style="height:${b.h}px">
        <div class="launcher">
          <div class="halo"></div>
          <div class="ring"></div>
          <div class="core" id="launchCore">${ico('play', 94)}</div>
        </div>
        <div class="launchlabel">LAUNCH</div>
        <div class="launchsub">Hold for quick settings</div>
        <div class="chips">
          ${chip('1.21.4', 'mint')}${chip('JRE 21', 'cyan')}${chip('4096 MB', 'violet')}${chip('MobileGlues', 'azure')}
        </div>
      </div>`;

    case 'tasks': return `
      <div class="tasks glass" style="height:${b.h}px">
        <div class="task" style="top:30px">
          ${orb('download', 'cyan', 56)}
          <div class="txt"><b>Downloading assets</b><small>62% · 148.2 MB / 239 MB</small>
          <div style="margin-top:16px">${bar(0.62, 'cyan')}</div></div>
        </div>
        <div class="task" style="bottom:30px">
          ${orb('file-archive', 'mint', 56)}
          <div class="txt"><b>Unpacking JRE 21</b>
          <div style="margin-top:16px">${bar(0.28, 'mint')}</div></div>
        </div>
      </div>`;

    case 'addaccount': return `
      <div class="addaccount glass" style="height:${b.h}px">
        <label>ADD ACCOUNT</label>
        <div class="chips">
          ${chip('Microsoft', 'cyan')}${chip('Ely.by', 'violet')}${chip('Offline', 'mint')}
        </div>
      </div>`;

    case 'instancecard': {
      const d = b.data, c = tint(d.tint);
      return `
      <div class="hero glass" style="height:${b.h}px;${d.active
        ? `background:linear-gradient(158deg,${mix(c, .22)},rgba(255,255,255,.06));box-shadow:inset 0 0 0 1.6px ${mix(c, .55)},0 16px 38px rgba(0,0,0,.42),0 0 30px ${mix(c, .22)}`
        : ''}">
        ${orb(d.icon, d.tint, 88, d.active)}
        <div class="txt">
          <h2 style="font-size:26px">${d.name}</h2>
          <p style="font-size:18px">${d.sub}</p>
          ${d.active ? `<p style="font-size:15px;font-weight:800;letter-spacing:2px;color:${c};margin-top:6px">RUNNING</p>` : ''}
        </div>
        ${chip(d.size, d.tint)}
        ${orb('more-horizontal', 'white', 60)}
      </div>`;
    }

    case 'editor': return `
      <div class="editor glass" style="height:${b.h}px">
        <div style="height:150px"></div>
        <div class="avatar glass" style="color:${tint('mint')}">${ico('box', 48)}
          <div class="badge">${ico('edit-3', 20)}</div></div>
        <div style="font-size:16px;font-weight:800;letter-spacing:2.2px;color:#6c88a1">PROFILE NAME</div>
        <div class="field glass" style="margin-top:8px">Aerix Survival</div>
        <div style="margin-top:22px;display:flex;flex-direction:column;gap:12px">
          ${rowBlock({ icon: 'layers', tint: 'mint', title: 'Version', sub: 'Minecraft 1.21.4', control: 'value', value: 'Minecraft 1.21.4', h: 92 })}
          ${rowBlock({ icon: 'gamepad-2', tint: 'azure', title: 'Control scheme', sub: 'Aerix default', control: 'value', value: 'Aerix default', h: 92 })}
          ${rowBlock({ icon: 'database', tint: 'violet', title: 'Shared data', sub: 'Disabled — private game directory', control: 'toggle_off', h: 92 })}
          ${rowBlock({ icon: 'terminal', tint: 'cyan', title: 'JVM arguments', sub: '-Xmx4096M -XX:+UseG1GC', control: 'value', value: '-Xmx4096M -XX:+UseG1GC', h: 92 })}
          ${rowBlock({ icon: 'coffee', tint: 'amber', title: 'Java runtime', sub: 'JRE 21.0.3', control: 'value', value: 'JRE 21.0.3', h: 92 })}
          ${rowBlock({ icon: 'monitor-play', tint: 'rose', title: 'Renderer', sub: 'MobileGlues (OpenGL ES)', control: 'value', value: 'MobileGlues', h: 92 })}
        </div>
        <div class="actions">
          <div class="btn-primary" style="height:84px"><span>${ico('check', 30)}SAVE</span></div>
          <div class="btn-ghost glass" style="height:84px;color:${tint('rose')}"><span>${ico('trash-2', 28)}DELETE</span></div>
        </div>
      </div>`;

    case 'installer': return `
      <div class="installer glass" style="height:${b.h}px">
        <div class="top">
          ${orb('puzzle', 'violet', 84, true)}
          <div><h3>Fabric Loader</h3><p>Lightweight mod loader for modern versions</p></div>
        </div>
        <div class="step">${chip('STEP 2', 'violet')}</div>
        <div class="rows">
          ${rowBlock({ icon: 'layers', tint: 'mint', title: 'Game version', control: 'value', value: '1.21.4', h: 88 })}
          ${rowBlock({ icon: 'package', tint: 'cyan', title: 'Loader version', control: 'value', value: '0.16.9', h: 88 })}
          ${rowBlock({ icon: 'shield-check', tint: 'violet', title: 'Only stable releases', control: 'toggle_on', h: 88 })}
        </div>
      </div>`;

    case 'modcard': {
      const d = b.data;
      return `
      <div class="modcard glass" style="height:${b.h}px">
        ${orb('puzzle', d.tint, 76)}
        <div class="txt"><b>${d.name}</b><small>${d.sub}</small><span>${d.ver}</span></div>
        ${chip(d.size, d.tint)}
        ${orb('download', d.tint, 60)}
      </div>`;
    }

    case 'stage': return `
      <div class="stage glass" style="height:${b.h}px">
        <div class="scene"></div><div class="grid"></div>
        <div class="hotbar" style="left:210px;top:96px;width:500px">
          ${Array.from({ length: 9 }, (_, i) => `<i class="${i === 2 ? 'on' : ''}"></i>`).join('')}
        </div>
        <div class="stick" style="left:66px;top:288px"><i></i></div>
        <div class="cbtn" style="left:748px;top:252px;color:${tint('mint')}">A</div>
        <div class="cbtn" style="left:856px;top:344px;color:${tint('rose')}">B</div>
        <div class="cbtn" style="left:748px;top:436px;color:${tint('azure')}">X</div>
        <div class="cbtn" style="left:856px;top:528px;color:${tint('amber')}">Y</div>
        <div class="cbtn sel" style="left:426px;top:422px;color:${tint('violet')}">⇧</div>
        <div class="cursor" style="left:534px;top:214px">${ico('mouse-pointer-2', 52)}</div>
        <div class="meta">GRID 8 × 8 · SNAP ON</div>
      </div>`;

    case 'toolbar': return `
      <div class="toolbar glass" style="height:${b.h}px">
        ${[['plus', 'mint'], ['columns-3', 'cyan'], ['circle-dot', 'azure'], ['copy-plus', 'violet'],
           ['folder-open', 'amber'], ['save', 'mint'], ['rotate-ccw', 'rose']]
          .map(([n, c]) => `<div class="slot">${orb(n, c, 62, n === 'plus')}</div>`).join('')}
      </div>`;

    case 'categories': {
      const cats = [
        ['Video and renderer', 'Resolution, renderer and performance', 'monitor-play', 'rose', '3 sections'],
        ['Control customization', 'Gestures, buttons and scaling', 'gamepad-2', 'azure', '6 sections'],
        ['Java tweaks', 'Runtimes, JVM arguments, RAM and sandbox', 'coffee', 'amber', '4 settings'],
        ['Miscellaneous', 'Version list and libraries check', 'folder-cog', 'cyan', '8 settings'],
        ['Experimental', 'Use with consideration, no support', 'flask-conical', 'violet', '5 flags'],
      ];
      const card = ([title, sub, icon, c, count], full) => `
        <div class="catcard glass" style="${full ? '' : 'width:490px'}">
          ${orb(icon, c, 68)}
          <h3>${title}</h3><p>${sub}</p>
          <div class="count" style="color:${tint(c)}">${count}</div>
          <div class="chev">${ico('chevron-right', 26)}</div>
        </div>`;
      return `<div style="display:flex;flex-wrap:wrap;gap:20px;height:${b.h}px;align-content:flex-start">
        ${card(cats[0])}${card(cats[1])}${card(cats[2])}${card(cats[3])}${card(cats[4], true)}
      </div>`;
    }

    case 'renderers': {
      const list = [
        ['GL4ES', 'OpenGL ES 2 · 1.21.4-', 1], ['Krypton Wrapper', 'OpenGL ES 3.2 · 26.2-', 0],
        ['Zink', 'Vulkan · all versions', 0], ['LTW', 'OpenGL ES 3 · 1.17+', 0],
        ['MobileGlues', 'OpenGL ES · 1.17+', 1], ['SFPEW / MobileGlues', 'OpenGL ES · all', 0],
        ['Freedreno (KGSL)', 'all versions', 0], ['Mesa (DRM)', 'all versions', 0],
        ['Mesa (DRM, external)', 'all versions', 0], ['Legacy Zink', 'Vulkan · all versions', 0],
      ];
      return `<div class="grid" style="grid-template-columns:1fr 1fr;gap:12px">` +
        list.map(([name, note, on]) => `
          <div class="picktile glass ${on ? 'on' : ''}" style="height:80px" data-pick>
            <div class="radio"></div>
            <div class="txt"><b>${name}</b><small>${note}</small></div>
          </div>`).join('') + `</div>`;
    }

    case 'runtimes': {
      const list = [
        ['JRE 8', 'Legacy · 1.16.5 and lower', false, 'cyan'],
        ['JRE 11', 'Older mod loaders and 1.17', false, 'azure'],
        ['JRE 17', 'Modern versions · 1.18 - 1.20.4', false, 'mint'],
        ['JRE 21', 'Default · 1.20.5 and newer', true, 'amber'],
      ];
      return `<div class="grid" style="grid-template-columns:1fr 1fr;gap:12px">` +
        list.map(([name, note, active, c]) => `
          <div class="runtile glass" style="${active
            ? `background:linear-gradient(158deg,${mix(tint(c), .22)},rgba(255,255,255,.05));box-shadow:inset 0 0 0 1.6px ${mix(tint(c), .5)},0 0 26px ${mix(tint(c), .2)}`
            : ''}">
            ${orb('coffee', c, 76, active)}
            <div class="txt"><b>${name}</b><small>${note}</small></div>
            ${active ? chip('DEFAULT', c) : orb('download', 'white', 56)}
          </div>`).join('') + `</div>`;
    }

    case 'args': return `
      <div class="args glass" style="height:${b.h}px">
        <label>JVM LAUNCH ARGUMENTS</label>
        <code>${b.data.value}</code>
      </div>`;

    case 'storage': {
      const items = [['Instances', '24.8 GB', .82, 'mint'], ['Runtimes', '3.6 GB', .34, 'amber'],
                     ['Cache', '2.0 GB', .20, 'violet']];
      return `<div class="storage glass" style="height:${b.h}px">` +
        items.map(([label, val, v, c]) => `
          <div class="line">
            ${orb('hard-drive', c, 52)}
            <div class="lab">${label}</div>
            <div class="bar-wrap" style="flex:1">${bar(v, c)}</div>
            <div class="val" style="color:${tint(c)}">${val}</div>
          </div>`).join('') + `</div>`;
    }

    case 'danger': return `
      <div class="danger glass" style="height:${b.h}px;background:linear-gradient(158deg,${mix(tint('rose'), .13)},rgba(255,255,255,.04))">
        <div class="top">
          ${orb('triangle-alert', 'rose', 60)}
          <div><b>Reset experimental options</b>
          <p>Every flag on this page returns to its default value</p></div>
        </div>
        <div class="act btn-ghost glass" style="color:${tint('rose')};font-size:21px">
          <span>${ico('rotate-ccw', 24)}RESET ALL FLAGS</span>
        </div>
      </div>`;

    case 'experiments': return `
      <div class="experiments glass" style="height:${b.h}px">
        <label>SESSION STATE</label>
        <div class="chips">
          ${chip('ANGLE', 'cyan')}${chip('Force VSync', 'mint')}${chip('Big-core', 'amber')}
          ${chip('Shaders dumped', 'violet')}${chip('Verbose off', 'white')}
        </div>
      </div>`;

    default: return '';
  }
}

// ----------------------------------------------------------------- shell ---
const dockEl = document.getElementById('dock');
const contentEl = document.getElementById('content');
const headEl = document.getElementById('head');
const legendEl = document.getElementById('legendList');
let current = 0;

function renderDock() {
  dockEl.innerHTML = TABS.map((tab, i) => {
    const c = tint(tab.tint);
    return `<div class="slot ${i === current ? 'on' : ''}" data-tab="${i}" style="color:${c}">
      ${i === current ? '<div class="pill"></div><div class="dot"></div>' : ''}
      ${ico(tab.icon, i === current ? 32 : 31)}
      <label>${tab.name}</label></div>`;
  }).join('');
}

function renderHead(i) {
  const s = SCREENS[i];
  headEl.innerHTML =
    (s.kicker ? `<div class="kicker">${s.kicker}</div>` : '') +
    `<h1>${s.title}</h1>` +
    (s.subtitle ? `<p>${s.subtitle}</p>` : '') +
    `<div class="head-actions">` +
    (s.actions || []).map((a) => orb(a.icon, a.tint, 72, a.glow)).join('') + `</div>`;
}

function renderPage(i, animate) {
  const page = document.createElement('div');
  page.className = 'page' + (animate ? ' enter' : '');
  page.innerHTML = SCREENS[i].blocks.map(block).join('');
  contentEl.innerHTML = '';
  contentEl.appendChild(page);
  if (animate) requestAnimationFrame(() => page.classList.remove('enter'));
  return page;
}

function renderLegend() {
  legendEl.innerHTML = SCREENS.map((s, i) =>
    `<li class="${i === current ? 'on' : ''}" data-tab="${i}"><b>${String(i + 1).padStart(2, '0')}</b>${s.title}</li>`
  ).join('');
}

function go(i) {
  if (i === current) return;
  const old = contentEl.querySelector('.page');
  if (old) old.classList.add('leave');
  current = i;
  renderHead(i);
  renderDock();
  renderLegend();
  const page = renderPage(i, true);
  setTimeout(() => { if (old) old.remove(); }, 380);
  return page;
}

// interactions -------------------------------------------------------------
document.addEventListener('click', (ev) => {
  const slot = ev.target.closest('[data-tab]');
  if (slot) { go(Number(slot.dataset.tab)); return; }

  const tg = ev.target.closest('[data-toggle]');
  if (tg) { tg.classList.toggle('on'); return; }

  const seg = ev.target.closest('[data-seg]');
  if (seg) {
    seg.parentElement.querySelectorAll('button').forEach((b) => b.classList.remove('on'));
    seg.classList.add('on');
    return;
  }

  const pick = ev.target.closest('[data-pick]');
  if (pick) {
    pick.parentElement.querySelectorAll('[data-pick]').forEach((p) => p.classList.remove('on'));
    pick.classList.add('on');
  }
});

document.addEventListener('keydown', (ev) => {
  if (ev.key === 'ArrowRight') go((current + 1) % SCREENS.length);
  if (ev.key === 'ArrowLeft') go((current - 1 + SCREENS.length) % SCREENS.length);
});

// launch pulse + parallax ---------------------------------------------------
document.addEventListener('pointerdown', (ev) => {
  const core = ev.target.closest('#launchCore');
  if (!core) return;
  core.animate(
    [{ transform: 'scale(1)' }, { transform: 'scale(.92)' }, { transform: 'scale(1.04)' }, { transform: 'scale(1)' }],
    { duration: 620, easing: 'cubic-bezier(.2,.9,.25,1)' }
  );
});

window.addEventListener('pointermove', (ev) => {
  const dx = (ev.clientX / window.innerWidth - .5) * 26;
  const dy = (ev.clientY / window.innerHeight - .5) * 26;
  document.querySelectorAll('.blob').forEach((b, i) => {
    const depth = 1 + (i % 3) * 0.7;
    b.style.setProperty('translate', `${dx * depth}px ${dy * depth}px`);
  });
});

function fit() {
  const wide = window.innerWidth > 1500 ? 420 : 80;
  const s = Math.min((window.innerWidth - wide) / 1080, (window.innerHeight - 70) / 2400);
  document.getElementById('phone').style.setProperty('--s', Math.max(0.2, Math.min(s, 0.62)));
}
window.addEventListener('resize', fit);

// boot ---------------------------------------------------------------------
document.getElementById('statusIcons').innerHTML =
  ['signal', 'wifi', 'battery-full'].map((n) => `<span class="ico" style="width:24px;height:24px">${svg(n)}</span>`).join('');
renderHead(0);
renderDock();
renderLegend();
renderPage(0, false);
fit();
