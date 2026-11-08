#!/usr/bin/env python3
"""Millida-style shell smoke test - the machine-checkable half of docs/millida-shell.md.

Checks the Millida visual language (near-black chrome, notched/stepped panels, lime-green Play,
blue START HERE card, grass character stage, five-tab bottom bar, coloured promo cards), that
every tile is wired to a real launcher action, that the living 3D character and emote engine are
preserved, that the appearance hierarchy keeps the Blocky Shell and classic dashboard reachable,
and that the preview's assets resolve.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app_pojavlauncher/src/main"
JAVA = MAIN / "java/net/kdt/pojavlaunch/aerix"
MILLIDA = JAVA / "ui/millida"
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
shell = SCREENS / "MillidaShellScreen.kt"
theme = MILLIDA / "MillidaTheme.kt"
components = MILLIDA / "MillidaComponents.kt"
blocky_shell = SCREENS / "BlockyShellScreen.kt"
blocky_doc = ROOT / "docs/blocky-shell.md"
skin_js = ASSETS / "skinview/skinview.js"
skin_kt = JAVA / "components/AerixPlayerSkin.kt"
prefs = JAVA / "bridge/AerixPreferences.kt"
launcher = JAVA / "ui/AerixLauncherScreen.kt"
settings = SCREENS / "SettingsScreen.kt"
strings = RES / "values/aerix_strings.xml"
doc = ROOT / "docs/millida-shell.md"
generator = ROOT / "tools/generate_millida_art.py"
preview_html = PREVIEW / "millida.html"
preview_png = PREVIEW / "millida-preview.png"
preview_server = ROOT / "tools/serve_millida_preview.py"
blocky_preview = PREVIEW / "index.html"
for p in (shell, theme, components, blocky_shell, blocky_doc, skin_js, skin_kt, prefs,
          launcher, settings, strings, doc, generator, preview_html, preview_png,
          preview_server, blocky_preview):
    assert p.is_file(), f"missing: {p}"

shell_src, theme_src, comp_src = read(shell), read(theme), read(components)
js_src, skin_src = read(skin_js), read(skin_kt)
prefs_src, launcher_src, settings_src = read(prefs), read(launcher), read(settings)
doc_src, html_src = read(doc), read(preview_html)

# --------------------------------------------------------------------------------------
# 2. The design contract, as written down.
# --------------------------------------------------------------------------------------
for phrase in ("Make the ui like the millida launcher one", "Near-black chrome",
               "Notched pixel panels", "One bright accent", "Grass character stage",
               "Five-tab bottom bar", "Pixel type", "Hard motion",
               "no mock destinations", "Appearance hierarchy"):
    assert phrase in doc_src, f"design contract lost: {phrase}"

# --------------------------------------------------------------------------------------
# 3. Notched pixel panels with a near-black outline and a hard offset shadow.
# --------------------------------------------------------------------------------------
assert "private fun notchedPath(" in comp_src
assert "moveTo(x0 + step, y0)" in comp_src and "lineTo(x1 - step, y1)" in comp_src
assert "MillidaPalette.Shadow" in comp_src and "MillidaPalette.Outline" in comp_src
assert 'style = Stroke(width = 2.dp.toPx())' in comp_src
assert "fill = if (enabled) fill else MillidaPalette.PanelSoft" in comp_src
assert "val Bg = Color(0xFF141416)" in theme_src
assert "val Green = Color(0xFF5CD65C)" in theme_src
assert "val Blue = Color(0xFF1D4EA8)" in theme_src
assert "val Outline = Color(0xFF08080A)" in theme_src
assert "val MillidaFont = FontFamily(Font(R.font.minecraft_ten))" in theme_src
assert "object MillidaMotion" in theme_src and "val CUT_MS = 0" in theme_src
assert "Math.floor(fraction.toDouble() * steps)" in theme_src
assert "enum class MillidaTone" in theme_src
tones = re.findall(r"^\s{4}([A-Z]+)\(MillidaPalette\.", theme_src, re.M)
assert len(tones) == 8 and len(set(tones)) == 8, tones
# Every destination card gets its own colour, preserving the earlier "different themes" rule.
entries = shell_src.split("val entries = listOf(", 1)[1].split("    val state =", 1)[0]
entry_tones = re.findall(r"MillidaTone\.([A-Z]+)", entries)
assert len(entry_tones) >= 8, entry_tones
assert "MillidaPalette.Grass" in comp_src and "drawMillidaGrass()" in comp_src
assert "drawMillidaRays(" in comp_src and "drawMillidaSparkles(" in comp_src
assert "fun Modifier.millidaScene()" in comp_src

# --------------------------------------------------------------------------------------
# 4. Five-tab bottom bar.
# --------------------------------------------------------------------------------------
assert "data class MillidaTab(" in comp_src and "fun MillidaTabBar(" in comp_src
tab_ids = re.findall(r'MillidaTab\(TAB_([A-Z]+),', shell_src)
assert sorted(tab_ids) == ["CATALOG", "CHARACTER", "MORE", "PLAY", "SHOP"], tab_ids
assert "fill = if (active) MillidaPalette.Green else Color.Transparent" in comp_src
for icon in ("grass", "book", "player", "emerald", "dots"):
    assert f"aerix_millida_ic_{icon}" in shell_src, icon

# --------------------------------------------------------------------------------------
# 5. Blue START HERE card, big green Play, MY BUILDS, promo row, grass stage.
# --------------------------------------------------------------------------------------
assert "fill = MillidaPalette.Blue" in shell_src, "blue START HERE card"
assert "aerix_millida_ic_chest" in shell_src
assert "guideAnchor(AerixGuideState.PLAY)" in shell_src
assert "MillidaButton(" in shell_src and "sound = BlockySound.Kind.LAUNCH" in shell_src
assert "fun MillidaBuildRow(" in shell_src and "fun MillidaPromoRow(" in shell_src
assert "fun MillidaCharacterStage(" in shell_src and ".millidaScene()" in shell_src
assert "fun MillidaWideLayout(" in shell_src and "fun MillidaCompactLayout(" in shell_src
assert "fun MillidaMoreSheet(" in shell_src
for label in ("aerix_millida_my_builds", "aerix_millida_where_to_play",
              "aerix_millida_start_here", "aerix_millida_play", "aerix_millida_build"):
    assert label in shell_src, label

# --------------------------------------------------------------------------------------
# 6. Living 3D character, autonomous + reactive emotes, pose chips, stepped sound.
# --------------------------------------------------------------------------------------
assert "AerixSkinPreview3D(" in shell_src
assert "useClientCape = true" in shell_src and "interactionEnabled = true" in shell_src
assert "animated = true" in shell_src and "emote = state.emote" in shell_src
assert "emoteNonce = state.emoteNonce" in shell_src
assert "onEmoteChanged = state.onEmoteChanged" in shell_src
assert "BlockyEmote.random()" in shell_src and "delay(8_000L)" in shell_src
assert "BlockyReactions.of(action)" in shell_src
assert "LaunchedEffect(progress)" in shell_src
assert "PojavAccountBridge.wardrobeRevision" in shell_src
assert "LaunchedEffect(account?.key)" in shell_src
assert "BlockySound.playEmote(" in shell_src
for pose in ("aerix_millida_pose_stand", "aerix_millida_pose_wave", "aerix_millida_pose_dance"):
    assert pose in shell_src, pose
assert "BlockyEmote.IDLE" in shell_src
assert "if (name === 'idle')" in js_src, "STAND chip must return to the living idle pose"
assert "function playEmote(name, durationMs)" in js_src
assert "function startIdleMotion(enabled)" in js_src
assert "fun playEmote(name: String, durationMs: Int = 2600)" in skin_src

# --------------------------------------------------------------------------------------
# 7. Every tile is a REAL launcher/wardrobe/mod action - no mock destinations.
# --------------------------------------------------------------------------------------
for action in ("PojavLaunchBridge.play(activity)", "PojavInstanceBridge.select(instance)",
               "PojavAccountBridge.openDetail(it)", "SocialBackend.inviteCode()",
               "PojavNavigator.shareLogs(activity)", "PojavNavigator.openWebsite(activity)",
               "PojavNavigator.openDiscord(activity)", "AerixGuideState.replay()",
               "AerixThemeManager.wallpapers", "AerixPreferences.saveMillidaUi(false)",
               "AerixPreferences.saveBlockyUi(false)",
               "navigate(LauncherSection.LIBRARY)", "navigate(LauncherSection.ACCOUNTS)",
               "navigate(LauncherSection.AERIX_CLIENT)", "navigate(LauncherSection.SOCIALS)",
               "navigate(LauncherSection.SERVERS)", "navigate(LauncherSection.SETTINGS)",
               "navigate(LauncherSection.CONTENT)", "navigate(LauncherSection.WALLPAPERS)",
               "navigate(LauncherSection.DISCOVER)"):
    assert action in shell_src, f"unwired tile: {action}"

# --------------------------------------------------------------------------------------
# 8. Wiring: default Millida -> Blocky -> classic, no rail, flat backdrop, settings switch.
# --------------------------------------------------------------------------------------
assert "val millidaActive = section == LauncherSection.HOME" in launcher_src
assert "AerixPreferences.millidaUi && !AerixGuideState.running" in launcher_src
assert "val blockyActive = section == LauncherSection.HOME && !millidaActive" in launcher_src
assert "val shellActive = millidaActive || blockyActive" in launcher_src
assert "!shellActive" in launcher_src
assert "shellActive && (millidaActive || AerixPreferences.blockyBackdrop)" in launcher_src
assert "MillidaShellScreen(activity, navigate)" in launcher_src
assert "BlockyShellScreen(activity, navigate)" in launcher_src
assert "HomeScreen(activity, navigate)" in launcher_src
assert "sectionTransition(transitionType, duration, extent, shellActive)" in launcher_src
assert "KEY_MILLIDA_UI" in prefs_src and '"aerixMillidaUi"' in prefs_src
assert "var millidaUi by mutableStateOf(true)" in prefs_src
assert "fun saveMillidaUi(value: Boolean)" in prefs_src
assert "AerixPreferences.millidaUi" in settings_src
assert "AerixPreferences.saveMillidaUi(it)" in settings_src

# --------------------------------------------------------------------------------------
# 9. Resources referenced from Kotlin must actually exist; no Millida branding in the app.
# --------------------------------------------------------------------------------------
xml_root = ET.parse(strings).getroot()
string_names = {el.attrib["name"] for el in xml_root.findall("string")}
for name in sorted(set(re.findall(r"R\.string\.(aerix_millida_[a-z_]+|aerix_settings_millida_[a-z_]+)",
                                 shell_src + settings_src + theme_src + comp_src))):
    assert name in string_names, f"missing string: {name}"
for name in sorted(set(re.findall(r"R\.drawable\.(aerix_millida_[a-z_]+)",
                                 shell_src + comp_src + theme_src))):
    assert (RES / f"drawable-nodpi/{name}.png").is_file(), f"missing drawable: {name}"
icons = sorted((RES / "drawable-nodpi").glob("aerix_millida_ic_*.png"))
assert len(icons) == 19, [p.name for p in icons]
assert "Image.Resampling.NEAREST" in read(generator)
# We describe the *style*, we do not rebrand Aerix as Millida.
for text in xml_root.findall("string"):
    value = "".join(text.itertext())
    assert "MILLIDA LAUNCHER" not in value.upper(), f"Millida branding leaked: {text.attrib['name']}"

# --------------------------------------------------------------------------------------
# 10. Preview, docs, and the old shell staying available.
# --------------------------------------------------------------------------------------
assert "/app_pojavlauncher/src/main/" in html_src, "preview uses root-relative asset URLs"
assert "localhost" not in html_src and "127.0.0.1" not in html_src
assert "skinview3d.bundle.js" in html_src and "skinview.js" in html_src
assert "steve.png" in html_src
assert 'o.type = "square"' in html_src, "square-wave chiptune"
assert "playEmote(" in html_src and "startIdleMotion(true)" in html_src
assert "steps(6, end)" in html_src, "stepped CSS motion"
assert "clip-path: polygon" in html_src, "notched pixel panels in the preview"
assert "aerix_millida_ic_" in html_src
ASSET_EXT = (".png", ".jpg", ".jpeg", ".js", ".ttf", ".webp")
for asset in re.findall(r'"(/app_pojavlauncher/src/main/[^"]+)"', html_src):
    if not asset.endswith(ASSET_EXT):
        continue
    assert (ROOT / asset.lstrip("/")).is_file(), f"broken preview asset: {asset}"
assert preview_png.stat().st_size > 10_000
assert "/preview/millida.html" in read(preview_server)
assert blocky_preview.is_file() and "Blocky" in read(blocky_preview)
assert (ROOT / "tests/blocky_shell_smoke.py").is_file()
assert (JAVA / "ui/blocky/BlockyTheme.kt").is_file()
for forbidden in ("debug-latest", "Aerix-Nightly.apk", "Aerix-Launcher-debug"):
    assert forbidden not in doc_src, f"pages.yml would reject {forbidden} in docs"

print("millida shell smoke: OK")
