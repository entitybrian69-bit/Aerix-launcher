# Aerix Launcher compatibility matrix

**Evidence status:** this is a source/runtime-archive audit, not a device-compatibility certification. Every cell that lacks direct evidence is explicitly marked **Not Tested**. Source support, packaged ABI presence, and successful Minecraft launch are separate claims.

## Android OS API levels

The manifest declares `minSdkVersion 23` (Android 6.0) and `targetSdkVersion 36`. This table does **not** imply that each release has been installed or tested.

| Android API | Manifest/source relationship | Install/runtime test | Physical-device or emulator test |
|---|---|---|---|
| API 23 | Declared minimum (`minSdkVersion 23`, Android 6.0) | Not Tested | Not Tested |
| API 24 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 25 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 26 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 27 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 28 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 29 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 30 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 31 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 32 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 33 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 34 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 35 | Inside declared API 23–36 range | Not Tested | Not Tested |
| API 36 | Declared target (`targetSdkVersion 36`) | Not Tested | Not Tested |

## Native ABI and runtime archive availability

| Android ABI | APK native-library verifier | Java 8/17 channel | Java 21 channel | Java 25 channel | Minecraft launch on this ABI |
|---|---|---|---|---|---|
| `arm64-v8a` | Present in both CI Debug APKs | Archive/channel available | Archive/channel available | Archive/channel available | Not Tested |
| `armeabi-v7a` | Present in both CI Debug APKs | Archive/channel available | Archive/channel available | Archive/channel available; **not proof of Minecraft 26.x ARM32 support** | Not Tested |
| `x86` | Present in both CI Debug APKs | Java 8/17 available | Filtered by launcher policy | Filtered by launcher policy; no Java 25 channel | Not Tested |
| `x86_64` | Present in both CI Debug APKs | Archive/channel available | Archive/channel available | Archive/channel available | Not Tested |

**APK ABI presence ≠ device launch test.** CI inspects native library entries in built APKs; it does not run Android, start a JVM on each ABI, or launch Minecraft. Runtime archives also do not guarantee that the matching Minecraft client, LWJGL, modloader, or native libraries exist for that ABI.

## Renderer/backend routes

| Renderer/backend | Source availability and prerequisites | Selection policy | Device/GPU/driver test |
|---|---|---|---|
| GL4ES | Bundled renderer route; Android manifest baseline is GLES 2.0. Smart Pick's existing version rule may select it for compatible older game releases. | Smart Pick eligible when the game-version rule allows it; also manually selectable. | Not Tested |
| LTW | Optional native library/AAR; requires a usable GLES 3 context and installed LTW library. | Smart Pick eligible only when GLES 3 is detected and LTW is present; otherwise not selected. | Not Tested |
| ANGLE | Existing system/external GLES provider code. System ANGLE is considered on supported Android releases; external ANGLE requires its plugin/library. It is not proof that ANGLE is packaged on a device. | Manual opt-in only; not selected by Smart Pick. | Not Tested |
| Vulkan (Zink / related routes) | Renderer implementations exist; Android Vulkan package-feature declarations are only a prerequisite. They do not prove Vulkan API version, required extensions/features, driver quality, or surface presentation. | **Manual-select only. Vulkan is never selected automatically.** | Not Tested |

Smart Pick currently uses only the GL4ES/LTW OpenGL routes. Renderer choices must remain capability-, ABI-, GLES-, installed-library-, and game-requirement-driven; manufacturer/model is not a compatibility signal. EGL entry-point fallback behavior is source-level only and not hardware-certified.

## Java runtime × Minecraft 26.x × ABI matrix

Mojang version metadata checked 2026-10-09 declares **Java 25** for 26.1.2, 26.2, and 26.3. Java 8 is included in the `full` APK; signed runtime channels are also available on demand. Java 17, 21, and 25 are signed on-demand channels. The Java 21/25 channels are filtered on 32-bit x86 by launcher policy. “Available” describes a runtime package/channel only; it does not certify the game/native ABI.

