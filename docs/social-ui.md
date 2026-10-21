# Social UI implementation and reference map

## Theme and boundaries

The user's final colour direction takes precedence over the purple values in the supplied plan:
Social uses blue glass, silver-blue ribbon artwork, cool borders and blue actions. In-game panels
use dark translucent surfaces over the running game. The theme is independent of launcher
wallpaper/accent preferences. No new dependencies or navigation framework were added.

`AerixLauncherScreen` selects either the complete launcher chrome or `SocialShell` at its root.
The launcher rail is not reused. Social navigation and history do not enter `AerixNav`.
The short entry/exit animation temporarily draws both outgoing/incoming roots; when it finishes,
the outgoing launcher composition is disposed. The shell has its own background, rail and back
handler. Back from a root returns to Play, while nested screens pop locally.

## Reference mapping

Numbers refer to the image manifest in the supplied plan, rather than upload ordering.

| Reference | Implementation |
|---|---|
| 1 – dashboard widget | `SocialWidgetCard`, included once in each responsive Home layout |
| 2 – Google sign-in | `SocialAuthScreen` |
| 3 – name, skin, cape setup | `SocialAppearanceScreen` / `ProfileContents` |
| 4 – friends, conversations, party | `SocialDashboardScreen`, `SocialFriendScreen`, `SocialChatScreen`, `SocialPartyScreen` |
| 5/26 – privacy and contact dropdowns | `SocialPrivacyScreen`, `SocialChoice` |
| 6 – cape selection | `SocialCapePicker` |
| 7 – skin selection | `SocialSkinPicker`, shared `AerixSkinPreview3D` |
| 8 – blocked accounts and safety | `SocialSafetyScreen` |
| 9 – notification preferences | `SocialNotificationsScreen` |
| 10 – in-game chat | `SocialCompactPanel`, `CompactChat` |
| 11 – voice party | `SocialCompactPanel`, Party mode |
| 12/16 – microphone intro/denial | `SocialVoiceScreen` |
| 13/17 – connection error/empty list | `SocialEmptyPanel`, preview from the dashboard overflow menu |
| 14 – signed-out in-game prompt | `SocialCompactPanel`, signed-out state |
| 15 – in-game entry/settings | `GameMenuScreen`; Settings opens the existing native drawer |
| 18/27 – expired/active invitation | `SocialCompactPanel`, Invite mode |
| 19 – reconnecting voice | Party mode, local reconnect preview and retry |
| 20 – request sent | `SocialAddFriendDialog` |
| 21 – block/report | `SocialBlockDialog`, separate three-step `SocialReportScreen` |
| 22 – sign-out confirmation | `SocialSignOutDialog` |
| 23 – leave-game confirmation | `SocialLeaveGameDialog` |
| 24/25 – deletion confirmation | `SocialDeletionScreen` |
| Additional instance mismatch image | Invite mode, explicit mismatch preview; never switches a real game |

## Local-only behaviour

Google sign-in is explicitly simulated. No OAuth clients, passwords or Social network endpoints
are used. “Not now” returns to the launcher. Initial sign-in requires completing appearance
before the dashboard. Subsequent sign-ins restore the existing local profile.

`SocialStore` owns demo identities, friendships, outgoing/incoming requests, messages, reactions,
party membership, safety reports, privacy and notification settings. Its private SharedPreferences
are loaded separately in the launcher and game processes; data survives the launcher process
being terminated by the existing game-launch path. This is not a multi-device synchronisation
service. Human-vector avatars are used in launcher Social; the compact game panel uses a
Minecraft-style avatar. Imported cosmetics are private Social files, not Minecraft account data.

Blocking removes the friend, their chat and party membership. Unblocking never restores the
friendship. Reporting is a separate private, local action. Deleting a Social profile clears Social
data and imported cosmetics only; Minecraft accounts, purchases and instances are untouched.

Voice permissions use Android's permission APIs, but the mock never records or transmits audio.
Invitations never connect to a server or terminate the current game. The original Settings drawer
and its native controls/logs/force-close behaviour remain available. Switching from Settings to
Social returns through the two-choice entry screen.

## Build repairs

This starting checkout contained a literal placeholder in `PojavAssetBridge.kt`, an empty
`DiscoverScreen.kt` and hundreds of missing default strings. The two implementation files and
missing strings were restored from existing repository source on `arena/da25bc48-aerix-launcher`;
existing string values were preserved. These are build repairs, not new Social networking code.

## Release artifacts and validation

Android CI builds `fullRelease` and `noruntimeRelease`, uploads APKs/checksums as workflow artifacts,
and has no Release-creation or Google Play step. Production signing is used when the existing
repository secret is configured. Otherwise CI explicitly opts into the debug **test** key for
release variants; those APKs are not debuggable but cannot update a production-key installation.
The build log and workflow summary disclose which signing path was used.

`python3 -m unittest discover -s tests -v` checks source architecture, onboarding order, resource
references, avatar assets, game menu wiring and the no-publication workflow. Compilation and these
checks do not establish pixel-perfect matching, Android permission behaviour, accessibility,
frame pacing, a working multiplayer backend, voice transport, or real-device Minecraft compatibility.
Visual validation at 360dp and tablet landscape still requires an Android device/emulator. The
illustrations and local skin fixtures are approximations, not extracted copies of mockup artwork.
