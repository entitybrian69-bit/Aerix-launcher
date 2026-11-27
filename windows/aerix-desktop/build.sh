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
#   ./build.sh --screenshot    render every page to build/screenshots (no display needed)
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
# On Windows the launchers are .exe/.bat, which `-x` and `command -v` treat differently, so a
# candidate counts if the file exists and is runnable by the shell we are actually using.
is_tool() {
  [[ -n "${1:-}" ]] || return 1
  if [[ "$1" == *.bat || "$1" == *.cmd ]]; then [[ -f "$1" ]]; return; fi
  [[ -f "$1" && -x "$1" ]]
}

find_jdk() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    if is_tool "$JAVA_HOME/bin/java" || is_tool "$JAVA_HOME/bin/java.exe"; then
      echo "$JAVA_HOME"; return
    fi
  fi
  for c in "$HERE/../../toolchain/jdk" "$HERE/../toolchain/jdk" "$HERE/toolchain/jdk"; do
    if is_tool "$c/bin/java" || is_tool "$c/bin/java.exe"; then echo "$c"; return; fi
  done
  if command -v javac >/dev/null 2>&1; then
    local bin; bin="$(dirname "$(readlink -f "$(command -v javac)")")"
    echo "$(dirname "$bin")"; return
  fi
  echo ""; }

find_kotlinc() {
  if is_tool "${KOTLINC:-}"; then echo "$KOTLINC"; return; fi
  for c in "$HERE/../../toolchain/kc/bin/kotlinc" "$HERE/../toolchain/kc/bin/kotlinc" "$HERE/toolchain/kc/bin/kotlinc"; do
    if is_tool "$c"; then echo "$c"; return; fi
  done
  # A .bat on Windows is not found by `command -v` unless it is on the path with its extension.
  for c in kotlinc kotlinc.bat kotlinc.cmd; do
    local found; found="$(command -v "$c" 2>/dev/null || true)"
    if [[ -n "$found" ]]; then echo "$found"; return; fi
  done
  echo ""; }

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

# Stable identity for the Windows installer. jpackage uses this UUID to decide that a new build is
# an *upgrade* of an existing installation, so it replaces the old one in place instead of adding a
# second copy to Add/Remove Programs. It must therefore never change between versions: changing it
# strands every existing installation. Generated once, hard-coded on purpose.
AERIX_UPGRADE_UUID="${AERIX_UPGRADE_UUID:-9f2c41a7-5d3e-4b86-8a19-2f7be0c4d153}"
STDIOUT="$OUT/stdlib"
mkdir -p "$OUT"

# The Kotlin home is the directory holding `lib/kotlin-stdlib.jar`. It is normally two levels above
# the launcher, but on Windows the launcher is a `.bat` that may live inside `bin/` of an extracted
# archive (the CI job does exactly that), so resolve it by walking up from the real file instead of
# assuming a depth.
kotlin_home() {
  local dir; dir="$(cd "$(dirname "$KOTLINC")" && pwd)"
  for _ in 1 2 3; do
    if [[ -f "$dir/lib/kotlin-stdlib.jar" ]]; then echo "$dir"; return; fi
    dir="$(dirname "$dir")"
  done
  echo "$(dirname "$(dirname "$KOTLINC")")"
}

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
      -kotlin-home "$(kotlin_home)" \
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
  local klib="$(kotlin_home)/lib"
  for jar in kotlin-stdlib.jar kotlin-stdlib-jdk8.jar kotlin-stdlib-jdk7.jar kotlin-reflect.jar; do
    [[ -f "$klib/$jar" ]] && cp "$klib/$jar" "$KOTLIN_LIBS/"
  done
  log "writing jar"
  # Always the in-repo packer, never the JDK's `jar`.
  #
  # There used to be two branches here, and the difference was not cosmetic. `jar` cannot unpack a
  # dependency into the archive: `-C <dir-of-jars> .` *stores the jars*, and a jar inside a jar is
  # not on the class path, so the launcher died at startup with
  #   NoClassDefFoundError: kotlin/KotlinNothingValueException
  # The no-`jar` branch did what was needed all along (JarPackMain --libs expands the stdlib
  # classes), so it is simply the only path now. That also means the jar is built the same way
  # everywhere instead of only on JRE images - which is exactly how the Windows CI runner, the one
  # platform this product ships for, ended up with a 7.1 MB jar that could not start.
  "$JAVA" -cp "$OUT/classes:$KOTLIN_LIBS/kotlin-stdlib.jar" \
      net.kdt.aerix.desktop.tools.JarPackMain \
      "$OUT/classes" "$OUT/aerix-launcher.jar" "$MAIN_CLASS" "$VERSION" --libs "$KOTLIN_LIBS"
  # du reports allocated blocks, which made a 13 MB jar log as 25 MB on a filesystem with larger
  # blocks; report the file size instead.
  log "built $OUT/aerix-launcher.jar ($(wc -c < "$OUT/aerix-launcher.jar" | awk '{printf "%.1f MB", $1/1048576}'))"
}

