# RustGL implementation delivery

> **Experimental policy update:** The user approved a source-built experimental
> bundle based on upstream's README MIT declaration, despite the missing LICENSE.
> See [experimental bundle instructions](rustgl-experimental-bundle.md). New CI
> test APKs now require real native payloads; older absence/hold statements below
> describe the earlier implementation/review. Stable release and legal clearance
> remain unapproved. No device gameplay or performance result is implied.


## Executive status

Aerix now has a native bundle installation/verification/activation/rollback path,
connected to the existing plugin renderer extension point, an isolated system-Vulkan
probe with a Settings diagnostic button, enforced RustGL GLES selection, a reviewed
release import tool, and phone benchmark collection/analysis tools.

**No RustGL native binary or JAR was added. No missing native Vulkan GL implementation
was completed. No phone benchmark was run.** Those are hard outstanding requirements,
not inferred successes. This checkout can build an integration-bearing launcher, not
an approved bundled RustGL gameplay release. Existing renderers remain available.

## Implementation sequence and concrete injection points

Implemented in this order: immutable bundle store → APK asset provider → existing
renderer discovery/cache/instance launch override → initialization quarantine and
clean launch failure → separate-process capability probe → audit-only import path →
benchmark tooling. No launcher rewrite, fabricated renderer JAR or automatic mod
installation is involved.

| File / class / method | Actual responsibility |
|---|---|
| `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix/renderer/RendererBundleStore.kt` / `install`, `activate`, `verify`, `beginInit`, `recover` | Bounded SHA-256 verification, immutable content-derived generations, fsync/atomic publication, cross-process locking, previous generation, failed/interrupted-init quarantine and approved rollback |
| Same directory / `BundledRustGl.kt` / `discover` | Read authenticated APK index; select process ABI; install approved current payload; retain verified previous; verify ELF and return an actual `AerixRendererPlugin` |
| `.../aerix/bridge/PojavPluginBridge.kt` / `loadPlugins` | Discover bundled provider before external APK scan; register real bundle in the existing renderer cache. No entry if absent/rejected |
| Same / `applyLaunchOverride` | Called from existing `game/GameActivity.java:onCreate`; re-scan a selected bundled renderer before environment mutation; retain built-in fallback if unavailable |
| Same / `AerixRendererPlugin.setupEnvironment` | Set resolved `LIBGL_NAME`, `SDL_EGL_LIBRARY`, `POJAV_RENDERER`, GLES backend environment |
| Same / `AerixRendererPlugin.librarySearchPath`, `setupRenderer` | Supply immutable native directory; hold init lease around `MojoExec.prepareEgl`; catch linkage/managed exceptions and return failure |
| `.../game/renderer/GameRenderer.java:maybeSetupRenderer` | Abort plugin failure after environment/native mutation instead of loading GL4ES against the plugin's stale environment |
| `.../utils/jre/GameRunner.java` existing lifecycle | Applies environment, sets native path and initializes renderer before starting JVM; its existing failure dialog handles a returned `false`. No classpath/mod-folder injection added |
| `.../aerix/renderer/RustGlBackendPolicy.kt` | Prevent external or bundled RustGL metadata selecting unfinished native Vulkan paths; use GLES before environment setup |
| `.../aerix/ui/screens/SettingsScreen.kt` | Preserve experimental confirmation for external/bundled RustGL; add **Check system Vulkan** and copyable diagnostic report |
| `scripts/prepare_rustgl_bundle.py` | Explicit release approval + license evidence + exact artifact hashes + ABI/ELF/DT_NEEDED/16 KiB checks, then prepare APK assets |

### Exact packaging and runtime paths

```text
# Build-time assets; absent until legal/technical approval:
app_pojavlauncher/src/main/assets/renderer-bundles/rustgl/index.json
app_pojavlauncher/src/main/assets/renderer-bundles/rustgl/<version>/<abi>/librust_gl.so
app_pojavlauncher/src/main/assets/renderer-bundles/rustgl/<version>/<abi>/<approved-dependent-lib>.so
app_pojavlauncher/src/main/assets/renderer-bundles/rustgl/<version>/<abi>/NOTICE.txt

# Private runtime storage (Context.noBackupFilesDir; do not hardcode Android user 0):
<dataDir>/no_backup/renderer-bundles/rustgl/<version>-<abi>-<content-id>/librust_gl.so
<dataDir>/no_backup/renderer-bundles/rustgl/active
<dataDir>/no_backup/renderer-bundles/rustgl/previous
<dataDir>/no_backup/renderer-bundles/rustgl/pending
<dataDir>/no_backup/renderer-bundles/rustgl/bad-<generation>

# Instance selection stored through existing renderer preference:
plugin:net.kdt.pojavlaunch.renderer.rustgl
```

