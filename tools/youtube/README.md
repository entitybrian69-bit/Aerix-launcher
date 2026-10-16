# The YouTube bot

Uploads videos to the channel without anyone sitting in Studio: a small JSON file describes the
video, GitHub's own servers do the upload, and YouTube publishes it at the moment the file asks
for. It also sets the thumbnail, adds the video to a playlist, uploads captions, and records the
video id back in the repository so the same video can never be posted twice.

Two ways to post something:

| How | What happens |
| --- | --- |
| Drop a `.json` into `youtube/queue/` and push | The workflow picks it up and uploads it |
| Actions → **YouTube** → *Run workflow* | Same thing, by hand, with a `check` mode that only prints what it would do |

It also wakes up **every 6 hours** on its own, so anything left in the queue goes out even if the
push was forgotten.

---

## What it can and cannot do

**Can:** upload, schedule (`publishAt`), set title/description/tags/category, set the thumbnail,
add to one playlist, upload WebVTT captions, keep a record, retry what failed.

**Cannot:** post Community posts (the API has no endpoint for them), reply to comments (possible,
deliberately not enabled yet), or edit the video itself. Automated comment activity is also the
fastest way to get a channel struck, so anything interactive stays with a human until it is
designed to be safe.

**Quota:** YouTube gives 10,000 units a day. An upload is 1,600 of them, so **about 5–6 uploads a
day** at most — the bot stops at 5 per run on purpose. Thumbnail 50, playlist 50, captions 400.

---

## One-time setup (about 15 minutes, all of it yours)

The bot needs permission to upload to *your* channel. That permission is a token you create, that
lives in GitHub's encrypted secrets, and that I never see.

1. **Create a Google Cloud project** at <https://console.cloud.google.com/projectcreate>
   — name it anything, e.g. `aerix-youtube`.
2. **Enable the API:** APIs & Services → Library → search **YouTube Data API v3** → Enable.
3. **OAuth consent screen:** APIs & Services → OAuth consent screen → **External** → app name
   (e.g. `Aerix YouTube bot`), your email, save. Add your own Google account under **Test users**.
   Then click **Publish app** (status: *In production*). This matters: while the app stays in
   *Testing*, Google throws the token away after 7 days.
4. **Create the client:** Credentials → Create credentials → OAuth client ID → application type
   **Desktop app** → Create → **Download JSON**. Save it somewhere private as `client_secret.json`
   (never commit it).
5. **Get the refresh token**, on your own computer:

   ```bash
   pip install google-auth-oauthlib
   python tools/youtube/get_refresh_token.py --client-secrets /path/to/client_secret.json
   ```

   Sign in as the account that owns the channel and accept. It prints three values. Put them in
   GitHub: **Settings → Secrets and variables → Actions → New repository secret**

   | Secret | Value |
   | --- | --- |
   | `YT_CLIENT_ID` | from the script's output |
   | `YT_CLIENT_SECRET` | from the script's output |
   | `YT_REFRESH_TOKEN` | from the script's output |

6. **Fill in `tools/youtube/config.json`** — channel name, `defaultCategoryId` (20 = Gaming),
   `defaultTags`, `defaultPlaylistId` (the `PL…` part of a playlist URL, if you want new videos
   added to one), and `defaultVisibility` (`scheduled` is a good default).
7. *(Only for big videos)* see **Big videos** below.

Until step 5 is done, the workflow still runs — it just says *"Waiting on setup"* instead of
uploading, so a missing secret never turns into a wall of red.

---

## Posting a video

Create `youtube/queue/<slug>.json`. Everything except `slug`, `title` and `video` has a default.

