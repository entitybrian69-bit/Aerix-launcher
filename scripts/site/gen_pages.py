#!/usr/bin/env python3
"""Generates the Aerix Launcher website as a set of standalone pages.

Every page is a complete HTML file that shares docs/styles.css, docs/app.js and docs/assets/, so
GitHub Pages serves them as-is with no build step. Keeping the shell in one place is what stops the
pages from drifting apart: change the nav here and every page gets it.
"""
import pathlib

DOCS = pathlib.Path(__file__).resolve().parents[2] / "docs"
SITE = "https://entitybrian69-bit.github.io/Aerix-launcher/"
REPO = "https://github.com/entitybrian69-bit/Aerix-launcher"
# GitHub's own permalink to the newest release's asset of this name: it redirects straight to the
# file, so the button downloads the APK without a visit to any GitHub page. The publish workflow
# uploads a copy under exactly these names next to the versioned ones.
LATEST_APK = f"{REPO}/releases/latest/download/Aerix-Launcher.apk"
LATEST_APK_LIGHT = f"{REPO}/releases/latest/download/Aerix-Launcher-noruntime.apk"
DISCORD = "https://discord.gg/RS7q9KaCm6"

NAV = [
    ("index.html", "Home"),
    ("screenshots.html", "Screenshots"),
    ("features.html", "Features"),
    ("renderers.html", "Renderers"),
    ("install.html", "Install"),
    ("faq.html", "FAQ"),
]

GITHUB_ICON = ('<svg viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M8 0C3.58 0 0 3.58 0 '
               '8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94'
               '-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66'
               '.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 '
               '.67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12'
               '.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21'
               '.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z"/></svg>')

AURORA = '''<!-- animated aurora: five soft bands drifting behind everything, purely decorative -->
<div class="aurora" aria-hidden="true">
  <span class="band band-1"></span>
  <span class="band band-2"></span>
  <span class="band band-3"></span>
  <span class="curtain curtain-1"></span>
  <span class="curtain curtain-2"></span>
</div>'''

SHOTS = [
    ("home", "Home",
     "No top bar: the page starts at the top of the screen, with Create a World, Open Library and PLAY one touch away."),
    ("instances", "Create New Instance",
     "The Minecraft version list owns the left column, the selected version is outlined with a check mark, and the modloader grid sits beside it."),
    ("discover", "Discover",
     "Browse and install mods, modpacks, shaders and resource packs, with filters for platform, category, game version and loader."),
    ("wallpapers", "Wallpapers",
     "25 built-in HD Minecraft wallpapers, each with its own accent theme, plus dim control and custom imports."),
    ("renderer", "Renderers",
     "Renderer, Vulkan driver and graphics API, chosen per version with a recommended default."),
    ("accounts", "Accounts &amp; skins",
     "Add Microsoft, offline or auth-server accounts, then browse a searchable three-column skin library and install a skin in one tap."),
]

FEATURES = [
    ("⚡", "Quick access",
     "Recent worlds and instances stay one tap away. Launch, edit, duplicate and delete from the same hub."),
    ("🧩", "Serious render stack",
     "LTW and LTW Legacy are built from source here and cover Minecraft 1.17+ and 1.8–1.16.5, alongside GL4ES, VirGL, Zink-class and Mesa options."),
    ("🧮", "RAM that you control",
     "A global memory slider, plus per-instance overrides when a pack needs more."),
    ("👤", "Accounts, skins, capes",
     "Microsoft login, offline accounts and auth-server login, with a 3D preview so you see the skin and cape before you launch."),
    ("🔍", "Content browser",
     "Modpacks, mods, resource packs, shaders and saves from Modrinth and CurseForge, with the real version and category lists of each source."),
    ("🎮", "Controls that fit",
     "A touch control editor with custom layouts, a gamepad with remapping, a file manager, per-instance server lists and a log viewer."),
    ("🛡️", "Private on purpose",
     "No ads, no telemetry, no account resale. Accounts, skins and capes stay on the device unless a login needs the network."),
    ("🔄", "Updated often",
     "Fixes and polish land on a regular cadence, with a built-in update check against GitHub Releases."),
]

