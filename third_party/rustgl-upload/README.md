# User-requested RustGL prebuilt

`native-libraries.zip` contains ONLY the four unmodified `librust_gl.so` entries
from the user-uploaded `app-debug.log` APK (original plugin 0.3.0/code 23).
It does not contain the plugin's DEX, UI, resources, or signing material.

- Original APK SHA256: `4ce0d57776132e0d782836d9b5ba7da57b0781b1f15f2eccf0766b239595e73f`
- Native-only archive SHA256: `57b1b16929a38f7bb12f282fef759e0a19886ef37c74d965d69645a689469687`
- Each native hash/ABI/size is pinned independently in `scripts/import_uploaded_rustgl.py`.
- User explicitly requested replacing our source-built renderer with these exact
  bytes after being informed of the retained memory bug. Experimental test only;
  not a stable release, verified upstream binary or legal/license clearance.
- No license/notices were present in the supplied APK. Upstream README's MIT
  declaration and dependency provenance for this prebuilt remain unresolved;
  this provenance document is not a substitute for rights-holder notices.
- See `docs/development/renderer-apk-review/` for static findings, including the
  confirmed ARM64/x86_64 `glGetStringi` use-after-free. We do NOT patch those bytes.

The dedicated importer checks archive and native SHA256, ELF ABI/type, system-only
dependencies, no writable-executable segments, and ABI-specific alignment: 16 KiB
for 64-bit, 4 KiB for the supplied 32-bit libraries. This explicit exception does
not relax `prepare_rustgl_bundle.py`'s existing all-ABI 16 KiB source-build policy.
The runtime still checks APK-authenticated hashes and loads only private verified
immutable generations. Old generations are retained on device, not selected or
packaged in this replacement, so no live library or instance data is deleted.

Aerix bundle version 0.3.3 is only a packaging generation, not the plugin version.
The manifest's `revision` is the native archive SHA1 for store identity, explicitly
labelled NOT an upstream Git revision. No verified source mapping is asserted.
