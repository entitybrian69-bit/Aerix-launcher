# Aerix Blocky Shell — design contract

> This file is the **"Remember this"** record for the launcher's new identity. It is not a
> proposal or a roadmap: it is the set of rules the shell is built to, and the checklist to
> re-apply whenever the UI changes again.
>
> **Update (2026-11-08):** the user then asked to "Make the ui like the millida launcher one".
> The Millida-style shell in `docs/millida-shell.md` is now the default HOME look. This Blocky
> Shell remains intact as the second appearance option (turn off `millidaUi` in Settings); its
> living 3D character, emote engine, square-wave sound, stepped motion and real-action wiring
> are shared with the Millida shell, so nothing in this contract was deleted.

## 1. The rules, as given

The old Lobby redesign was scrapped and must not be restored. This is a **different, whole new
shell** — a new identity, navigation and visual language — drawn from the hand-drawn sketch in
`.design_ref/`.

From the request, kept in the user's own terms:

1. "I think / We should actually overhaul the ui for this big project" → chosen scope:
   **a new shell entirely**, not a HOME-only widget and not just extra destinations.
2. "All the options will have a black outline" → **every** option tile has a black outline.
3. "a different color" / "All options will have different different colors and themes" →
   every option has its **own** colour *and* its own pattern theme.
4. "and a white text saying what it is" → every option is labelled in **white** pixel text
   naming the action.
5. "In the middle will be a smooth 3d character with the skin applied" → a real, smooth,
   rotatable WebGL character wearing the applied wardrobe skin and client cosmetics — not a
   flat sprite.
6. "Which can do emotes on its own Dance Jump Wave Performe action" → autonomous
   **Dance / Jump / Wave / Action** emotes (plus Cheer), cycling when idle.
7. "React to what user is doing and their actions" → the character emotes in response to taps,
   launcher navigation, downloads, wardrobe changes and account switches.
8. "The launcher is to be in a blocky theme / Blocky feel / Blocky look / Blocky sound /
   Blocky animations / Blocky transition / Blocky motions / All of it" → **all of it**:
   - **Look**: crisp NEAREST-scaled pixel art, square corners, hard stepped shadows, pixel font.
   - **Feel**: press travels into the tile's own shadow; no ripples, no rounded glass.
   - **Sound**: square-wave, 4-bit, stepped-envelope chiptune SFX on every tap, transition,
     emote and reward.
   - **Animations**: a six-step quantised easing; nothing glides smoothly.
   - **Transitions**: hard cuts between launcher sections.
   - **Motions**: emote joints are quantised into discrete poses even though the 3D model
     itself is smooth and antialiased.
9. "Remember this." → this document, plus `tests/blocky_shell_smoke.py`, which fails the build
   if any rule above is dropped.
10. "This should be finished by tomorrow." → the shell, preview, verification and a fresh build
    were delivered in one pass.

## 2. Sketch → layout

The sketch has a stepped sunburst hanging from the top, a tall left panel split by a hard
horizontal divider, a stack of panels on the right, and grass spikes along the bottom edge.

| Sketch element | Implementation |
| --- | --- |
| Top hanging rays | `BlockySunburst` (`aerix_blocky_sun.png`) across the top bar |
| Tall left panel + divider | `BlockyStage`: 3D character above, 5 dp black divider, emote deck below |
| Right stacked panels | `BlockyOptionGrid`: twelve coloured, black-outlined option cards in two columns |
| Bottom spikes | `BlockySpikes`, drawn as stepped square grass teeth |
| Center character | `AerixSkinPreview3D` with `animated = true`, draggable rotation, applied skin + client cape |

Wide layout puts the stage left and the option grid right. Compact layout stacks the stage above a
scrollable grid. Both share the same top chrome and bottom dock.

## 3. Where everything lives

| File | Job |
| --- | --- |
| `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/ui/screens/BlockyShellScreen.kt` | The whole shell, layouts, sheets, dock, option list and reaction engine |
| `.../ui/blocky/BlockyTheme.kt` | Palette (14 distinct themes), black outline, pixel font, stepped easing |
| `.../ui/blocky/BlockyComponents.kt` | `BlockyBox`, `BlockyButton`, `BlockyCard`, patterns, sunburst, spikes, stepped progress |
| `.../ui/blocky/BlockySound.kt` | Runtime-synthesised 8-bit WAVs + `SoundPool`; no binary audio assets |
| `.../ui/blocky/BlockyEmote.kt` | Emote vocabulary, labels, colours and the user-action → emote mapping |
| `.../components/AerixPlayerSkin.kt` | `playEmote`, `startIdleMotion`, `onEmote` JS bridge, new preview params |
| `app_pojavlauncher/src/main/assets/skinview/skinview.js` | Five real `PlayerAnimation` subclasses: dance, jump, wave, action, cheer; idle motion |
| `.../assets/wallpapers/wp_27_blocky_plaza.jpg` | Crisp voxel plaza backdrop |
| `.../res/drawable-nodpi/aerix_blocky_ic_*.png` | 29 white pixel icons + sunburst |
| `tools/generate_blocky_art.py` | Regenerates all of the above |
| `preview/index.html` | Live browser preview: real WebGL character, working emotes, chiptune buttons |
| `tools/render_blocky_preview.py`, `preview/blocky-preview.png` | Static PNG preview |
| `tests/blocky_shell_smoke.py` | Rule checklist, run in CI |

## 4. Behaviour contract

- **No mock destinations.** Every tile calls a real bridge: `PojavLaunchBridge.play`,
  `PojavInstanceBridge.select`, `PojavAccountBridge.openDetail`, `AerixNav` / `navigate`,
  `SocialBackend.inviteCode`, `PojavNavigator.shareLogs` / `openWebsite` / `openDiscord`,
  `AerixGuideState.replay`, `AerixThemeManager.wallpapers`.
- **Emotes are real joint animation**, driven by `skinview3d.PlayerAnimation`; the WebGL loop
  stays alive (`renderPaused = false`) so the character is never a still frame.
- **Reactions**: taps map through `BlockyReactions.of(action)`; the auto-emote loop picks a
  random emote every 8 s; download progress, wardrobe revision and account key changes each
  trigger their own reaction.
- **Opt-outs** in Settings → Launcher: Blocky Shell, Blocky sound, Auto emotes, Pixel plaza
  backdrop. Turning off `Blocky Shell` restores the classic glass dashboard immediately.
- **First-run guide** still works: it temporarily falls back to the classic dashboard so its
  anchors stay where they were authored. The Play tile registers `AerixGuideState.PLAY`.
- **Blocky transitions**: while the shell is active, section changes are hard cuts.

## 5. Feature concept kept in view (not implemented here)

The underlying project remains **the companion mod and the wardrobe**: a mod downloaded
automatically into any instance, a wardrobe with Hats / Suit / Jacket / "much much more", and
cosmetics visible to other players who have the mod. That is a backend/rendering effort and is
deliberately **not** part of this UI overhaul. The shell's *Wardrobe* and *Companion mod* blocks
are the real, existing destinations for that work — they are not placeholders.

## 6. Verification

```bash
PYTHONPATH=/tmp/pillowlib python3 tools/generate_blocky_art.py
python3 tools/render_blocky_preview.py
python3 tests/blocky_shell_smoke.py
python3 tools/serve_blocky_preview.py   # http://0.0.0.0:8000/
```
