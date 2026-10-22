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

## Entry screen: the portal

The first Social screen is the chosen concept, built as live Compose (no bitmap, no video):

- **Portal** — a breathing mint portal with two counter-rotating light arcs, a glass interior,
  orbiting highlight, and ground light pools on the surface it stands on.
- **Player** — a blocky silhouette standing at the portal's edge, bobbing and shifting weight;
  on touch its rim light flares and a light gradient reaches its face.
- **Air** — aurora ribbons drift across the top, 26 dust motes rise and twinkle.
- **Pill** — one wide "Continue with Google" with the real Google mark, a spring press (0.96),
  a specular band that glides across the glass, and a mint glow underneath.
- **Touch** — every press spawns ripple rings at the exact finger position, fires two light arcs
  from the pill into the portal, pulls the motes faster, speeds the portal spin and brightens
  the player. Ripples and arcs fade over ~1.4s, so the screen keeps answering after release.
- **Account chip** — top right, shows the address this device last signed in with.
- **Links** — "Create an account" and "Not now" are quiet text links that underline/brighten on
  press; the small print is Privacy · Terms.

Sign-in and character setup are **full-bleed**: the rail and header are hidden while onboarding,
so the portal owns the whole screen and "Not now" is the way back to the launcher.

## Google sign-in (real)

The first Social screen now runs Android's **Credential Manager** flow. Tapping *Sign in with
Google* opens the system account sheet; Google returns an ID token, the launcher reads the
email/name from it, stores the address on the profile and continues to the character step (or
straight to the dashboard if this device already has a profile). Nothing is typed into the
launcher and no password reaches it.

**One-time setup (needs your Google account, cannot be done from the repo):**

1. Google Cloud Console → *APIs & Services* → *OAuth consent screen*: create it (External, add
   your own address as a test user).
2. *Credentials* → *Create credentials* → *OAuth client ID* → **Web application**.
3. Copy the client id (`…apps.googleusercontent.com`).
4. Put it in `gradle.properties` as `aerixGoogleWebClientId=…` (or build with
   `-PaerixGoogleWebClientId=…`). The value is injected as the `aerix_google_web_client_id`
   resource; it is not a secret and is safe to keep in the tree.

No SHA-1 fingerprint and no `google-services.json` are needed for this flow, because the client
is a *Web application* client used as the server client id.

Until the id is present, the auth screen says exactly that ("This build has no Google OAuth
client id…") — it never fakes a sign-in. The token is used as identity only; a hosted backend
would verify its signature before trusting it.

## Real device wiring

Social is no longer a mockup: every control below touches the device.

| Feature | What actually happens |
| --- | --- |
| Image / GIF / file / music | The chip opens the system document picker for its mime type; the file is copied into `filesDir/social_media/<uuid>.<ext>`, its real name, size and mime type are read, and the message keeps that path. |
| Image preview | Thumbnails are decoded from the copied file at ≤720px, off the main thread. |
| Music | `SocialAudioPlayer` plays the picked track with a real `MediaPlayer`; the bubble button becomes Play/Pause and stops when the chat closes. |
| Microphone | `SocialMic` opens an `AudioRecord` and publishes the input amplitude; the settings test card and the party / in-game voice rooms draw that level. Android asks for `RECORD_AUDIO` the first time. |
| Notifications | Every incoming message posts a real notification on the `aerix_social` channel, respecting the in-app switches, the preview setting and the Android 13 `POST_NOTIFICATIONS` permission (asked once when Social opens). |
| Instance share | The picker lists the real `PojavInstanceBridge.instances`; the receiving bubble selects the instance if this device has it and says so plainly when it does not. |
| Persistence | Friends, requests, chats (including attachment paths), posts, blocked list, party and read ids are written to a dedicated DataStore file (`aerix_social_content`); the seed directory only fills a fresh install. |
| Read state | `markSeen()` writes read message ids to disk, so the in-game red badge stays cleared after a restart. |

**Backend seam.** Screens and ViewModels only know `SocialRepository` (interface) and
`SocialContentStore`. `LocalSocialRepository` is the on-device implementation; a hosted backend
drops in by replacing those two objects in `SocialGraph`, with no screen changes.

## Motion

| Transition | Spec |
| --- | --- |
| Launcher → shell | 240ms fade + 0.98 → 1.0 scale |
| Screen → screen | 200ms fade + 1/12 width slide |
| Tabs | 120ms `Crossfade` |
| Chat open | 180ms fade + 12dp slide |
| Dialogs | 160ms fade + 0.96 → 1.0 |

## Tests (269 in the module, 24 new)

- `SocialViewModelTest` — accept/decline, online-offline split, send + reactions, instance
  attachment download → ready, music toggle, Gmail search + request, username-only search,
  like/follow/comment, publishing a post, party create/rename/group call.
- `SocialProfileViewModelTest` — validation, character save, the one-week username lock,
  bio + links (with `@` handles), sign-out keeps the name, delete clears everything.
- `SocialSettingsViewModelTest` — privacy, profile lock + status, chat permissions, microphone
  settings, notifications.
- `GoogleAuthTest` — ID-token claims and base64url decoding, malformed tokens rejected, a
  sign-in stores the Google address and re-signing keeps the chosen name, and failures surface
  a reason.
- `SocialPersistenceTest` — a conversation and its real attachment path survive a reopen, read
  ids are remembered, and the whole snapshot round-trips through JSON.
- `DataStoreSocialStoreTest` — defaults, full profile/settings round-trip including links and the
  username timestamp, and merge-instead-of-reset on a second write.
