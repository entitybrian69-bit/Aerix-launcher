# In-game Performance Recorder

Open the floating A-logo menu and choose **Screen recorder**. Resolution and FPS
are independent: 240/360/480/720/1080p and 15/24/30/48/60/90/120 FPS. The first
choice is 480p/24 if supported, otherwise the lowest available combination. The
last supported user choice is remembered. An unsupported pair disables Start.
Resolution denotes the short edge; codec alignment may lower it by a few pixels.
Screen aspect/orientation is preserved rather than stretching to 16:9.

The query runs off the UI thread. API 29+ uses Android's hardware/software codec
flags; older Android relies on conservative vendor codec names and excludes known
software implementations. `areSizeAndRateSupported` checks the exact aligned size
and FPS. The chosen hardware encoder is instantiated explicitly: no silent CPU
encoder fallback. Reported support is not a simultaneous Minecraft performance
guarantee. 90/120 FPS cannot invent frames absent from the game or capture source.

Capture requires fresh Android MediaProjection consent for each session. The
non-exported mediaProjection foreground service lives in the same `:game` process
as GameActivity, with a persistent Stop notification. A small red REC/Stop badge
is visible in-game, and the A-logo menu can stop/save. Android 13+ can limit
notification visibility if its notification permission is denied; the in-game
badge and Android foreground-service controls remain available.

MediaProjection feeds a hardware MediaCodec input Surface. Asynchronous codec
callbacks mux encoded H.264 to MP4 on a background HandlerThread. No renderer
hooks, CPU bitmap copies, editing/re-encoding pass or per-frame main-thread work.
The first implementation is **video only**, not microphone/internal-audio capture.

Android 10+ writes directly to a pending Movies/Aerix MediaStore item and publishes
after finalization. Android 6–9 writes to external app-specific Movies/Aerix; that
folder is removed on uninstall. Failed/empty outputs are deleted. Stop drains EOS
with a bounded timeout and releases projection/display/codec/surface/file handles.
Projection revocation stops recording; service restart is NOT_STICKY and never
reuses consent. Leaving the game requests stop to avoid recording other apps.
Android's picker may still allow full-screen capture including notifications: the
settings explain this and recommend app-only capture where offered. Rotation or
resizing during capture uses fixed output dimensions and can be letterboxed.

## Validation / limits

Policy tests cover range/defaults, aspect/alignment, bitrate bounds and hardware
filters. Android CI verifies compilation and the existing regression suite. A
real Android device is required to validate MediaProjection permission/revocation,
actual H.264 encoder behavior, playable MP4 finalization, notification Stop,
app-only capture, background/lock handling and device-specific encoder limits.
The recorder requires at least 64 MB free to start and requests a graceful stop
below 16 MB, checked off the UI thread every two seconds of video. Android 10+
best-effort recovery on the next recording removes only the last tracked pending
URI after checking IS_PENDING; already published recordings are never removed.
No claim of zero lag, guaranteed 120 FPS or measured low-end performance is made.
Test short 240p/15 and 480p/24 clips before raising settings; monitor heat, FPS,
free space and output playback. Kill/power loss can leave a pending/unfinished
recording; it must not be advertised as a successfully saved video.
