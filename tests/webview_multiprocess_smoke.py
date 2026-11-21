#!/usr/bin/env python3
"""Smoke test ensuring WebView uses distinct per-process data directories and degrades safely."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch"

app_java = (APP / "PojavApplication.java").read_text(encoding="utf-8")
skin_kt = (APP / "aerix/components/AerixPlayerSkin.kt").read_text(encoding="utf-8")

assert "setDataDirectorySuffix(webViewDataDirSuffixForProcess(procName))" in app_java, \
    "PojavApplication must isolate WebView data directories per process on Android 9+"

on_create = app_java.split("public void onCreate()")[1].split("if (procName != null")[0]
assert "configureWebViewDataDirectory(procName);" in on_create, \
    "configureWebViewDataDirectory must run at the top of Application.onCreate() before any WebView"
assert "Build.VERSION_CODES.P" in app_java, \
    "WebView.setDataDirectorySuffix is API 28+ (Android P) and must be version-gated"

assert "runCatching {\n                            playerSkin.loadWebView(" in skin_kt, \
    "AerixSkinPreview3D must guard WebView construction and fall back to the 2D skin preview"

print("webview_multiprocess_smoke: per-process WebView isolation and fallback verified")