RENDERERS = [
    ("Auto (Smart Pick)", "default", "Reads the Minecraft version you are about to launch and picks the renderer that fits it."),
    ("LTW", "1.17+", "Built from source in this repository. The modern core renderer for current versions."),
    ("LTW Legacy", "1.8 – 1.16.5", "Also built from source here, for the older versions that need the legacy path."),
    ("MobileGlues", "1.17+", "Bundled for the versions where its GL translation fits best."),
    ("vGPU", "up to 1.16.5 · fast", "The quick one for older versions, when raw speed matters more than effects."),
    ("VGPU 1.3.6β", "up to 1.16.5", "The shaderconv build, for older versions when a pack needs shader conversion."),
    ("Kopper Zink", "Vulkan", "A Vulkan path, chosen per instance alongside its Vulkan driver."),
    ("GL4ES, VirGL, Mesa", "available", "Further backends that ship with the launcher and can be selected per instance."),
]

FAQ = [
    ("Is it free?",
     "Yes. Aerix is open source under GPL-3.0, with no ads, no telemetry and no paid tier. Downloads come from GitHub Releases."),
    ("Do I need a CurseForge API key?",
     "No. The content browser works out of the box: it falls back to a public mirror when no key is configured, and an own key can be set in Settings if you have one."),
    ("Which Minecraft versions work?",
     "The version list inside the launcher is the complete Mojang manifest, and the downloader lists every version the source (Modrinth or CurseForge) knows. Renderers are chosen to match the version you launch."),
    ("Will it run on my phone?",
     "Android 8.0 or newer, and 4 GB of RAM or more is the comfortable spot. Layouts, accounts, skins and the launch flow are tested on more than one device."),
    ("Can I use a controller, or my own touch layout?",
     "Yes. There is a gamepad with remapping and a touch control editor, including imported custom layouts and layouts from other launchers."),
    ("How do updates work?",
     "Settings → About → Check for update asks the GitHub Releases API, compares your build and points you at the newest APK. No mystery JSON, no dead link."),
    ("Is this an official Minecraft app?",
     "No. Minecraft is a trademark of Mojang Synergies AB. Aerix Launcher is an independent project and is not affiliated with, endorsed by or associated with Mojang or Microsoft."),
    ("Something broke. Where do I say so?",
     "The Discord channel is the fastest place — the same link is in the footer of every page."),
]


def head(title, description, page, extra_meta=""):
    return f'''<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{title}</title>
<meta name="description" content="{description}">
<meta name="theme-color" content="#07131d">
<meta name="color-scheme" content="dark">
<link rel="icon" href="assets/aerix-logo.png">
<link rel="apple-touch-icon" href="assets/aerix-logo.png">
<link rel="canonical" href="{SITE}{'' if page == 'index.html' else page}">
{extra_meta}<link rel="stylesheet" href="styles.css">
</head>
<body>
{AURORA}

<a class="skip" href="#main">Skip to content</a>
'''


def nav(page):
    links = []
    for href, label in NAV:
        active = ' class="is-active" aria-current="page"' if href == page else ""
        links.append(f'      <a href="{href}"{active}>{label}</a>')
    return f'''
<header class="nav">
  <a class="brand" href="index.html">
    <img class="brand-logo" src="assets/aerix-logo.png" alt="Aerix Launcher logo" width="40" height="40">
    <span class="brand-text">AERIX<span class="brand-sub">LAUNCHER</span></span>
  </a>

  <nav class="nav-links" id="navLinks" aria-label="Pages">
{chr(10).join(links)}
  </nav>

  <div class="nav-actions">
    <a class="icon-btn" href="{REPO}" target="_blank" rel="noopener" aria-label="GitHub repository">{GITHUB_ICON}</a>
    <a class="btn btn-discord btn-sm" href="{DISCORD}" target="_blank" rel="noopener">
      <span class="dot" aria-hidden="true"></span>Discord
    </a>
    <button class="nav-toggle" id="navToggle" type="button" aria-expanded="false" aria-controls="navLinks" aria-label="Open menu">
      <span></span><span></span><span></span>
    </button>
  </div>
</header>
'''


