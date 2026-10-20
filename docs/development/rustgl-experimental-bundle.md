# Experimental bundled RustGL GLES

The user explicitly approved proceeding with an experimental bundle based on upstream's
README MIT declaration despite its missing LICENSE file. This supersedes the previous
hold **for experimental test APKs only**. It is not legal clearance or permission from
an upstream rights-holder. No stable release is authorized by this acceptance.

## Build and installation

The Android workflow now builds `gl-compat` from pinned source
`whaltermc/rust-renderer@eb22559581cf400af29470205a9ebbff31e0db33` for arm64-v8a,
armeabi-v7a, x86 and x86_64 using Android API 26 and NDK 29. It does not download an
unverified plugin APK, copy upstream debug keys, or insert a placeholder entry.

The native job has read-only repository permissions, no launcher signing secrets,
and credential-free checkouts. Cargo verifies locked registry packages; the build
captures package SPDX declarations and license/copyright texts. Texts omitted from
a published crate are recovered from its exact recorded Git revision, not a moving
branch. Unsupported license expressions or missing evidence fail the build.

`scripts/build_rustgl_bundle.py` invokes the existing verified importer with an explicit
experimental policy. The manifest records `redistributionApproved: false` and
`experimentalUserAuthorized: true`; the runtime accepts either a cleared release or
this explicitly authorized experimental mode. The APK signature authenticates the
manifest and hashes. The SHA-256/ABI/native-dependency/16-KiB checks remain mandatory.

The launcher job depends on successful native build and downloads **that same workflow
run's** generated assets. Both final APKs are inspected by `verify_rustgl_apk.py`, which
requires matching checksums and all four ABI payloads. Build failure stops APK delivery;
it cannot quietly fall back to producing another empty integration-only APK.

Artifacts on the successful Android CI run:

- `app-release`: full launcher APK, including the experimental native renderer.
- `app-release-noruntime`: launcher without bundled Java runtime, also including RustGL.
- `apk-sha256`: SHA-256 sums of both APK files.
- `rustgl-build-evidence`: source/build log, exact package declarations/texts and
  generated experimental manifest approval/provenance.
- `rustgl-bundle-assets`: generated immutable payload files and manifest.

Install the **new experimental APK** over Aerix only with the matching signing identity;
do not uninstall or clear app data. Then open the instance's **Renderer** selector and
choose **RustGL (Experimental)**. No separately installed RustGL plugin APK is needed
for this build. Older stable/integration-only APKs do not gain assets retroactively.

Android 8/API26+, GLES3 and matching native ABI are required. The selector only registers
a bundle after real file installation/verification and compatibility checks. Unsupported
or quarantined bundles remain unavailable. Logs use `BundledRustGL` and
`AerixRendererPlugin` for discovery/init failures.

## Scope and safety

This is upstream's partial desktop-GL-to-GLES renderer, **not a completed Vulkan backend**.
RustGL backend policy forces GLES. Its upstream-compatible defaults advertise desktop GL
and disable LWJGL pointer checks; the experimental confirmation states that risk. There
is no runtime renderer JAR, Fabric mod or modification of the instance's `mods/` folder.

The existing bundle store retains atomic generation publication, checksums, approved
rollback and failed/interrupted-init quarantine. Device rendering, native crash behavior,
Android private-path loading, storage fault recovery and phone performance remain
unverified until actual device reports exist. A successful APK build is not gameplay or
FPS proof. The stable-release compliance checklist remains open; see
`rustgl-compliance.md` and `rustgl-benchmark.md`.

## Crash-review candidate: bundle integration 0.3.1

This is an **Aerix integration revision**, not a claim that upstream released RustGL
0.3.1. Native source uses `eb22559581cf400af29470205a9ebbff31e0db33`
(upstream 0.3.0) plus two hash-verified Aerix patches in `scripts/patches/`. The APK contains all four real native ABI payloads as before.

- Source review found a **use-after-free in `glGetStringi`**: it returned an extension
  string pointer from a local cloned vector that was dropped on return. LWJGL reads
  those pointers during capability initialization. The patch returns the persistent
  cache's bytes instead, with a pointer-ownership regression test. Host smoke checks
  every returned extension's spelling as well as non-nullness. This is a concrete
  memory-safety defect that can explain native startup failure; matching the phone's
  exact crash still requires a same-attempt native stack/device test.
