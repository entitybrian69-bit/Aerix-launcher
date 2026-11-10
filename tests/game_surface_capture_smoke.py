#!/usr/bin/env python3
"""Gate for game-surface capture: controls on screen, controls out of the video.

The recorder used to mirror the display, which makes "hide the controls but let me keep playing" a
promise Android does not allow. This build changes the capture *source*: the encoder's input surface is
handed to the process that owns the game's frames, which produces into it directly. These checks are the
parts that a device cannot tell you about afterwards - whether the fallback is actually wired, whether
the producer is released before the display takes the surface over, and whether the tested arithmetic is
the code that runs rather than a file nobody imports.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app_pojavlauncher" / "src" / "main"
SRC = ROOT / "java" / "net" / "kdt" / "pojavlaunch"
SERVICE = SRC / "recording" / "ScreenRecordingService.kt"
FEED = SRC / "recording" / "GameSurfaceFeed.kt"
CORE = SRC / "recording" / "GameCapture.kt"
CONTROLLER = SRC / "recording" / "RecorderController.kt"
OPTIONS = SRC / "recording" / "RecordingOptions.kt"
GAME_VIEW = SRC / "game" / "GameView.java"
GAME_ACTIVITY = SRC / "game" / "GameActivity.java"
MANIFEST = ROOT / "AndroidManifest.xml"

failures: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def first(haystack: str, needle: str, label: str) -> int:
    index = haystack.find(needle)
    if index < 0:
        failures.append(f"{label}: missing {needle!r}")
        return -1
    return index


for path in (SERVICE, FEED, CORE, CONTROLLER, OPTIONS, GAME_VIEW, GAME_ACTIVITY, MANIFEST):
    if not path.is_file():
        print(f"FAIL: missing {path.relative_to(ROOT.parent.parent)}")
        sys.exit(1)

core = text(CORE)
feed = text(FEED)
service = text(SERVICE)
controller = text(CONTROLLER)
options = text(OPTIONS)
game_view = text(GAME_VIEW)
game_activity = text(GAME_ACTIVITY)
manifest = text(MANIFEST)

# --- the pure core stays pure, because that is what makes it testable with no device
check("import android" not in core, "GameCapture.kt must not import Android, or it cannot be run offline")
check("package net.kdt.pojavlaunch.recording" in core, "GameCapture.kt must live in the recorder's package")
for rule in ("planCopy", "nextDelayMs", "decide", "startupGraceMs", "shouldUseGameSurface", "isBulkCopy"):
    check(re.search(rf"fun {rule}\(", core) is not None, f"GameCapture.{rule} must exist for the feed to use")
check("@JvmStatic" in core, "the core rules need @JvmStatic so the Kotlin-free harness can call them")

# --- the feed must be the thing that produces frames, and must not touch the codec
for api in ("ImageWriter.newInstance(surface, MAX_IMAGES)", "dequeueInputImage", "queueInputImage"):
    check(api in feed, f"GameSurfaceFeed must use {api}; that is how a surface gets produced from memory")
check("newInstance(surface, width, height" not in feed,
      "ImageWriter has no size argument for a Surface producer; the surface's own geometry decides it")
check("acquireNextImage" not in feed,
      "ImageWriter has no acquireNextImage - that is ImageReader; dequeueInputImage throws on back-pressure instead")
check("import android.view.PixelCopy" in feed and "import android.media.PixelCopy" not in feed,
      "PixelCopy is android.view.PixelCopy, not android.media")
check("GameCapture.pixelCopySuccessCode" in feed,
      "the PixelCopy status value must be resolved by the tested rule, never hard-coded")
check("PixelCopy::class.java.fields" in feed,
      "the status constant has changed name and value between releases, so read it off the device")
check("image.planes.size == 1" in feed,
      "planar (YUV) input must be refused, not half-written as if it were packed RGBA")
# ImageWriter has no pixel-format argument for a Surface producer: it is fixed to RGBA_8888, so the
# stride arithmetic has to verify the plane instead of assuming it.
check("plane.pixelStride != GameCapture.BYTES_PER_PIXEL" in feed,
      "the feed must check the plane's pixel stride before trusting 4 bytes per pixel")
check("import android.graphics.PixelFormat" not in feed,
      "there is no ImageWriter overload that takes a format; asking for one is the bug this prevents")
check("PixelCopy.request(view, reuse, listener, copyHandler)" in feed,
      "SurfaceView content is only readable through PixelCopy, not getBitmap")
check("getBitmap(full)" not in feed and "OnPixelCopyFinishedListener" in feed,
      "the SurfaceView path must not call a hidden getBitmap, which does not compile against the SDK")
check("copyHandler = Handler(Looper.getMainLooper())" in feed,
      "the copy must call back on a looper the capture thread is not blocking on")
abandon = first(feed, "private fun abandonStaging()", "abandonStaging")
if abandon >= 0:
    check("scratch = null" in feed[abandon:abandon + 260] and "recycle" not in feed[abandon:abandon + 260],
          "a destination an unfinished copy may still write into has to be dropped, never recycled")
    check("abandonStaging()" in feed[feed.find("private inner class PixelCopySource"):feed.find("private inner class PixelCopySource") + 2000],
          "the timed-out copy path must be the one that abandons the staging bitmap")
check("image.timestamp = System.nanoTime()" in feed,
      "hand-fed frames must carry this process's monotonic timestamp, or the muxer's offsets drift")
for load_bearing in ("GameCapture.planCopy", "GameCapture.isBulkCopy", "GameCapture.decide",
                    "GameCapture.nextDelayMs", "GameCapture.startupGraceMs",
                    "GameCapture.shouldUseGameSurface", "GameCapture.pixelCopySuccessCode"):
    check(load_bearing in feed, f"{load_bearing} must be used by the feed, not just tested in isolation")
check("MediaCodec" not in feed and "MediaMuxer" not in feed and "createVirtualDisplay" not in feed,
      "the game process must never touch MediaCodec/muxer/ projection - that isolation is why :recorder exists")
check("getBitmap" in feed and "SurfaceView" in feed and "TextureView" in feed,
      "both render paths need a readback, and one of them is the case that fails")
check("Build.VERSION.SDK_INT >= 24" in feed,
      "PixelCopy is API 24 and minSdk is 23, so the SurfaceView readback must be guarded")
check("COPY_TIMEOUT_MS" in feed and "await(COPY_TIMEOUT_MS" in feed,
      "a copy that never finishes must be given up on, not waited on forever")
check("MAX_STAGED_PIXELS" in feed, "the staging bitmap must be bounded, or a 4K readback allocates freely")
check("return null" in feed, "a refused readback must return null rather than a half-written frame")

# --- stop ordering: the producer lets go of the surface before anything else can take it
stop = first(feed, "private fun stopFeeding()", "GameSurfaceFeed")
if stop >= 0:
    body = feed[stop:stop + 900]
    check("writer?.close()" in body, "the feed must close its ImageWriter when the recorder asks it to stop")
    check("GameCapture.MSG_STOP_ACK" in feed,
          "the feed must acknowledge MSG_STOP; the recorder waits on that ack before mirroring")
    ack_at = feed.find("report(GameCapture.MSG_STOP_ACK")
    stop_branch = feed.find("GameCapture.MSG_STOP ->")
    check(0 <= stop_branch < ack_at,
          "the ack must be sent from the stop branch, after the writer is released")

# --- the service must branch on the capture source, and never lose a session to the attempt
check("RecordingOptions.CaptureMode.GAME" in service, "the service must have a game-surface path")
display_line = first(service, "createVirtualDisplay", "ScreenRecordingService")
in_attach = service.rfind("private fun attachDisplay", 0, display_line)
check(in_attach >= 0, "the VirtualDisplay must be created inside attachDisplay, so the fallback can reuse it")
check(service.count("createVirtualDisplay") == 1,
      "exactly one place may create the capture display, or a session can end up with two producers")
start_feed = first(service, "private fun startGameFeed", "ScreenRecordingService")
fallback = first(service, "private fun fallbackToMirror", "ScreenRecordingService")
on_feed = first(service, "private fun onFeedMessage", "ScreenRecordingService")
stop_feed = first(service, "private fun stopGameFeed", "ScreenRecordingService")
check(start_feed >= 0 and on_feed >= 0 and fallback >= 0 and stop_feed >= 0,
      "the handshake needs begin, receive, fallback and stop paths in the service")
if start_feed >= 0:
    block = service[start_feed:fallback if fallback > start_feed else start_feed + 2000]
    check("GameCapture.MSG_BEGIN" in block, "the service must send the encoder's surface to the game")
    check("putParcelable(GameCapture.KEY_SURFACE" in block,
          "the surface travels as a parcelled handle so pixels never cross the process boundary")
    check("replyTo = feedMessenger" in block,
          "the game needs the service's messenger back, or it cannot report the first frame")
    check("postDelayed(feedTimeout" in block,
          "a session that never gets a frame must fall back on a timer, not on hope")
    check("GameCapture.startupGraceMs(" in block,
          "the give-up window must come from the tested sizing rule")
if fallback >= 0:
    block = service[fallback:on_feed if on_feed > fallback else fallback + 1600]
    order = first(block, "stopGameFeed()", "fallbackToMirror")
    attach = first(block, "attachDisplay(config)", "fallbackToMirror")
    check(0 <= order < attach,
          "the producer must be released before the display is attached, or the fallback fails too")
    check("GameCapture.MSG_FALLBACK" in service[fallback:fallback + 2200],
          "the game must be told to fade the controls once the recorder is mirroring")
    check("finish(false" in block or "finish(false," in service[fallback:fallback + 1600],
          "if neither source works the session must end with a stated failure, not a silent black file")
if stop_feed >= 0:
    block = service[stop_feed:stop_feed + 900]
    check("latch.await(" in block and "CountDownLatch" in service,
          "the stop must wait for the game's ack, bounded so a dead :game cannot hang the recorder")
    check("TimeUnit.MILLISECONDS" in block, "the wait for the producer must be time-bounded")
check("feedMessenger by lazy { Messenger(Handler(Looper.getMainLooper())" in service,
      "the handshake handler must not run on the worker thread the stop-wait blocks on")
stop_recording = first(service, "private fun stopRecording()", "stopRecording")
if stop_recording >= 0:
    end = service.find("\n    private fun ", stop_recording + 10)
    stop_body = service[stop_recording:end if end > 0 else stop_recording + 1200]
    eos = first(stop_body, "signalEndOfInputStream()", "stopRecording")
    feed_stop = first(stop_body, "stopGameFeed()", "stopRecording")
    check(0 <= feed_stop < eos,
          "the producer must be stopped before end-of-stream, or a frame lands after it and kills the file")
begin_at = first(service, "private fun begin(", "begin")
if begin_at >= 0:
    begin_block = service[begin_at:begin_at + 12000]
    check("config.capture == RecordingOptions.CaptureMode.GAME" in begin_block,
          "begin() must choose the capture source from the saved mode")
    check("startGameFeed(config" in begin_block and "attachDisplay(config)" in begin_block,
          "begin() must either feed from the game or mirror the display, with no third silent path")
finish_at = first(service, "private fun finish(usable", "finish")
if finish_at >= 0:
    finish_block = service[finish_at:finish_at + 1200]
    check("gameFeed = null" in finish_block, "a finished session must drop the game's messenger")
    check("removeCallbacks(feedTimeout)" in finish_block,
          "a finished session must not leave a fallback timer that fires on the next one")

# --- the mode has to be persisted, requested, and read by something
check("enum class CaptureMode" in options and "MIRROR" in options and "GAME" in options,
      "the capture source needs a real mode type")
check("fun saved(value: String?): CaptureMode" in options,
      "the mode must round-trip through the preference store like the audio source does")
check("val capture: CaptureMode" in options, "the session config must carry the chosen source")
check("capture = if (feed == null)" in service,
      "the service must fall back to mirroring when no game messenger was supplied")

# --- the controller: one checkbox drives a real capture decision
check('captureCleanRequested = !withControls' in controller,
      "unchecking 'record with controls' must be what asks for the game's frames")
check("GameCapture.EXTRA_CLEAN_VIDEO" in controller and "GameCapture.EXTRA_FEED" in controller,
      "the request and the reply messenger must both go to the service")
check("ensureGameSurfaceFeed()" in controller, "the feed must be created on demand, not for every session")
check("gameSurfaceFeed?.shutdown()" in controller, "the activity must stop producing when it goes away")
check("!cleanCaptureActive" in controller,
      "the controls must only be faded when the clean capture is not what is being encoded")
check("onCleanCaptureUnavailable" in controller,
      "a fallback must be handled where the recording decision is made")
fallback_fn = first(controller, "private fun onCleanCaptureUnavailable", "RecorderController")
if fallback_fn >= 0:
    body = controller[fallback_fn:fallback_fn + 1400]
    check("Toast.makeText" in body,
          "the player has to be told a fallback happened; a quiet downgrade reads as a broken feature")
    check("captureGaveUp = true" in body and "applyControlsToRecording()" in body,
          "the fallback must latch and fade the controls for the rest of the take")
check("RecordingOptions.CaptureMode.GAME" in controller and "config.copy(" in controller,
      "the chosen mode must reach the pending config the start intent is built from")
caption_start = controller.find("fun describeControls")
caption = controller[caption_start:controller.find("withControls.setOnCheckedChangeListener")]
check("game's own frames" in caption and "tappable" in caption,
      "the setting must describe what the new architecture actually does")
check("GameCapture.readbackIsDirect" in caption,
      "the caption must say which render path this device is on, because that decides the odds")
check("never appear in the recording" in caption, "the promise has to be stated where it is chosen")

# --- the game must expose the surface, and only as a view
check("public View getRecordingSurfaceView()" in game_view and "return mSurface;" in game_view,
      "GameView must hand the recorder its surface view")
check("launcherGLView.getRecordingSurfaceView()" in game_activity,
      "GameActivity must forward the surface view to the recorder")
check("setVisibility" not in game_view[game_view.find("getRecordingSurfaceView"):game_view.find("getRecordingSurfaceView") + 400],
      "reading the game's frames must never change what the player sees")

# --- no new attack surface: the handshake stays inside the app
service_tag = manifest[manifest.find("ScreenRecordingService"):manifest.find("ScreenRecordingService") + 400]
check('android:exported="false"' in service_tag, "the recorder service must stay unexported")
check('android:process=":recorder"' in service_tag.replace("android:process=\":recorder\"", 'android:process=":recorder"')
      or ":recorder" in service_tag, "the recorder must stay isolated in its own process")
check("bindService" not in service and "IBinder" not in feed,
      "the handshake must not add a bindable interface, only a one-way start extra")

if failures:
    for failure in failures:
        print(f"FAIL: {failure}")
    sys.exit(1)

print("game surface capture wiring passed")