FOOTER = f'''
<footer class="footer">
  <div class="footer-left">
    <img class="footer-avatar" src="assets/creator-entitybrian.jpg" alt="" width="44" height="44" loading="lazy" decoding="async">
    <div>
      <p class="footer-name">Made by entitybrian</p>
      <p class="footer-sub">designs it, writes it, tests it on real phones</p>
    </div>
  </div>
  <div class="footer-right">
    <a href="{DISCORD}" target="_blank" rel="noopener">discord.gg/RS7q9KaCm6</a>
    <p class="footer-sub">GPL-3.0 · <a href="{REPO}" target="_blank" rel="noopener">github.com/entitybrian69-bit/Aerix-launcher</a></p>
  </div>
</footer>

<script src="app.js" defer></script>
</body>
</html>
'''


def page_hero(eyebrow, h1, lede):
    return f'''  <section class="section page-hero">
    <p class="eyebrow">{eyebrow}</p>
    <h1>{h1}</h1>
    <p class="lede">{lede}</p>
  </section>
'''


def cta_band(title, text, buttons):
    return f'''  <section class="section">
    <div class="cta-band reveal">
      <div>
        <h2>{title}</h2>
        <p>{text}</p>
      </div>
      <div class="cta-actions">{buttons}</div>
    </div>
  </section>
'''


def shot_tile(name, title, caption, eager=False):
    loading = 'eager' if eager else 'lazy'
    return f'''      <article class="tile reveal">
        <figure class="shot"><img src="assets/screenshots/shot-{name}.jpg" alt="{title}" width="1200" height="537" loading="{loading}" decoding="async"></figure>
        <h3>{title}</h3>
        <p>{caption}</p>
      </article>
'''


# ─────────────────────────────── home ───────────────────────────────

