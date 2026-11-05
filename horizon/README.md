# Minecraft Rust in Aerix

Aerix uses the MIT-licensed Horizon Android 0.7.7 native engine, patched against
upstream commit `80700a6f26afe53a368032d8b28bd364f7c3e7e4`. When Play is
selected on the Rust screen, Aerix shows a preparation screen while the engine
downloads and verifies the required Minecraft 26.3 game resources from Mojang.
It then starts the game directly, without creating Horizon's launcher window;
when the game exits, the isolated process returns to Aerix. Downloads are
retained across retries. Rust worlds and preferences remain separate from Java
instances. This is unofficial and requires a legally owned copy of Minecraft.

The selected Aerix username is passed as an offline name if valid; Aerix account
tokens are not transferred. Horizon's separate saved sessions, if present, are
still managed by Horizon. Device compatibility, controls and online authentication
must be verified on a real device; a successful build does not prove gameplay.

## Building

The app must contain the patched native engine, **not** the stock Horizon APK
libraries, which open a separate launcher. The build fails if patched libraries
are missing. GitHub Actions builds all four ABIs from the pinned source using
`.github/workflows/horizon-native-check.yml`, then passes its artifacts to the
Android build. For a local Linux build, install Rust/rustup, a JDK, Android SDK
build-tools and NDK, set `ANDROID_HOME`, and run
`tools/build_horizon_native.sh` first. Generated libraries live under
`app_pojavlauncher/build/horizon-native` by default; set
`AERIX_HORIZON_LIB_DIR` to use a different build output directory. Do not put
built `.so` files in Git. `AERIX_HORIZON_APK_DIR` can provide an offline mirror
of the SHA-verified upstream ARM64 APK for its generated engine tables only.
No Minecraft game files are bundled. The modified source patch is included here
and upstream licensing and attribution are in the app's third-party notices.
