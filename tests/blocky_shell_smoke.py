#!/usr/bin/env python3
"""Blocky Shell smoke test - the machine-checkable half of docs/blocky-shell.md.

It verifies the design contract (black outline, per-option colours/themes, white labels, a real
3D skin with autonomous + reactive emotes, blocky sound and stepped motion), that every tile is
wired to a real launcher action, that the old Lobby redesign was not resurrected, and that the
preview and its assets resolve.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app_pojavlauncher/src/main"
JAVA = MAIN / "java/net/kdt/pojavlaunch/aerix"
BLOCKY = JAVA / "ui/blocky"
SCREENS = JAVA / "ui/screens"
ASSETS = MAIN / "assets"
RES = MAIN / "res"
PREVIEW = ROOT / "preview"


def read(path: Path) -> str:
    assert path.is_file(), f"missing file: {path}"
    return path.read_text(encoding="utf-8")


# --------------------------------------------------------------------------------------
# 1. Files exist.
# --------------------------------------------------------------------------------------
shell = SCREENS / "BlockyShellScreen.kt"
theme = BLOCKY / "BlockyTheme.kt"
components = BLOCKY / "BlockyComponents.kt"
sound = BLOCKY / "BlockySound.kt"
emotes = BLOCKY / "BlockyEmote.kt"
skin_kt = JAVA / "components/AerixPlayerSkin.kt"
skin_js = ASSETS / "skinview/skinview.js"
prefs = JAVA / "bridge/AerixPreferences.kt"
background = JAVA / "components/AerixBackground.kt"
launcher = JAVA / "ui/AerixLauncherScreen.kt"
settings = SCREENS / "SettingsScreen.kt"
strings = RES / "values/aerix_strings.xml"
theme_manager = JAVA / "theme/AerixThemeManager.kt"
doc = ROOT / "docs/blocky-shell.md"
generator = ROOT / "tools/generate_blocky_art.py"
preview_html = PREVIEW / "index.html"
preview_png = PREVIEW / "blocky-preview.png"
for p in (shell, theme, components, sound, emotes, skin_kt, skin_js, prefs, background,
          launcher, settings, strings, theme_manager, doc, generator, preview_html,
          preview_png, ASSETS / "wallpapers/wp_27_blocky_plaza.jpg"):
    assert p.is_file(), f"missing: {p}"

shell_src, theme_src, comp_src, sound_src, emote_src = (
    read(shell), read(theme), read(components), read(sound), read(emotes)
)
skin_src, js_src = read(skin_kt), read(skin_js)
prefs_src, bg_src, launcher_src, settings_src = (
    read(prefs), read(background), read(launcher), read(settings)
)
doc_src, html_src = read(doc), read(preview_html)

# --------------------------------------------------------------------------------------
# 2. The design contract, as written down ("Remember this").
# --------------------------------------------------------------------------------------
for phrase in ("Remember this", "black outline", "different different colors and themes",
               "smooth 3d character", "Dance / Jump / Wave / Action", "React to what user is doing",
               "Blocky feel", "Blocky look", "Blocky sound", "Blocky animations",
               "Blocky transition", "Blocky motions", "All of it", "finished by tomorrow"):
    assert phrase in doc_src, f"design contract lost: {phrase}"

# --------------------------------------------------------------------------------------
# 3. Black outline on EVERY option, own colour/theme, white pixel text.
# --------------------------------------------------------------------------------------
assert "val BlockyOutline = Color(0xFF07090D)" in theme_src
assert "outline: Color = BlockyOutline" in comp_src
assert ".border(outlineWidth, outline)" in comp_src
assert "val BlockyWhite = Color(0xFFF7FAFF)" in theme_src
assert "val BlockyFont = FontFamily(Font(R.font.minecraft_ten))" in theme_src
assert "color = BlockyWhite" in comp_src
assert "text = title.uppercase(Locale.ROOT)" in comp_src
assert "enum class BlockyTheme" in theme_src
assert "enum class BlockyPattern { CHECKER, STRIPES, DOTS, STEPS, GRID, BRICKS, BOLTS }" in theme_src
themes = re.findall(r"^\s{4}([A-Z]+)\(Color\(0xFF", theme_src, re.M)
assert len(themes) == 14, themes
assert len(set(themes)) == 14, "every option theme must be distinct"
assert "pattern = theme.pattern" in comp_src and "patternColor = theme.shade" in comp_src
# Twelve differently themed option cards, each with its own colour.
option_block = shell_src.split("val options = listOf(", 1)[1].split("\n    val state =", 1)[0]
card_themes = re.findall(r"BlockyTheme\.([A-Z]+)", option_block)
assert len(card_themes) >= 12, card_themes
assert len(set(card_themes)) == len(card_themes), (
    f"every option needs a different colour/theme: {card_themes}"
)

# --------------------------------------------------------------------------------------
# 4. Smooth 3D character with the applied skin/cosmetics, plus real emote animation.
# --------------------------------------------------------------------------------------
assert "AerixSkinPreview3D(" in shell_src
assert "useClientCape = true" in shell_src and "interactionEnabled = true" in shell_src
assert "animated = true" in shell_src and "emote = state.emote" in shell_src
assert "emoteNonce = state.emoteNonce" in shell_src
assert "fun playEmote(name: String, durationMs: Int = 2600)" in skin_src
assert "fun startIdleMotion(enabled: Boolean = true)" in skin_src
assert "fun onEmote(name: String?)" in skin_src and "var emoteCallback" in skin_src
assert "emote: String? = null" in skin_src and "emoteNonce: Int = 0" in skin_src
assert "animated: Boolean = false" in skin_src and "onEmoteChanged: (String) -> Unit = {}" in skin_src
for cls in ("BlockyDanceAnimation", "BlockyJumpAnimation", "BlockyWaveAnimation",
            "BlockyActionAnimation", "BlockyCheerAnimation"):
    assert f"class {cls} extends skinview3d.PlayerAnimation" in js_src, cls
for fn in ("function playEmote(name, durationMs)", "function startIdleMotion(enabled)",
           "function notifyEmote(name)", "function makeEmoteAnimation(name)"):
    assert fn in js_src, fn
assert "renderPaused = false" in js_src
assert "quantize(" in js_src, "blocky (stepped) motions"

# Autonomous emotes and reactions to user actions.
assert "BlockyEmote.random()" in shell_src and "delay(8_000L)" in shell_src
assert "BlockyReactions.of(action)" in shell_src
assert "LaunchedEffect(progress)" in shell_src and "PojavAccountBridge.wardrobeRevision" in shell_src
assert "LaunchedEffect(account?.key)" in shell_src
for key in ('"wave"', '"dance"', '"jump"', '"action"', '"cheer"', '"idle"'):
    assert key in emote_src, key
assert "object BlockyReactions" in emote_src
assert "IDLE(\"idle\"" in emote_src

# --------------------------------------------------------------------------------------
# 5. Blocky sound / animations / transitions / motions.
# --------------------------------------------------------------------------------------
assert "object BlockySound" in sound_src and "SoundPool.Builder()" in sound_src
assert "o" not in ()  # no-op guard: square wave is checked below
assert "if (sin(phase) >= 0.0) 1.0 else -1.0" in sound_src, "square wave = blocky sound"
assert "Math.round(square * level * 8.0) / 8.0" in sound_src, "4-bit quantised crunch"
assert "AerixPreferences.blockySound" in sound_src
assert "BlockySound.play(" in comp_src and "BlockySound.playEmote(" in shell_src
for kind in ("TAP", "OPEN", "CLOSE", "WAVE", "DANCE", "JUMP", "ACTION", "CHEER",
             "REWARD", "LAUNCH"):
    assert kind in sound_src, kind
assert "val StepEasing: Easing" in theme_src and "Math.floor(fraction.toDouble() * steps)" in theme_src
assert "tween(140, easing = BlockyMotion.StepEasing)" in comp_src
assert "val CUT_MS = 0" in theme_src
assert "blocky: Boolean = false" in launcher_src and "if (blocky || duration == 0" in launcher_src
assert "fadeIn(snap()) togetherWith fadeOut(snap())" in launcher_src
assert "BlockySpikes(" in shell_src and "BlockySunburst(" in shell_src
assert "BlockyStepsProgress(" in shell_src

# --------------------------------------------------------------------------------------
# 6. Every tile is a REAL launcher/wardrobe/mod action - no mock destinations.
# --------------------------------------------------------------------------------------
for action in ("PojavLaunchBridge.play(activity)", "PojavInstanceBridge.select(instance)",
               "PojavAccountBridge.openDetail(it)", "SocialBackend.inviteCode()",
               "PojavNavigator.shareLogs(activity)", "PojavNavigator.openWebsite(activity)",
               "PojavNavigator.openDiscord(activity)", "AerixGuideState.replay()",
               "AerixThemeManager.wallpapers", "AerixPreferences.saveBlockyUi(false)",
               "navigate(LauncherSection.LIBRARY)", "navigate(LauncherSection.ACCOUNTS)",
               "navigate(LauncherSection.AERIX_CLIENT)", "navigate(LauncherSection.SOCIALS)",
               "navigate(LauncherSection.SERVERS)", "navigate(LauncherSection.SETTINGS)",
               "navigate(LauncherSection.CONTENT)", "navigate(LauncherSection.WALLPAPERS)"):
    assert action in shell_src, f"unwired tile: {action}"
assert "guideAnchor(AerixGuideState.PLAY)" in shell_src

# --------------------------------------------------------------------------------------
# 7. Wiring: shell switch, no rail, own backdrop, opt-outs, wallpaper preset.
# --------------------------------------------------------------------------------------
assert "val blockyActive = section == LauncherSection.HOME" in launcher_src
assert "AerixPreferences.blockyUi && !AerixGuideState.running" in launcher_src
assert "!shellActive" in launcher_src  # the Millida shell also hides the rail
assert ("AerixBackground(skipWallpaper = shellActive && "
            "(millidaActive || AerixPreferences.blockyBackdrop))") in launcher_src
assert "blockyActive -> BlockyShellScreen(activity, navigate)" in launcher_src
assert "skipWallpaper: Boolean = false" in bg_src and "AerixThemeManager.WALLPAPER_NONE" in bg_src
assert "wp_27_blocky_plaza" in read(theme_manager)
for key in ("KEY_BLOCKY_UI", "KEY_BLOCKY_SOUND", "KEY_BLOCKY_EMOTES", "KEY_BLOCKY_BACKDROP"):
    assert key in prefs_src, key
for var in ("blockyUi", "blockySound", "blockyEmotes", "blockyBackdrop"):
    assert f"var {var} by mutableStateOf(true)" in prefs_src, var
for fn in ("saveBlockyUi", "saveBlockySound", "saveBlockyEmotes", "saveBlockyBackdrop"):
    assert f"fun {fn}(value: Boolean)" in prefs_src, fn
for fn in ("aerix_settings_blocky_ui", "aerix_settings_blocky_sound",
           "aerix_settings_blocky_emotes", "aerix_settings_blocky_backdrop"):
    assert fn in settings_src, fn

# --------------------------------------------------------------------------------------
# 8. Resources referenced from Kotlin must actually exist.
# --------------------------------------------------------------------------------------
xml_root = ET.parse(strings).getroot()
string_names = {el.attrib["name"] for el in xml_root.findall("string")}
for name in sorted(set(re.findall(r"R\.string\.(aerix_blocky_[a-z_]+|aerix_settings_blocky_[a-z_]+)",
                                 shell_src + settings_src + emote_src + theme_src + comp_src))):
    assert name in string_names, f"missing string: {name}"
for name in sorted(set(re.findall(r"R\.drawable\.(aerix_blocky_[a-z_]+)",
                                 shell_src + emote_src + comp_src))):
    assert (RES / f"drawable-nodpi/{name}.png").is_file(), f"missing drawable: {name}"
assert (RES / "font/minecraft_ten.ttf").is_file()

# --------------------------------------------------------------------------------------
# 9. Old Lobby redesign stays scrapped.
# --------------------------------------------------------------------------------------
assert not (SCREENS / "AerixLobbyScreen.kt").exists()
for banned in ("AerixLobbyScreen", "lobbyVillageBackdrop", "lobbyActive", "wp_26_lobby_village"):
    assert banned not in shell_src + launcher_src + prefs_src + bg_src, f"Lobby came back: {banned}"

# --------------------------------------------------------------------------------------
# 10. Preview, docs, and the generated art.
# --------------------------------------------------------------------------------------
assert "/app_pojavlauncher/src/main/" in html_src, "preview must use root-relative asset URLs"
assert "localhost" not in html_src and "127.0.0.1" not in html_src
assert "skinview3d.bundle.js" in html_src and "skinview.js" in html_src
assert "steve.png" in html_src
assert 'o.type = "square"' in html_src
assert "playEmote(" in html_src and "startIdleMotion(true)" in html_src
assert "steps(6, end)" in html_src, "stepped CSS motion"
ASSET_EXT = (".png", ".jpg", ".jpeg", ".js", ".ttf", ".webp")
for asset in re.findall(r'"(/app_pojavlauncher/src/main/[^"]+)"', html_src):
    if not asset.endswith(ASSET_EXT):
        continue  # e.g. the icon-name prefix used for string concatenation
    assert (ROOT / asset.lstrip("/")).is_file(), f"broken preview asset: {asset}"
# Icon names referenced by the preview's ICON + name + ".png" concatenation.
icon_names = ["sun", "grid", "accounts", "settings", "sound", "help", "mod", "play", "gift"]
icon_names += re.findall(r'ICON \+ ([a-zA-Z_]+) \+ "\.png"', html_src)
icon_names += [m for m in re.findall(r'"([a-z_]+)", "(?:[^"]*)",', html_src)]
for name in set(icon_names):
    assert (RES / f"drawable-nodpi/aerix_blocky_ic_{name}.png").is_file(), (
        f"preview references missing icon: {name}"
    )
assert "Image.Resampling.NEAREST" in read(generator), "crisp blocky art, no blur"
assert ".blur(" not in shell_src, "the blocky backdrop is never blurred"
assert preview_png.stat().st_size > 10_000
# The generated site must never be tripped up by debug-download strings in this doc.
for forbidden in ("debug-latest", "Aerix-Nightly.apk", "Aerix-Launcher-debug"):
    assert forbidden not in doc_src, f"pages.yml would reject {forbidden} in docs"

print("blocky shell smoke: OK")