There are **zero renderer runtime JARs** in the reviewed RustGL architecture.
`gl-compat` emits a native cdylib; an AAR or plugin APK is a wrapper, not a Minecraft
mod. Instance `<gameDirectory>/mods/`, saves, loader installation and Java classpath
are left untouched. Any later Java companion/mod needs its own loader compatibility,
license, artifact identity and conflict-safe per-instance lifecycle; none is invented here.

### Release import (only after approved audit)

```sh
python3 scripts/prepare_rustgl_bundle.py \
  --approval /audited-release/approval.json \
  --input /audited-release/native-by-abi \
  --version 0.3.0 \
  --readelf /android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf
```

Approval schema and unresolved legal conclusions are in `rustgl-compliance.md`.
The script requires all four ABI payloads and does not fetch, build or silently
approve upstream binaries. The version above is illustrative, not a produced release.
Sign the resulting Aerix APK with its existing update identity; install as an Android
package update, not uninstall/data-clear. Android authenticates the APK and index.
No network manifest, mods directory or writable shared-storage path authorizes native
code. SHA-256 checks supplement this APK trust anchor; an unsigned checksum downloaded
beside a binary would not authenticate it.

### Update, atomicity and rollback

- Content IDs include version/revision/ABI/file sizes and hashes. Native generations
  are immutable and old generations remain while they may be loaded in another process.
- Every file is bounded, verified and made read-only before publication. File and
  directory fsync precede/follow atomic directory and pointer renames. A cross-process
  file lock serializes store operations; a separate init lease distinguishes a running
  init from an interrupted one. No partial directory becomes active.
- An update APK includes current and previous **approved** manifest entries. Previous
  files can remain privately installed; a rollback is allowed only against hashes still
  approved by the current signed index. Never trust an arbitrary on-disk old manifest.
- Managed failure or a missing library becomes an ordinary initialization failure. A
  failed init quarantines that generation; the next scan uses the verified previous
  generation, or leaves the selection unavailable and uses the built-in fallback before
  environment setup. A crash during native init leaves `pending`; process death releases
  the lease, allowing the next scan to quarantine and roll back.
- SIGSEGV cannot be caught by Kotlin. This mechanism recovers on the **next launch**;
  it cannot promise that arbitrary native code never crashes. Crashes after a successful
  EGL init are not automatically attributed/quarantined by this init-only mechanism.
- No hot switch after GL/Vulkan handles are created. A false plugin init aborts the
  current launch before JVM startup. A process-wide failure flag refuses any further
  renderer initialization in that game process; changing backends requires a fresh game
  process. No existing launcher-wide fallback behavior for non-plugin renderers is rewritten.
- Fault-injection on real Android storage remains required, including full disk and
  power loss. Interrupted staging directories can remain; no unsafe live generation
  garbage collection or silent native overwrite is implemented.

The security boundary is the signed APK and private app storage. An attacker already
executing arbitrary game/native code with Aerix's UID can change files or hook loading;
checksums are not a sandbox against same-UID code. Existing separately installed plugin
trust/consent is retained; this work does not certify arbitrary external plugin APKs.

## Vulkan detection and fallback

`src/main/jni/renderer/vulkan_probe.cpp` dynamically opens only the system Vulkan
loader in `VulkanProbeService` (`exported=false`, `isolatedProcess=true`). The probe
collects loader API, physical device API/vendor/device/driver IDs, the
`VK_KHR_dynamic_rendering` extension string and the actual `dynamicRendering` feature
via `vkGetPhysicalDeviceFeatures2`. It handles enumeration failure/changes, count
bounds and missing entry points. The application skips normal private-storage/runtime
initialization in its isolated process. No plugin or custom driver is loaded there.

