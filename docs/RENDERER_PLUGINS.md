# Renderer plugins

Aerix can use renderer plugin APKs installed by the user, the same way Fold Craft
Launcher (FCL) and Zalith Launcher 2 do. A plugin is a normal APK that ships its
own native renderer libraries; Aerix picks it up, lists it as a renderer, and
hands the launch environment to it.

## Accepted plugin formats

A plugin APK declares itself with a boolean `<meta-data>` entry inside the
`<application>` element. Any of these markers is read:

| Marker | Origin |
|---|---|
| `fclPlugin` | Fold Craft Launcher renderer plugins |
| `zalithRendererPlugin` | Zalith Launcher 2 renderer plugins |
| `pojavRendererPlugin` | PojavLauncher-style renderer plugins |
| `aerixPlugin` | Aerix-native renderer plugins |
| `emesisPlugin` | Emesis renderer plugins |

Packages inside the `com.emesis` space are treated as `emesisPlugin` even when
the marker is missing. A plugin that sets no marker at all is ignored, so
unrelated apps never show up in the renderer list.

## Metadata a plugin provides

```xml
<application>
    <meta-data android:name="fclPlugin" android:value="true" />

    <!-- <rendererId>:<glLibrary>:<eglLibrary> -->
    <meta-data android:name="renderer" android:value="opengles2_5:libgl.so:libEGL_plugin.so" />

    <meta-data android:name="des" android:value="My Renderer" />
    <meta-data android:name="minMCVer" android:value="1.17" />
    <meta-data android:name="maxMCVer" android:value="1.21.5" />
    <meta-data android:name="pojavEnv"
               android:value="MESA_LOADER_DRIVER_OVERRIDE=zink:DLOPEN=libvulkan.so,libzink.so" />
</application>
```

* `renderer` — the renderer id, the GL library and the EGL entry library. A path
  starting with `/` is resolved inside the plugin's native library directory.
* `des` — display name. Without it Aerix uses the app label.
* `pojavEnv` — `KEY=VALUE` pairs separated by `:`. `POJAV_RENDERER` overrides the
  renderer id, `DLOPEN` lists extra libraries to load before the renderer
  starts, and `LIB_MESA_NAME` / `MESA_LIBRARY` values are resolved against the
  plugin's native library directory.
* `minMCVer` / `maxMCVer` — informative version range shown next to the renderer.

Emesis-style plugins may omit `renderer` entirely; Aerix then assumes the id
`emesis` and the libraries `libemesis.so`.

## How it works

1. `PojavApplication` starts a scan on a background thread at app start and
   rescans whenever any package is installed, replaced or removed, so a freshly
   installed plugin appears without restarting the launcher.
2. `PojavPluginBridge` walks the installed packages, parses every APK that
   carries a marker, and registers each one in PojavLauncher's `RendererCache`
   with the tag `plugin:<packageName>`.
3. Because the tag lives in `RendererCache`, the plugin shows up in every
   renderer list: Settings, the instance editor, the classic preference screens
   and the Mirai-style settings sheet.
4. At launch, `GameRenderer` is built from the instance's renderer tag, then
   `PojavPluginBridge.applyLaunchOverride()` swaps in the plugin's `RenderSpec`.
   The plugin's environment variables are applied, its `DLOPEN` libraries are
   loaded, Vulkan is preloaded for Vulkan/Mesa-style plugins, and
   `MojoExec.prepareEgl()` is called with the plugin's EGL library.

If setup fails, Pojav's normal fallback (GL4ES) takes over, so a broken plugin
cannot prevent the game from starting.

## Package visibility

Android 11 and newer hide other apps from `getInstalledApplications()` unless the
launcher can see them. Aerix declares
`android.permission.QUERY_ALL_PACKAGES` in its manifest for exactly that reason,
plus a `<queries>` fallback for plugin APKs that carry a launcher activity. Aerix
is distributed as an APK from GitHub Releases, so the Play Store policy that
restricts this permission for store apps does not apply. A Play build would need
the permission removed and the plugin list limited to the packages named in
`<queries>`.

## Managing installed plugins

Plugins are ordinary apps. Uninstalling one removes its renderer from the list
on the next scan, and any instance that pointed at it falls back to GL4ES.
