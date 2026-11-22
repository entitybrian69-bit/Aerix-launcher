#!/usr/bin/env python3
"""Smoke checks for the hardware-accelerated 1080p60 MP4 intro splash screen."""
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app_pojavlauncher/src/main/res/raw/aerix_intro.mp4"
JAVA = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

assert RAW.is_file() and RAW.stat().st_size == 3365447, "aerix_intro.mp4 must preserve original 1080p60 bytes"
head = RAW.read_bytes()[:32]
assert head[4:8] == b"ftyp", "aerix_intro.mp4 must be a valid MP4 container"

gradle = (ROOT / "app_pojavlauncher/build.gradle").read_text()
assert "noCompress += 'mp4'" in gradle

splash_xml = (ROOT / "app_pojavlauncher/src/main/res/drawable/aerix_splash.xml").read_text()
assert "#FF000000" in splash_xml

test_storage = (JAVA / "TestStorageActivity.java").read_text()
assert "LauncherActivity.EXTRA_PLAY_INTRO" in test_storage
assert "overridePendingTransition(0, 0)" in test_storage

launcher = (JAVA / "LauncherActivity.java").read_text()
assert "EXTRA_PLAY_INTRO" in launcher
assert "shouldPlayIntroOnLaunch" in launcher
assert "AerixIntroController" in launcher
assert "mIntroController.onPause()" in launcher and "mIntroController.onDestroy()" in launcher

intro = (JAVA / "aerix/ui/AerixIntroSplash.kt").read_text()
assert "SurfaceView" in intro and "MediaPlayer" in intro
assert "R.raw.aerix_intro" in intro
assert "prepareAsync()" in intro
assert "MEDIA_INFO_VIDEO_RENDERING_START" in intro
assert "maxOf(cw.toFloat() / vw.toFloat(), ch.toFloat() / vh.toFloat())" in intro
assert "AerixIntroState" in intro

screen = (JAVA / "aerix/ui/AerixLauncherScreen.kt").read_text()
assert "AerixIntroState.active" in screen
bg = (JAVA / "aerix/components/AerixBackground.kt").read_text()
assert "AerixIntroState.active" in bg

print("Intro splash screen (1080p60 SurfaceView + MediaPlayer): smoke checks passed")