| JRE channel | Delivery | Minecraft version | ABI | Required Java major | Runtime channel availability | Metadata-level compatibility / caveat | Actual Android game launch |
|---|---|---|---|---|---|---|---|
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.1.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.1.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.1.2 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.1.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.2 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.3 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.3 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.3 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 8 | Full APK bundle (plus signed on-demand channel) | 26.3 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.1.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.1.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.1.2 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.1.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.2 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.3 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.3 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.3 | `x86` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 17 | Signed on-demand channel | 26.3 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.1.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.1.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.1.2 | `x86` | Java 25 required | Filtered by launcher policy | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.1.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.2 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.2 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.2 | `x86` | Java 25 required | Filtered by launcher policy | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.2 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.3 | `arm64-v8a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.3 | `armeabi-v7a` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.3 | `x86` | Java 25 required | Filtered by launcher policy | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 21 | Signed on-demand channel | 26.3 | `x86_64` | Java 25 required | Available | Not compatible: Mojang metadata requires Java 25 | Not Tested |
| Java 25 | Signed on-demand channel | 26.1.2 | `arm64-v8a` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |
| Java 25 | Signed on-demand channel | 26.1.2 | `armeabi-v7a` | Java 25 required | Available | Runtime exists; NOT proof of ARM32 Minecraft 26.x support | Not Tested |
| Java 25 | Signed on-demand channel | 26.1.2 | `x86` | Java 25 required | Not available: Java 25 is filtered on x86 | Not compatible: no Java 25 runtime channel for x86 | Not Tested |
| Java 25 | Signed on-demand channel | 26.1.2 | `x86_64` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |
| Java 25 | Signed on-demand channel | 26.2 | `arm64-v8a` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |
| Java 25 | Signed on-demand channel | 26.2 | `armeabi-v7a` | Java 25 required | Available | Runtime exists; NOT proof of ARM32 Minecraft 26.x support | Not Tested |
| Java 25 | Signed on-demand channel | 26.2 | `x86` | Java 25 required | Not available: Java 25 is filtered on x86 | Not compatible: no Java 25 runtime channel for x86 | Not Tested |
| Java 25 | Signed on-demand channel | 26.2 | `x86_64` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |
| Java 25 | Signed on-demand channel | 26.3 | `arm64-v8a` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |
| Java 25 | Signed on-demand channel | 26.3 | `armeabi-v7a` | Java 25 required | Available | Runtime exists; NOT proof of ARM32 Minecraft 26.x support | Not Tested |
| Java 25 | Signed on-demand channel | 26.3 | `x86` | Java 25 required | Not available: Java 25 is filtered on x86 | Not compatible: no Java 25 runtime channel for x86 | Not Tested |
| Java 25 | Signed on-demand channel | 26.3 | `x86_64` | Java 25 required | Available | Java major matches metadata; game/native ABI support Not Tested | Not Tested |

**ARM32 warning:** Java 25 being present in the runtime archive manifest for `armeabi-v7a` is **NOT proof** that Minecraft 26.x, its client jar, LWJGL, modloader, or native libraries support ARM32. Those rows remain **Not Tested**.

For Minecraft versions other than 26.1.2, 26.2, and 26.3, this cross-product matrix is **Not Tested** here; check that version's official `javaVersion` metadata and the native artifacts used by the selected profile.

## Form factors and orientation

The launcher activities declare `sensorLandscape`; this is a source-level orientation request, not proof of correct layout, input, window-inset, fold posture, or game behavior. Large-screen Android policies may override requested orientation.

| Form factor | Launcher orientation/layout source declaration | UI / inset / posture test | Game launch test |
|---|---|---|---|
| Phone in landscape | Landscape-first launcher shell; `sensorLandscape` declared | Not Tested | Not Tested |
| Tablet | Responsive rail/card layouts exist; `sensorLandscape` declared | Not Tested | Not Tested |
| Foldable | `sensorLandscape` declared; hinge/posture-specific adaptation not verified | Not Tested | Not Tested |
| Chromebook | `sensorLandscape` declared; keyboard/mouse paths exist in source | Not Tested | Not Tested |

## CI and test boundary

| Evidence | Status |
|---|---|
| Latest passing code CI | Run [38018016829](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829), code commit `2e7b3ac`: unit tests, wallpaper verifier, full/no-runtime Debug APK builds, and four-ABI APK verifier passed. |
| CI artifacts | [Full Debug artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829/artifacts/11657700995) and [no-runtime Debug artifact](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/38018016829/artifacts/11657246349). They contain APKs and MD5 files. |
| Release AAB | Not produced by this fork's Debug CI. CI produces Debug APKs, **not a release AAB**. |
| Mesa external artifact lookup | **Non-blocking — Not Found. Do not retry.** Optional Mesa assets/device availability are not verified by that lookup. |
| Physical devices | Not Tested |
| Android emulators | Not Tested |
| API 23 handset/device | Not Tested |
| API 36 device/emulator | Not Tested |
| Narzo 50 / Mali-G57 MC2 regression | Not Tested |
| Adreno Vulkan / GLES-only, Mali Vulkan / GLES-only, ARM32, x86, x86_64 and memory-tier combinations | Not Tested |

No universal Android or GPU compatibility claim is made. The source-level envelope is API 23–36 and the four ABIs listed above; successful launch remains unverified for every device/game/runtime/renderer combination.