run_tests() {
  if [[ ! -d src/test/kotlin ]]; then
    # There is no separate JUnit tree: every check lives in `--selftest`, which needs nothing but
    # the jar. Say so rather than failing on a missing directory or dropping into the REPL.
    log "no src/test/kotlin; the checks live in --selftest"
    run_selftest
    return
  fi
  log "running unit and integration tests"
  local outdir="$OUT/test-classes"
  rm -rf "$outdir"; mkdir -p "$outdir"
  "$KOTLINC" -J-Xmx1200m -J-XX:+UseSerialGC -nowarn -jvm-target 17 \
      -kotlin-home "$(kotlin_home)" \
      -cp "$OUT/classes" -d "$outdir" $(find src/test/kotlin -name '*.kt' | sort | tr '\n' ' ')
  cp -r src/test/resources/. "$outdir"/ 2>/dev/null || true
  "$JAVA" -Djava.awt.headless=true -cp "$outdir:$OUT/classes" net.kdt.aerix.desktop.test.TestMainKt
}

run_selftest() {
  log "running headless self test"
  "$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$OUT/aerix-launcher.jar" \
      net.kdt.aerix.desktop.MainKt --selftest "$@"
}

run_screenshot() {
  log "rendering every page to build/screenshots"
  "$JAVA" -Djava.awt.headless=true -Dfile.encoding=UTF-8 -cp "$OUT/aerix-launcher.jar" \
      net.kdt.aerix.desktop.MainKt --screenshot "$@"
  log "pages: $OUT/screenshots"
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
  # The packer is itself Kotlin, so the stdlib has to be on *its* class path while it runs. The
  # jar-tool branch above passes it; this call used to run with only the classes directory and died
  # with NoClassDefFoundError: kotlin/jvm/internal/Intrinsics on any JDK without a `jar` tool.
  "$JAVA" -cp "$OUT/classes:$KOTLIN_LIBS/kotlin-stdlib.jar" net.kdt.aerix.desktop.tools.JarPackMain "$appdir" "$DIST/AerixLauncher-$VERSION-portable.zip" "" "$VERSION"
  log "portable build: $DIST/AerixLauncher-$VERSION-portable.zip"
}

