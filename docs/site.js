/* ============================================================================
   Aerix Launcher — site script

   Five small jobs, no dependencies, no build step:
     1. keep the download links and their sizes true to the newest release,
     2. open screenshots in a lightbox,
     3. run the mobile menu and the sticky header,
     4. reveal sections as they scroll in,
     5. count the stat band up when it comes into view,
     6. total the APK downloads GitHub reports for every release.

   Everything degrades: with JavaScript off the buttons still point at GitHub's
   releases/latest permalinks, which download straight away.
   ============================================================================ */

(function () {
  'use strict';

  var REPO = 'entitybrian69-bit/Aerix-launcher';
  var LATEST_API = 'https://api.github.com/repos/' + REPO + '/releases/latest';
  var API = 'https://api.github.com/repos/' + REPO + '/releases?per_page=15';

  function $(id) { return document.getElementById(id); }
  function all(selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  }

  function megabytes(bytes) {
    if (!bytes || !isFinite(bytes)) return null;
    var mb = Math.round((bytes / 1048576) * 10) / 10;
    return (mb % 1 === 0 ? mb : mb.toFixed(1)) + ' MB';
  }

  function shortDate(iso) {
    if (!iso) return null;
    var d = new Date(iso);
    if (isNaN(d.getTime())) return null;
    return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  }

  /* ── the newest release ─────────────────────────────────────────────────── */

  function isPublicRelease(r) {
    return !!(r && !r.draft && !r.prerelease && r.tag_name && !/debug/i.test(r.tag_name) &&
      (r.assets || []).some(function (a) {
        return /\.apk$/i.test(a.name || '') && !/debug/i.test(a.name || '');
      }));
  }

  function newestRelease(releases) {
    var usable = (releases || []).filter(isPublicRelease);
    return usable[0] || null;
  }

  /* Map an asset name to the card it belongs in. */
  function kindOf(name) {
    if (!/\.apk$/i.test(name)) return null;
    var n = name.toLowerCase();
    if (n.indexOf('debug') !== -1) return null;
    var noruntime = n.indexOf('noruntime') !== -1;
    if (n.indexOf('arm64') !== -1) return noruntime ? 'noruntime-arm64' : 'arm64';
    if (n.indexOf('armeabi') !== -1) return noruntime ? 'noruntime-armv7' : 'armv7';
    if (noruntime) return 'noruntime-universal';
    return 'universal';
  }

  function applyRelease(release) {
    if (!release) return;
    var version = (release.tag_name || '').replace(/^v/, '');
    var date = shortDate(release.published_at);

    all('[data-release-version]').forEach(function (el) { el.textContent = version || ''; });
    all('[data-release-date]').forEach(function (el) { el.textContent = date || ''; });
    all('[data-release-link]').forEach(function (el) { el.href = release.html_url; });

    var byKind = {};
    (release.assets || []).forEach(function (asset) {
      var kind = kindOf(asset.name);
      if (kind && !byKind[kind]) byKind[kind] = asset;
    });

    all('[data-dl]').forEach(function (card) {
      var kind = card.getAttribute('data-dl');
      var asset = byKind[kind];
      if (!asset) {
        card.setAttribute('aria-disabled', 'true');
        var size = card.querySelector('.size');
        if (size) size.textContent = 'not in this release';
        return;
      }
      card.href = asset.browser_download_url;
      card.setAttribute('download', asset.name);
      var sizeEl = card.querySelector('.size');
      if (sizeEl) sizeEl.textContent = megabytes(asset.size) || '';
      var nameEl = card.querySelector('.asset');
      if (nameEl) nameEl.textContent = asset.name;
      card.removeAttribute('aria-disabled');
    });

    // The big button gets the build most phones want; the picker below covers the rest.
    var primary = byKind.arm64 || byKind.universal;
    all('[data-primary-download]').forEach(function (btn) {
      if (!primary) return;
      btn.href = primary.browser_download_url;
      btn.setAttribute('download', primary.name);
      var note = btn.querySelector('.btn-size');
      if (note) note.textContent = megabytes(primary.size) || '';
    });

    var facts = $('downloadFacts');
    if (facts) {
      var parts = [];
      if (version) parts.push('v' + version);
      if (date) parts.push(date);
      facts.textContent = parts.filter(Boolean).join(' · ');
    }
  }

  /* ── downloads, summed from the releases themselves ─────────────────────── */

  function setupDownloads() {
    var targets = all('[data-downloads]');
    if (!targets.length || !window.fetch) return;

    var api = 'https://api.github.com/repos/' + REPO + '/releases?per_page=100';
    var total = 0;
    var seen = {};

    function paint() {
      if (!total) return;
      var text = total.toLocaleString();
      targets.forEach(function (el) {
        el.textContent = text;
        el.classList.add('is-live');
      });
    }

    function add(list) {
      (list || []).forEach(function (release) {
        if (!release || release.draft || release.prerelease || /debug/i.test(release.tag_name || '')) return;
        var id = release.id || release.tag_name;
        if (seen[id]) return;
        seen[id] = true;
        (release.assets || []).forEach(function (asset) {
          if (!/\.apk$/i.test(asset.name || '') || /debug/i.test(asset.name || '')) return;
          if (typeof asset.download_count === 'number') total += asset.download_count;
        });
      });
    }

    // One page covers 100 releases; that is already more than this project will ever publish,
    // and a failure only means the counter keeps its placeholder.
    fetch(api, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (response) { return response.ok ? response.json() : []; })
      .then(function (list) { add(list); paint(); })
      .catch(function () { /* leave the dashes */ });
  }

  function loadRelease() {
    if (!window.fetch) return;
    fetch(LATEST_API, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (release) {
        if (isPublicRelease(release)) {
          applyRelease(release);
          return null;
        }
        return fetch(API, { headers: { Accept: 'application/vnd.github+json' } })
          .then(function (r2) { return r2.ok ? r2.json() : []; })
          .then(function (releases) { applyRelease(newestRelease(releases)); });
      })
      .catch(function () {
        /* Offline, rate limited, or blocked: the static links and sizes stay. */
      });
  }

  /* ── lightbox ───────────────────────────────────────────────────────────── */

  function setupLightbox() {
    var links = all('[data-lightbox]');
    if (!links.length) return;

    var box = document.createElement('div');
    box.className = 'lightbox';
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-modal', 'true');
    box.setAttribute('aria-label', 'Screenshot');
    box.innerHTML = '<button class="lightbox-close" type="button" aria-label="Close">✕</button>';
    var image = document.createElement('img');
    box.appendChild(image);
    document.body.appendChild(box);

    var lastFocus = null;

    function close() {
      box.classList.remove('is-open');
      image.removeAttribute('src');
      document.body.style.overflow = '';
      if (lastFocus) lastFocus.focus();
    }

    function open(link) {
      var full = link.getAttribute('href');
      var thumb = link.querySelector('img');
      image.src = full;
      image.alt = (thumb && thumb.alt) || 'Screenshot';
      box.classList.add('is-open');
      document.body.style.overflow = 'hidden';
      lastFocus = link;
      box.querySelector('.lightbox-close').focus();
    }

    links.forEach(function (link) {
      link.addEventListener('click', function (event) {
        if (event.metaKey || event.ctrlKey || event.shiftKey) return; // let "open in new tab" work
        event.preventDefault();
        open(link);
      });
    });

    box.addEventListener('click', function (event) {
      if (event.target === box || event.target.classList.contains('lightbox-close')) close();
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && box.classList.contains('is-open')) close();
    });
  }

  /* ── navigation ─────────────────────────────────────────────────────────── */

  function setupNav() {
    var nav = document.querySelector('.nav');
    var toggle = $('navToggle');
    var links = $('navLinks');

    function onScroll() {
      if (!nav) return;
      nav.classList.toggle('is-stuck', window.scrollY > 8);
      var doc = document.documentElement;
      var span = doc.scrollHeight - window.innerHeight;
      var done = span > 0 ? Math.min(1, Math.max(0, window.scrollY / span)) : 0;
      nav.style.setProperty('--progress', done.toFixed(4));
    }

    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });

    if (!toggle || !links) return;

    function setOpen(open) {
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
      links.classList.toggle('is-open', open);
    }

    toggle.addEventListener('click', function () {
      setOpen(toggle.getAttribute('aria-expanded') !== 'true');
    });

    all('a', links).forEach(function (link) {
      link.addEventListener('click', function () { setOpen(false); });
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setOpen(false);
    });

    window.addEventListener('resize', function () {
      if (window.innerWidth > 900) setOpen(false);
    });
  }

  /* ── reveal on scroll ───────────────────────────────────────────────────── */

  function setupReveal() {
    var items = all('.reveal');
    if (!items.length) return;

    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || !('IntersectionObserver' in window)) {
      items.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-in');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.06 });

    items.forEach(function (el) { observer.observe(el); });
  }

  /* ── the stat band counts itself in ─────────────────────────────────────── */

  function setupCounters() {
    var numbers = all('[data-count]');
    if (!numbers.length) return;

    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced || !('IntersectionObserver' in window)) return;

    function run(el) {
      var target = parseInt(el.getAttribute('data-count'), 10);
      if (!isFinite(target)) return;
      var start = null;
      var seen = 0;
      function step(now) {
        if (start === null) start = now;
        var t = Math.min(1, (now - start) / 900);
        var eased = 1 - Math.pow(1 - t, 3);
        var value = Math.round(target * eased);
        if (value !== seen) { el.textContent = String(value); seen = value; }
        if (t < 1) requestAnimationFrame(step);
        else el.textContent = String(target);
      }
      requestAnimationFrame(step);
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        run(entry.target);
      });
    }, { threshold: 0.4 });

    numbers.forEach(function (el) { observer.observe(el); });
  }

  /* ── go ─────────────────────────────────────────────────────────────────── */

  function start() {
    setupNav();
    setupReveal();
    setupCounters();
    setupLightbox();
    loadRelease();
    setupDownloads();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
