# RustGL native distribution review

Review target: `whaltermc/rust-renderer`, commit
`eb22559581cf400af29470205a9ebbff31e0db33`. This is an engineering release
assessment, not a legal opinion or a replacement for a rights-holder's grant.

## Findings and compliance matrix

| Component / scope | License evidence at reviewed revision | Redistribute native binary now? | Required release material / alternative |
|---|---|---|---|
| RustGL workspace: renderer-core, backend, gl-compat, format-translate, shader-translate, shader-manager | README names MIT; no root LICENSE in reviewed tree; workspace Cargo package manifests have no license field. GitHub LICENSE endpoint returned 404 during this implementation. | **Not approved by this review.** Missing full grant/copyright evidence; cannot resolve by adding our own MIT file. | Obtain rights-holder's license text/copyright coverage for this exact revision, or choose a revision with clear evidence and re-review it. Compliant current alternative: ship Aerix's own integration with no RustGL payload, retaining existing renderers. |
| Locked Rust dependencies, including libloading, Naga, android_logger/log, regex, once_cell, thiserror and transitives | `rustgl-cargo-inventory.json`: **89 entries**, package version/source/checksum recorded from upstream Cargo.lock; each conclusion currently UNREVIEWED. This is a conservative workspace-wide inventory, not a linked-object SBOM. | **No blanket approval.** Familiar crate names do not establish the license of the exact package bytes. | Obtain exact packages, verify Cargo checksums, extract package-specific SPDX/license and copyright texts, resolve dual-license choice, and retain evidence. Importer rejects every UNREVIEWED component. Prune to a verified linked SBOM only through a separate reviewed tool revision, not by deleting inconvenient entries. |
| Optional git Naga / wgpu revision `14685942a11755e4581c40729dd44591d1d73680` (backend `spirv` feature) | Pinned dependency in Cargo.toml; optional feature, distinct from registry Naga used by shader-translate | Not approved; inclusion is build-feature-dependent | Record exact feature set and revision, license evidence and notices. Do not infer its inclusion from presence in Cargo.lock. |
| Rust standard library, Rust compiler support libraries and NDK compiler runtimes | Not covered by the Cargo dependency list alone | Conditional on exact toolchain audit | Record rustc/NDK versions, sysroot and compiler-runtime notices and applicable LLVM exception terms. Static runtime/license obligations must be captured separately. Include any shipped libc++_shared.so in native artifact approval; never silently rely on a different APK's copy. |
| glslang and apitrace submodules, fixtures / tool-only assets | Listed upstream submodules; not fetched/copied into Aerix; reviewed Cargo GL bridge does not reference glslang | **Not approved for copying by this review; not needed for current integration** | Exclude trace/video/fixture/debug-key/tool payloads from runtime. If a future build links or redistributes these, audit that precise revision and dependency tree first. |
| Android system libvulkan, EGL/GLES, libc/libm/libdl, Android logging / vendor GPU driver | Loaded from Android's system linker namespace, not copied | No redistribution needed for system APIs | Depend on device-provided libraries. Do not copy vendor driver blobs into a bundle. A capability report for a system driver does not validate Turnip or another custom driver. |
| Existing Aerix + adapted plugin bridge | Repository root LGPL-3.0; bridge file has GPL-3.0-or-later notice from Zalith-derived code | Existing launcher obligations remain; root license alone does not describe every file | Preserve applicable copyright/license texts, provide corresponding source/build scripts for distributed covered code, assess combined-work GPL requirements before shipping. This change does not certify all existing launcher dependencies. |
| New Aerix bundle store, probe and tooling | Authored in this checkout under applicable project/file terms | Source can accompany launcher subject to project obligations | Keep source and build scripts available with releases. These files grant no rights in RustGL. |
| New renderer JARs / loader mods | **None.** `gl-compat` builds `librust_gl.so`; plugin APK/AAR wrappers are packaging, not a Fabric mod or runtime renderer JAR. | No JAR to ship | Leave instance `mods/` and game classpath unchanged. Do not invent a renderer mod or ship the plugin's debug signing key. |

## Import approval contract

`scripts/prepare_rustgl_bundle.py` is deliberately unusable without an explicit
review. Its approval JSON requires:

- `redistributionApproved: true`, a nonempty `reviewedBy`, the exact `sourceRevision`;
- one `components` entry per inventory `(name, version)`, each with a resolved
  `licenseConclusion` and `evidence: [{"file": "local-license.txt", "sha256": "..."}]`;
- `artifacts: [{"abi": "arm64-v8a", "name": "librust_gl.so", "bytes": ..., "sha256": "..."}, ...]`,
  including every native dependency for **all four** supported ABIs.

The automatic importer's narrow license set is MIT, Apache-2.0, ISC,
BSD-2-Clause, BSD-3-Clause, Zlib and the specified MIT/Apache dual-license forms.
This is a distribution policy, not a claim that other licenses prohibit distribution.
Copyleft/unknown/custom terms require a separately reviewed distribution method.
For LGPL libraries, assess conveying corresponding source, modification notices,
reverse-engineering permissions for debugging modifications and relinking/replacement
requirements; static linking commonly requires suitable relinkable objects or another
compliant mechanism. GPL components can require source for the covered combined work
and installation information where applicable. Permissive terms still require their
actual copyright/license notices; Apache terms also require applicable NOTICE content
and modification marking. Do not reduce compliance to an SPDX label.

Evidence files are copied into `NOTICE.txt`. Reviewers must include all applicable
copyright, license, NOTICE, toolchain and generated-code material; the importer cannot
prove ownership or completeness from a checkbox or a license filename. Maintain the
signed-off source archive/SBOM/toolchain audit alongside the release, outside game mods.
No approval file or RustGL binary has been fabricated or imported in this change.

## Release checklist

- [ ] Rights-holder grant verified for the exact RustGL revision; all contributors and included code covered.
- [ ] 89-entry inventory resolved or replaced with an independently verified, documented linked SBOM.
- [ ] Toolchain/runtime/static-generated-code audit complete; signed-off notices and corresponding source attached as required.
- [ ] Reproducible build log captures source, Cargo.lock, flags/features, Rust/NDK/linker versions and outputs.
- [ ] Native DT_NEEDED closure checked; all ABIs, 16 KiB pages, no writable-executable segments verified.
- [ ] Approved artifact hashes match the bytes imported into the APK; APK verified with `apksigner verify --verbose --print-certs`.
- [ ] APK update uses the existing signing identity. Do not bypass Android's package-signature verification.
- [ ] On-device initialization, known-world rendering, shader validation, resize/resume, process-death recovery and rollback evidence attached.
- [ ] Phone benchmarks and thermal reports attached before any performance claim.
- [ ] Native Vulkan GL state, resources, shaders, synchronization, swapchain and device-loss handling implemented and validated before exposing Vulkan.

Until these checks pass, do not publish a RustGL-bearing APK. A build containing
only the new Aerix integration/probe has no RustGL native redistribution in it.