def build_index():
    head_html = head(
        "Aerix Launcher — Minecraft: Java Edition on Android",
        "Aerix Launcher brings Minecraft: Java Edition to Android: instances, renderers, accounts, "
        "skins and a content browser in one launcher. Private by design, updated often, tested on real phones.",
        "index.html",
        extra_meta=f'''<meta property="og:type" content="website">
<meta property="og:title" content="Aerix Launcher — Minecraft: Java Edition on Android">
<meta property="og:description" content="Instances, renderers, accounts, skins and a content browser in one launcher.">
<meta property="og:url" content="{SITE}">
<meta property="og:image" content="{SITE}assets/screenshots/shot-home.jpg">
<meta property="og:image:alt" content="The Aerix Launcher home screen">
<meta name="twitter:card" content="summary_large_image">
''')

    body = f'''<main id="main">

  <section class="hero">
    <div class="hero-glow" aria-hidden="true"></div>
    <div class="hero-copy">
      <p class="chip"><span class="pulse" aria-hidden="true"></span><span id="releaseChipText">latest release</span></p>
      <h1>Minecraft: Java Edition <span class="accent">on Android</span></h1>
      <p class="lede">
        Instances, renderers, accounts, skins, capes and a content browser in one launcher.
        Private by design, updated often, tested on real phones.
      </p>
      <div class="cta-row">
        <a class="btn btn-primary btn-lg" id="downloadBtn" href="{LATEST_APK}" download>
          <span class="btn-ico" aria-hidden="true">↓</span>Download the latest APK
        </a>
        <a class="btn btn-ghost btn-lg" href="install.html">How to install</a>
      </div>
      <p class="meta" id="downloadMeta">Android 8.0+ · release build, signed with the project key</p>
      <p class="meta small" id="downloadAlt" hidden></p>
    </div>

    <div class="hero-art">
      <figure class="shot shot-lg float-a">
        <img src="assets/screenshots/shot-home.jpg" alt="Aerix Launcher home screen with a recent world and the Play button" width="1200" height="537" loading="eager" decoding="async">
      </figure>
      <div class="shot-pair">
        <figure class="shot float-b">
          <img src="assets/screenshots/shot-discover.jpg" alt="Discover screen browsing mods with filters" width="1200" height="537" loading="lazy" decoding="async">
        </figure>
        <figure class="shot float-c">
          <img src="assets/screenshots/shot-instances.jpg" alt="Create New Instance screen with the version list and loader grid" width="1200" height="537" loading="lazy" decoding="async">
        </figure>
      </div>
    </div>
  </section>

  <section class="strip reveal" aria-label="At a glance">
    <div class="strip-item"><strong id="releaseVersion">1.1.0</strong><span>current release</span></div>
    <div class="strip-item"><strong>Android 8.0+</strong><span>minimum version</span></div>
    <div class="strip-item"><strong>GPL-3.0</strong><span>open source</span></div>
    <div class="strip-item"><strong>No trackers</strong><span>no ads, no telemetry</span></div>
  </section>

  <section class="section">
    <header class="section-head reveal">
      <p class="eyebrow">Start here</p>
      <h2>Three pages tell you everything</h2>
      <p class="section-lede">What the launcher looks like, what it does, and how to get it running.</p>
    </header>
    <div class="cards start-cards">
      <article class="card reveal">
        <span class="card-ico" aria-hidden="true">📸</span>
        <h3>Screenshots</h3>
        <p>All six screens of the current build — home, instances, discover, wallpapers, renderers and accounts.</p>
        <a class="card-link" href="screenshots.html">See the screens</a>
      </article>
      <article class="card reveal">
        <span class="card-ico" aria-hidden="true">🧩</span>
        <h3>Features</h3>
        <p>The render stack, RAM control, accounts, skins and capes, the content browser and the control editor.</p>
        <a class="card-link" href="features.html">Read the features</a>
      </article>
      <article class="card reveal">
        <span class="card-ico" aria-hidden="true">📥</span>
        <h3>Install</h3>
        <p>Three steps, which file to pick, and the one note about package names worth knowing first.</p>
        <a class="card-link" href="install.html">How to install</a>
      </article>
    </div>
  </section>

  <section class="section">
    <header class="section-head reveal">
      <p class="eyebrow">Screenshots</p>
      <h2>A look inside</h2>
      <p class="section-lede">Every image is from the current build — no concepts, no mock-ups.</p>
    </header>
    <div class="rail">
{shot_tile("home", "Home", "Create a World, Open Library and PLAY are one touch away.", eager=True)}{shot_tile("discover", "Discover", "Mods, modpacks, shaders and resource packs with real filters.")}{shot_tile("accounts", "Accounts &amp; skins", "Microsoft, offline or auth-server accounts, with a 3D preview.")}
    </div>
    <div class="cta-row">
      <a class="btn btn-ghost" href="screenshots.html">See all six screenshots</a>
    </div>
  </section>

  <section class="section">
    <div class="split">
      <figure class="shot shot-wide reveal">
        <img src="assets/screenshots/shot-renderer.jpg" alt="Renderer list with Auto Smart Pick, vgpu, VGPU, LTW, LTW Legacy and Kopper Zink" width="1200" height="537" loading="lazy" decoding="async">
      </figure>
      <div class="split-copy reveal">
        <p class="eyebrow">Renderers</p>
        <h2>Picked per version, not per launcher</h2>
        <p class="section-lede">Aerix bundles MobileGlues for Minecraft 1.17+, two VGPU options for 1.16.5 and older, and chooses the right renderer for each version by default — with every option still one tap away.</p>
        <a class="card-link" href="renderers.html">See the whole lineup</a>
      </div>
    </div>
  </section>

  <section class="section creator">
    <div class="creator-card reveal">
      <img class="creator-avatar" src="assets/creator-entitybrian.jpg" alt="entitybrian" width="132" height="132" loading="lazy" decoding="async">
      <div class="creator-text">
        <p class="eyebrow">Who made it</p>
        <h2>entitybrian</h2>
        <p class="creator-role">Owner, developer, tester</p>
        <p>entitybrian designs, writes, tests and ships Aerix Launcher. No studio, no outsourced UI, no AI pass that “fills in the screens” — the person who adds offline capes is the person who checks them on another phone, and who cuts a release when it is actually ready.</p>
        <div class="cta-row">
          <a class="btn btn-ghost" href="https://github.com/entitybrian69-bit" target="_blank" rel="noopener">GitHub profile</a>
          <a class="btn btn-discord" href="{DISCORD}" target="_blank" rel="noopener"><span class="dot" aria-hidden="true"></span>Join the Discord</a>
        </div>
      </div>
    </div>

    <aside class="discord-card reveal">
      <p class="eyebrow">Community</p>
      <h2>Discord</h2>
      <p>Join other Aerix users, report a problem, and suggest what should land next.</p>
      <p class="discord-link">discord.gg/RS7q9KaCm6</p>
      <a class="btn btn-discord btn-lg" href="{DISCORD}" target="_blank" rel="noopener"><span class="dot" aria-hidden="true"></span>Open the invite</a>
    </aside>
  </section>

</main>
'''
    return head_html + nav("index.html") + body + FOOTER


