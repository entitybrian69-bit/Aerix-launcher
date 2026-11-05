#!/usr/bin/env bash
# Build all supported Android ABIs from pinned upstream source and Aerix's patch.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEST=${AERIX_HORIZON_LIB_DIR:-"$ROOT/app_pojavlauncher/build/horizon-native"}
WORK="$ROOT/app_pojavlauncher/build/horizon-source"
SHA=80700a6f26afe53a368032d8b28bd364f7c3e7e4
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK with NDK and build-tools}"
command -v rustup >/dev/null || { echo 'Install rustup and Rust 1.95.0 first.' >&2; exit 1; }
mkdir -p "$(dirname "$WORK")"
if [ ! -d "$WORK/.git" ]; then
  git clone --depth=1 --branch v0.7.7 https://github.com/Vasysik/horizon-android.git "$WORK"
fi
[ "$(git -C "$WORK" rev-parse HEAD)" = "$SHA" ] || { echo "Unexpected Horizon source revision" >&2; exit 1; }
# Reuse only an unchanged checkout or the exact patch; preserve user edits.
if git -C "$WORK" apply --reverse --check "$ROOT/horizon/patches/direct-game.patch" 2>/dev/null; then
  : # The requested patch is already applied.
elif git -C "$WORK" diff --quiet && git -C "$WORK" apply --check "$ROOT/horizon/patches/direct-game.patch"; then
  git -C "$WORK" apply "$ROOT/horizon/patches/direct-game.patch"
else
  echo "Horizon checkout has unexpected changes: $WORK" >&2; exit 1
fi
rustup toolchain install 1.95.0 --profile minimal
rustup target add --toolchain 1.95.0 aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
NDK=$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
for entry in 'arm64-v8a:aarch64-linux-android' 'armeabi-v7a:armv7-linux-androideabi' 'x86:i686-linux-android' 'x86_64:x86_64-linux-android'; do
  ABI=${entry%%:*}; TARGET=${entry#*:}
  (cd "$WORK" && python3 tools/android_build.py --public --rust-only --profile fast --abi "$ABI")
  mkdir -p "$DEST/horizon-direct-native-$ABI"
  cp "$WORK/target/$TARGET/fast/libhorizon_android.so" "$DEST/horizon-direct-native-$ABI/"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$DEST/horizon-direct-native-$ABI/libhorizon_android.so"
done
printf 'Patched Rust game engines built in %s\n' "$DEST"
