# Aerix Social Ecosystem

A second launcher inside Aerix, opened from a Play-dashboard widget. Not a section of the
existing rail: while Social is open the Play dashboard, the Play button and the launcher rail
are not composed at all.

- Branch: `arena/2f1249a3-aerix-launcher`
- Code commit: `d81d8fa1c48ab72ddc716f72033c8e4ad0e4e44a`
- Debug APK commit: `b417dc9dc` (`apk/Aerix-Launcher-debug.apk`, 102,205,734 B,
  sha256 `2a3aed53cda88abbfab93f9817bbf141f946ea6a426c97e7777fb5300b72f602`)
- CI runs: `diag social` 39730501429 (success), `Android CI` 39730501451 (success),
  `Aerix debug (testing)` 39730501582 (success, built the APK above)

## Architecture

```
Appearance / name / skin / cape + privacy + notifications
        │
   SocialStore (interface)
        ├── DataStoreSocialStore   Preferences DataStore, file "aerix_social"   (production)
        └── InMemorySocialStore    JVM tests

Friends / requests / chats / party / invite / connection
        │
   SocialRepository (interface)
        └── MockSocialRepository   in-memory, every mutation is suspend + delay, runs on io

   SocialGraph  ── one repository + one store for the whole process (launcher shell and
                   the in-game overlay observe the same StateFlows)
```

Unidirectional flow: Compose collects `StateFlow<...UiState>`, sends events to a ViewModel,
the ViewModel calls a suspend repository/store function inside `viewModelScope` on
`Dispatchers.IO`, the repository emits new state, the UI recomposes.

| ViewModel | State | Writes |
| --- | --- | --- |
| `SocialShellViewModel` | `isOpen`, `route`, `tab`, back stack | navigation only |
| `SocialProfileViewModel` | `ProfileUiState` | DataStore: sign-in flag, name, skin, cape |
| `SocialDashboardViewModel` | `DashboardUiState` | accept / decline / add friend / party |
| `SocialChatViewModel` | `ChatUiState` | send message, toggle reaction |
| `SocialSettingsViewModel` | `SettingsUiState` | DataStore: 6 privacy + 8 notification fields |
| `InGameSocialViewModel` | `InGameSocialUiState` | shared repository: party, invite, chat |

Models: `AerixUser`, `ChatMessage` (reactions map + `myReaction`), `FriendRequest`,
`PartyState`, `PartyMember`, `ServerInvite`, `PrivacySettings`, `NotificationSettings`,
`SocialPersisted`.

## Sheet → screen map (27 items)

| # | Sheet | Implementation |
| --- | --- | --- |
| 1 | Play dashboard widget | `social/ui/SocialWidgetCard.kt` + `SocialWidgetRow` in `ui/screens/HomeScreen.kt` |
| 2 | Social sign-in (Google) | `SocialAuthScreen` — `social/ui/screens/SocialOnboardingScreens.kt` |
| 3 | Appearance: name / skin / cape | `SocialAppearanceScreen` — same file |
| 4 | Dashboard | `SocialDashboardScreen` — `social/ui/screens/SocialDashboardScreen.kt` |
| 5 | Privacy & presence | `SocialPrivacyScreen` — `social/ui/screens/SocialSettingsScreens.kt` |
| 6 | Cape picker | `SocialCapeScreen` — onboarding file |
| 7 | Skin picker | `SocialSkinScreen` — onboarding file |
| 8 | Safety | `SocialSafetyScreen` — settings file |
| 9 | Notifications | `SocialNotificationsScreen` — settings file |
| 10 | Compact panel: chat | `SocialCompactPanel` `CompactMode.CHAT` |
| 11 | Compact panel: voice | `CompactMode.VOICE` → `VoicePanel` |
| 12 | Compact panel: sign-in required | `CompactMode.AUTH_REQUIRED` |
| 13 | Compact panel: expired invite | `CompactMode.INVITE` + `ServerInviteState.EXPIRED` |
| 14 | Compact panel: reconnecting | `VoicePanel(reconnecting = true)` |
| 15 | Friend request sent | dashboard `addFriendSentTo` dialog |
| 16 | Active invite with Join | `CompactMode.INVITE` + `matchesInstance` copy + `Join` |
| 17 | Voice: pre-permission | `SocialVoiceScreen` (default state) |
| 18 | Voice: microphone denied | `SocialVoiceScreen` (`denied = true` after Deny) |
| 19 | Dashboard error state | `SocialErrorState` (retry) |
| 20 | Dashboard empty state | `SocialEmptyState` (add by name) |
| 21 | Profile | `SocialProfileScreen` |
| 22 | Sign-out dialog | `SocialProfileScreen` + `confirmSignOut` |
| 23 | Delete account (type `DELETE`) | `SocialSafetyScreen` + `confirmDelete` |
| 24 | Block dialog | `SocialCompactPanel` + `blockTarget` |
| 25 | Leave-game dialog | `SocialCompactPanel` + `confirmJoin` |
| 26 | In-game settings overlay | `ui/game/GameMenuScreen.kt`: Social `MenuTile` + `SocialCompactPanel` host |
| 27 | Chat with reactions | `SocialChatScreen` + `ChatBubble` (👍 ❤️ 😂) |

## Design rules honoured

- Fixed palette in `social/ui/SocialUi.kt`: accent `#8B5CF6`, bright `#A78BFA`, background
  `#140F26` → `#1B1436`. `AerixSurface` is never referenced inside `aerix/social`.
- No Aerix logo inside Social (back arrow in the top bar, no brand tile in the rail).
- No Minecraft heads in launcher Social UI: `aerix_social_avatar_1..6.xml` generic vectors.
  MC heads are only used by the in-game overlay / 3D skin preview elsewhere.
- 20/16dp page padding, ≥48dp touch targets, ≥60dp list rows, 22/16/14sp type scale.
- Rail is 84dp with Friends / Chats / Party / Profile + Exit.

## Motion

| Transition | Spec |
| --- | --- |
| Dashboard → shell | 240ms fade in, scale 0.98 → 1.0 (`SocialShell`) |
| Screen → screen | 200ms fade + 1/12 width slide (`AnimatedContent`) |
| Rail selection | immediate |
| Tabs | 120ms `Crossfade` |
| Chat open | 180ms fade + 12dp slide |
| Dialogs | 160ms fade + 0.96 → 1.0 scale |

## Tests

12 new JVM tests, run in CI together with the module's 251 tests:

- `SocialViewModelTest` — accept moves a sender into friends, decline only removes the
  request, send appends + clears the draft, reaction toggles on and off, name validation
  rejects < 3 characters, a valid name persists and signs in, sign-out keeps the name,
  privacy toggle persists, notification + dropdown values persist.
- `DataStoreSocialStoreTest` — defaults match the mockups, profile and settings round-trip
  through a real Preferences DataStore file, a second update merges instead of resetting.

`./gradlew :app_pojavlauncher:compileNoruntimeDebugKotlin :app_pojavlauncher:testNoruntimeDebugUnitTest`
is green in run 39730501429.