# ─────────────────────────────── screenshots ───────────────────────────────

def build_screenshots():
    tiles = "".join(shot_tile(n, t, c, eager=(i == 0)) for i, (n, t, c) in enumerate(SHOTS))
    body = f'''<main id="main">

{page_hero("Screenshots", "Every screen, from the current build", "Home, Create New Instance, Discover, Wallpapers, Renderers and Accounts — captured from the release that is on the download page.")}

  <section class="section">
    <div class="rail">
{tiles}    </div>
  </section>

{cta_band("Want to see it on your own phone?", "The download button on the install page always points at the newest release.",
          f'<a class="btn btn-primary" href="install.html">Install guide</a><a class="btn btn-discord" href="{DISCORD}" target="_blank" rel="noopener"><span class="dot" aria-hidden="true"></span>Discord</a>')}
</main>
'''
    return head("Screenshots — Aerix Launcher",
                "All six screens of the current Aerix Launcher build: home, instances, discover, wallpapers, renderers and accounts.",
                "screenshots.html") + nav("screenshots.html") + body + FOOTER


# ─────────────────────────────── features ───────────────────────────────

def build_features():
    cards = "".join(f'''      <article class="card reveal">
        <span class="card-ico" aria-hidden="true">{icon}</span>
        <h3>{title}</h3>
        <p>{text}</p>
      </article>
''' for icon, title, text in FEATURES)

    body = f'''<main id="main">

{page_hero("Features", "Tools for your setup", "Aerix keeps launcher controls, renderer options and instance management in one place.")}

  <section class="section">
    <div class="cards">
{cards}    </div>
  </section>

  <section class="section">
    <div class="split">
      <div class="split-copy reveal">
        <p class="eyebrow">Quality bar</p>
        <h2>Made by hand, kept by its owner</h2>
        <ul class="ticks">
          <li><strong>100% human-made, non-AI.</strong> Every screen is developed, not generated.</li>
          <li><strong>Owner maintenance.</strong> The people who own the bugs are the people who fix them.</li>
          <li><strong>Real-phone testing</strong> on more than one device before a build is treated as good.</li>
          <li><strong>Frequent updates,</strong> with CI on push so broken builds do not silently ship.</li>
        </ul>
      </div>
      <div class="split-copy reveal">
        <p class="eyebrow">Private on purpose</p>
        <h2>Nothing to sign away</h2>
        <ul class="ticks">
          <li><strong>No ads</strong> and <strong>no telemetry.</strong></li>
          <li><strong>No account resale, no profile harvesting.</strong></li>
          <li><strong>Accounts, skins and capes stay on the device</strong> unless you choose a login that needs the network.</li>
          <li><strong>Open source under GPL-3.0,</strong> so the behaviour is inspectable.</li>
        </ul>
      </div>
    </div>
  </section>

{cta_band("Renderers get their own page", "LTW, LTW Legacy, MobileGlues, vGPU, Kopper Zink and the rest, with the Minecraft versions each one covers.",
          '<a class="btn btn-ghost" href="renderers.html">Renderer lineup</a>')}
</main>
'''
    return head("Features — Aerix Launcher",
                "Renderers, RAM control, accounts, skins and capes, the content browser, the control editor and the privacy bar of Aerix Launcher.",
                "features.html") + nav("features.html") + body + FOOTER


