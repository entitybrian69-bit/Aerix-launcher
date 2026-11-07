# Aerix Lobby

The Lobby is the default home screen. It replaces the glass dashboard with a full-bleed
Minecraft build, one big rotatable player skin, and hard-edged blocky cards with stepped dark
shadows. There is no navigation rail on the Lobby; the grid button in the top-right opens a sheet
with every launcher section.

## Layout

- **Background** — `wp_26_lobby_village.jpg`, a golden-hour voxel village, blurred slightly and
  darkened at the edges. It is also registered as a normal wallpaper preset ("Golden Village
  Lobby").
- **Centre** — `AerixSkinPreview3D` with touch rotation enabled, the account's skin, the equipped
  Aerix cape, and the same 2D pixel-body fallback used elsewhere for devices without WebGL.
- **Left stack** — Shop (with the `!` badge) → Discover, My skin → Accounts, Play with friends →
  Socials. The artwork is generated from the bundled `assets/steve.png` skin texture.
- **Top right** — All sections, Accounts, Daily reward, Settings.
- **Right** — an invite promo card; tapping it copies the `aerix-social://friend/<code>` link.
- **Centre bottom** — Find a party → Socials, Invite a friend → copy the invite link.
- **Bottom dock** — a blue instance card (`START HERE`, with a live instance picker and a
  "Manage instances" entry), a green **Play** tile (`PojavLaunchBridge.play`), a yellow
  **Open chest** tile, and an X that collapses the START HERE hint. While assets install, the
  green tile becomes the "Assets NN%" progress tile and opens Discover.
- **Bottom right** — Help: replay the tour, website, Discord, share logs.

**Open chest / gift** reveals a random bundled wallpaper. "Use it" applies it immediately (and
switches the backdrop to your chosen wallpaper mode); "Keep the village" leaves everything alone.
There is no fake currency, paid pass or loot-box logic — the tile is a real, free cosmetic.

## Turning it off

Two switches live on **Settings → Launcher**:

| Switch | Default | Effect |
| --- | --- | --- |
| Lobby home screen | on | off restores the glass dashboard and the navigation rail |
| Lobby village backdrop | on | off uses your chosen wallpaper/animation behind the Lobby |

The same "switch back" action is available from the Lobby's All sections sheet. During the
first-run tour the classic dashboard is shown temporarily, because the tour highlights the rail
and the docked skin card.

## Portraits and narrow screens

`AerixLobbyScreen` uses `BoxWithConstraints`. Landscape / wide windows get the absolute-positioned
lobby matching the reference; portrait and narrow windows get a scrollable column with the same
tiles in a sensible order. Both layouts share one `LobbyState`, so every tile is wired to the
same live actions.

## Assets and preview

- `tools/generate_lobby_art.py` regenerates the sunburst cards and pixel icons from
  `assets/steve.png` (requires Pillow). Output: `res/drawable-nodpi/aerix_lobby_*.png`.
- `tools/serve_lobby_preview.py` serves `preview/index.html` on port 8000. It is a static mock of
  the Compose layout using the exact same assets, so the design can be reviewed in a browser
  without building an APK. The real app skin is the 3D WebView; the preview uses a 2D stand-in.
- `tests/lobby_ui_smoke.py` guards the wiring, assets, strings and opt-out switches.
