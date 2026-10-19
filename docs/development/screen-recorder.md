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
Both **in-game audio and microphone are mandatory**. Android 10+ playback capture
is filtered to this application's UID and GAME/MEDIA/UNKNOWN usages, not other
apps. A separate blocking read thread combines stereo game PCM and mono microphone
PCM at 48 kHz with 50/50 integer mixing, then encodes stereo AAC-LC at 128 kbps.
Video and audio tracks must both be ready before the muxer starts, with a bounded
4 MB startup buffer. Stop drains both streams. Audio initialization/read/encoder
failure aborts the session rather than saving a video-only fallback. AAC may use
a software audio encoder; hardware-only selection applies to the H.264 video.

Microphone permission is requested before screen-capture consent. The foreground
service declares both mediaProjection and microphone types/permissions. Android
6–9 cannot provide this supported internal-audio path, so recording is unavailable
there rather than falsely claiming both sources work. The launcher itself still
supports those Android versions. Android privacy controls, audio capture policies,
OEM restrictions, muted game audio and competing microphone users can silence a
source even after APIs initialize; audible capture needs device confirmation.
Headphones are recommended to avoid the microphone re-recording speaker output.

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

Policy tests cover range/defaults, aspect/alignment, bitrate bounds, hardware
filters and PCM mixing/clipping. UI tests cover selectors, consent/denial, stop
requests, indicators and microphone permission denial. Android CI verifies compilation and the existing regression suite. A
real Android device is required to validate MediaProjection permission/revocation,
actual H.264/AAC behavior, audible game+microphone mixing and A/V sync, playable
MP4 finalization, notification Stop,
app-only capture, background/lock handling and device-specific encoder limits.
The recorder requires at least 64 MB free to start and requests a graceful stop
below 16 MB, checked off the UI thread every two seconds of video. Android 10+
best-effort recovery on the next recording removes only the last tracked pending
URI after checking IS_PENDING; already published recordings are never removed.
No claim of zero lag, guaranteed 120 FPS or measured low-end performance is made.
Test short 240p/15 and 480p/24 clips before raising settings; monitor heat, FPS,
free space and output playback. Kill/power loss can leave a pending/unfinished
recording; it must not be advertised as a successfully saved video.

## Selectable audio sources

The recorder now offers **Game audio**, **Microphone**, and **Game audio +
microphone**. The choice is saved separately from resolution/FPS and is passed
through the consent flow to the service. Both remains the default for existing
users. Game-only creates no microphone AudioRecord and requests no microphone
foreground-service type; microphone-only creates no playback-capture AudioRecord.
Android's RECORD_AUDIO runtime permission is still required in every mode,
including playback capture. Single-source output retains full volume; Both uses
the existing 50/50 mix. No automatic switch to another source on failure.

The game process explicitly permits playback capture before Minecraft initializes
its audio backend. Own-UID matching remains in place so unrelated apps are not
recorded. This does not override a native backend's per-player capture policy,
Android/OEM restrictions, or muted Minecraft audio. A user report of silent game
audio cannot be confirmed fixed without an on-device test: compare Game audio
with Both while audible Minecraft sound plays, preferably with headphones.

The bundled ALSoft backend (`alsoft-android-aar:f369161d5f`, OpenAL Soft
`dc7d7054a5b4f3bec1dc23a42fd616a0847af948`) requests Oboe Game/LowLatency output.
The launcher's pre-existing `alsoftForceOpenSL` switch is explicitly documented
as a capture workaround on some devices. Recorder help now points to Settings →
Game → Forcefully use OpenSL for audio, followed by restarting Minecraft, when
Game-only remains silent. The recorder does not silently change the backend,
record other apps, or substitute microphone sound for internal game audio.
