#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Aerix Launcher for Windows - toolchain-agnostic build script.
#
# It only needs a JDK (17+) and the Kotlin compiler. Nothing is downloaded:
# set JAVA_HOME / KOTLINC, or put them on PATH, or drop a JDK into ../../toolchain/jdk
# and the compiler into ../../toolchain/kc (the layout the repository's dev container uses).
#
#   ./build.sh                 compile + package build/aerix-launcher.jar
#   ./build.sh --test          compile and run the built-in test suite
#   ./build.sh --selftest      run the headless UI/data self test (writes screenshots)
#   ./build.sh --dist          jar + portable app image in dist/AerixLauncher/
#   ./build.sh --installer     dist + .msi (needs WiX 3 on Windows) or .exe
#   ./build.sh --clean
# ---------------------------------------------------------------------------
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"
OUT="$HERE/build"
DIST="$HERE/dist"
MAIN_CLASS="net.kdt.aerix.desktop.MainKt"
APP_NAME="Aerix Launcher"

# ---- toolchain discovery ---------------------------------------------------
find_jdk() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then echo "$JAVA_HOME"; return; fi
  for c in "$HERE/../../toolchain/jdk" "$HERE/../toolchain/jdk" "$HERE/toolchain/jdk"; do
    [[ -x "$c/bin/java" ]] && { echo "$c"; return; }
  done
  if command -v javac >/dev/null 2>&1; then
    local bin; bin="$(dirname "$(readlink -f "$(command -v javac)")")"
    echo "$(dirname "$bin")"; return
  fi
  echo ""; }

find_kotlinc() {
  if [[ -n "${KOTLINC:-}" && -x "$KOTLINC" ]]; then echo "$KOTLINC"; return; fi
  for c in "$HERE/../../toolchain/kc/bin/kotlinc" "$HERE/../toolchain/kc/bin/kotlinc" "$HERE/toolchain/kc/bin/kotlinc"; do
    [[ -x "$c" ]] && { echo "$c"; return; }
  done
  command -v kotlinc 2>/dev/null || echo ""; }

JDK="$(find_jdk)"
KOTLINC="$(find_kotlinc)"
if [[ -z "$JDK" ]]; then
  echo "error: no JDK found. Set JAVA_HOME to a JDK 17+ (javac must exist)." >&2; exit 1
fi
if [[ -z "$KOTLINC" ]]; then
  echo "error: no Kotlin compiler found. Set KOTLINC to the kotlinc launcher." >&2; exit 1
fi
JAVA="$JDK/bin/java"
export JAVA_HOME="$JDK"

VERSION="$(grep -E '^versionName=' "$HERE/../../version.properties" 2>/dev/null | cut -d= -f2 | tr -d '\r' || true)"
VERSION="${VERSION:-2.4.4}"
STDIOUT="$OUT/stdlib"
mkdir -p "$OUT"

log() { printf '\033[38;5;44m[aerix]\033[0m %s\n' "$*"; }

compile() {
  log "compiling (JDK $("$JAVA" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/'))"
  local sources cp="$OUT/resources"
  sources="$(find src/main/kotlin -name '*.kt' | sort | tr '\n' ' ')"
  if [[ -z "${sources// /}" ]]; then echo "error: no sources" >&2; exit 1; fi
  # Stage resources next to classes so the jar is self-contained.
  rm -rf "$OUT/classes" "$OUT/resources"
  mkdir -p "$OUT/classes" "$OUT/resources"
  cp -r src/main/resources/. "$OUT/resources"/
  "$KOTLINC" \
      -J-Xmx1200m -J-XX:+UseSerialGC \
      -nowarn \
      -jvm-target 17 \
      -kotlin-home "$(dirname "$(dirname "$KOTLINC")")" \
      -d "$OUT/classes" \
      $sources
  log "kotlin OK"
  # merge resources into classes dir
  cp -r "$OUT/resources/." "$OUT/classes/"
  # Kotlin runtime: the app is Kotlin, so its stdlib travels inside the jar (there is no Maven to
  # fetch it from, and a nested jar would not be on the class path). Only the stdlib jars are
  # unpacked - the klib directory also holds the whole compiler and its dependencies.
  KOTLIN_LIBS="$OUT/stdlib"
  rm -rf "$KOTLIN_LIBS"; mkdir -p "$KOTLIN_LIBS"
  local klib="$(dirname "$(dirname "$KOTLINC")")/lib"
  for jar in kotlin-stdlib.jar kotlin-stdlib-jdk8.jar kotlin-stdlib-jdk7.jar kotlin-reflect.jar; do
    [[ -f "$klib/$jar" ]] && cp "$klib/$jar" "$KOTLIN_LIBS/"
  done
  log "writing jar"
  if [[ -x "$JDK/bin/jar" ]]; then
    local manifest="$OUT/MANIFEST.MF"
    printf 'Manifest-Version: 1.0\r\nMain-Class: %s\r\nImplementation-Title: Aerix Launcher\r\nImplementation-Version: %s\r\n\r\n' \
        "$MAIN_CLASS" "$VERSION" > "$manifest"
    "$JDK/bin/jar" --create --file "$OUT/aerix-launcher.jar" --manifest "$manifest" -C "$OUT/classes" .
  else
    # Some trimmed JDK images ship without the jar tool; fall back to the in-repo packer.
    "$JAVA" -cp "$OUT/classes:$KOTLIN_LIBS/kotlin-stdlib.jar" net.kdt.aerix.desktop.tools.JarPackMain "$OUT/classes" "$OUT/aerix-launcher.jar" "$MAIN_CLASS" "$VERSION" --libs "$KOTLIN_LIBS"
  fi
  log "built $OUT/aerix-launcher.jar ($(du -h "$OUT/aerix-launcher.jar" | cut -f1))"
}

