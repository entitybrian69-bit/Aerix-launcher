# Aerix Launcher PC

Cross-platform desktop edition of **Aerix Launcher** for **Windows (`x64`)**, **Linux (`x64`)**, and **macOS (`arm64` & `x64`)**, built with Electron + TypeScript.

## Features
- **Accounts**: Microsoft OAuth (Live -> Xbox Live -> XSTS -> Minecraft Services), Aerix Account (Supabase Social + `aerix-auth` via bundled `authlib-injector`), and Offline accounts (Mojang v3 UUID).
- **Loaders & Aerix Client PC**: Vanilla, Fabric, Quilt, Forge, NeoForge, plus 1-click **Aerix Client PC** (Fabric + Sodium/Lithium/QoL stack + `Aerix-Client-Pack.zip` placed strictly below user resource packs).
- **Instance & Content Manager**: Per-instance Mods (enable/disable `.jar` <-> `.jar.disabled`), Resource Packs (auto-unwrapping single-root ZIPs + `options.txt` priority), Shaders, Worlds, Screenshots, RAM/JVM/resolution settings.
- **Discover Browser**: Search and install Mods, Modpacks (`.mrpack` auto-instance), Resource Packs, and Shaders from **Modrinth** and **CurseForge**.
- **Automatic Java Management**: Scans system JDKs and auto-downloads Eclipse Temurin Java 8 / 17 / 21 / 25 per Minecraft version.
- **Live Console & Themes**: Real-time JVM stdout/stderr viewer, 6 accent themes, and 12 bundled Aerix shader wallpapers.

## Development & Packaging
```bash
cd aerix-pc
npm ci
npm run typecheck
npm test
npm run build
npm start
```

### Build Desktop Installers
```bash
npm run dist:win    # NSIS .exe + portable .zip
npm run dist:linux  # AppImage, .deb, .tar.gz
npm run dist:mac    # arm64 & x64 .dmg + .zip
```
