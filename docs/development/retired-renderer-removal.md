# Retired experimental renderer removal

The user requested complete removal after repeated failed phone attempts.

Removed from the current tree and active APK pipeline:
- All bundled native payloads and native-only uploaded archive, all ABIs/generations.
- Discovery/initialization/store/backend policy and experimental renderer UI.
- Dedicated native mirror, JNI hooks, startup configuration and checkpoints.
- Dedicated Vulkan probe service/native helper/UI and isolated-process scaffolding.
- Source build/import/smoke/benchmark recipes, patches, old evidence and inspection docs.
- CI asset-import jobs and old renderer-positive packaging gates.

Other renderers, generic external-plugin support, FPS counter, recording, accounts,
mods, instances and worlds are retained.

A minimal upgrade tombstone recognizes the two retired package IDs, excludes the
external package from discovery, resets only the retired global selection, and
invalidates only that instance override. Its sole file cleanup target is the old
private no-backup renderer cache; symlinks must not be followed into game data.
The tombstone is not renderer functionality and cannot load/install native code.
Existing installed APKs require installing the new update to receive removal;
we do not rewrite old Git commits, old artifacts or uninstall external apps.

New CI tests reject retired asset/native/probe names and renamed copies of the
four original native payload hashes in both final APK variants. Unit tests cover
retired-tag recognition, preserving other choices, recursive cleanup, idempotence,
and preserving sibling world data behind a symlink. Generic V2 schema tests use
an independent example plugin fixture instead of the retired plugin's identities.

## Completed build validation

Runtime source: `608e8aa00bf6ac3fac926a68b189a9971c44e31b`.
CI https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39376190627
completed **success**: packaging-policy tests, launcher regressions, both APK
builds, native FPS regression and retired-payload absence checks all passed.
No on-device gameplay claim is made by those checks.

- Existing-runtime update ZIP (86,702,363 bytes):
  https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39376190627/artifacts/12242644601
- Full runtime ZIP (115,797,801 bytes):
  https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39376190627/artifacts/12243607281
- SHA-256:
  https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39376190627/artifacts/12242828990

Install the APK as an update, not an uninstall/data reset. Existing-runtime
variant relies on the Java runtimes already installed by the earlier full build.
