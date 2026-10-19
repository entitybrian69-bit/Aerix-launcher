# RustGL integration: experimental external-plugin adapter

## What this change does

Aerix reads RendererPlugin-v2 JSON metadata (`fclPlugin_V2`) from installed
plugins, resolves `**|` native path markers, applies supported environment defaults,
checks extracted ELF architecture against the current process/GLES 3/Android 8+, and registers the real plugin
through the existing renderer cache and launch override. Legacy plugins remain
supported. A blank Mesa-driver override no longer incorrectly selects Vulkan EGL
setup. Desktop-EGL selection for legacy Mesa plugins is retained separately from
Vulkan preloading. V2 plugins receive their resolved SDL_EGL_LIBRARY path. The application explicitly declares package visibility for RustGL.

RustGL is a separate installed APK, **not bundled into Aerix** by this change. No
fake renderer entry is created when its native library is absent. Install only a
trusted build of `dev.rustrenderer.plugin`, then restart Aerix to rescan installed
plugins and select RustGL (Experimental) in renderer settings. This is a test path,
not a confirmed working Android gameplay configuration.

Source reviewed: https://github.com/whaltermc/rust-renderer
Revision: eb22559581cf400af29470205a9ebbff31e0db33
Schema checked against ZalithLauncher/RendererPlugin-v2 1.0.1. No upstream native
renderer source, fixture archives, binaries or debug signing keys are copied.

## Supported V2 subset

Single renderer object; NormalEnv, enabled SelectableEnv defaults, non-null
CustomizableEnv defaults and enabled ToggleableEnv values. Paths must name actual
packaged native libraries; bare system `libEGL.so` is accepted for EGL. Plugins
requiring dlopen preloads are explicitly rejected, not silently launched without
their dependencies. Editable per-plugin environment controls are not implemented.
RustGL's reviewed configuration has no preload dependencies and uses GLES.

## Outstanding work — not completed or claimed

1. Licensing: upstream README says MIT, but the reviewed tree has no LICENSE file
   and GitHub reports no detected license. Obtain a clear grant/license text and
   audit dependencies before vendoring or redistributing native renderer builds.
2. Native completeness: DirectVK contains unsupported/empty operations; the main
   bridge uses GLES for game drawing. This adapter does not implement missing GL,
   shader features, dynamic Vulkan shaders or swapchain presentation.
3. Upstream always advertises GL 4.5 despite partial coverage. Its default
   JAVA_TOOL_OPTIONS disables LWJGL checks; defaults are preserved for compatibility,
   not endorsed as safe/universally correct. Test with checks enabled as well.
4. Performance: no phone benchmark has been run. No "maximum optimization", FPS
   improvement or universal compatibility is established. Do not change defaults
   or enable Vulkan/hybrid labels on the strength of the metadata alone.
5. Device validation: test launch/menu/world rendering, resize/resume, shader
   compilation and errors on Adreno/Mali hardware; compare equal settings against
   existing renderers, logging frame times, memory and sustained thermal behavior.
6. Full vendoring/build/signing/provenance workflow and native hot-path optimization
   remain separate follow-up work. No public release is made for this adapter.
