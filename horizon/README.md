# Direct-game integration (in progress)

Aerix currently packages the unmodified Horizon Android 0.7.7 native library,
which starts Horizon's own launcher. It is **not** a direct-game build.

`patches/direct-game.patch` is a work-in-progress against upstream commit
`80700a6f26afe53a368032d8b28bd364f7c3e7e4`. It starts the install/game flow
automatically, reports progress and errors through an Aerix Activity callback,
and exits the host when the game returns. The `horizon-native-check` workflow
tries to build it for both ARM ABIs, without publishing a release or altering
Aerix's packaged libraries.

This patch is not wired into the app and is not a completed feature. The host
Activity must implement the status callback and provide a cancellable progress
and error screen; the build must package the patched libraries instead of the
upstream ones; account behavior and lifecycle/device handling require work and
verification. Do not treat the generated artifacts as a finished APK.