- Real-library host testing of unpatched upstream reproduced an initialization GL
  error. Source review identified indexed compute limits queried with the scalar
  `glGetIntegerv`, a desktop dual-source limit incorrectly used as an indirect
  draw-count limit, an unconditional anisotropy query and NUL-inconsistent extension
  matching. The reviewed patch corrects these, adds an extension regression test,
  and is shipped verbatim with SHA256 evidence in the bundle notices. Clean upstream
  revision and patch hash are checked before building; no unrecorded dirty source.
  These are concrete startup faults, not proof of the user's exact SIGSEGV cause.
- Bundled RustGL now uses normal Android library loading (`prepareEgl` namespace
  bypass **false**), appropriate for its public system EGL/GLES dependencies. Other
  plugins retain their existing bypass policy. This removes an unnecessary custom
  graphics namespace; it is a suspected integration hazard, **not a confirmed
  explanation of the user's native crash**.
- Bundle version 0.3.1 creates a fresh immutable generation. A previous 0.3.0 init
  quarantine remains preserved but cannot suppress this revised integration. A
  new failure still quarantines the new generation; failures are not silently reset.
- Launcher `latestlog.txt` records the actual renderer tag, bundle version, resolved
  EGL path, namespace/GLES policy and native-provider load outcome. A successful
  `prepareEgl` means only the provider loaded, **not** a working context or gameplay.
- CI now runs serialized `gl-compat` tests and `scripts/smoke_rustgl.py` against the
  real host cdylib and Mesa surfaceless EGL: ES3 context, extension enumeration,
  RGBA clear/readback, desktop shader translation/compile/link, swap, rebind and
  teardown. These checks complement four-ABI Android builds, ELF/APK integrity
  checks and actual-payload Android discovery/old-quarantine regression tests.
  They do not reproduce a PowerVR Android driver, Android window lifecycle or Minecraft.

The supplied newer Minecraft 1.16.5 log ends during LWJGL initialization, without a
native stack. The earlier 1.20.1 native report includes LTW and an Android surface
fault; it is not proof of RustGL's current failure. Keep GLES-only policy, existing
NoChecks compatibility setting and licensing restrictions unchanged. Device
verification is still required; do not publish this as a stable crash fix or claim
FPS, full desktop GL, Vulkan or gameplay support based on host smoke results.

### Reproducible fix review and current validation

- Build source: `2e36872d329b6d4bd3aa5bb31bf76126d780513b`.
- CI: https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39301821983
- `rustgl-native-build.json` records this run's four native file hashes and both
  reviewed source-patch hashes. Native builds, core/compatibility unit tests and
  real-library host Mesa smoke passed; launcher regression tests also passed.
  Both APK builds, final four-ABI native-payload verification and uploads also passed.
  Device testing remains open. Download the full APK ZIP:
  https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39301821983/artifacts/12213235133
  (extract `app-release-full.apk`; install as an update, never uninstall/clear data).
  SHA-256 checksum artifact:
  https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39301821983/artifacts/12213070335
- `rustgl-crash-fix.patch` is the code/workflow/tooling diff from `4ab44af` to this
  candidate; `git apply --reverse --check docs/development/rustgl-crash-fix.patch`
  passes on the candidate. `rustgl-implementation.patch` is the earlier integration
  snapshot, not the new crash-fix diff.
- Locally: 15 Python tooling tests, Python syntax checks, diff whitespace checks
  and the native frame-counter regression passed. Android/Gradle/Rust testing was
  performed in CI, not claimed as local phone testing.

## Phone failure after 0.3.1: diagnostic integration 0.3.2

The user confirmed `latest (6).log` was produced **after** installing the patched
APK and selecting RustGL. The phone failure remains unresolved. That Minecraft
log stops at the LWJGL backend banner without a fatal stack, even though host
smoke and unit tests passed. Do not present 0.3.1 as a working phone fix.

0.3.2 is a **diagnostic candidate**, not a new claimed renderer fix:

- Bundled RustGL has opt-in, bounded entry/return checkpoints around EGL display,
  initialization/configuration, surface/context creation, make-current and first swap.
  No signal handler is replaced. No per-frame log writes are added.
- A startup-only native mirror appends the verified bundle identity and selected
  native renderer messages to **the same Minecraft `logs/latest.log`** the user
  can already share. It waits for Minecraft's LWJGL banner, never creates/truncates
  that log, rejects symlinks/non-regular files, caps output at 64 KiB, excludes
  arbitrary account/environment/mod stdout and stops after the first successful swap.
  Pipe capture is best-effort: an immediate native death or a driver using only
  Android logcat can still lose its final event. This is not full crash-stack capture.
- Fixed an independent out-of-bounds-read risk in the existing native log reader:
  it passed an unterminated `read()` buffer to `strstr`. This is a concrete logger
  bug, **not established as the phone crash's cause**.
