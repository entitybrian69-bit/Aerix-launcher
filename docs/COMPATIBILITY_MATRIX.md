# Aerix Launcher Android compatibility matrix

**Status:** source audit and implementation guardrails, not a device-compatibility certification. This document reflects the checked-out launcher source and the published runtime archive manifest as inspected on 2026-10-09. It does not claim that every listed combination has launched Minecraft successfully.

## Android platform and native ABI declarations

| Area | Source-level declaration | Verification status |
| --- | --- | --- |
| Minimum Android | API 23 (`minSdkVersion` in `app_pojavlauncher/build.gradle`) | Declared; no physical API 23 device tested in this environment |
| Target Android | API 36 (`targetSdkVersion`) | Declared; no API 36 device/emulator test run |
| Native ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` appear in `jniLibs` and the shipped renderer AARs; native CMake targets are not restricted by an `abiFilters` list | Source assets inspected; APK split contents/build not verified |
| Baseline graphics API | The manifest requests OpenGL ES 2.0; GL4ES is the GLES 2 route. LTW and several newer wrappers require GLES 3 or later | Source inspected; hardware behavior is unverified |
| Vulkan | Android Vulkan hardware-level and hardware-version package features are used as a prerequisite for Vulkan renderer visibility. Those flags do not prove a driver's backend-specific extension/feature set | Source inspected; no Vulkan device query or physical Vulkan test was run |

The application targets Android 36; this is not a promise that all future Android releases or every device configuration behave identically. Android 16+ may also apply large-screen orientation policies that differ from handset behavior.

## Runtime and ABI matrix

The launcher selects a Java major from each installed Minecraft version's `javaVersion` metadata and installs a compatible runtime. The checkout lists Java 8, 17, 21, and 25 runtime channels. Java 8 is assembled into the `full` build; newer runtime archives are downloaded on demand and verified against the signed runtime manifest.

The published runtime manifest tree is available at [MojoLauncher/android-openjdk-build-multiarch runtime downloads](https://github.com/MojoLauncher/jre-download). Its archive entries show:

| Process ABI | Java 8 / 17 (`jre-new`) | Java 21 (`jre-21`) | Java 25 (`jre-25`) | Launcher caveat |
| --- | --- | --- | --- | --- |
| `arm64-v8a` | Archive present | Archive present | Archive present | Native game libraries and renderer support still vary by Minecraft version/modloader |
| `armeabi-v7a` | Archive present | Archive present | Archive present | Java runtime availability does not prove the game's LWJGL/native artifacts support 32-bit ARM |
| `x86` | Archive present | Archive present, but launcher currently filters Java 21+ for 32-bit x86 | No archive present | Treat Java 8/17 as the current launcher-supported runtime set |
| `x86_64` | Archive present | Archive present | Archive present | Game/modloader native artifacts still have to provide x86_64 variants |

The runtime archive manifest is evidence of available runtime packages, not evidence that Minecraft itself runs on every ABI. In particular, ARM32 Java 25 availability is **not** a claim that any Minecraft 26.x release has ARM32 game/native support.

## Renderer routes and constraints

| Route | Source-level constraint | Selection policy |
| --- | --- | --- |
| GL4ES | Bundled for all four declared ABIs; intended for GLES 2 and older Minecraft compatibility contexts | Smart Pick uses this only for versions identified by the existing release-date compatibility rule |
| LTW | Available only where the LTW native library is installed and GLES 3 is detected | Smart Pick uses this for newer versions when the device and installed library meet those checks |
| NG-GL4ES / MobileGlues / SFPEW | Present as renderer implementations and packaged dependencies; several require GLES 3 | Kept as manual choices when the device package reports them compatible. Game-version support must be confirmed for the specific release/modpack |
| Zink / Mesa / Freedreno | Some variants are optional or external and require their matching native assets; Zink uses Vulkan | Smart Pick does not select a Vulkan-only renderer. Android feature flags are only a prerequisite, not backend-extension certification |
| Renderer EGL entry points | SDL needs a complete set of core EGL calls, often obtained through `eglGetProcAddress` | Renderer setup now checks the selected library's usable EGL entry points and retries the GL4ES fallback before reporting failure |

The launch pipeline reads the official Mojang version JSON and uses its declared Java major version. The official manifest and per-version JSON were checked on 2026-10-09: **26.1.2, 26.2, and 26.3 each declare Java 25**. The Java requirement is therefore known; Android ABI and successful game launch are separate questions.

Mojang's official 26.2 release notes describe Vulkan as experimental, with OpenGL as a fallback, and list Vulkan 1.2 plus dynamic-rendering and push-descriptor support as the then-current Vulkan requirement. Mojang's Java Edition system-requirements page, updated 2026-07-21, now uses Vulkan 1.3-capable graphics as its minimum PC target; it also notes that below-minimum hardware may still launch, without guaranteed performance or visuals. Those PC requirements do **not** certify an Android translation layer. The 26.3 per-version launcher JSON supplies Java/runtime metadata, not an Android renderer compatibility declaration.

The launcher currently does not query the loaded Vulkan driver's API version, physical-device features/extensions, or presentation-surface support. Its package feature flags are only a coarse prerequisite, and Smart Pick currently chooses only GL4ES/LTW OpenGL routes; it does not auto-select Zink/Vulkan. Consequently, Minecraft 26.1.2/26.2/26.3 metadata is available and the required Java major is known, but those game versions' Android renderer/ABI combinations remain **unverified**. The presence of a Java 25 archive for ARM32 is not evidence that a 26.x client or its native dependencies support ARM32.

Sources checked: [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), [26.1.2 metadata](https://piston-meta.mojang.com/v1/packages/78941de799d2675be5bddca699b245d7cbd567ae/26.1.2.json), [26.2 metadata](https://piston-meta.mojang.com/v1/packages/d367f3dfbc0b3e14688df2311359deb609b234e3/26.2.json), [26.3 metadata](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json), [Mojang's 26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2), and [Java Edition system requirements](https://www.minecraft.net/en-us/article/minecraft-java-edition-system-requirements).

## Test coverage and claims

- **CI tests/builds:** GitHub Actions run [37955759081](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081) succeeded on commit `98cbad6` (`arena/fb5cd066-aerix-launcher`). It ran `:app_pojavlauncher:testFullDebugUnitTest`, assembled both `fullDebug` and `noruntimeDebug`, and ran the APK ABI verifier against each output.
- **Debug APK artifacts:** [full-debug ZIP](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081/artifacts/11627633705) (`aerix-launcher-1.0.0-full-debug`, 121,385,695 bytes) and [no-runtime-debug ZIP](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/37955759081/artifacts/11627608769) (`aerix-launcher-1.0.0-noruntime-debug`, 92,288,597 bytes). Both ZIPs contain the APK and MD5 file.
- **Optional Mesa artifact:** the CI lookup for an external Mesa AAR returned Not Found and was configured as non-blocking. The CI verification therefore confirms the required libraries in those two built APKs, but does not certify optional/external Mesa renderer availability on a device.
- **Physical devices tested:** none are attached/available to this session.
- **Emulators tested:** none.
- **Current host:** x86_64 Linux only. Host architecture is not Android ABI/GPU coverage.
- **Narzo 50 / Mali-G57 MC2:** retained as a required regression case; not physically tested in this session.
- **Adreno/Vulkan, Mali/Vulkan, Mali GLES-only, ARM32, x86/x86_64, memory tiers, API 23, and API 36:** all require device/emulator testing before making a runtime compatibility claim.

Aerix does not claim to work on all Android devices. The source-level envelope is Android API 23+, with the four listed native ABIs and renderer/runtime availability constrained by the actual device, game metadata, and native libraries. Successful game launch on a specific combination remains unverified until that combination is tested.
