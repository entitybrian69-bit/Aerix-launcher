# Aerix Social Ecosystem

A second launcher inside Aerix, opened from a Play-dashboard widget. Not a section of the
existing rail: while Social is open the Play dashboard, the Play button and the launcher rail
are not composed at all. **Social now uses the launcher's own glass theme** — same wallpaper,
same `AerixSurface` tokens, same accent — instead of a separate purple palette.

- Branch: `arena/2f1249a3-aerix-launcher`
- Code commit: `0c015fe` (look-and-feel pass), earlier: `d1fbb547`
- Debug APK: `apk/Aerix-Launcher-debug.apk` — 102,392,169 B,
  sha256 `f8fcceaea93e020f322ebf1524c77393a4333928c3347eca7bd0c657c5a02706`
  (`apk/DEBUG_BUILD.txt`, run `39745699736`, package `net.kdt.pojavlaunch.debug`)
- CI: `diag social` **39745699604** (compile + 265 unit tests, success),
  `Android CI` 39745699649 (success), `Aerix debug (testing)` **39745699736** (success)

## First-run flow

1. **Google account** (`SocialAuthScreen`): *Sign in with Google* (account chooser → straight to
   the dashboard, stored profile kept) or *Create a new account*.
2. **Character screen** (`SocialCharacterScreen`): display name, username, skin, cape and an
   optional theme. Nothing here starts the username cooldown — that starts on the first rename.
3. **Dashboard** (`SocialDashboardScreen`): empty state reads "You have no friends yet" with an
   **Add friends** button; once friends exist it lists who is **online** and **offline** and each
   row has **Message**.

## Rail (84dp)

`Social` (dashboard) · `Add friend` · `Activity` · `Customise` · `Profile`.

No chat entry (chat lives on the dashboard) and **no exit entry** — the top-left arrow leaves
Social, as in the launcher. The gear in the top-right opens Settings.

## Screens

| Screen | File |
| --- | --- |
| Google sign-in / sign-up, account chooser | `ui/screens/SocialOnboardingScreens.kt` |
| Character setup, skin picker, cape picker | `ui/screens/SocialOnboardingScreens.kt` |
| Dashboard (friends online/offline, chats, party) | `ui/screens/SocialDashboardScreen.kt` |
| Chat: images, GIFs, files of any size, links, instances, music | `ui/screens/SocialDashboardScreen.kt` |
| Add a friend (username **or** Gmail search, Add button) | `ui/screens/SocialAddFriendScreen.kt` |
| Activity: posts, videos, text, 24h stories, likes/comments/follow | `ui/screens/SocialActivityScreen.kt` |
| Profile (bio, counters, platform links) and Edit profile | `ui/screens/SocialProfileScreens.kt` |
| Customise: the same 25 launcher wallpapers + themes | `ui/screens/SocialThemeScreen.kt` |
| Settings hub, Privacy, Microphone, Chats, Account, Notifications, Safety | `ui/screens/SocialSettingsScreens.kt` |
| In-game compact panel (chat / voice / invite / sign-in required) | `ui/ingame/SocialCompactPanel.kt` |
| Play-dashboard widget | `ui/SocialWidgetCard.kt` |

## Feature notes

- **Add friend**: search by Aerix username or by the Gmail on the account (`SocialAddFriendViewModel`
  → `repository.search(query, byEmail)`). Results exclude existing friends and show **Add**.
  Only after the other side accepts can chat, calls, files and parties happen.
- **Attachments** (`AttachmentKind`): IMAGE, GIF, FILE (any size), LINK, INSTANCE, MUSIC.
  A shared instance renders a **Download and select** button: `installSharedInstance` moves the
  share from `IDLE` → `DOWNLOADING` → `READY`, i.e. it downloads and auto-selects the instance.
  A music message has a **Play in chat** toggle so it keeps playing while you type or talk.
- **Party**: any number of friends (minimum three is explained in the UI), renameable, with a
  logo and a **group call**; everything sendable in a chat is sendable in the party.