- Only bundled RustGL enables this instrumentation. Other renderer loading policy,
  instances, mods, worlds and the stable release remain untouched.
- Local ASan/UBSan C regression checks cover split lines, stale-log preservation,
  sensitive-line exclusion, missing-file/symlink rejection, oversized lines and
  output bounds. Python tooling tests also check the new source patch hash.

A useful device report is now the same `latest.log`, looking for
`[Aerix renderer diagnostics] bundle=0.3.2` and `[Aerix startup]` checkpoints.
Absence of those lines is an inconclusive capture, not evidence of a particular
renderer, mod fault or successful startup. No uninstall or data clearing is required.

### Diagnostic APK validation

Source `b24cbc2b22283deff69807d078ae998ede0f0c1c`, CI
https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39354886594
completed successfully: four-ABI native builds, core/compat tests, actual EGL
smoke/checkpoint checks, launcher regressions, ASan/UBSan startup-mirror tests,
both APK builds and final native-payload checks. No device rendering result.

Full APK ZIP (extract `app-release-full.apk`, install as an update):
https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39354886594/artifacts/12234413964

`rustgl-startup-diagnostics-build.json` records native hashes, reviewed patch
hashes, validation and checksum-artifact link. `rustgl-startup-diagnostics.patch`
is the code/tooling diff from `3a4b46c`, including the additional partial-write
regression tested locally after the APK build; production code is identical to
that tested build. Older crash-fix build records remain historical, not claims
that 0.3.1 worked on the phone.

Minecraft appenders can rotate or overwrite mirrored tails during later logging;
this remains a best-effort startup diagnostic, not an exclusive log writer or
complete crash recorder. The primary Aerix launcher log remains unchanged.

## User-directed replacement: original plugin binary, bundle 0.3.3

The user explicitly requested removing our source-built renderer and using their
uploaded APK instead, after disclosure of its retained memory bug. This supersedes
the previous source-build/default diagnostic candidates **for experimental builds**.

Active Android CI now imports `third_party/rustgl-upload/native-libraries.zip`,
which contains only the four exact native libraries from that upload. It no longer
checks out or builds upstream RustGL or applies our patches. Plugin DEX/UI/resources
and debug signing identity are not installed or included. The selector stays
**RustGL (Experimental)**; its summary identifies the user-provided 0.3.0 payload
and Aerix packaging generation 0.3.3. No extra plugin APK is required.

All four bytes/size/SHA256 values are pinned independently. The importer validates
archive identity, ELF ABI/type, native dependencies and segment permissions, and
uses the original ABI-appropriate 16-KiB (64-bit)/4-KiB (32-bit) alignments. The
existing source-built importer is not weakened. Final APK verification checks the
native bytes against the uploaded hashes, requires only the four new generations
and rejects any leftover source-built payload under the bundle asset directory.

Normal system EGL namespace loading and the existing private verified bundle
store are retained. Old installed generations are not selected by the new manifest;
they are not deleted from under any running process. Worlds, instances, account
data, mods and other renderers are untouched. Source recipes remain historical/
manual tools, not active build inputs. The uploaded binary does not implement our
custom EGL trace checkpoints, so their environment flag is removed. The existing
bounded launcher-log mirror may still capture original renderer stderr, but lacks
that custom first-swap stop signal; its 64-KiB cap remains enforced.

Known risks are intentionally unchanged: ARM64/x86_64 glGetStringi use-after-free,
unverified source/signature provenance, missing APK license notices and unknown
prebuilt dependency coverage. Approval flags remain experimental only, with no
claimed license clearance, stable release, crash fix or phone-success proof.
The user request does not turn missing third-party notices into a license grant.
See `third_party/rustgl-upload/README.md` and `scripts/import_uploaded_rustgl.py`.

### Exact uploaded replacement APK: validated build

CI https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39362612150
completed successfully for source `4eb58a7cfccd9eb0f4a0611a300846f330127c7d`.
The importer, 20 Python tests, launcher regressions, native helpers, both APK
builds, and final checks against the four original uploaded-library hashes passed.
Verification also rejected leftover source-built payloads in the packaged bundle.
No dynamic renderer or device gameplay result is claimed for this binary.

Full APK ZIP:
https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39362612150/artifacts/12238112884

Extract `app-release-full.apk`, install as an update without uninstall/data clearing,
and select RustGL (Experimental). The summary identifies original plugin 0.3.0 /
Aerix bundle 0.3.3. `rustgl-upload-replacement-build.json` records exact native hashes,
build source, validation scope and checksum-artifact link. Stable release unchanged.