```json
{
  "slug": "aerix-1-2-devlog",
  "title": "Aerix Launcher 1.2 — the download dialog",
  "description": "What changed, why, and what is next.\n\n0:00 Intro\n0:35 Dependencies\n2:10 Minimize",
  "tags": ["aerix launcher", "minecraft android"],
  "categoryId": "20",
  "visibility": "scheduled",
  "publishAt": "2026-11-02T17:00:00+05:45",
  "video": { "file": "youtube/videos/devlog.mp4" },
  "thumbnail": { "file": "youtube/thumbs/devlog.jpg" },
  "playlistId": "PLxxxxxxxxxxxxxxxx"
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `slug` | yes | lowercase words with dashes; names the record file, so it must be unique |
| `title` | yes | at most 100 characters |
| `description` | no | at most 5000 characters; chapters and links go here |
| `tags` | no | falls back to `defaultTags`; YouTube allows 500 characters of tags |
| `categoryId` | no | defaults to 20 (Gaming) |
| `visibility` | no | `scheduled` (default), `public`, `unlisted`, `private` |
| `publishAt` | with `scheduled` | ISO-8601 with a timezone: `2026-11-02T17:00:00+05:45` or `...Z` |
| `madeForKids` | no | defaults to false |
| `playlistId` | no | one playlist per video |
| `video` | yes | `{"file": "…"}` in the repo or `{"url": "…", "headers": {…}}` |
| `thumbnail` | no | `{"file": "…jpg"}`, 1280×720, under 2 MB |
| `captions` | no | list of `{"file": "…vtt", "language": "en", "name": "English"}` |

`visibility: "scheduled"` uploads the video **private** and lets YouTube flip it public at
`publishAt` — that is how scheduling works through the API. Any other value publishes immediately.

Then push. Or run the workflow by hand with `mode: check` first, which prints the full plan
(including the quota it will spend and any missing file) without touching YouTube.

When it is done, the manifest has moved to `youtube/published/<slug>.json` with the video id in it,
and the bot has committed that record back (with `[skip ci]`, so it does not trigger another build).

---

## Big videos

A phone recording is far too large for a git repository, and this one is public. The pattern:

1. Make a **private** repository, e.g. `yt-staging`. It holds nothing but video files.
2. Upload the finished recording as a **release asset** there (2 GB per file is fine).
3. Fine-grained PAT with *Contents: Read* on that repository only → add it as the secret
   `STAGE_TOKEN`.
4. In the manifest, point at the asset:

```json
"video": {
  "url": "https://github.com/YOU/yt-staging/releases/download/v1/devlog.mp4",
  "headers": { "Authorization": "Bearer ${STAGE_TOKEN}" }
}
```

`${STAGE_TOKEN}` is replaced from the environment at upload time, so the URL never carries the
token itself. (Release assets of a draft/pre-release in a private repo stay private.)

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Run says *"Waiting on setup"* | Secrets missing — do step 5 |
| `invalid_grant` | The refresh token was revoked, or the OAuth app is still in *Testing* and the 7 days ran out. Re-run step 5, and publish the app (step 3) |
| `403 quotaExceeded` | The 10,000 units for the day are spent. The manifest stays queued; it goes out tomorrow |
| `403 forbidden` on the thumbnail | The channel is not verified. Verify it by phone in YouTube Studio |
| Video stays private forever | `publishAt` is in the future — that is the point — or the manifest used `visibility: private` |
| `publishAt … is in the past` | Fix the date, or use `visibility: public` to post now |
| A manifest failed | It is left in `youtube/queue/`; fix what the annotation says and push again |
| Private staging download fails | `STAGE_TOKEN` missing, expired, or without read access to that repository |

Everything the bot prints is also written to the run's summary, and a failure shows up as a red
annotation on the run itself.

---

## Running it locally

```bash
python tools/youtube/yt_bot.py check                    # validate youtube/queue
python tools/youtube/yt_bot.py check --dir youtube/examples
YT_CLIENT_ID=… YT_CLIENT_SECRET=… YT_REFRESH_TOKEN=… \
  python tools/youtube/yt_bot.py upload --max 1         # one real upload, by hand
```

`check` needs nothing installed but Python 3.11+. `upload` needs
`pip install google-api-python-client google-auth google-auth-oauthlib`.
