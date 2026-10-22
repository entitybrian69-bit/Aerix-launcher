# Release notices and team projects

## Launcher updates

`LauncherUpdateNoticeHost` checks GitHub's latest stable release while the launcher
activity is resumed: at startup/resume (15-minute process-level throttle), then
every 30 minutes. This is polling, not a server push notification; it never runs
in the game activity. Errors, drafts, prereleases, missing edition APKs, equal
versions and older versions do not automatically open a dialog.

Cancel, outside/back dismissal and accepting Download acknowledge that release's
numeric version in `aerix_update_notices` shared preferences. Acknowledgement is
monotonic: a release rollback cannot make an old version prompt again. Displaying
an offer alone does not acknowledge it. Accepting a download keeps the current
window open for progress/installation, but suppresses future automatic offers of
that release, even if the download fails or is cancelled. Settings retains manual
checking, retrying, downloading and installing. Existing checksum, size, package
and signing-certificate validation remain in use. No silent installation.

What's new displays the actual GitHub release notes, not a fabricated summary.

## Other releases

`teamReleases` in `OtherReleasesScreen.kt` is an explicitly curated catalogue,
not an automatically imported feed. Its reusable cards support all `AssetType`
values; add entries only for supplied projects and verify descriptions and
compatibility against their original pages. Do not fabricate future releases,
benchmarks, download counts or a current "latest file".

Initial source, checked 2026-10-19:
https://www.curseforge.com/minecraft/mc-mods/aetherium-fabric

Aetherium (Fabric), Team_Aerix: client-side Fabric, no Fabric API requirement;
separate exact-version files; incompatible with Sodium, Embeddium, Rubidium and
OptiFine. Feature/performance descriptions are attributed project claims, not
launcher benchmarks. The project is LGPL-3.0-only; the icon remains the project's
remote CurseForge logo, not a copied/generated substitute. The API/mirror also
verified its numeric project ID **1749327**, slug and 256px logo URL.

Download looks up the verified project ID through the existing authenticated
API/mirror path (slug lookup is available for future entries without a verified ID). `CurseForgeProjectLink` validates exact slug, Minecraft game ID,
content class and positive numeric project ID. It never selects the first result
or a title lookalike, never switches to the NeoForge edition, and never invents
a numeric ID. Failed/offline resolution leaves a retry message on the card.

The exact `AssetProject` is sent to Discover and rendered by its existing
`AssetDetailContent`, retaining live versions, target-instance selection,
dependency checks and installation. Detail Back returns to the team catalogue;
choosing another Discover category returns to normal browsing.

## Validation still requiring an Android device

- Startup with newer/equal/older release, Cancel/back, relaunch, newer next release.
- Download acceptance, progress/cancellation, background return and installer.
- Manual update checking alongside the automatic offer, rotation and app resume.
- Narrow/landscape layouts, font scaling, dark/light themes and rail scrolling.
- Live CurseForge project resolution, Fabric detail/files and instance installation.