# Signs each given PE with the configured certificate and then *verifies* the result.
#
# A signature that was applied but does not verify is worse than none: the file still shows as
# unsigned to SmartScreen while the build log claims otherwise. So the verify is part of the build,
# not a suggestion in the docs.
sign_pe() {
  local files=("$@")
  # Drop empty entries the callers produce when a glob matched nothing.
  local targets=()
  for f in "${files[@]}"; do [[ -n "$f" && -f "$f" ]] && targets+=("$f"); done
  if [[ ${#targets[@]} -eq 0 ]]; then return; fi
  if [[ -z "${AERIX_SIGN_CERT:-}" || -z "${AERIX_SIGN_PASSWORD:-}" ]]; then
    for f in "${targets[@]}"; do
      log "UNSIGNED: $(basename "$f") (set AERIX_SIGN_CERT/AERIX_SIGN_PASSWORD to sign)"
    done
    return
  fi
  if ! command -v osslsigncode >/dev/null 2>&1; then
    echo "error: a signing certificate is configured but osslsigncode is not installed" >&2
    exit 1
  fi
  local certdir; certdir="$(mktemp -d)"
  printf '%s' "$AERIX_SIGN_CERT" | base64 -d > "$certdir/aerix.pfx" 2>/dev/null \
    || cp "$AERIX_SIGN_CERT" "$certdir/aerix.pfx"
  local f
  for f in "${targets[@]}"; do
    log "signing $(basename "$f")"
    # -ts is an RFC 3161 timestamp; -t is the older Authenticode one. Some verifiers prefer one or
    # the other, and a current timestamp keeps the signature valid after the certificate expires,
    # so both are requested.
    if ! osslsigncode sign -pkcs12 "$certdir/aerix.pfx" -pass "$AERIX_SIGN_PASSWORD" \
          -n "Aerix Launcher" -i "https://github.com/luciferstudios/Aerix-launcherPc" \
          -ts http://timestamp.digicert.com -t http://timestamp.digicert.com \
          -h sha256 -in "$f" -out "$f.signed" 2>&1 | tail -3; then
      rm -rf "$certdir"
      echo "error: signing failed for $(basename "$f")" >&2
      exit 1
    fi
    mv "$f.signed" "$f"
    # Prove it took. osslsigncode verify exits non-zero only when the signature is absent or
    # broken; a missing trust anchor is reported on stdout but is expected without a Windows cert
    # store, so the check is on the digest rather than on the chain.
    local verify
    verify="$(osslsigncode verify -in "$f" 2>&1 || true)"
    if ! printf '%s' "$verify" | grep -qiE "Message digest algorithm|Signature verification"; then
      rm -rf "$certdir"
      echo "error: signed $(basename "$f") but it does not verify:" >&2
      printf '%s\n' "$verify" | tail -5 >&2
      exit 1
    fi
    if printf '%s' "$verify" | grep -qi "verification: failed"; then
      rm -rf "$certdir"
      echo "error: signature on $(basename "$f") failed verification" >&2
      exit 1
    fi
    log "signed and verified: $(basename "$f")"
  done
  rm -rf "$certdir"
}

# Builds the jpackage *app image* (a folder, not an installer): AerixLauncher.exe next to the
# runtime image it uses.
#
# This exists separately from make_installer because jpackage deletes its staging app image once it
# has written an .exe or .msi - so by the time the installer exists, the only copy of the app image
# is inside it. The portable build has to be made before that happens.
make_app_image() {
  local jp="$JDK/bin/jpackage"
  if [[ ! -x "$jp" || ! -x "$JDK/bin/jlink" ]]; then return 1; fi
  local icon="$HERE/packaging/aerix.ico"
  local runtime_image="$DIST/runtime"
  local runtime_flag=()
  [[ -d "$runtime_image" ]] && runtime_flag=(--runtime-image "$runtime_image")
  rm -rf "$DIST/app-image"; mkdir -p "$DIST/app-image"
  log "jpackage --type app-image (for the portable build)"
  "$jp" --type app-image --name "AerixLauncher" --app-version "$VERSION" \
    --dest "$DIST/app-image" --input "$DIST/AerixLauncher" --main-jar aerix-launcher.jar \
    --main-class "$MAIN_CLASS" --icon "$icon" "${runtime_flag[@]}" \
    --description "Aerix Launcher - Minecraft: Java Edition for Windows" \
    --vendor "Aerix Launcher contributors" \
    --java-options "-Xmx1g" --java-options "-Dfile.encoding=UTF-8" >/dev/null 2>&1 || return 1
  [[ -f "$DIST/app-image/AerixLauncher/AerixLauncher.exe" ]]
}

make_installer() {
  make_dist
  # Build the app image first: it is what the portable build is packaged from, and it disappears
  # once jpackage writes the installer below. Its bundled jar is replaced with the final one
  # afterwards, so the portable build always matches the installer exactly.
  local app_image_ok=0
  make_app_image && app_image_ok=1 || true
  local jp="$JDK/bin/jpackage"
  if [[ ! -x "$jp" ]]; then log "jpackage missing (JDK without jpackage) - portable zip only"; return; fi
  # jpackage wants a JDK image with jlink; a JRE-based one fails here, and failing loudly is better
  # than the quiet `|| true` this used to be, which reported success without producing anything.
  if [[ ! -x "$JDK/bin/jlink" ]]; then log "jlink missing (JRE image) - portable zip only"; return; fi
  local icon="$HERE/packaging/aerix.ico"
  if [[ ! -f "$icon" ]]; then echo "error: missing $icon" >&2; exit 1; fi
  rm -rf "$DIST/image"; mkdir -p "$DIST/image"
  # .exe and .msi are Windows-only formats. Elsewhere jpackage can still produce a runnable
  # app-image, which is the same layout an .exe installs, so it is worth building.
  local type="app-image"
  case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) type="exe"; [[ -n "${AERIX_MSI:-}" ]] && type="msi";; esac
  # Build a *minimal* runtime image instead of bundling the whole JDK.
  #
  # jpackage with no --runtime-image links in every module in the JDK, and the resulting installer
  # carries jdk.compiler, jdk.jshell, jdk.jcmd and friends - tens of megabytes of development tools
  # that a launcher never loads. It is also a bad look for an installer that a security scanner will
  # pick apart. These are the modules the launcher actually needs:
  #   java.base     - everything else, and javax.crypto (AES-GCM) lives here since Java 9
  #   java.desktop  - Swing, Java2D, ImageIO, AWT
  #   java.xml      - the XML icon parser (DocumentBuilderFactory)
  #   java.net.http  - the HttpClient every download and API call goes through
  #   java.management - java.lang.management, used to size the RAM slider from physical memory
  #   jdk.unsupported - sun.misc.Unsafe, which older libraries and some JDK internals still reach for
  local runtime_image="$DIST/runtime"
  if [[ -x "$JDK/bin/jlink" ]]; then
    rm -rf "$runtime_image"
    log "jlink --add-modules java.base,java.desktop,java.xml,java.net.http,java.management,jdk.unsupported"
    if ! "$JDK/bin/jlink" --add-modules java.base,java.desktop,java.xml,java.net.http,java.management,jdk.unsupported \
        --strip-debug --no-header-files --no-man-pages --compress=2 \
        --output "$runtime_image" 2>&1 | tail -3; then
      echo "error: jlink failed" >&2
      exit 1
    fi
  fi
  local runtime_flag=()
  if [[ -d "$runtime_image" ]]; then runtime_flag=(--runtime-image "$runtime_image"); fi
  log "jpackage --type $type"
  # Windows-only installer switches. Without --win-upgrade-uuid every release installs *beside* the
  # previous one, which leaves two launchers in Add/Remove Programs and two copies of a 48 MB
  # runtime on disk; the UUID fixes that and is deliberately stable across versions so an upgrade
  # replaces in place. The others give the normal Start Menu entry, a desktop shortcut with a
  # prompt, and a directory chooser.
  local win_flags=()
  if [[ "$type" != "app-image" ]]; then
    if [[ -z "${AERIX_UPGRADE_UUID:-}" ]]; then
      echo "error: AERIX_UPGRADE_UUID is empty; refusing to ship an installer that cannot upgrade in place" >&2
      exit 1
    fi
    win_flags=(--win-menu --win-shortcut --win-shortcut-prompt --win-dir-chooser
               --win-upgrade-uuid "$AERIX_UPGRADE_UUID")
  fi
  if ! "$jp" --type "$type" --name "AerixLauncher" --app-version "$VERSION" \
      --dest "$DIST/image" --input "$DIST/AerixLauncher" --main-jar aerix-launcher.jar \
      --main-class "$MAIN_CLASS" --icon "$icon" "${runtime_flag[@]}" \
      "${win_flags[@]}" \
      --description "Aerix Launcher - Minecraft: Java Edition for Windows" \
      --vendor "Aerix Launcher contributors" \
      --java-options "-Xmx1g" --java-options "-Dfile.encoding=UTF-8"; then
    echo "error: jpackage failed (on Windows this usually means WiX is not installed)" >&2
    exit 1
  fi
  local found
  found="$(find "$DIST/image" -maxdepth 1 -type f \( -name '*.exe' -o -name '*.msi' \) 2>/dev/null)"
  # Authenticode signing, when a certificate is available.
  #
  # An unsigned installer is what makes Windows SmartScreen say "unrecognised app", and it is the
  # single largest contributor to a heuristic verdict: a large PE that unpacks a runtime and starts a
  # child process is structurally what a dropper looks like, and with no signature a model has
  # nothing to weigh against that shape. Signing is the only thing that actually clears it.
  #
  # Everything we ship as a PE gets signed, not just the installer: the app-image launcher is
  # downloaded and run directly by people using the portable build, so it needs the same treatment.
  #
  # Nothing here invents an identity: without AERIX_SIGN_CERT (a base64 PFX) and
  # AERIX_SIGN_PASSWORD the build produces an unsigned installer and says so.
  local app_exe="$DIST/app-image/AerixLauncher/AerixLauncher.exe"
  if [[ "$app_image_ok" == "1" && -f "$app_exe" ]]; then found="${found:+$found
}$app_exe"; fi
  sign_pe $found

  if [[ -n "$found" ]]; then log "installer: $found"; else log "app image: $DIST/image/AerixLauncher"; fi

  # The portable build, packaged from the jpackage *app image* rather than from a bare jar.
  #
  # This is the download most people will use, and its shape matters: the jar-only ZIP needs a Java
  # installation on the machine, while the installer is a self-extracting PE. An app-image ZIP is
  # neither - it is a plain folder holding a real AerixLauncher.exe next to the runtime it uses,
  # with no stub, no extraction at startup and no temporary directory to clean up afterwards. That
  # is the least ambiguous thing we can ship, so it becomes the recommended download while the
  # installer stays available for people who want Start Menu entries and Add/Remove Programs.
  local app_image="$DIST/app-image/AerixLauncher"
  if [[ "$app_image_ok" == "1" && -d "$app_image" && -f "$app_image/AerixLauncher.exe" ]]; then
    # jpackage copies the jar it was given; replace it with the final one so the portable build and
    # the installer contain byte-identical application code. The location differs between
    # `app/aerix-launcher.jar` and (for some jpackage versions) the image root, so replace every
    # copy found rather than assuming one.
    while IFS= read -r j; do cp "$OUT/aerix-launcher.jar" "$j"; done < <(find "$app_image" -name 'aerix-launcher.jar' -type f)
    # The image is signed above, so the ZIP distributes signed binaries rather than a signed
    # container holding unsigned ones.
    local portapp="$DIST/AerixLauncher-$VERSION-windows-portable"
    rm -rf "$portapp" "$DIST/AerixLauncher-$VERSION-windows-portable.zip"
    mkdir -p "$portapp"
    cp -R "$app_image/." "$portapp/"
    cat > "$portapp/README.txt" <<EOF
Aerix Launcher $VERSION (Windows, portable)
===========================================
Run AerixLauncher.exe. No installation and no separate Java are needed: the runtime is in this
folder and only this launcher uses it.

Your settings live in %APPDATA%\\Aerix Launcher and your game files in %USERPROFILE%\\Aerix, exactly
as with the installer, so the two builds can be swapped without losing anything.

Move or copy this folder anywhere. Deleting it removes the launcher and nothing else.
The console build (AerixLauncher-console.exe) is only useful for support: it prints the log.
EOF
    "$JAVA" -cp "$OUT/classes:$KOTLIN_LIBS/kotlin-stdlib.jar" net.kdt.aerix.desktop.tools.JarPackMain \
      "$portapp" "$DIST/AerixLauncher-$VERSION-windows-portable.zip" "" "$VERSION"
    log "portable (app image) build: $DIST/AerixLauncher-$VERSION-windows-portable.zip"
  fi
}

case "${1:-build}" in
  build) compile ;;
  jar) compile ;;
  --test|test) compile; run_tests ;;
  --selftest|selftest) compile; shift || true; run_selftest "$@" ;;
  --screenshot|screenshot) compile; shift || true; run_screenshot "$@" ;;
  --dist|dist) compile; make_dist ;;
  --installer|installer) compile; make_installer ;;
  --clean|clean) rm -rf "$OUT" "$DIST"; log "cleaned" ;;
  *) echo "usage: $0 [build|--test|--selftest|--screenshot|--dist|--installer|--clean]" >&2; exit 2 ;;
esac