# ─────────────────────────────── renderers ───────────────────────────────

def build_renderers():
    specs = "".join(f'''      <article class="spec reveal{'' if rng not in ('available',) else ' is-extra'}">
        <span class="range">{rng}</span>
        <h3>{name}</h3>
        <p>{text}</p>
      </article>
''' for name, rng, text in RENDERERS)

    body = f'''<main id="main">

{page_hero("Renderers", "Picked per version, not per launcher", "Minecraft on Android lives or dies by its renderer. Aerix chooses one for the version you are launching, and leaves every option one tap away.")}

  <section class="section">
    <div class="split">
      <figure class="shot shot-wide reveal">
        <img src="assets/screenshots/shot-renderer.jpg" alt="Renderer list in the launcher showing Auto Smart Pick and the available backends" width="1200" height="537" loading="lazy" decoding="async">
      </figure>
      <div class="split-copy reveal">
        <h2>What the screen gives you</h2>
        <ul class="ticks">
          <li><strong>Renderer</strong> per instance, with a recommended default.</li>
          <li><strong>Vulkan driver</strong> and <strong>graphics API</strong> chosen alongside it.</li>
          <li><strong>LTW</strong> for 1.17+ and <strong>LTW Legacy</strong> for 1.8–1.16.5, both built from source in this repository.</li>
          <li><strong>MobileGlues</strong> for 1.17+ and <strong>two VGPU options</strong> for 1.16.5 and older.</li>
        </ul>
      </div>
    </div>
  </section>

  <section class="section">
    <header class="section-head reveal">
      <p class="eyebrow">The lineup</p>
      <h2>Every backend, and where it fits</h2>
      <p class="section-lede">Ranges are the Minecraft versions each renderer is meant for. MobileGlues and the VGPU pair ship with the launcher; LTW and LTW Legacy are built from its source.</p>
    </header>
    <div class="spec-grid">
{specs}    </div>
  </section>

{cta_band("Not sure which one?", "Leave it on Auto (Smart Pick) — it reads the version you are about to launch and chooses for you.",
          f'<a class="btn btn-primary" href="install.html">Install Aerix</a><a class="btn btn-discord" href="{DISCORD}" target="_blank" rel="noopener"><span class="dot" aria-hidden="true"></span>Ask on Discord</a>')}
</main>
'''
    return head("Renderers — Aerix Launcher",
                "The renderer lineup in Aerix Launcher: Auto Smart Pick, LTW, LTW Legacy, MobileGlues, vGPU, Kopper Zink, GL4ES, VirGL and Mesa.",
                "renderers.html") + nav("renderers.html") + body + FOOTER


# ─────────────────────────────── install ───────────────────────────────

