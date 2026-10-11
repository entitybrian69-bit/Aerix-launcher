# Mirai → Aerix UI port

Source: `github.com/entitybrian69-bit/mirai-launcher-` (branch `Mirai-launcher`),
which is Zalith Launcher 2 (C) MovTery, GPL-3.0-or-later, rebranded as
`com.entitybrian69.mirailauncher`. This file records the interface facts we
need in order to rebuild the look inside Aerix. It is a specification, not
vendored source: the implementation below it is written fresh in XML/Java.

## Why it is a rebuild and not a copy

| | Mirai | Aerix |
|---|---|---|
| Language | Kotlin | Java |
| UI toolkit | Jetpack Compose (Material3) | XML layouts + AppCompat |
| DI / architecture | Hilt, ViewModels, Room | plain fragments, static prefs |
| Modules | CardGrid, ColorPicker, Guide, InputMap, LWJGL, LayerController, Terracotta | Pojav native trio (glfw, sdl, mojoexec) |
| Files | ~828 `.kt` | ~600 `.java` |

Compose cannot be pasted into a View based app, so every screen is rebuilt to
match while all Aerix feature code (instances, installers, runtimes, accounts,
control editor) stays where it is.

## Shell

Left vertical navigation rail, content to the right of it (`MainScreen.kt`:
`Row { MiraiNavigationRail(); content }`).

Rail destinations, in order:

1. Home
2. New Instance
3. Library
4. Discover
5. Wallpapers
6. Settings

Account avatar sits at the top of the rail; the rail collapses to icons only
and expands to icon + label.

## Metrics (`AerixMetrics`, all dp)

| Token | Value |
|---|---|
| `navigationRailWidth` | 68 |
| `expandedNavigationRailWidth` | 78 |
| `shellHeaderHeight` | 60 (compact 48) |
| `shellActionHeight` | 40 (compact 36) |
| `pageHeaderHeight` | 44 |
| `pillTabHeight` | 38 |
| rail item icon | 24 (compact 20) |
| account avatar | 38 (compact 32) |
| glass elevation: surface / floating / rail / dialog | 1 / 3 / 2 / 3 |
| glass elevation: selected / subtle | 0 / 0 |

## Spacing (`AerixSpacing`, all dp)

0, 1 hairline, 2 xxs, 3 tiny, 4 xs, 5 xsPlus, 6 smCompact, 7 smTight, 8 sm,
9 smNarrow, 10 smPlus, 11 mdTight, 12 md, 14 mdPlus, 16 lg, 18 lgPlus, 20 xl,
22 xlPlus, 24 xxl, 32 xxxl.

## Palettes (`ColorThemeType`)

DYNAMIC, EMBERMIRE, VELVET_ROSE, MISTWAVE, GLACIER, VERDANTFIELD, URBAN_ASH,
VERDANT_DAWN, CUSTOM — nine swatches per Material role, light and dark.

Extracted light roles (order: embermire, velvetRose, mistwave, glacier,
verdantField, urbanAsh, verdantDawn):

```
primary               A63A17 723D57 426469 006684 676014 5E5E5F 004814
primaryContainer      FE7A52 9B607C B1D5DB 4CAFD6 EFE58B AEAEAE 276E31
onPrimaryContainer    2F0700 FFFFFF 1E4146 001B26 1F1C00 222324 FFFFFF
secondary             894F3D 725762 546163 426372 635F41 5F5E5E 4A6548
secondaryContainer    FFBBA7 FFDAE8 D8E6E9 CAECFE EAE3BE E8E6E5 CFEFCA
tertiary              6E5E00 773F27 655975 794C91 406653 615D5F 003C77
tertiaryContainer     BAA015 A26247 D8C9E9 C390DB C1ECD4 B2ADAF 2960A6
background            FFF8F6 FFF8F8 FAF9F9 F6FAFD FEF9EB FCF8F8 F7FBF2
onBackground          241916 1F1A1C 1A1C1C 181C1F 1D1C14 1C1B1B 181D18
surface               FFF8F6 FFF8F8 FAF9F9 F6FAFD FEF9EB FCF8F8 F7FBF2
surfaceVariant        FCDBD3 F1DEE4 DDE4E5 DAE4EA E7E3D0 E0E3E3 DCE5D7
onSurfaceVariant      58423B 504348 414849 3E484E 49473A 444748 40493E
outline               8B716A 827378 71787A 6E797E 7A7768 747878 707A6D
outlineVariant        DFC0B7 D4C2C8 C1C8C9 BEC8CE CBC7B5 C4C7C7 C0C9BB
surfaceContainerLow   FFF1ED FCF1F3 F4F3F3 F0F4F7 F8F3E6 F7F3F2 F1F5EC
surfaceContainer      FFE9E4 F7EBED EEEEEE EBEEF1 F2EEE0 F1EDEC ECEFE6
surfaceContainerHigh  FAE3DD F1E5E8 E8E8E8 E5E9EC EDE8DA EBE7E7 E6E9E1
surfaceContainerHigh+ F4DED8 EBE0E2 E3E2E2 DFE3E6 E7E2D5 E5E2E1 E0E4DB
inverseSurface        3B2D2A 352F31 2F3131 2D3134 323128 313030 2D322C
```

Dark roles (`primaryDark`, `onPrimaryDark`, …) are in
`ui/theme/Color.kt` and still need to be captured — the launcher runs dark.

## Background

`CaveBackground.kt` draws the animated backdrop behind the whole shell;
`WallpaperPage.kt` lets the user pick a wallpaper, so the background is
user-settable and animated.

## Screens to rebuild (ui/screens/content)

| Mirai screen | Aerix destination |
|---|---|
| `LauncherScreen` (home) | main menu: instance, launch, quick actions |
| `VersionsManageScreen` | version / instance list |
| `VersionSettingsScreen` | instance editor |
| `DownloadScreen` (+ `download/`) | mod + modpack search, installers |
| `AccountManageScreen` | account spinner + login methods |
| `SettingsScreen` (+ `settings/`) | the six preference screens |
| `FileSelectorScreen` | file selector |
| `LogViewScreen` | log viewer / share logs |
| `WebViewScreen` | wiki, news |
| `LicenseScreen` | about / licences |
| `VersionExportScreen` | export instance |
| `assetinfo/` | resource / asset info |
| `control_editor/` | control editor (own activity in Mirai too) |

## Type scale

System sans-serif, default Material3 sizes, weights tightened by
`AppTypography` in `ui/theme/Type.kt`. No bundled font files.
