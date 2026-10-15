/* ============================================================================
   Aerix Launcher — website script

   Two jobs, both small:
     1. keep the download button pointing at the newest GitHub release, so the
        page never hands out a stale APK, and
     2. reveal sections as they scroll in, and run the mobile menu.

   No dependencies, no build step. If the Releases API cannot be reached the
   markup already points at /releases/latest, which GitHub keeps current anyway.
   ============================================================================ */

(function () {
  'use strict';

  var REPO = 'entitybrian69-bit/Aerix-launcher';
  var RELEASES = 'https://github.com/' + REPO + '/releases';
  var API = 'https://api.github.com/repos/' + REPO + '/releases/latest';
  var CACHE_KEY = 'aerix.latest-release';
  var CACHE_TTL = 10 * 60 * 1000; // the page can be open for a while; ten minutes is plenty

  /* ── small helpers ─────────────────────────────────────────────────────── */

  function $(id) { return document.getElementById(id); }

  function megabytes(bytes) {
    if (!bytes || !isFinite(bytes)) return null;
    return Math.round(bytes / 1048576) + ' MB';
  }

  function isApk(name) {
    return /\.apk$/i.test(name || '');
  }

  /* The full build carries its own Java runtime; the other one downloads it. */
  function pickAssets(assets) {
    var apks = (assets || []).filter(function (a) { return isApk(a.name); });
    var full = apks.filter(function (a) { return !/noruntime/i.test(a.name); })[0] || apks[0] || null;
    var light = apks.filter(function (a) { return /noruntime/i.test(a.name); })[0] || null;
    return { full: full, light: light };
  }

  /* ── the live download button ──────────────────────────────────────────── */

  function applyRelease(release) {
    var chipText = $('releaseChipText');
    var button = $('downloadBtn');
    var meta = $('downloadMeta');
    var alt = $('downloadAlt');
    if (!release) return;

    var tag = (release.tag_name || '').replace(/^v/i, '');
    var assets = pickAssets(release.assets);

    if (chipText && tag) {
      chipText.textContent = 'v' + tag + ' · latest release';
    }

    if (button && assets.full && assets.full.browser_download_url) {
      button.href = assets.full.browser_download_url;
      var label = 'Download ' + (tag ? 'v' + tag + ' ' : 'the latest ') + 'APK';
      button.innerHTML = '<span class="btn-ico" aria-hidden="true">↓</span>' + label;
    }

    if (meta) {
      var size = assets.full ? megabytes(assets.full.size) : null;
      meta.innerHTML = (size ? size + ' · ' : '') +
        'Android 8.0+ · release build, signed with the project key' +
        ' · <a href="' + RELEASES + '" target="_blank" rel="noopener">all releases</a>';
    }

    if (alt && assets.light && assets.light.browser_download_url) {
      var lightSize = megabytes(assets.light.size);
      alt.hidden = false;
      alt.innerHTML = 'Smaller download: <a href="' + assets.light.browser_download_url +
        '">' + assets.light.name + (lightSize ? ' (' + lightSize + ')' : '') +
        '</a> — the same build without the bundled Java runtime.';
    }
  }

  function loadRelease() {
    var cached = readCache();
    if (cached) {
      applyRelease(cached);
      return;
    }

    if (!window.fetch) return; // the markup already links to /releases/latest

    fetch(API, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (release) {
        if (release && !release.prerelease && release.tag_name) {
          writeCache(release);
          applyRelease(release);
        }
      })
      .catch(function () {
        /* Offline, rate-limited or blocked: the static link stays, and it still
           points at the newest release because that is what /releases/latest is. */
      });
  }

  function readCache() {
    try {
      var raw = window.localStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      var parsed = JSON.parse(raw);
      if (!parsed || !parsed.at || Date.now() - parsed.at > CACHE_TTL) return null;
      return parsed.release;
    } catch (e) {
      return null;
    }
  }

  function writeCache(release) {
    try {
      window.localStorage.setItem(CACHE_KEY, JSON.stringify({ at: Date.now(), release: release }));
    } catch (e) {
      /* private mode: not worth reporting */
    }
  }

  /* ── the mobile menu ───────────────────────────────────────────────────── */

  function wireMenu() {
    var toggle = $('navToggle');
    var links = $('navLinks');
    if (!toggle || !links) return;

    function setOpen(open) {
      links.classList.toggle('open', open);
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    }

    toggle.addEventListener('click', function () {
      setOpen(toggle.getAttribute('aria-expanded') !== 'true');
    });

    links.addEventListener('click', function (event) {
      if (event.target.tagName === 'A') setOpen(false);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setOpen(false);
    });

    /* A resize back to the desktop layout must not leave the panel open. */
    window.addEventListener('resize', function () {
      if (window.innerWidth > 900) setOpen(false);
    });
  }

  /* ── reveal on scroll ──────────────────────────────────────────────────── */

  function wireReveals() {
    var items = document.querySelectorAll('.reveal');
    if (!items.length) return;

    var quiet = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (quiet || !('IntersectionObserver' in window)) {
      for (var i = 0; i < items.length; i++) items[i].classList.add('in');
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in');
          observer.unobserve(entry.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });

    for (var j = 0; j < items.length; j++) observer.observe(items[j]);
  }

  /* ── go ────────────────────────────────────────────────────────────────── */

  function start() {
    loadRelease();
    wireMenu();
    wireReveals();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