def build_install():
    body = f'''<main id="main">

{page_hero("Install", "Three steps to play", "Android 8.0 or newer, best on 4 GB of RAM or more. The button below always points at the newest release.")}

  <section class="section" id="download">
    <div class="cta-band reveal download-band">
      <div>
        <p class="chip"><span class="pulse" aria-hidden="true"></span><span id="releaseChipText">latest release</span></p>
        <h2>Get the launcher</h2>
        <p id="downloadMeta">Android 8.0+ · release build, signed with the project key</p>
        <p class="meta small" id="downloadAlt" hidden></p>
        <p class="meta small">Smaller download: <a href="{LATEST_APK_LIGHT}">the no-runtime build</a> — same launcher, Java runtime fetched on demand.</p>
        <p class="meta small">Prefer to pick a file yourself? <a href="{REPO}/releases" target="_blank" rel="noopener">All releases on GitHub</a>.</p>
      </div>
      <div class="cta-actions">
        <a class="btn btn-primary btn-lg" id="downloadBtn" href="{LATEST_APK}" download>
          <span class="btn-ico" aria-hidden="true">↓</span>Download the latest APK
        </a>
      </div>
    </div>
  </section>

  <section class="section">
    <ol class="steps">
      <li class="card reveal">
        <span class="step-n">1</span>
        <h3>Download the APK</h3>
        <p>Take the full build unless you are short on space: it carries its own Java runtime. The no-runtime build is smaller and fetches the runtime on demand.</p>
      </li>
      <li class="card reveal">
        <span class="step-n">2</span>
        <h3>Allow the install</h3>
        <p>Android will ask to allow installing apps from unknown sources the first time. Aerix is signed with the project's own release key.</p>
      </li>
      <li class="card reveal">
        <span class="step-n">3</span>
        <h3>Pick a version and play</h3>
        <p>Create an instance, add an offline account or sign in with Microsoft, equip a skin and cape, and launch.</p>
      </li>
    </ol>

    <div class="note reveal">
      <h3>A note on package names</h3>
      <p>The release build installs as <code>net.kdt.pojavlaunch</code>. The earlier test builds used the <code>.debug</code> package, so the two can sit side by side — but their instances and game data do not carry over, and an installed original PojavLauncher has to be removed first because the signature differs.</p>
    </div>

    <div class="note reveal">
      <h3>Keeping it current</h3>
      <p>Aerix checks GitHub Releases for you: Settings → About → Check for update compares your build and points you at the newest APK. This page's download button asks the same API, so it never hands out an old file.</p>
    </div>
  </section>

{cta_band("Stuck on a step?", "The Discord is the fastest place to get an answer — the same invite is in the footer of every page.",
          f'<a class="btn btn-discord" href="{DISCORD}" target="_blank" rel="noopener"><span class="dot" aria-hidden="true"></span>Join the Discord</a><a class="btn btn-ghost" href="faq.html">Read the FAQ</a>')}
</main>
'''
    return head("Install — Aerix Launcher",
                "How to install Aerix Launcher on Android: download the newest APK, allow the install, and create your first instance.",
                "install.html") + nav("install.html") + body + FOOTER


# ─────────────────────────────── faq ───────────────────────────────

def build_faq():
    items = "".join(f'''      <details class="reveal">
        <summary>{q}</summary>
        <p>{a}</p>
      </details>
''' for q, a in FAQ)

    body = f'''<main id="main">

{page_hero("FAQ", "Questions people actually ask", "If something here does not answer it, the Discord will.")}

  <section class="section">
    <div class="faq-list">
{items}    </div>
  </section>

{cta_band("Still curious?", "Screenshots, the renderer lineup and the install steps are each one tap away.",
          f'<a class="btn btn-ghost" href="screenshots.html">Screenshots</a><a class="btn btn-ghost" href="renderers.html">Renderers</a><a class="btn btn-ghost" href="install.html">Install</a>')}
</main>
'''
    return head("FAQ — Aerix Launcher",
                "Aerix Launcher questions: is it free, do I need an API key, which Minecraft versions work, updates, controllers and licences.",
                "faq.html") + nav("faq.html") + body + FOOTER


PAGES = {
    "index.html": build_index,
    "screenshots.html": build_screenshots,
    "features.html": build_features,
    "renderers.html": build_renderers,
    "install.html": build_install,
    "faq.html": build_faq,
}

if __name__ == "__main__":
    for name, builder in PAGES.items():
        path = DOCS / name
        path.write_text(builder())
        print(f"wrote {path}  ({len(path.read_text().splitlines())} lines)")