run_tests() {
  log "running unit and integration tests"
  local outdir="$OUT/test-classes"
  rm -rf "$outdir"; mkdir -p "$outdir"
  "$KOTLINC" -J-Xmx1200m -J-XX:+UseSerialGC -nowarn -jvm-target 17 \
      -kotlin-home "$(dirname "$(dirname "$KOTLINC")")" \
      -cp "$OUT/classes" -d "$outdir" $(find src/test/kotlin -name '*.kt' | sort | tr '\n' ' ')
  cp -r src/test/resources/. "$outdir"/ 2>/dev/null || true
  "$JAVA" -Djava.awt.headless=true -cp "$outdir:$OUT/classes" net.kdt.aerix.desktop.test.TestMainKt
}

run_selftest() {
  log "running headless self test"
  "$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$OUT/aerix-launcher.jar" \
      net.kdt.aerix.desktop.MainKt --selftest "$@"
}

make_dist() {
  local appdir="$DIST/AerixLauncher"
  rm -rf "$appdir"; mkdir -p "$appdir"
  cp "$OUT/aerix-launcher.jar" "$appdir/aerix-launcher.jar"
  if [[ -f "$HERE/packaging/aerix.cfg" ]]; then cp "$HERE/packaging/aerix.cfg" "$appdir/"; fi
  cat > "$appdir/Aerix Launcher.bat" <<EOF
@echo off
setlocal
if exist "%LOCALAPPDATA%\\Programs\\Eclipse Adoptium" goto :found
if defined JAVA_HOME goto :javahome
where javaw >nul 2>nul && (start "" javaw -jar "%~dp0aerix-launcher.jar" %*) && goto :eof
echo Aerix Launcher needs Java 17 or newer. Install it from https://adoptium.net
pause
exit /b 1
:javahome
start "" "%JAVA_HOME%\\bin\\javaw.exe" -jar "%~dp0aerix-launcher.jar" %*
goto :eof
:found
start "" javaw -jar "%~dp0aerix-launcher.jar" %*
EOF
  cat > "$appdir/README.txt" <<EOF
Aerix Launcher $VERSION (Windows portable)
=========================================
Run "Aerix Launcher.bat" (or double-click aerix-launcher.jar if Java 17+ is installed).
Your settings live in %APPDATA%\\Aerix Launcher and your game files in %USERPROFILE%\\Aerix.
You can move this folder anywhere; nothing is written into it.
EOF
  "$JAVA" -cp "$OUT/classes" net.kdt.aerix.desktop.tools.JarPackMain "$appdir" "$DIST/AerixLauncher-$VERSION-portable.zip" "" "$VERSION"
  log "portable build: $DIST/AerixLauncher-$VERSION-portable.zip"
}

make_installer() {
  make_dist
  local jp="$JDK/bin/jpackage"
  if [[ ! -x "$jp" ]]; then log "jpackage missing (JDK without jpackage) - portable zip only"; return; fi
  rm -rf "$DIST/image"; mkdir -p "$DIST/image"
  local type="app-image"
  case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) type="exe"; [[ -n "${AERIX_MSI:-}" ]] && type="msi";; esac
  "$jp" --type "$type" --name "AerixLauncher" --app-version "$VERSION" \
      --dest "$DIST/image" --input "$DIST/AerixLauncher" --main-jar aerix-launcher.jar \
      --main-class "$MAIN_CLASS" --icon "$HERE/packaging/aerix.ico" \
      --description "Aerix Launcher - Minecraft: Java Edition for Windows" \
      --vendor "Aerix Launcher contributors" \
      --java-options "-Xmx1g" --java-options "-Dfile.encoding=UTF-8" 2>&1 | tail -5 || true
  log "installer/app image: $DIST/image"
}

case "${1:-build}" in
  build) compile ;;
  jar) compile ;;
  --test|test) compile; run_tests ;;
  --selftest|selftest) compile; shift || true; run_selftest "$@" ;;
  --dist|dist) compile; make_dist ;;
  --installer|installer) compile; make_installer ;;
  --clean|clean) rm -rf "$OUT" "$DIST"; log "cleaned" ;;
  *) echo "usage: $0 [build|--test|--selftest|--dist|--installer|--clean]" >&2; exit 2 ;;
esac