`VulkanProbeClient` asynchronously handles IPC errors, service death and a five-second
timeout. A failed/unknown report is unavailable capability, not a positive result.
A native probe signal stays in the child process; this does not guarantee recovery from
a system-wide/vendor GPU-driver hang. The diagnostic never enables Vulkan rendering.

`VulkanEligibility` tests API>=1.1, actual feature=true, and either core Vulkan 1.3
or the extension before 1.3. It supports exact evidence-backed vendor/device/driver
rules rather than speculative whole-vendor blacklists. **No known-bad tuple is invented
without a device report. The current rule list has no established device evidence.**
The evaluator is a tested policy helper, not a completed native rendering backend.

Even a positive probe is insufficient: `RustGlBackendPolicy` currently forces GLES;
`BundledRustGl` refuses an index declaring Vulkan. Completing actual Vulkan also needs
working GL resource/state mapping, dynamic SPIR-V shader pipelines, descriptors,
vertex/index/texture uploads, framebuffer/render-pass transitions, synchronization,
Android swapchain presentation, resize/lifecycle and VK_ERROR_DEVICE_LOST recovery.
Those upstream draw/resource gaps have **not** been implemented by a capability probe.

## Benchmark and optimization

See `rustgl-benchmark.md` for the phone matrix, identical-world/settings protocol,
thermal controls, randomized paired repeats, 15-minute sustained windows, frame source
validation and measurement-driven optimization experiments. `renderer_benchmark.py`
collects raw evidence and computes interval metrics from caller-verified game-present
CSV. Synthetic unit tests establish arithmetic only. No FPS benefit or native hot-path
optimization is claimed.

## Licensing

See `rustgl-compliance.md` for component-by-component conditions, source/relink/notice
requirements and release checklist. `rustgl-cargo-inventory.json` records 89 locked
packages with unresolved license conclusions. Root grant, exact dependency packages,
toolchain/static-runtime notices and artifact provenance remain release blockers.
The compliant alternative currently shipped is Aerix integration code without RustGL
native payloads, preserving existing renderers and the separately trusted plugin path.

## Tests and ranked blockers

Automated additions: 15 bundle-store tests, 6 backend/capability-policy tests,
1 isolated-application initialization test and 8 importer/benchmark-tool tests, alongside the earlier 16 metadata/ELF tests. Coverage
includes traversal, symlinks, checksums, size bounds, tampering, rollback approval,
concurrent init vs interrupted init, quarantine, Vulkan version/feature/extension and
bad-driver matching, license refusal, native dependency/alignment refusal and metric
math. Store/native-file fixtures are synthetic, not runnable RustGL binaries.

Local command:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_renderer*.py' -v
```

Android CI runs that command, launcher regression tests, full/no-runtime APK builds
(including the C++ probe), and the existing native FPS counter regression. Exact final
run/result is recorded in the delivery reply, not guessed here.

| Impact | Open blocker | Verifiable closure evidence |
|---|---|---|
| P0: prevents approved native redistribution | Root license grant and exact component/toolchain audit | Rights-holder evidence and completed review with notice/source obligations |
| P0: prevents a working Vulkan renderer claim | Native GL/Vulkan operations and presentation remain incomplete | Native implementation, validation-layer/gameplay/resize/device-loss tests on phones |
| P1: prevents a working bundled GLES claim | No approved native payload imported; Android ELF/private-path/game launch not tested | Audited artifacts, importer log, APK hash/signature and real world rendering report |
| P1: prevents optimization/FPS claims | No attached phones or actual benchmark measurements | Raw attributed traces, repeated paired metrics, thermal/correctness/device reports |
| P1: resilience validation | Probe isolation/permissions and power-loss/full-disk/relaunch behavior untested on device | Fault-injection and timeout/process-death reports, no instance-data loss |

### Smallest next verifiable action

Install the integration-bearing CI APK over Aerix with its matching signing identity,
open **Settings → Renderer → Check system Vulkan**, and copy the report with the exact
APK hash/device/build. This diagnostic requires **no RustGL APK and no native RustGL
redistribution**. It produces the first phone capability evidence while the legal and
native-rendering blockers remain explicit. This environment cannot perform that physical
phone action on the user's behalf.

For full source changes relative to pre-surgery commit `2ddafaf`, use the accompanying
`rustgl-implementation.patch`; it contains real code/tool/workflow diffs, not pseudocode.
