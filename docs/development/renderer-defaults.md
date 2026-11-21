# Renderer defaults by game version

## The rule

A game version picks its own renderer when the player has not:

| Version | Renderer |
|---|---|
| 1.17 and newer | **LTW** (`opengles3_ltw`) - the OpenGL 3 core renderer |
| below 1.17 | **Emesis** (`emesis`) - one library that loads a gl4es core for old versions |

`RendererDefaults` holds that rule once. Everything that needs it asks there, so the settings page,
the instance editor, the installer and the launch path cannot drift apart:

* `RendererDefaults.tagForVersion(versionId)` - answer from the version name alone.
* `RendererDefaults.tagForVersion(version, versionId)` - prefer the version's own release date; a
  snapshot such as `24w14a` has no readable number, but its json has a date, and 1.17 is the same
  boundary `GameRunner` already uses for the OpenGL 3 core context switch.
* `RendererDefaults.launchTagForVersion(...)` - the same answer resolved against what the build
  actually ships: the version's renderer when its libraries are packaged, otherwise the other bundled
  one, otherwise GL4ES.

Version-name parsing has to survive every id the launcher produces, because a wrong answer here is a
game that starts on a renderer its version cannot use:

| id | reads as |
|---|---|
| `1.16.5`, `1.21.4` | the whole id |
| `fabric-loader-0.16.9-1.21.4` | `1.21.4` - a loader's own `0.x` is skipped, never read as the game |
| `1.20.1-forge-47.2.0`, `quilt-loader-0.24.0-1.20.6` | the game number |
| `neoforge-21.1.77` | `21.1` - the game major is 21 in that scheme, still 1.17+ |
| `bta-7.3` | nothing, deliberately: Better Than Adventure numbers its own game `7.x` while being a
  1.7.3 Beta fork, so its number says nothing about the renderer |

An id with no readable number counts as "below 1.17": 1.17 is where the compatibility profile stops
being the safe answer, so an unknown version keeps the renderer that still speaks it.

## Where it is applied

* **New instances** (`PojavCreateInstanceBridge`, `FastClientInstaller`) are created with their
  version's renderer already written onto the instance, so the choice is visible in the editor and
  survives a later change to the global renderer.
* **Launch** (`GameRunner.launchGame`) replaces an *automatic* renderer that the version makes wrong.
  `RendererDefaults.appliesToAutoSelection` limits that to LTW, Emesis and GL4ES: a pick
  the player made - Zink, Proton, SFPEW, an external plugin, or an explicit per-instance renderer - is
  never touched. The older compatibility switches (Sodium on 1.17+, compat-context on LTW) stay exactly
  where they were, as the last word on a device that cannot start the version's renderer.
* **Automatic default** (`LauncherPreferences.defaultRendererId`) is Emesis.
* **Retired selections** (`RetiredRendererCleanup.isRetiredTag`) clears legacy `opengles_mobileglues`
  and `mobileglues` preferences or instance overrides so existing installs fall back cleanly.

## Tests

`tests/renderer_defaults_smoke.py` compiles and runs `RendererDefaults`
against Android stubs, checks 20+ real version ids on both sides of 1.17, and then asserts the wiring:
the launch path asks the rule and only for automatic picks, new instances are pinned, and the Global
Renderer picker stays removed from Settings. It runs in the Aerix Nightly test step next to
`instance_renderer_smoke.py`.