- **Activity**: photos, videos, text posts and stories. A story puts a **crimson ring** on the
  avatar and expires after 24 hours. Tapping a profile shows the picture, the
  likes/followers/following/posts counters, and every platform link (YouTube, Instagram,
  Discord, X, TikTok, Twitch, website) with its own icon that opens the destination.
- **Profile**: account logo (6 avatars), display name, bio and links.
- **Username**: one change per week (`SocialPersisted.USERNAME_COOLDOWN_MS`), with the remaining
  time shown in Settings → Account.

## Settings (gear, top right)

| Section | Contents |
| --- | --- |
| Privacy | status text, profile lock, online status, activity sharing, server details, who can send requests / messages / party invites |
| Microphone (mic icon in the middle) | join muted, push-to-talk, voice activity, noise suppression, echo cancellation, input and output volume |
| Chats | who may message you: strangers / friends / close friends / party; media auto-download, read receipts, keep media |
| Account settings | username (weekly lock), disable account, delete account (typed `DELETE`), blocked accounts and reporting |

## Architecture

```
SocialStore (interface) ── DataStoreSocialStore (Preferences "aerix_social")
                        └─ InMemorySocialStore (JVM tests)

SocialRepository (interface) ── MockSocialRepository (in-memory, suspend + delay on an io dispatcher)

SocialGraph: one store + one repository for the whole process (shell and in-game overlay)
```

Compose collects `StateFlow<...UiState>`, sends events to a ViewModel, the ViewModel calls a
suspend repository/store function in `viewModelScope` on `Dispatchers.IO`, the repository emits
new state, the UI recomposes.

ViewModels: `SocialShellViewModel`, `SocialProfileViewModel`, `SocialDashboardViewModel`,
`SocialAddFriendViewModel`, `SocialChatViewModel`, `SocialActivityViewModel`,
`SocialSettingsViewModel`, `InGameSocialViewModel`.

## Look and feel (latest pass)

- **Real artwork**: Customise decodes the bundled `assets/wallpapers/*.jpg` thumbnails (the same
  files the Play dashboard uses), with the theme chip and the "applied" tick on the card.
- **Brighter**: the tint over the wallpaper dropped from 0x4D–0x8C to 0x14–0x33, and panels are
  72–82% opaque glass, so the wallpaper reads through everywhere.
- **Compact top bar**: 20sp title on the left; the exit arrow (dashboard only) and the settings
  gear sit together in the top-right at 36dp. No tall header pushing content down.
- **Motion**: buttons, chips, icon buttons and rail tiles spring to 0.94–0.96 on press; rail
  selection animates colour and lifts the icon; the dashboard tab underline grows and recolours;
  story rings breathe; wallpaper cards lift slightly when selected.

## In game

The pause-menu **Social** tile shows a red badge with the unread count. Opening the overlay
clears it (`markRead()`), so the badge only reflects messages you have not looked at yet. Inside
the overlay you can switch between friends, message them, call them and run the party without
leaving the game.

## Motion

| Transition | Spec |
| --- | --- |
| Launcher → shell | 240ms fade + 0.98 → 1.0 scale |
| Screen → screen | 200ms fade + 1/12 width slide |
| Tabs | 120ms `Crossfade` |
| Chat open | 180ms fade + 12dp slide |
| Dialogs | 160ms fade + 0.96 → 1.0 |

## Tests (265 in the module, 20 new)

- `SocialViewModelTest` — accept/decline, online-offline split, send + reactions, instance
  attachment download → ready, music toggle, Gmail search + request, username-only search,
  like/follow/comment, publishing a post, party create/rename/group call.
- `SocialProfileViewModelTest` — validation, character save, the one-week username lock,
  bio + links (with `@` handles), sign-out keeps the name, delete clears everything.
- `SocialSettingsViewModelTest` — privacy, profile lock + status, chat permissions, microphone
  settings, notifications.
- `DataStoreSocialStoreTest` — defaults, full profile/settings round-trip including links and the
  username timestamp, and merge-instead-of-reset on a second write.
