/* ============================================================================
   Aerix Launcher — website script

   Three jobs, all small:
     1. keep the download button pointing at the newest APK, so the page never
        hands out a stale file,
     2. acknowledge the tap, because a 112 MB download takes a moment to start,
     3. reveal sections as they scroll in, and run the mobile menu.

   No dependencies and no build step. The markup already carries GitHub's own
   permalink to the newest release's APK (releases/latest/download/<name>), which
   downloads straight away with no page in between — so the button works even if
   this file never runs, or runs before the network answers.
   ============================================================================ */

(function () {
  'use strict';

  var REPO = 'entitybrian69-bit/Aerix-launcher';
  var RELEASES = 'https://github.com/' + REPO + '/releases';
  var API = 'https://api.github.com/repos/' + REPO + '/releases?per_page=15';
  var CACHE_KEY = 'aerix.latest-release';
  var CACHE_TTL = 10 * 60 * 1000; // the page can be open for a while; ten minutes is plenty

  /* ── small helpers ─────────────────────────────────────────────────────── */

  function $(id) { return document.getElementById(id); }

  /* Sizes are shown to a tenth of a megabyte: rounding 112.5 up to "113 MB" overstates what the
     download costs, and the release page says 112.5 too. */
  function megabytes(bytes) {
    if (!bytes || !isFinite(bytes)) return null;
    var mb = Math.round((bytes / 1048576) * 10) / 10;
    return (mb % 1 === 0 ? mb : mb.toFixed(1)) + ' MB';
  }

  function isApk(name) {
    return /\.apk$/i.test(name || '');
  }

  /* "2026-10-16T02:58:28Z" becomes "16 Oct 2026". */
  function releaseDate(iso) {
    if (!iso) return null;
    var date = new Date(iso);
    if (isNaN(date.getTime())) return null;
    return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  /* GitHub reports an asset's checksum as "sha256:<hex>"; the hex is what people compare. */
  function checksum(asset) {
    var digest = asset && asset.digest ? String(asset.digest) : '';
    var hex = digest.replace(/^sha256:/i, '');
    return /^[0-9a-f]{64}$/i.test(hex) ? hex : null;
  }

  /* The full build carries its own Java runtime; the other one downloads it. */
  function pickAssets(assets) {
    var apks = (assets || []).filter(function (a) { return isApk(a.name); });
    var full = apks.filter(function (a) { return !/noruntime/i.test(a.name); })[0] || apks[0] || null;
    var light = apks.filter(function (a) { return /noruntime/i.test(a.name); })[0] || null;
    return { full: full, light: light };
  }

  /*
   * The newest published build that actually has an APK on it.
   *
   * The list is used rather than the single "latest" endpoint because this project
   * publishes signed release builds under both kinds of tag - v1.0.0 and the
   * aerix-<sha> builds - and whichever was published last is the one to install.
   * Prereleases count: they are the same release-signed builds, just tagged that way.
   */
  function newestWithApk(releases) {
    var usable = (releases || []).filter(function (release) {
      if (!release || release.draft) return false;
      return !!pickAssets(release.assets).full;
    });
    usable.sort(function (a, b) {
      var at = Date.parse(b.published_at || b.created_at || 0) || 0;
      var bt = Date.parse(a.published_at || a.created_at || 0) || 0;
      return at - bt;
    });
    return usable[0] || null;
  }

  /* ── the download button ───────────────────────────────────────────────── */

  function applyRelease(release) {
    if (!release) return;
    var chipText = $('releaseChipText');
    var button = $('downloadBtn');
    var meta = $('downloadMeta');
    var alt = $('downloadAlt');
    var version = $('releaseVersion');
    var facts = $('downloadFacts');
    var notes = $('releaseNotes');
    var sha = $('downloadSha');

    // "v1.0.0" stays "v1.0.0"; a build tag such as "aerix-9b26f17" is shown as it is, so the
    // label never invents a "v" in front of something that is not a version.
    var raw = (release.tag_name || '').trim();
    var tag = /^\d/.test(raw) ? 'v' + raw : raw;
    var assets = pickAssets(release.assets);

    if (chipText && tag) {
      chipText.textContent = tag + ' · latest release';
    }

    // The "current release" figure of the at-a-glance strip follows the same answer as the
    // button, so it cannot go stale between the two.
    if (version && tag) {
      version.textContent = tag;
    }

    if (button && assets.full && assets.full.browser_download_url) {
      // The direct file: tapping this downloads the APK, no GitHub page in between.
      button.href = assets.full.browser_download_url;
      button.setAttribute('download', assets.full.name || '');
      button.setAttribute('data-asset', assets.full.name || '');
      var label = 'Download' + (tag ? ' ' + tag : ' the latest') + ' APK';
      button.innerHTML = '<span class="btn-ico" aria-hidden="true">↓</span>' + label;
    }

    if (meta && assets.full) {
      var size = megabytes(assets.full.size);
      meta.textContent = (size ? size + ' · ' : '') +
        'Android 8.0+ · release build, signed with the project key';
    }

    // What is being handed out, spelled out: which release, how big, from when, and the checksum
    // of the exact bytes, so a download from this page can be checked against the release.
    if (facts && tag) {
      var pieces = [tag];
      var fullSize = megabytes(assets.full && assets.full.size);
      if (fullSize) pieces.push(fullSize);
      var when = releaseDate(release.published_at || release.created_at);
      if (when) pieces.push(when);
      facts.textContent = pieces.join(' · ');
    }

    if (notes && release.html_url) {
      notes.href = release.html_url;
    }

    if (sha) {
      var hex = checksum(assets.full);
      if (hex) sha.textContent = hex;
    }

    if (alt && assets.light && assets.light.browser_download_url) {
      var lightSize = megabytes(assets.light.size);
      alt.hidden = false;
      alt.innerHTML = 'Smaller download: <a href="' + assets.light.browser_download_url +
        '" download>' + assets.light.name + (lightSize ? ' (' + lightSize + ')' : '') +
        '</a> — the same build without the bundled Java runtime.';
    }
  }

  /*
   * A tap on a 112 MB file looks like nothing happening for a second or two, so the
   * button says so. The navigation is left alone: the browser is already fetching the
   * APK from the link itself, and a download also survives this page being closed.
   */
  function wireDownload() {
    var button = $('downloadBtn');
    if (!button) return;

    button.addEventListener('click', function () {
      if (button.classList.contains('is-starting')) return;
      button.classList.add('is-starting');
      button.setAttribute('aria-live', 'polite');

      var note = $('downloadNote');
      if (!note) {
        note = document.createElement('p');
        note.id = 'downloadNote';
        note.className = 'meta small download-note';
        (button.parentNode.parentNode || button.parentNode).appendChild(note);
      }
      note.textContent = 'Starting your download… if nothing happens, tap the button again.';

      window.setTimeout(function () {
        button.classList.remove('is-starting');
      }, 4000);
    });
  }

  function loadRelease() {
    var cached = readCache();
    if (cached) {
      applyRelease(cached);
      return;
    }

    if (!window.fetch) return; // the markup's permalink already points at the newest APK

    fetch(API, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (releases) {
        var newest = newestWithApk(releases);
        if (newest) {
          writeCache(newest);
          applyRelease(newest);
        }
      })
      .catch(function () {
        /* Offline, rate-limited or blocked: the markup already carries GitHub's own
           permalink to the newest release's APK, so nothing needs to change. */
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
    wireDownload();
    wireMenu();
    wireReveals();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
